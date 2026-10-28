package vn.edu.hcmute.uteexpress.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.hcmute.uteexpress.entity.AppUser;
import vn.edu.hcmute.uteexpress.entity.DraftOrder;

import java.util.List;
import java.util.Optional;

/**
 * Truy vấn giỏ đơn chờ xác nhận. Giống sổ địa chỉ, mọi phương thức đều kèm AppUser
 * để người dùng không xem/xoá được đơn nháp của người khác bằng cách đổi id trên URL.
 */
public interface DraftOrderRepository extends JpaRepository<DraftOrder, Long> {

    /** Đơn thêm sau nằm trên, giống cách giỏ hàng thường hiển thị. */
    List<DraftOrder> findByUserOrderByCreatedAtDesc(AppUser user);

    Optional<DraftOrder> findByIdAndUser(Long id, AppUser user);

    long countByUser(AppUser user);

    void deleteByUser(AppUser user);
}
