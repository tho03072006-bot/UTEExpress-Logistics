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
public class OrderDraftServiceImpl implements OrderDraftService {

    /** Giới hạn số đơn nháp trong giỏ, tránh người dùng gom quá nhiều rồi xác nhận một lần gây treo. */
    private static final int MAX_DRAFTS_PER_USER = 30;

    private final DraftOrderRepository draftOrderRepository;
    private final AppUserRepository appUserRepository;
    private final OrderCreationWriter creationWriter;
    private final org.springframework.transaction.support.TransactionTemplate writes;
    private final jakarta.persistence.EntityManager em;
    private final PromoCodeService promoCodeService;
    private final jakarta.validation.Validator validator;
    private final vn.edu.hcmute.uteexpress.service.OrderSubmissionGuard submissions;
    private final vn.edu.hcmute.uteexpress.service.OrderPricingService pricing;

    public OrderDraftServiceImpl(DraftOrderRepository draftOrderRepository,
                                 AppUserRepository appUserRepository,
                                 OrderCreationWriter creationWriter,
                                 PromoCodeService promoCodeService, jakarta.validation.Validator validator,
                                 vn.edu.hcmute.uteexpress.service.OrderSubmissionGuard submissions,
                                 vn.edu.hcmute.uteexpress.service.OrderPricingService pricing,
                                 org.springframework.transaction.PlatformTransactionManager transactions,
                                 jakarta.persistence.EntityManager em) {
        this.draftOrderRepository = draftOrderRepository;
        this.appUserRepository = appUserRepository;
        this.creationWriter = creationWriter;
        this.writes = new org.springframework.transaction.support.TransactionTemplate(transactions);
        this.em = em;
        this.promoCodeService = promoCodeService;
        this.validator=validator;
        this.submissions=submissions;
        this.pricing=pricing;
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
        DraftOrder existing = writes.execute(status -> {
            var owner = requireLockedUser(username);
            var retry = existingDraft(request, owner);
            if (retry == null) requireCartSpace(owner);
            return retry;
        });
        if (existing != null) return existing;
        var route = pricing.resolveRoute(request.getSenderAddress(), request.getReceiverAddress());
        return writes.execute(status -> addResolvedDraft(request, username, route));
    }

    private DraftOrder existingDraft(OrderCreateRequest request, AppUser owner) {
        var existing = submissions.findExisting(request, owner, vn.edu.hcmute.uteexpress.entity.OrderFormSubmission.Purpose.DRAFT);
        if (existing.isEmpty()) return null;
        return draftOrderRepository.findByIdAndUser(existing.get().getResourceId(), owner)
                .orElseThrow(vn.edu.hcmute.uteexpress.service.OrderSubmissionGuard::reused);
    }

    private DraftOrder addResolvedDraft(OrderCreateRequest request, String username,
            vn.edu.hcmute.uteexpress.service.OrderPricingService.ResolvedRoute route) {
        AppUser owner = requireLockedUser(username);
        var submission = submissions.reserve(request, owner, vn.edu.hcmute.uteexpress.entity.OrderFormSubmission.Purpose.DRAFT);
        if (submission.getResourceId() != null) {
            return draftOrderRepository.findByIdAndUser(submission.getResourceId(), owner)
                .orElseThrow(vn.edu.hcmute.uteexpress.service.OrderSubmissionGuard::reused);
        }

        requireCartSpace(owner);

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
        // Giỏ đơn báo đúng cước sẽ thu (có phụ phí khoảng cách); lúc xác nhận tạo đơn vẫn tính lại ở OrderService.
        BigDecimal fee = pricing.priceResolved(route, request.getSenderAddress(), request.getReceiverAddress(),
                request.getServiceType(), request.getWeightKg()).total();
        draft.setEstimatedFee(fee);

        // Kiem tra ma ngay luc them vao gio de bao som, thay vi de nguoi dung gom xong
        // ca gio roi bam xac nhan moi biet ma sai. Luc xac nhan van kiem tra lai lan nua.
        if (request.getPromoCode() != null && !request.getPromoCode().isBlank()) {
            promoCodeService.requireUsableCode(request.getPromoCode(), fee);
        }

        DraftOrder saved = draftOrderRepository.save(draft);
        submission.bindResource(saved.getId());
        return saved;
    }

    @Override
    @Transactional
    public void removeDraft(Long draftId, String username) {
        draftOrderRepository.delete(requireOwnedDraft(draftId, username));
    }

