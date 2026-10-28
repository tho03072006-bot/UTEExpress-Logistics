package vn.edu.hcmute.uteexpress.repository;

import java.util.Optional;
import vn.edu.hcmute.uteexpress.entity.AppUser;

/** Phần tự viết của AppUserRepository: tìm tài khoản theo tên đăng nhập, dùng lại kết quả trong cùng một lượt tải trang. */
public interface AppUserLookup {
    Optional<AppUser> findByUsername(String username);
}
