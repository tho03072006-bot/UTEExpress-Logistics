package vn.edu.hcmute.uteexpress.service.manager.impl;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.hcmute.uteexpress.dto.manager.*;
import vn.edu.hcmute.uteexpress.entity.*;
import vn.edu.hcmute.uteexpress.entity.admin.OperationsCase;
import vn.edu.hcmute.uteexpress.exception.StaffConflictException;
import vn.edu.hcmute.uteexpress.repository.*;
import vn.edu.hcmute.uteexpress.repository.admin.OperationsCaseRepository;
import vn.edu.hcmute.uteexpress.service.admin.*;
import vn.edu.hcmute.uteexpress.service.manager.OperationsCaseService;
import vn.edu.hcmute.uteexpress.service.tracking.*;

@Service @Transactional(readOnly = true)
public class OperationsCaseServiceImpl implements OperationsCaseService {
    private static final List<OperationsCase.Status> ACTIVE = List.of(OperationsCase.Status.REQUESTED,
            OperationsCase.Status.APPROVED, OperationsCase.Status.IN_PROGRESS);
    private static final List<OperationsCase.Status> ACCEPTED = List.of(OperationsCase.Status.APPROVED, OperationsCase.Status.COMPLETED);
    private final OperationsCaseRepository cases;
    private final OrderRepository orders;
    private final CodSettlementRepository settlements;
    private final OrderComplaintRepository complaints;
    private final OrderPaymentRepository payments;
    private final OrderStatusHistoryRepository history;
    private final OperationsPolicyService policies;
    private final StaffAccessPolicy access;
    private final LogisticsAccess logistics;
    private final ShipmentImageService images;
    private final AuditService audit;
    private final Clock clock;
    private final jakarta.persistence.EntityManager entityManager;
    private final org.springframework.context.ApplicationEventPublisher events;
    private final vn.edu.hcmute.uteexpress.service.media.CloudMediaStorageService cloud;
    private final vn.edu.hcmute.uteexpress.service.routing.BranchScope scope;
    private final vn.edu.hcmute.uteexpress.repository.routing.OrderRouteRepository routes;
    public OperationsCaseServiceImpl(OperationsCaseRepository cases, OrderRepository orders,
            CodSettlementRepository settlements, OrderComplaintRepository complaints, OrderPaymentRepository payments,
            OrderStatusHistoryRepository history, OperationsPolicyService policies, StaffAccessPolicy access,
            LogisticsAccess logistics, ShipmentImageService images, AuditService audit, Clock clock,
            jakarta.persistence.EntityManager entityManager, org.springframework.context.ApplicationEventPublisher events,
            vn.edu.hcmute.uteexpress.service.media.CloudMediaStorageService cloud,
            vn.edu.hcmute.uteexpress.service.routing.BranchScope scope,
            vn.edu.hcmute.uteexpress.repository.routing.OrderRouteRepository routes) {
        this.cases = cases; this.orders = orders; this.settlements = settlements; this.complaints = complaints;
        this.payments = payments; this.history = history; this.policies = policies; this.access = access;
        this.logistics = logistics; this.images = images; this.audit = audit; this.clock = clock;
        this.entityManager = entityManager;
        this.events = events;
        this.cloud = cloud;
        this.scope = scope;
        this.routes = routes;
    }
    /** Tuyến liên tỉnh của đơn (null với đơn nội tỉnh hoặc đơn cũ chưa có tuyến): hoàn hàng phải đi chuyến trung chuyển ngược (B9). */
    private vn.edu.hcmute.uteexpress.entity.routing.OrderRoute interprovincialRoute(Order order) {
        return routes.findByOrder_Id(order.getId()).filter(vn.edu.hcmute.uteexpress.entity.routing.OrderRoute::isInterprovincial).orElse(null);
    }
    private vn.edu.hcmute.uteexpress.entity.routing.OrderRoute lockedInterprovincialRoute(Order order) {
        var route = interprovincialRoute(order);
        if (route != null) entityManager.refresh(route, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        return route;
    }
    /** Đọc hồ sơ được ở cả hai đầu tuyến, nhưng yêu cầu giao lại/hoàn phải do kho đang giao hàng xử lý. */
    private void requireDeliveryHub(String actor, Order order) {
        var route = interprovincialRoute(order);
        if (route != null) requireHub(actor, route.getDestBranch(), "Lập yêu cầu giao lại/hoàn hàng liên tỉnh");
    }
    /** Chỉ Manager của đúng kho (hoặc Admin) thao tác bước hoàn ở kho đó. */
    private void requireHub(String actor, vn.edu.hcmute.uteexpress.entity.routing.Branch branch, String step) {
        Long mine = scope.of(actor, null);
        if (mine != null && !mine.equals(branch.getId()))
            throw new org.springframework.security.access.AccessDeniedException(step + " do kho " + branch.getName() + " thực hiện.");
    }
    public Page<OperationsCaseResponse> list(String actor, OperationsCase.Kind kind, OperationsCase.Status status, Long branchId, int page) {
        access.requireOperator(actor); access.validatePage(page);
        // Thứ tự (Chờ duyệt trước, mới trước) nằm trong câu truy vấn.
        return cases.search(kind, status, scope.of(actor, branchId), PageRequest.of(page, 20));
    }
    public java.util.List<vn.edu.hcmute.uteexpress.entity.routing.Branch> branches(String actor) { return scope.branches(actor); }
    public OperationsCase view(String actor, Long id) {
        access.requireOperator(actor); access.validateId(id);
        var record = cases.findById(id).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy hồ sơ."));
        requireBranch(actor, record.getOrder(), record.getSettlement());
        return record;
    }
    /** Manager chỉ thao tác hồ sơ của đơn đi/đến chi nhánh mình hoặc phiếu của Shipper chi nhánh mình. */
    private void requireBranch(String actor, Order order, CodSettlement settlement) {
        if (order != null) scope.requireOrder(actor, order);
        if (settlement != null) scope.requireShipper(actor, settlement.getShipper());
    }
    /**
     * Bảng phân quyền đã chốt: phiếu tiền (nộp bù, trả lại tiền thừa, bồi hoàn) và giao lại vượt giới hạn do Manager lập, Admin duyệt.
     * Admin có mọi chức năng khác của Manager nhưng không lập các hồ sơ này, để người lập và người duyệt luôn là hai người.
     */
    private void requireManagerCreates(String actor, OperationsCase.Kind kind, boolean overLimit) {
        if (!scope.isAdmin(actor)) return;
        if (kind.isFinancial())
            throw new org.springframework.security.access.AccessDeniedException("Phiếu tiền do Manager chi nhánh lập; Admin chỉ duyệt.");
        if (overLimit)
            throw new org.springframework.security.access.AccessDeniedException("Giao lại vượt giới hạn do Manager chi nhánh lập; Admin chỉ duyệt.");
    }
    public byte[] readEvidence(String actor,Long id) {
        var record = view(actor,id);
        if (!record.isHasEvidence()) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
        if (record.getEvidenceAsset() == null) throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND,"Chứng từ cũ chưa được chuyển lên cloud chung.");
        return cloud.read(record.getEvidenceAsset(),vn.edu.hcmute.uteexpress.service.media.MediaPurpose.RECEIPT).bytes();
    }
    public boolean requiresAdminApproval(String actor, Long id) {
        var record = view(actor, id);
        return isFinancial(record) || (record.getKind() == OperationsCase.Kind.RETRY
                && history.countByOrderAndStatus(record.getOrder(), Order.OrderStatus.FAILED) >= policies.current().getMaxDeliveryAttempts());
    }
    public OperationsCaseRequest prepare(String actor, OperationsCase.Kind kind, Long id) {
        access.requireOperator(actor); access.validateId(id);
        if (kind == null) throw new IllegalArgumentException("Chọn loại hồ sơ.");
        long revision;
        requireManagerCreates(actor, kind, false);
        switch (kind) {
            case RETRY, RETURN -> {
                var order = orders.findById(id).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn."));
                requireBranch(actor, order, null);
                requireDeliveryHub(actor, order);
                if (kind == OperationsCase.Kind.RETRY) requireManagerCreates(actor, kind, overLimit(order));
                revision = order.getRevision();
            }
            case CASH_SUPPLEMENT, CASH_REFUND -> {
                var settlement = settlements.findById(id).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phiếu nộp."));
                requireBranch(actor, null, settlement);
                revision = settlement.getRevision();
            }
            case COMPENSATION -> {
                var complaint = complaints.findById(id).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy khiếu nại."));
                requireBranch(actor, complaint.getOrder(), null);
                if (!isPaid(complaint.getOrder()))
                    throw new IllegalArgumentException("Đơn chưa thanh toán nên không lập phiếu bồi hoàn cước.");
                revision = complaint.getRevision();
            }
            default -> throw new IllegalArgumentException("Loại hồ sơ không hợp lệ.");
        }
        return new OperationsCaseRequest(kind, id, revision, null, null, "", "", UUID.randomUUID().toString());
    }
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public Long request(String actor, OperationsCaseRequest form, MultipartFile evidence) {
        access.requireOperator(actor); access.validate(form);
        requireManagerCreates(actor, form.kind(), false);
        String note = LogisticsInput.text(form.reason(), 5, 500, "Lý do");
        var now = LocalDateTime.now(clock);
        Order order = null; CodSettlement settlement = null; OrderComplaint complaint = null;
        byte[] image = images.png(evidence);
        switch (form.kind()) {
            case RETRY, RETURN -> {
                if (form.amount() != null) throw new IllegalArgumentException("Giao lại/hoàn hàng không nhập số tiền.");
                order = lockedOrder(form.targetId());
                requireBranch(actor, order, null);
                requireDeliveryHub(actor, order);
                if (form.kind() == OperationsCase.Kind.RETRY) requireManagerCreates(actor, form.kind(), overLimit(order));
                checkRevision(order.getRevision(), form.revision());
                requireFailed(order);
                if (order.getShipper() == null || !order.getShipper().isEnabled() || order.getShipper().isLockedByAdmin())
                    throw new IllegalArgumentException("Đơn phải có Shipper đang hoạt động để xử lý giao lại/hoàn.");
                if (cases.existsByOrderAndStatusIn(order, ACTIVE)) throw new StaffConflictException("Đơn đã có hồ sơ đang xử lý.");
                if (form.kind() == OperationsCase.Kind.RETRY) validateAppointment(form.nextAttemptAt(), now);
                else if (form.nextAttemptAt() != null) throw new IllegalArgumentException("Yêu cầu hoàn không có lịch giao lại.");
                // Chạm vận đơn để revision của form không được dùng lại sau khi tạo hồ sơ.
                entityManager.lock(order, jakarta.persistence.LockModeType.PESSIMISTIC_FORCE_INCREMENT);
                order.setUpdatedAt(now); orders.saveAndFlush(order);
            }
            case CASH_SUPPLEMENT, CASH_REFUND -> {
                requireMoney(form.amount());
                settlement = lockedSettlement(form.targetId());
                requireBranch(actor, null, settlement);
                checkRevision(settlement.getRevision(), form.revision());
                if (settlement.getStatus() != CodSettlement.Status.CONFIRMED)
                    throw new IllegalArgumentException("Chỉ ghi bổ sung cho phiếu đã xác nhận có thiếu tiền.");
                BigDecimal reserved = cases.sumAmounts(settlement, form.kind(), List.of(OperationsCase.Status.REQUESTED));
                BigDecimal available = form.kind() == OperationsCase.Kind.CASH_SUPPLEMENT ? outstanding(settlement) : refundable(settlement);
                if (form.amount().compareTo(available.subtract(reserved)) > 0)
                    throw new IllegalArgumentException("Số tiền vượt khoản thiếu/thừa chưa được lập hồ sơ.");
                if (image == null) throw new IllegalArgumentException("Phiếu bổ sung cần ảnh chứng từ nộp tiền.");
                LogisticsInput.text(form.receiptReference(), 5, 100, "Mã biên nhận");
                if (form.nextAttemptAt() != null) throw new IllegalArgumentException("Phiếu tiền không có lịch giao lại.");
            }
            case COMPENSATION -> {
                requireMoney(form.amount());
                complaint = complaints.findById(form.targetId()).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy khiếu nại."));
                requireBranch(actor, complaint.getOrder(), null);
                order = lockedOrder(complaint.getOrder().getId());
                entityManager.refresh(complaint, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
                checkRevision(complaint.getRevision(), form.revision());
                if (complaint.getStatus() == OrderComplaint.Status.REJECTED)
                    throw new IllegalArgumentException("Không đề xuất chi trả cho khiếu nại đã từ chối.");
                if (cases.existsByComplaintAndKindAndStatusIn(complaint, OperationsCase.Kind.COMPENSATION,
                        List.of(OperationsCase.Status.REQUESTED, OperationsCase.Status.APPROVED, OperationsCase.Status.COMPLETED)))
                    throw new StaffConflictException("Khiếu nại đã có đề xuất đang chờ hoặc đã được duyệt.");
                validateCompensation(order, form.amount());
                if (image == null && !cloud.isReference(complaint.getImageAsset(),vn.edu.hcmute.uteexpress.service.media.MediaPurpose.COMPLAINT))
                    throw new IllegalArgumentException("Cần ảnh khiếu nại đã chuyển lên cloud hoặc ảnh đính kèm đề xuất.");
                if (form.nextAttemptAt() != null) throw new IllegalArgumentException("Đề xuất chi trả không có lịch giao lại.");
            }
        }
        var record = new OperationsCase(form.kind(), order, settlement, complaint, form.amount(), form.nextAttemptAt(), note, actor, now);
        record.setRequestToken(form.requestToken());
        record.attach(form.receiptReference() == null ? null : LogisticsInput.text(form.receiptReference(), 0, 100, "Mã biên nhận"), null);
        if (image != null) record.setEvidenceAsset(cloud.storeBytes(vn.edu.hcmute.uteexpress.service.media.MediaPurpose.RECEIPT,image,"receipt.png"));
        cases.saveAndFlush(record);
        audit.record(actor, "CASE_REQUEST", "operations-case:" + record.getId(), form.kind() + "; " + note);
        // Phân quyền: chuyển hoàn và giao lại còn trong giới hạn có hiệu lực ngay khi lập, không cần ai duyệt.
        // Giao lại vượt giới hạn và mọi hồ sơ tiền vẫn ở "Chờ duyệt" cho Admin.
        boolean immediate = form.kind() == OperationsCase.Kind.RETURN || (form.kind() == OperationsCase.Kind.RETRY && !overLimit(order));
        if (immediate) {
            record.review(true, actor, "Có hiệu lực ngay khi lập (không cần duyệt).", now); cases.saveAndFlush(record);
            audit.record(actor, "CASE_APPROVE", "operations-case:" + record.getId(), "Tự có hiệu lực khi lập.");
        }
        publishProgress(record, immediate && form.kind() == OperationsCase.Kind.RETRY
                ? vn.edu.hcmute.uteexpress.dto.tracking.SenderOrderUpdateEvent.Kind.RETRY_APPROVED : null);
        return record.getId();
    }
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void review(String actor, Long id, OperationsReviewRequest form) {
        access.requireOperator(actor); access.validate(form);
        String note = LogisticsInput.text(form.reason(), 5, 500, "Lý do duyệt/từ chối");
        var record = lock(actor, id, form.revision());
        if (record.getStatus() != OperationsCase.Status.REQUESTED) throw new StaffConflictException("Hồ sơ đã được xử lý.");
        if (isFinancial(record)) {
            access.requireAdmin(actor);
            if (actor.equals(record.getCreatedBy())) throw new IllegalArgumentException("Người lập không được tự duyệt hồ sơ tiền của mình.");
        }
        if (record.getKind() == OperationsCase.Kind.RETRY
                && history.countByOrderAndStatus(record.getOrder(), Order.OrderStatus.FAILED) >= policies.current().getMaxDeliveryAttempts()) {
            access.requireAdmin(actor);
        }
        if (form.approve()) {
            switch (record.getKind()) {
                case RETRY -> {
                    requireFailed(record.getOrder()); validateAppointment(record.getNextAttemptAt(), LocalDateTime.now(clock));
                    if (history.countByOrderAndStatus(record.getOrder(), Order.OrderStatus.FAILED) >= policies.current().getMaxDeliveryAttempts())
                        access.requireAdmin(actor);
                }
                case RETURN -> requireFailed(record.getOrder());
                case CASH_SUPPLEMENT, CASH_REFUND -> {
                    BigDecimal available = record.getKind() == OperationsCase.Kind.CASH_SUPPLEMENT
                            ? outstanding(record.getSettlement()) : refundable(record.getSettlement());
                    if (record.getSettlement().getStatus() != CodSettlement.Status.CONFIRMED
                            || record.getAmount().compareTo(available) > 0)
                        throw new StaffConflictException("Khoản thiếu đã thay đổi hoặc phiếu chưa được xác nhận.");
                }
                case COMPENSATION -> {
                    if (record.getComplaint().getStatus() == OrderComplaint.Status.REJECTED)
                        throw new StaffConflictException("Khiếu nại đã bị từ chối.");
                    validateCompensation(record.getOrder(), record.getAmount());
                }
            }
        }
        record.review(form.approve(), actor, note, LocalDateTime.now(clock)); cases.saveAndFlush(record);
        audit.record(actor, form.approve() ? "CASE_APPROVE" : "CASE_REJECT", "operations-case:" + id, note);
        publishProgress(record, form.approve() && record.getKind() == OperationsCase.Kind.RETRY
                ? vn.edu.hcmute.uteexpress.dto.tracking.SenderOrderUpdateEvent.Kind.RETRY_APPROVED : null);
    }
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void startReturn(String actor, Long id, long revision, String reason) {
        access.requireOperator(actor); String note = LogisticsInput.text(reason, 5, 500, "Lý do");
        var record = lock(actor, id, revision);
        if (record.getKind() != OperationsCase.Kind.RETURN || record.getStatus() != OperationsCase.Status.APPROVED)
            throw new StaffConflictException("Chỉ bắt đầu hoàn sau khi yêu cầu hoàn được duyệt.");
        requireFailed(record.getOrder());
        var now = LocalDateTime.now(clock);
        var route = lockedInterprovincialRoute(record.getOrder());
        if (route != null) {
            // B9: kho chi nhánh nhận bắt đầu hoàn; hàng về kho nhận, shipper giao hết giữ đơn, chờ chuyến về chi nhánh gửi.
            requireHub(actor, route.getDestBranch(), "Bắt đầu hoàn hàng liên tỉnh");
            var order = record.getOrder();
            route.beginReturn();
            order.setShipper(null); order.setUpdatedAt(now); orders.saveAndFlush(order);
            history.save(new OrderStatusHistory(order, logistics.internal(actor), Order.OrderStatus.FAILED, Order.OrderStatus.FAILED, null,
                    "Chuyển hoàn liên tỉnh theo hồ sơ " + id + ": hàng về kho " + route.getDestBranch().getName()
                            + ", chờ chuyến về " + route.getOriginBranch().getName() + ".", null, now));
        }
        record.start(now); cases.saveAndFlush(record);
        audit.record(actor, "RETURN_START", "operations-case:" + id, note);
        publishProgress(record, vn.edu.hcmute.uteexpress.dto.tracking.SenderOrderUpdateEvent.Kind.RETURN_STARTED);
    }
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void complete(String actor, Long id, long revision, String reference, MultipartFile evidence) {
        access.requireOperator(actor);
        String receipt = LogisticsInput.text(reference, 5, 100, "Tên người nhận hoàn / mã chứng từ chi trả");
        byte[] image = images.png(evidence);
        if (image == null) throw new IllegalArgumentException("Cần ảnh biên nhận bàn giao/chi trả hợp lệ.");
        var record = lock(actor, id, revision);
        var now = LocalDateTime.now(clock);
        if (record.getKind() == OperationsCase.Kind.RETURN && record.getStatus() == OperationsCase.Status.IN_PROGRESS) {
            var order = record.getOrder(); requireFailed(order);
            var route = lockedInterprovincialRoute(order);
            if (route != null) {
                // B9: chỉ hoàn tất khi hàng hoàn đã về kho chi nhánh gửi; kho gửi ghi nhận bàn giao trả người gửi.
                if (!route.isReturnLeg() || route.getStage() != vn.edu.hcmute.uteexpress.entity.routing.OrderRoute.Stage.ORIGIN_HUB)
                    throw new StaffConflictException("Hàng hoàn chưa về tới kho " + route.getOriginBranch().getName() + ".");
                requireHub(actor, route.getOriginBranch(), "Trả hàng hoàn cho người gửi");
            }
            order.setStatus(Order.OrderStatus.RETURNED); order.setUpdatedAt(now); orders.saveAndFlush(order);
            history.save(new OrderStatusHistory(order, logistics.internal(actor), Order.OrderStatus.FAILED,
                    Order.OrderStatus.RETURNED, null, "Bàn giao hoàn theo hồ sơ " + id + ": " + receipt, null, now));
        } else if ((record.getKind() == OperationsCase.Kind.COMPENSATION || record.getKind() == OperationsCase.Kind.CASH_REFUND)
                && record.getStatus() == OperationsCase.Status.APPROVED) {
            access.requireAdmin(actor);
            if (record.getCreatedBy().equals(actor)) throw new IllegalArgumentException("Người đề xuất không tự ghi nhận chi trả.");
            if (record.getKind() == OperationsCase.Kind.COMPENSATION) {
                if (record.getComplaint().getStatus() == OrderComplaint.Status.REJECTED)
                    throw new StaffConflictException("Khiếu nại đã bị từ chối; không được ghi nhận chi trả.");
                validateCompensation(record.getOrder(), record.getAmount());
            }
        } else throw new StaffConflictException("Hồ sơ chưa đủ điều kiện hoàn tất hoặc đã kết thúc.");
        record.complete(receipt, null, now);
        record.setEvidenceAsset(cloud.storeBytes(vn.edu.hcmute.uteexpress.service.media.MediaPurpose.RECEIPT,image,"receipt.png"));
        cases.saveAndFlush(record);
        audit.record(actor, "CASE_COMPLETE", "operations-case:" + id, "Ghi nhận chứng từ ngoài hệ thống: " + receipt);
        publishProgress(record, record.getKind() == OperationsCase.Kind.RETURN
                ? vn.edu.hcmute.uteexpress.dto.tracking.SenderOrderUpdateEvent.Kind.RETURNED : null);
    }

    private void publishProgress(OperationsCase record, vn.edu.hcmute.uteexpress.dto.tracking.SenderOrderUpdateEvent.Kind kind) {
        Order order = record.getOrder();
        if (order == null) return;
        events.publishEvent(new vn.edu.hcmute.uteexpress.dto.tracking.OrderStatusChangedEvent(
                order.getId(), order.getTrackingCode(), order.getStatus().name(), LocalDateTime.now(clock)));
        if (kind != null && order.getSender() != null) {
            var sender = order.getSender();
            events.publishEvent(new vn.edu.hcmute.uteexpress.dto.tracking.SenderOrderUpdateEvent(
                    sender.getEmail(), sender.getFullName() == null ? order.getSenderName() : sender.getFullName(),
                    order.getTrackingCode(), kind, null, record.getNextAttemptAt()));
        }
    }
    public BigDecimal outstanding(CodSettlement settlement) {
        if (settlement.getStatus() != CodSettlement.Status.CONFIRMED) return BigDecimal.ZERO;
        return settlement.getTotalAmount().subtract(settlement.getDeclaredAmount())
                .subtract(cases.sumAmounts(settlement, OperationsCase.Kind.CASH_SUPPLEMENT, ACCEPTED)).max(BigDecimal.ZERO);
    }
    public boolean canRetry(Order order) {
        // Quyền giao lại dùng chung cho API, form chi tiết và xử lý hàng loạt.
        if (order.getStatus() != Order.OrderStatus.FAILED || cases.existsByOrderAndKindAndStatusIn(order, OperationsCase.Kind.RETURN, ACTIVE)) return false;
        var approved = cases.findByOrderAndKindAndStatus(order, OperationsCase.Kind.RETRY, OperationsCase.Status.APPROVED);
        if (!approved.isEmpty()) return !approved.get(0).getNextAttemptAt().isAfter(LocalDateTime.now(clock));
        var latest = history.findFirstByOrderAndStatusAndPreviousStatusNotOrderByOccurredAtDescIdDesc(order, Order.OrderStatus.FAILED, Order.OrderStatus.FAILED);
        if (latest.isEmpty() || latest.get().getNextAttemptAt() == null
                || latest.get().getNextAttemptAt().isAfter(LocalDateTime.now(clock))) return false;
        return !cases.existsByOrderAndKindAndStatusIn(order, OperationsCase.Kind.RETRY, List.of(OperationsCase.Status.REQUESTED))
                && history.countByOrderAndStatus(order, Order.OrderStatus.FAILED) < policies.current().getMaxDeliveryAttempts();
    }
    public BigDecimal refundable(CodSettlement settlement) {
        if (settlement.getStatus() != CodSettlement.Status.CONFIRMED) return BigDecimal.ZERO;
        return settlement.getDeclaredAmount().subtract(settlement.getTotalAmount())
                .subtract(cases.sumAmounts(settlement, OperationsCase.Kind.CASH_REFUND, ACCEPTED)).max(BigDecimal.ZERO);
    }
    @Transactional public void beforeStatusChange(Order order, String shipper, Order.OrderStatus status) {
        var actor = logistics.shipper(shipper);
        if (order.getShipper() == null || !order.getShipper().getId().equals(actor.getId()))
            throw new org.springframework.security.access.AccessDeniedException("Đơn không thuộc Shipper này.");
        if (status == Order.OrderStatus.RETURNED)
            throw new IllegalArgumentException("Hoàn hàng cần hồ sơ duyệt và ảnh bàn giao người gửi tại khu Manager.");
        if (order.getStatus() == Order.OrderStatus.FAILED && status == Order.OrderStatus.IN_TRANSIT) {
            if (!canRetry(order)) throw new IllegalArgumentException("Chưa tới lịch, đã hết số lần giao hoặc đang chờ duyệt/hoàn hàng.");
            for (var record : cases.findByOrderAndKindAndStatus(order, OperationsCase.Kind.RETRY, OperationsCase.Status.APPROVED)) {
                record.complete("Giao lại bởi " + shipper, null, LocalDateTime.now(clock)); cases.saveAndFlush(record);
                audit.record(shipper, "RETRY_START", "operations-case:" + record.getId(), "Bắt đầu lượt giao lại đã được duyệt.");
            }
        }
    }
    private OperationsCase lock(String actor, Long id, long revision) {
        var existing = view(actor, id);
        if (existing.getOrder() != null) lockedOrder(existing.getOrder().getId());
        if (existing.getSettlement() != null) lockedSettlement(existing.getSettlement().getId());
        if (existing.getComplaint() != null)
            entityManager.refresh(existing.getComplaint(), jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        // view() đã nạp hồ sơ trước lúc chờ khóa cha: đọc lại để không duyệt trạng thái cũ trong persistence context.
        entityManager.refresh(existing, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        requireBranch(actor, existing.getOrder(), existing.getSettlement());
        checkRevision(existing.getRevision(), revision); return existing;
    }
    /** Đơn đã thất bại đủ số lần tối đa trong chính sách: giao lại thêm cần Admin duyệt. */
    private boolean overLimit(Order order) {
        return history.countByOrderAndStatus(order, Order.OrderStatus.FAILED) >= policies.current().getMaxDeliveryAttempts();
    }
    private Order lockedOrder(Long id) {
        access.validateId(id);
        var order = orders.findById(id).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy vận đơn."));
        entityManager.refresh(order, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        return order;
    }
    private CodSettlement lockedSettlement(Long id) {
        access.validateId(id);
        var settlement = settlements.findById(id).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phiếu nộp."));
        entityManager.refresh(settlement, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        return settlement;
    }
    private void requireFailed(Order order) {
        if (order.getStatus() != Order.OrderStatus.FAILED) throw new StaffConflictException("Chỉ xử lý giao lại/hoàn cho đơn đang giao thất bại.");
    }
    private void checkRevision(long expected, long submitted) {
        if (submitted < 0 || expected != submitted) throw new StaffConflictException("Dữ liệu đã thay đổi. Hãy tải lại trang.");
    }
    private void validateAppointment(LocalDateTime time, LocalDateTime now) {
        if (time == null || !time.isAfter(now) || time.isAfter(now.plusDays(30)))
            throw new IllegalArgumentException("Lịch giao lại phải sau hiện tại và trong 30 ngày.");
    }
    private void requireMoney(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0 || amount.stripTrailingZeros().scale() > 0
                || amount.compareTo(new BigDecimal("999999999999")) > 0)
            throw new IllegalArgumentException("Số tiền phải là VND nguyên dương, tối đa 12 chữ số.");
    }
    private void validateCompensation(Order order, BigDecimal amount) {
        var payment = payments.findByOrder(order).orElseThrow(() -> new IllegalArgumentException("Đơn chưa có thanh toán."));
        if (payment.getStatus() != OrderPayment.PaymentStatus.PAID || amount.compareTo(payment.getAmount()) > 0)
            throw new IllegalArgumentException("Mô hình hiện tại chỉ hỗ trợ hoàn/bồi hoàn cước không vượt cước đã thanh toán, không phải giá trị hàng hóa.");
    }
    public boolean isPaid(Order order) {
        return order != null && payments.findByOrder(order).map(p -> p.getStatus() == OrderPayment.PaymentStatus.PAID).orElse(false);
    }
    private boolean isFinancial(OperationsCase record) {
        return record.getKind() == OperationsCase.Kind.CASH_SUPPLEMENT || record.getKind() == OperationsCase.Kind.CASH_REFUND
                || record.getKind() == OperationsCase.Kind.COMPENSATION;
    }
}