    @Override
    @Transactional
    public void clearDrafts(String username) {
        draftOrderRepository.deleteByUser(requireLockedUser(username));
    }

    @Override
    public Order confirmDraft(Long draftId, String username) {
        var snapshot = writes.execute(status -> snapshot(requireOwnedDraft(draftId, username)));
        var route = pricing.resolveRoute(snapshot.request().getSenderAddress(), snapshot.request().getReceiverAddress());
        return writes.execute(status -> {
            var draft = requireOwnedDraft(draftId, username);
            requireUnchanged(snapshot, draft);
            var order = creationWriter.createResolved(snapshot.request(), username, route);
            draftOrderRepository.delete(draft);
            return order;
        });
    }

    @Override
    public List<Order> confirmAllDrafts(String username) {
        List<DraftSnapshot> snapshots = writes.execute(status -> {
            var owner = requireLockedUser(username);
            var drafts = draftOrderRepository.findByUserOrderByCreatedAtDesc(owner);
            if (drafts.isEmpty()) throw new IllegalStateException("Giỏ đơn đang trống, chưa có đơn nào để xác nhận.");
            if (drafts.size() > MAX_DRAFTS_PER_USER) throw new IllegalStateException("Giỏ đơn vượt giới hạn 30 đơn.");
            return drafts.stream().map(this::snapshot).toList();
        });
        // Tra toàn bộ địa chỉ TRƯỚC transaction ghi, không giữ khóa trong lúc đợi mạng.
        var routes = snapshots.stream().map(s -> pricing.resolveRoute(s.request().getSenderAddress(), s.request().getReceiverAddress())).toList();
        return writes.execute(status -> {
            var owner = requireLockedUser(username);
            var current = draftOrderRepository.findByUserOrderByCreatedAtDesc(owner);
            if (!current.stream().map(DraftOrder::getId).sorted().toList().equals(snapshots.stream().map(DraftSnapshot::id).sorted().toList()))
                throw cartChanged();
            var byId = current.stream().collect(java.util.stream.Collectors.toMap(DraftOrder::getId, d -> d));
            List<Order> created = new ArrayList<>();
            for (int i = 0; i < snapshots.size(); i++) {
                var snapshot = snapshots.get(i);
                requireUnchanged(snapshot, byId.get(snapshot.id()));
                created.add(creationWriter.createResolved(snapshot.request(), username, routes.get(i)));
            }
            // Cước/khuyến mãi được kiểm tra lại trong cùng transaction: một đơn lỗi thì rollback cả giỏ.
            draftOrderRepository.deleteAll(current);
            return created;
        });
    }

    private record DraftSnapshot(Long id, OrderCreateRequest request, String hash) { }
    private DraftSnapshot snapshot(DraftOrder draft) {
        em.refresh(draft);
        var request = toCreateRequest(draft);
        var errors = validator.validate(request);
        if (!errors.isEmpty()) throw new IllegalStateException(errors.iterator().next().getMessage());
        return new DraftSnapshot(draft.getId(), request, vn.edu.hcmute.uteexpress.service.OrderSubmissionGuard.fingerprint(request));
    }
    private void requireUnchanged(DraftSnapshot before, DraftOrder current) {
        if (current == null || !before.hash().equals(snapshot(current).hash())) throw cartChanged();
    }
    private static IllegalStateException cartChanged() {
        return new IllegalStateException("Giỏ đơn vừa thay đổi. Hãy tải lại trang và xác nhận lại.");
    }

    // ----- Phần dùng chung trong service -----

    private void requireCartSpace(AppUser owner) {
        if (draftOrderRepository.countByUser(owner) >= MAX_DRAFTS_PER_USER)
            throw new IllegalStateException("Giỏ đơn chỉ chứa tối đa " + MAX_DRAFTS_PER_USER
                    + " đơn. Vui lòng xác nhận bớt trước khi thêm đơn mới.");
    }

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
     * Đổ đơn nháp về DTO để dùng chung OrderCreationWriter với luồng tạo đơn trực tiếp - nhờ vậy
     * đơn xác nhận từ giỏ và đơn tạo trực tiếp đi qua đúng một luồng nghiệp vụ
     * (sinh mã vận đơn, tính cước, đặt trạng thái PENDING_PICKUP).
     */
    private OrderCreateRequest toCreateRequest(DraftOrder draft) {
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
}
