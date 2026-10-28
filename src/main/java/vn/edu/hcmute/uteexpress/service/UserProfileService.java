package vn.edu.hcmute.uteexpress.service;

import vn.edu.hcmute.uteexpress.dto.PasswordChangeRequest;
import vn.edu.hcmute.uteexpress.dto.ProfileUpdateRequest;
import vn.edu.hcmute.uteexpress.entity.AppUser;

/**
 * Hồ sơ tài khoản của người gửi hàng: xem thông tin, sửa thông tin, đổi mật khẩu.
 * TV1 phụ trách.
 *
 * Chức năng này chỉ ĐỌC và GHI lại các cột đã có sẵn của AppUser (full_name, email,
 * phone) nên không sinh entity mới và không cần script tạo bảng - đây là ngoại lệ
 * duy nhất so với các chức năng khác của TV1. Tuyệt đối không thêm cột vào AppUser.
 */
public interface UserProfileService {

    /** Lấy tài khoản đang đăng nhập để đổ ra trang hồ sơ. */
    AppUser findByUsername(String username);

    /**
     * Cập nhật hồ sơ. Họ tên và số điện thoại được lưu ngay; riêng email thì KHÔNG ghi
     * đè trực tiếp mà chuyển sang luồng xác thực OTP của EmailChangeService.
     *
     * @return true nếu người dùng có đổi email và vừa được gửi mã OTP - Controller dựa
     *         vào đây để đưa họ sang trang nhập mã. false nghĩa là đã lưu xong hết.
     * @throws IllegalStateException nếu email mới đã thuộc về một tài khoản khác
     */
    boolean updateProfile(String username, ProfileUpdateRequest form);

    /**
     * Đổi mật khẩu cho tài khoản đang đăng nhập.
     * Ném IllegalStateException nếu mật khẩu hiện tại sai hoặc mật khẩu mới trùng mật khẩu cũ.
     */
    void changePassword(String username, PasswordChangeRequest form);
}
