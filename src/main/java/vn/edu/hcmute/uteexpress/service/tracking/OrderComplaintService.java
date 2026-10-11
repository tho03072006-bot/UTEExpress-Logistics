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
    public OrderComplaintService(OrderRepository orders, OrderComplaintRepository complaints,
            LogisticsAccess access, ShipmentImageService images, Clock clock) {
        this.orders = orders; this.complaints = complaints; this.access = access; this.images = images; this.clock = clock;
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
        String title = LogisticsInput.text(subject, 5, 100, "Tiêu đề");
        String body = LogisticsInput.text(description, 10, 2000, "Nội dung khiếu nại");
        byte[] attachment = images.png(image);
        var order = orders.findLockedById(orderId).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy vận đơn."));
        if (!customer.getId().equals(order.getSender().getId()))
            throw new org.springframework.security.access.AccessDeniedException("Vận đơn không thuộc tài khoản của bạn.");
        if (complaints.findByOrder(order).isPresent()) throw new IllegalArgumentException("Vận đơn đã có hồ sơ khiếu nại.");
        complaints.save(new OrderComplaint(order, title, body, attachment, LocalDateTime.now(clock)));
    }
    @Transactional
    public void respond(Long id, String actor, long revision, OrderComplaint.Status status, String reply) {
        var manager = access.internal(actor);
        var complaint = view(id, actor);
        checkRevision(complaint, revision);
        if (status == null || status == OrderComplaint.Status.OPEN)
            throw new IllegalArgumentException("Chọn trạng thái đang xử lý, đã giải quyết hoặc từ chối.");
        complaint.respond(manager, status, LogisticsInput.text(reply, 5, 2000, "Phản hồi"), LocalDateTime.now(clock));
        complaints.saveAndFlush(complaint);
    }
    @Transactional
    public void supplement(Long id, String username, long revision, String note) {
        access.shipper(username);
        var complaint = view(id, username);
        checkRevision(complaint, revision);
        complaint.supplement(LogisticsInput.text(note, 5, 1000, "Thông tin bổ sung"), LocalDateTime.now(clock));
        complaints.saveAndFlush(complaint);
    }
    private void checkRevision(OrderComplaint complaint, long revision) {
        if (complaint.getRevision() != revision) throw new IllegalArgumentException("Hồ sơ đã thay đổi. Hãy tải lại trang.");
        if (complaint.getStatus().isClosed()) throw new IllegalArgumentException("Hồ sơ đã kết thúc, không thể sửa phản hồi.");
    }
}
