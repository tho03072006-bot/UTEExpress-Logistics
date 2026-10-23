package vn.edu.hcmute.uteexpress.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.uteexpress.dto.OrderCreateRequest;
import vn.edu.hcmute.uteexpress.entity.AppUser;
import vn.edu.hcmute.uteexpress.entity.DraftOrder;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.repository.AppUserRepository;
import vn.edu.hcmute.uteexpress.repository.DraftOrderRepository;
import vn.edu.hcmute.uteexpress.service.OrderDraftService;
import vn.edu.hcmute.uteexpress.service.PromoCodeService;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class OrderDraftServiceImpl implements OrderDraftService {

    /** Giới hạn số đơn nháp trong giỏ, tránh người dùng gom quá nhiều rồi xác nhận một lần gây treo. */
    private static final int MAX_DRAFTS_PER_USER = 30;

    private final DraftOrderRepository draftOrderRepository;
    private final AppUserRepository appUserRepository;
    private final OrderCreationWriter creationWriter;
    private final jakarta.persistence.EntityManager em;
    private final PromoCodeService promoCodeService;
    private final jakarta.validation.Validator validator;
    private final vn.edu.hcmute.uteexpress.service.OrderSubmissionGuard submissions;
    private final vn.edu.hcmute.uteexpress.service.routing.OrderRoutingService routing;
    private final vn.edu.hcmute.uteexpress.repository.routing.DraftOrderAreaRepository draftAreas;

    public OrderDraftServiceImpl(DraftOrderRepository draftOrderRepository,
                                 AppUserRepository appUserRepository,
                                 OrderCreationWriter creationWriter,
                                 PromoCodeService promoCodeService, jakarta.validation.Validator validator,
                                 vn.edu.hcmute.uteexpress.service.OrderSubmissionGuard submissions,
                                 vn.edu.hcmute.uteexpress.service.routing.OrderRoutingService routing,
                                 vn.edu.hcmute.uteexpress.repository.routing.DraftOrderAreaRepository draftAreas, jakarta.persistence.EntityManager em) {
        this.draftOrderRepository = draftOrderRepository;
        this.appUserRepository = appUserRepository;
        this.creationWriter = creationWriter; this.em = em;
        this.promoCodeService = promoCodeService;
        this.validator=validator;
        this.submissions=submissions;
        this.routing=routing; this.draftAreas=draftAreas;
    }

    @Override
    @Transactional(readOnly = true)
    public List<DraftOrder> findDrafts(String username) {
        return draftOrderRepository.findByUserOrderByCreatedAtDesc(requireUser(username));
    }

    @Override
    @Transactional(readOnly = true)
    public long countDrafts(String username) {
        return draftOrderRepository.countByUser(requireUser(username));
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal totalEstimatedFee(String username) {
        return findDrafts(username).stream()
                .map(DraftOrder::getEstimatedFee)
                .filter(fee -> fee != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    public DraftOrder addDraft(OrderCreateRequest request, String username) {
        if (request == null || request.getPaymentMethod() == null || !request.isPaymentMethodSupported()) {
            throw new IllegalStateException("Chỉ hỗ trợ COD hoặc MoMo mô phỏng. Vui lòng chọn lại phương thức thanh toán.");
        }
        var errors=validator.validate(request);
        if(!errors.isEmpty()) throw new IllegalStateException(errors.iterator().next().getMessage());
        // Giỏ đơn chưa lưu hình thức gửi; đơn tự mang ra chi nhánh phải gửi ngay để giữ đúng mức giảm.
        if (request.getPickupMode() == vn.edu.hcmute.uteexpress.entity.routing.OrderRoute.PickupMode.DROP_OFF)
            throw new IllegalStateException("Giỏ đơn chỉ nhận đơn Shipper đến lấy. Đơn tự mang ra chi nhánh hãy bấm Gửi đơn ngay.");
        AppUser owner = requireLockedUser(username);
        var submission = submissions.reserve(request, owner, vn.edu.hcmute.uteexpress.entity.OrderFormSubmission.Purpose.DRAFT);
        if (submission.getResourceId() != null) {
            return draftOrderRepository.findByIdAndUser(submission.getResourceId(), owner)
                .orElseThrow(vn.edu.hcmute.uteexpress.service.OrderSubmissionGuard::reused);
        }

        if (draftOrderRepository.countByUser(owner) >= MAX_DRAFTS_PER_USER) {
            throw new IllegalStateException("Giỏ đơn chỉ chứa tối đa " + MAX_DRAFTS_PER_USER
                    + " đơn. Vui lòng xác nhận bớt trước khi thêm đơn mới.");
        }

        DraftOrder draft = new DraftOrder();
        draft.setUser(owner);
        draft.setSenderName(request.getSenderName().trim());
        draft.setSenderAddress(request.getSenderAddress().trim());
        draft.setReceiverName(request.getReceiverName().trim());
        draft.setReceiverPhone(request.getReceiverPhone().trim());
        draft.setReceiverAddress(request.getReceiverAddress().trim());
        draft.setWeightKg(request.getWeightKg());
        draft.setServiceType(request.getServiceType());
        draft.setPaymentMethod(request.getPaymentMethod());
        draft.setPromoCode(request.getPromoCode());
        var quote = routing.quote(request);
        BigDecimal fee = quote.total();
        draft.setEstimatedFee(fee);

        // Kiem tra ma ngay luc them vao gio de bao som, thay vi de nguoi dung gom xong
        // ca gio roi bam xac nhan moi biet ma sai. Luc xac nhan van kiem tra lai lan nua.
        if (request.getPromoCode() != null && !request.getPromoCode().isBlank()) {
            promoCodeService.requireUsableCode(request.getPromoCode(), fee);
        }

        DraftOrder saved = draftOrderRepository.save(draft);
        draftAreas.save(new vn.edu.hcmute.uteexpress.entity.routing.DraftOrderArea(saved, quote.pickupArea(), quote.deliveryArea()));
        submission.bindResource(saved.getId());
        return saved;
    }

    @Override
    public void removeDraft(Long draftId, String username) {
        draftOrderRepository.delete(requireOwnedDraft(draftId, username));
    }

    @Override
    public void clearDrafts(String username) {
        draftOrderRepository.deleteByUser(requireLockedUser(username));
    }

    @Override
    public Order confirmDraft(Long draftId, String username) {
        DraftOrder draft = requireOwnedDraft(draftId, username);
        Order order = creationWriter.createRouted(toCreateRequest(draft), username);
        draftOrderRepository.delete(draft);
        return order;
    }

    @Override
    public List<Order> confirmAllDrafts(String username) {
        AppUser owner = requireLockedUser(username);
        List<DraftOrder> drafts = draftOrderRepository.findByUserOrderByCreatedAtDesc(owner);
        if (drafts.isEmpty()) {
            throw new IllegalStateException("Giỏ đơn đang trống, chưa có đơn nào để xác nhận.");
        }

        List<Order> created = new ArrayList<>();
        for (DraftOrder draft : drafts) {
            created.add(creationWriter.createRouted(toCreateRequest(draft), username));
        }
        // Ca vong lap nam trong cung mot transaction: neu mot don loi thi khong don nao duoc tao,
        // gio van con nguyen de nguoi dung sua lai roi xac nhan lai.
        draftOrderRepository.deleteAll(drafts);
        return created;
    }

    // ----- Phần dùng chung trong service -----

    private AppUser requireUser(String username) {
        return appUserRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy người dùng: " + username));
    }

    private DraftOrder requireOwnedDraft(Long draftId, String username) {
        if(draftId==null || draftId<=0) throw new IllegalStateException("ID đơn nháp không hợp lệ.");
        return draftOrderRepository.findByIdAndUser(draftId, requireLockedUser(username))
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy đơn nháp này trong giỏ đơn của bạn"));
    }

    private AppUser requireLockedUser(String username) {
        var owner=appUserRepository.findLockedByUsername(username).orElseThrow(()->new IllegalStateException("Không tìm thấy tài khoản."));
        em.refresh(owner, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if(!owner.isEnabled() || owner.isLockedByAdmin() || owner.getRole()!=AppUser.Role.USER)
            throw new org.springframework.security.access.AccessDeniedException("Chỉ người gửi đang hoạt động được thay đổi giỏ đơn.");
        return owner;
    }

    /**
     * Đổ đơn nháp về lại DTO để tái sử dụng nguyên OrderService.createOrder - nhờ vậy
     * đơn xác nhận từ giỏ và đơn tạo trực tiếp đi qua đúng một luồng nghiệp vụ
     * (sinh mã vận đơn, tính cước, đặt trạng thái PENDING_PICKUP).
     */
    private OrderCreateRequest toCreateRequest(DraftOrder draft) {
        var request = draftFields(draft);
        var area = draftAreas.findByDraftOrder_Id(draft.getId())
                .orElseThrow(() -> new IllegalStateException("Đơn nháp cũ thiếu khu vực giao nhận. Hãy chọn lại khu vực trước khi xác nhận."));
        request.setPickupProvinceCode(area.getPickupProvinceCode()); request.setPickupAreaCode(area.getPickupArea().getAreaCode());
        request.setDeliveryProvinceCode(area.getDeliveryProvinceCode()); request.setDeliveryAreaCode(area.getDeliveryArea().getAreaCode());
        return request;
    }
    private OrderCreateRequest draftFields(DraftOrder draft) {
        if (draft.getPaymentMethod() == vn.edu.hcmute.uteexpress.entity.OrderPayment.PaymentMethod.VNPAY) {
            throw new IllegalStateException("Đơn nháp VNPay cũ không còn được hỗ trợ. Hãy tạo lại đơn nháp và chọn COD hoặc MoMo.");
        }
        OrderCreateRequest request = new OrderCreateRequest();
        request.setSubmissionToken(java.util.UUID.nameUUIDFromBytes(("UTEExpress:draft:"+draft.getUser().getId()+":"+draft.getId())
            .getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString());
        request.setSenderName(draft.getSenderName());
        request.setSenderAddress(draft.getSenderAddress());
        request.setReceiverName(draft.getReceiverName());
        request.setReceiverPhone(draft.getReceiverPhone());
        request.setReceiverAddress(draft.getReceiverAddress());
        request.setWeightKg(draft.getWeightKg());
        request.setServiceType(draft.getServiceType());
        request.setPaymentMethod(draft.getPaymentMethod());
        request.setPromoCode(draft.getPromoCode());
        return request;
    }
    @Override @Transactional(readOnly = true)
    public vn.edu.hcmute.uteexpress.dto.DraftAreaRequest prepareAreaForm(Long id, String username) {
        var draft = draftOrderRepository.findByIdAndUser(id, requireUser(username))
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy đơn nháp của bạn."));
        var form = new vn.edu.hcmute.uteexpress.dto.DraftAreaRequest(); form.setExpectedUpdatedAt(draft.getUpdatedAt());
        draftAreas.findByDraftOrder_Id(id).ifPresent(a -> {
            form.setPickupProvinceCode(a.getPickupProvinceCode()); form.setPickupAreaCode(a.getPickupArea().getAreaCode());
            form.setDeliveryProvinceCode(a.getDeliveryProvinceCode()); form.setDeliveryAreaCode(a.getDeliveryArea().getAreaCode());
        });
        return form;
    }
    @Override public void selectAreas(Long id, vn.edu.hcmute.uteexpress.dto.DraftAreaRequest form, String username) {
        if (form == null) throw new IllegalStateException("Thiếu thông tin khu vực.");
        var errors = validator.validate(form);
        if (!errors.isEmpty()) throw new IllegalStateException(errors.iterator().next().getMessage());
        var draft = requireOwnedDraft(id, username);
        if (!java.util.Objects.equals(draft.getUpdatedAt(), form.getExpectedUpdatedAt()))
            throw new IllegalStateException("Đơn nháp đã thay đổi. Hãy tải lại trước khi chọn khu vực.");
        var request = draftFields(draft);
        request.setPickupProvinceCode(form.getPickupProvinceCode()); request.setPickupAreaCode(form.getPickupAreaCode());
        request.setDeliveryProvinceCode(form.getDeliveryProvinceCode()); request.setDeliveryAreaCode(form.getDeliveryAreaCode());
        var quote = routing.quote(request);
        var record = draftAreas.findByDraftOrder_Id(id).orElseGet(() -> new vn.edu.hcmute.uteexpress.entity.routing.DraftOrderArea(draft, quote.pickupArea(), quote.deliveryArea()));
        record.configure(quote.pickupArea(), quote.deliveryArea()); draftAreas.save(record);
        draft.setEstimatedFee(quote.total()); draft.setUpdatedAt(java.time.LocalDateTime.now());
    }
}
