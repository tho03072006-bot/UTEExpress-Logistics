package vn.edu.hcmute.uteexpress.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.edu.hcmute.uteexpress.entity.AppUser;
import vn.edu.hcmute.uteexpress.entity.UserProfileDetail;

public interface UserProfileDetailRepository extends JpaRepository<UserProfileDetail, Long> {

    Optional<UserProfileDetail> findByUser(AppUser user);

    /** Tùy chọn email cập nhật vận đơn theo địa chỉ email; rỗng nghĩa là chưa chỉnh, mặc định vẫn gửi. */
    @Query("select d.notifyOrderEmail from UserProfileDetail d where lower(d.user.email) = lower(:email)")
    List<Boolean> orderEmailPreferences(@Param("email") String email);
}
