package vn.edu.hcmute.uteexpress.service;

import vn.edu.hcmute.uteexpress.dto.OrderCreateRequest;
import vn.edu.hcmute.uteexpress.dto.OrderTimelineStep;
import vn.edu.hcmute.uteexpress.entity.Order;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Nghiep vu van don - vai tro User (TV1 phu trach, muc 03 ke hoach).
 */
public interface OrderService {

    /**
     * Tao san form "Tao don gui hang" cho lan mo dau tien, da dien truoc dia chi lay hang
     * mac dinh trong so dia chi cua nguoi dung (neu co) de khoi phai go lai moi lan.
     * CO Y khong dien truoc dia chi nguoi nhan: moi don thuong gui cho mot nguoi khac nhau,
     * dien san de dan toi gui nham - nguoi dung tu chon tu so dia chi neu muon.
     */
    OrderCreateRequest prepareCreateForm(String username);

    Order createOrder(OrderCreateRequest request, String senderUsername);

    /**
     * Tinh cuoc theo loai dich vu va khoi luong.
     * Mo ra ngoai interface de gio don cho xac nhan (viec 2) hien cuoc tam tinh bang
     * dung mot cong thuc voi luc tao don that - tranh viet lai roi hai cho lech nhau.
     */
    BigDecimal estimateFee(Order.ServiceType serviceType, double weightKg);

    List<Order> findOrdersOfUser(String username);

    /**
     * Loc lich su don theo trang thai. statusFilter = null nghia la lay tat ca (khong loc).
     */
    List<Order> findOrdersOfUser(String username, Order.OrderStatus statusFilter);

    /**
     * Lich su don co phan trang, ket hop loc trang thai va tim theo tu khoa.
     * statusFilter = null la khong loc; keyword rong/null la khong tim.
     * Tu khoa tra theo ma van don, ten nguoi nhan hoac so dien thoai nguoi nhan.
     */
    Page<Order> searchOrdersOfUser(String username, Order.OrderStatus statusFilter,
                                   String keyword, Pageable pageable);

    Optional<Order> findByTrackingCode(String trackingCode);

    /**
     * Lay mot don cu the de hien trang chi tiet, kem kiem tra quyen so huu.
     * Nem IllegalStateException neu khong tim thay don HOAC don khong phai cua username nay -
     * co y dung chung mot cau bao loi cho ca hai truong hop: neu bao "don khong phai cua ban"
     * thi nguoi la co the do id de biet don nao co that trong he thong.
     */
    Order findOwnedOrder(Long orderId, String username);

    /**
     * Dung danh sach cac moc hanh trinh cua don de ve timeline o trang chi tiet.
     * Luong binh thuong di qua 4 moc: cho lay hang - da lay hang - dang giao - giao thanh cong.
     * Don ket thuc bat thuong (huy / that bai / hoan tra) thi moc cuoi doi thanh trang thai do.
     */
    List<OrderTimelineStep> buildTimeline(Order order);

    /**
     * Nguoi gui tu huy don khi don chua duoc lay hang (PENDING_PICKUP).
     * Nem IllegalStateException neu don khong thuoc ve username nay hoac da qua trang thai cho huy.
     */
    void cancelOrder(Long orderId, String username);
}
