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
import vn.edu.hcmute.uteexpress.service.OrderService;
import vn.edu.hcmute.uteexpress.service.PromoCodeService;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import vn.edu.hcmute.uteexpress.validation.OrderRequestRules;

@Service
@Transactional
public class OrderDraftServiceImpl implements OrderDraftService {

    /** Giới hạn số đơn nháp trong giỏ, tránh người dùng gom quá nhiều rồi xác nhận một lần gây treo. */
    private static final int MAX_DRAFTS_PER_USER = 30;

    private final DraftOrderRepository draftOrderRepository;
    private final AppUserRepository appUserRepository;
    private final OrderService orderService;
    private final PromoCodeService promoCodeService;

    public OrderDraftServiceImpl(DraftOrderRepository draftOrderRepository,
                                 AppUserRepository appUserRepository,
                                 OrderService orderService,
                                 PromoCodeService promoCodeService) {
        this.draftOrderRepository = draftOrderRepository;
        this.appUserRepository = appUserRepository;
        this.orderService = orderService;
        this.promoCodeService = promoCodeService;
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
        OrderRequestRules.validate(request);
        AppUser owner = requireUserForUpdate(username);

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
        BigDecimal fee = orderService.estimateFee(request.getServiceType(), request.getWeightKg());
        draft.setEstimatedFee(fee);

        // Kiem tra ma ngay luc them vao gio de bao som, thay vi de nguoi dung gom xong
        // ca gio roi bam xac nhan moi biet ma sai. Luc xac nhan van kiem tra lai lan nua.
        if (request.getPromoCode() != null && !request.getPromoCode().isBlank()) {
            promoCodeService.requireUsableCode(request.getPromoCode(), fee);
        }

        return draftOrderRepository.save(draft);
    }

    @Override
    public void removeDraft(Long draftId, String username) {
        draftOrderRepository.delete(requireOwnedDraft(draftId, username));
    }

    @Override
    public void clearDrafts(String username) {
        AppUser owner = requireUserForUpdate(username);
        draftOrderRepository.deleteAll(draftOrderRepository.findByUserOrderByCreatedAtDesc(owner));
    }

    @Override
    public Order confirmDraft(Long draftId, String username) {
        DraftOrder draft = requireOwnedDraft(draftId, username);
        Order order = orderService.createOrder(toCreateRequest(draft), username);
        draftOrderRepository.delete(draft);
        return order;
    }

    @Override
    public List<Order> confirmAllDrafts(String username) {
        AppUser owner = requireUserForUpdate(username);
        List<DraftOrder> drafts = draftOrderRepository.findByUserOrderByCreatedAtDesc(owner);
        if (drafts.isEmpty()) {
            throw new IllegalStateException("Giỏ đơn đang trống, chưa có đơn nào để xác nhận.");
        }

        drafts.forEach(draft -> OrderRequestRules.validate(toCreateRequest(draft)));
        // Different owners may confirm batches sharing several codes; lock codes in one order.
        drafts.stream().filter(draft -> draft.getPromoCode() != null && !draft.getPromoCode().isBlank())
                .sorted(Comparator.comparing(draft -> draft.getPromoCode().strip().toUpperCase(java.util.Locale.ROOT)))
                .forEach(draft -> promoCodeService.requireUsableCode(draft.getPromoCode(),
                        orderService.estimateFee(draft.getServiceType(), draft.getWeightKg())));

        List<Order> created = new ArrayList<>();
        for (DraftOrder draft : drafts) {
            created.add(orderService.createOrder(toCreateRequest(draft), username));
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
        return draftOrderRepository.findByIdAndUser(draftId, requireUserForUpdate(username))
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy đơn nháp này trong giỏ đơn của bạn"));
    }

    /** Serialize all cart writers for an owner before checking quota or consuming drafts. */
    private AppUser requireUserForUpdate(String username) {
        return appUserRepository.findByUsernameForUpdate(username)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy người dùng: " + username));
    }

    /**
     * Đổ đơn nháp về lại DTO để tái sử dụng nguyên OrderService.createOrder - nhờ vậy
     * đơn xác nhận từ giỏ và đơn tạo trực tiếp đi qua đúng một luồng nghiệp vụ
     * (sinh mã vận đơn, tính cước, đặt trạng thái PENDING_PICKUP).
     */
    private OrderCreateRequest toCreateRequest(DraftOrder draft) {
        OrderCreateRequest request = new OrderCreateRequest();
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
}
