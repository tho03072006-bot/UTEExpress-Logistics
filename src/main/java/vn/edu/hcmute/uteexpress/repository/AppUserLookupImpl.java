package vn.edu.hcmute.uteexpress.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Optional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import vn.edu.hcmute.uteexpress.entity.AppUser;

/**
 * Một trang thường gọi findByUsername 5-6 lần (bộ lọc phiên, controller, từng service); mỗi câu SQL lên Aiven ~100 ms.
 * Lần đầu trong lượt tải trang vẫn truy vấn như cũ và ghi nhớ id; các lần sau lấy bằng em.find(id) - trong cùng
 * persistence context (open-in-view) Hibernate trả đúng đối tượng đã nạp mà không gửi SQL, y như truy vấn lại
 * (truy vấn lại cũng trả về chính đối tượng đang quản lý). Ngoài request web thì luôn truy vấn.
 */
public class AppUserLookupImpl implements AppUserLookup {
    private static final String KEY = AppUserLookupImpl.class.getName() + ":";

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Optional<AppUser> findByUsername(String username) {
        if (username == null) return Optional.empty();
        RequestAttributes request = RequestContextHolder.getRequestAttributes();
        if (request != null && request.getAttribute(KEY + username, RequestAttributes.SCOPE_REQUEST) instanceof Long id) {
            AppUser cached = entityManager.find(AppUser.class, id);
            if (cached != null && username.equals(cached.getUsername())) return Optional.of(cached);
        }
        // Đọc hết kết quả ngay (không dùng getResultStream): ngoài giao dịch, như lúc Spring Security kiểm tra đăng nhập,
        // EntityManager dùng chung đóng kết nối ngay sau lệnh, đọc kiểu luồng sẽ lỗi "connection is closed".
        Optional<AppUser> found = entityManager.createQuery("select u from AppUser u where u.username = :username", AppUser.class)
                .setParameter("username", username).setMaxResults(1).getResultList().stream().findFirst();
        if (request != null) found.ifPresent(user -> request.setAttribute(KEY + username, user.getId(), RequestAttributes.SCOPE_REQUEST));
        return found;
    }
}
