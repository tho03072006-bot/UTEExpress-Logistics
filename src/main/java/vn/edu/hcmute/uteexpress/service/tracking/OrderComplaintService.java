package vn.edu.hcmute.uteexpress.service.tracking;

import java.time.*;
import java.util.Optional;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.hcmute.uteexpress.dto.tracking.ComplaintSummary;
import vn.edu.hcmute.uteexpress.entity.*;
import vn.edu.hcmute.uteexpress.repository.*;

@Service
@Transactional(readOnly = true)
public class OrderComplaintService {
    private final OrderRepository orders;
    private final OrderComplaintRepository complaints;
    private final LogisticsAccess access;
    private final ShipmentImageService images;
    private final Clock clock;
    private final vn.edu.hcmute.uteexpress.service.media.CloudMediaStorageService cloud;
    private final vn.edu.hcmute.uteexpress.repository.admin.ComplaintActivityRepository activities;
    private final vn.edu.hcmute.uteexpress.repository.admin.OperationsCaseRepository cases;
    public OrderComplaintService(OrderRepository orders, OrderComplaintRepository complaints,
            LogisticsAccess access, ShipmentImageService images, Clock clock,
            vn.edu.hcmute.uteexpress.repository.admin.ComplaintActivityRepository activities,
            vn.edu.hcmute.uteexpress.repository.admin.OperationsCaseRepository cases,
            vn.edu.hcmute.uteexpress.service.media.CloudMediaStorageService cloud) {
        this.orders = orders; this.complaints = complaints; this.access = access; this.images = images; this.clock = clock;
        this.activities = activities; this.cases = cases;
        this.cloud = cloud;
    }
    public Optional<OrderComplaint> forOrder(Order order, String username) {
        access.checkOrder(order, username);
        return complaints.findByOrder(order);
    }
    public OrderComplaint view(Long id, String username) {
        var complaint = complaints.findById(id).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy khiếu nại."));
        access.checkOrder(complaint.getOrder(), username);
        return complaint;
    }
    public Page<ComplaintSummary> list(String actor, OrderComplaint.Status status, int page) {
        access.internal(actor);
        return complaints.search(status, PageRequest.of(LogisticsInput.page(page), 10, Sort.by("createdAt", "id").descending()));
    }
    @Transactional
    public void create(Long orderId, String username, String subject, String description, MultipartFile image) {
        AppUser customer = access.customer(username);
        String title = LogisticsInput.text(subject, 1, 100, "Tiêu đề");
        String body = LogisticsInput.text(description, 1, 2000, "Nội dung khiếu nại");
        byte[] attachment = images.png(image);
        var order = orders.findLockedById(orderId).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy vận đơn."));
        if (!customer.getId().equals(order.getSender().getId()))
            throw new org.springframework.security.access.AccessDeniedException("Vận đơn không thuộc tài khoản của bạn.");
        if (complaints.findByOrder(order).isPresent()) throw new IllegalArgumentException("Vận đơn đã có hồ sơ khiếu nại.");
        var complaint = new OrderComplaint(order, title, body, null, LocalDateTime.now(clock));
        if (attachment != null) complaint.setImageAsset(cloud.storeBytes(vn.edu.hcmute.uteexpress.service.media.MediaPurpose.COMPLAINT,attachment,"complaint.png"));
        complaints.save(complaint);
        activity(complaint, username, "CREATE", body);
    }
    public byte[] readImage(Long id,String actor) {
        var complaint = view(id,actor);
        if (!complaint.isHasImage()) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
        if (complaint.getImageAsset() == null) throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND,"Ảnh cũ chưa được chuyển lên cloud chung.");
        return cloud.read(complaint.getImageAsset(),vn.edu.hcmute.uteexpress.service.media.MediaPurpose.COMPLAINT).bytes();
    }
    @Transactional
    public void respond(Long id, String actor, long revision, OrderComplaint.Status status, String reply) {
        var manager = access.internal(actor);
        var complaint = lockedComplaint(id, actor);
        checkRevision(complaint, revision);
        if (complaint.getHandledBy() != null && !complaint.getHandledBy().getId().equals(manager.getId())
                && manager.getRole() != AppUser.Role.ADMIN)
            throw new org.springframework.security.access.AccessDeniedException("Hồ sơ đang do Manager khác phụ trách.");
        if (status == null || status == OrderComplaint.Status.OPEN)
            throw new IllegalArgumentException("Chọn trạng thái đang xử lý, đã giải quyết hoặc từ chối.");
        if (status == OrderComplaint.Status.REJECTED && cases.existsByComplaintAndKindAndStatusIn(complaint,
                vn.edu.hcmute.uteexpress.entity.admin.OperationsCase.Kind.COMPENSATION, java.util.List.of(
                vn.edu.hcmute.uteexpress.entity.admin.OperationsCase.Status.APPROVED,
                vn.edu.hcmute.uteexpress.entity.admin.OperationsCase.Status.COMPLETED)))
            throw new IllegalArgumentException("Không từ chối khiếu nại đã có khoản bồi hoàn được duyệt/chi trả.");
        complaint.respond(manager, status, LogisticsInput.text(reply, 1, 2000, "Phản hồi"), LocalDateTime.now(clock));
        complaints.saveAndFlush(complaint);
        activity(complaint, actor, "RESPOND", reply);
    }
    @Transactional
    public void supplement(Long id, String username, long revision, String note) {
        access.shipper(username);
        var complaint = lockedComplaint(id, username);
        checkRevision(complaint, revision);
        complaint.supplement(LogisticsInput.text(note, 1, 1000, "Thông tin bổ sung"), LocalDateTime.now(clock));
        complaints.saveAndFlush(complaint);
        activity(complaint, username, "SUPPLEMENT", note);
    }
    @Transactional public void claim(Long id, String actor, long revision) {
        var manager = access.internal(actor); var complaint = lockedComplaint(id, actor); checkRevision(complaint, revision);
        if (complaint.getHandledBy() != null && !complaint.getHandledBy().getId().equals(manager.getId())
                && manager.getRole() != AppUser.Role.ADMIN)
            throw new org.springframework.security.access.AccessDeniedException("Hồ sơ đã có người phụ trách.");
        complaint.respond(manager, OrderComplaint.Status.IN_PROGRESS, complaint.getManagerReply(), LocalDateTime.now(clock));
        complaints.saveAndFlush(complaint); activity(complaint, actor, "CLAIM", "Nhận phụ trách hồ sơ.");
    }
    /**
     * Người gửi nhắn tiếp vào hồ sơ, không giới hạn số lần. Hồ sơ đã kết thúc thì được mở lại để Manager xử lý tiếp;
     * khoản bồi hoàn đã duyệt (nếu có) giữ nguyên.
     */
    @Transactional
    public void customerMessage(Long id, String username, String message) {
        AppUser customer = access.customer(username);
        var complaint = lockedComplaint(id, username);
        if (!customer.getId().equals(complaint.getOrder().getSender().getId()))
            throw new org.springframework.security.access.AccessDeniedException("Vận đơn không thuộc tài khoản của bạn.");
        String text = LogisticsInput.text(message, 1, 2000, "Tin nhắn");
        if (complaint.getStatus().isClosed()) complaint.reopen(LocalDateTime.now(clock));
        else complaint.touch(LocalDateTime.now(clock));
        complaints.saveAndFlush(complaint);
        activity(complaint, username, "CUSTOMER_MESSAGE", text);
    }
    public Page<vn.edu.hcmute.uteexpress.entity.admin.ComplaintActivity> timeline(Long id, String actor, int page) {
        view(id, actor); return activities.findByComplaintIdOrderByCreatedAtAscIdAsc(id, PageRequest.of(LogisticsInput.page(page), 50));
    }
    private void activity(OrderComplaint complaint, String actor, String action, String message) {
        activities.save(new vn.edu.hcmute.uteexpress.entity.admin.ComplaintActivity(complaint, actor, action,
                LogisticsInput.text(message, 1, 2000, "Nội dung"), LocalDateTime.now(clock)));
    }
    private OrderComplaint lockedComplaint(Long id, String actor) {
        var complaint = complaints.lockById(id).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy khiếu nại."));
        access.checkOrder(complaint.getOrder(), actor);
        return complaint;
    }
    private void checkRevision(OrderComplaint complaint, long revision) {
        if (complaint.getRevision() != revision) throw new IllegalArgumentException("Hồ sơ đã thay đổi. Hãy tải lại trang.");
        if (complaint.getStatus().isClosed()) throw new IllegalArgumentException("Hồ sơ đã kết thúc, không thể sửa phản hồi.");
    }
}
