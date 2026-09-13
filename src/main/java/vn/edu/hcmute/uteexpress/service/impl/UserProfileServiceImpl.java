package vn.edu.hcmute.uteexpress.service.impl;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.uteexpress.dto.PasswordChangeRequest;
import vn.edu.hcmute.uteexpress.dto.ProfileUpdateRequest;
import vn.edu.hcmute.uteexpress.entity.AppUser;
import vn.edu.hcmute.uteexpress.repository.AppUserRepository;
import vn.edu.hcmute.uteexpress.service.EmailChangeService;
import vn.edu.hcmute.uteexpress.service.UserProfileService;

@Service
@Transactional
public class UserProfileServiceImpl implements UserProfileService {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailChangeService emailChangeService;

    public UserProfileServiceImpl(AppUserRepository appUserRepository, PasswordEncoder passwordEncoder,
                                  EmailChangeService emailChangeService) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailChangeService = emailChangeService;
    }

    @Override
    @Transactional(readOnly = true)
    public AppUser findByUsername(String username) {
        return requireUser(username);
    }

    @Override
    public boolean updateProfile(String username, ProfileUpdateRequest form) {
        AppUser appUser = requireUser(username);
        String newEmail = form.getEmail().trim();

        // Họ tên và số điện thoại sai thì người dùng tự sửa lại được, lưu ngay không sao.
        appUser.setFullName(form.getFullName().trim());
        appUser.setPhone(form.getPhone().trim());
        appUserRepository.save(appUser);

        // Email thì khác: đây là đường khôi phục tài khoản duy nhất khi quên mật khẩu.
        // Gõ nhầm một địa chỉ không phải của mình rồi lưu thẳng là mất luôn đường về,
        // nên chỉ ghi đè sau khi người dùng nhập đúng mã gửi tới chính hộp thư mới đó.
        if (newEmail.equalsIgnoreCase(appUser.getEmail())) {
            return false;
        }
        emailChangeService.requestChange(username, newEmail);
        return true;
    }

    @Override
    public void changePassword(String username, PasswordChangeRequest form) {
        AppUser appUser = requireUser(username);

        // Mật khẩu trong DB là chuỗi băm BCrypt, không thể so sánh bằng equals.
        // matches() băm lại chuỗi người dùng vừa nhập rồi mới đối chiếu.
        if (!passwordEncoder.matches(form.getCurrentPassword(), appUser.getPassword())) {
            throw new IllegalStateException("Mật khẩu hiện tại không đúng.");
        }
        if (passwordEncoder.matches(form.getNewPassword(), appUser.getPassword())) {
            throw new IllegalStateException("Mật khẩu mới phải khác mật khẩu hiện tại.");
        }

        appUser.setPassword(passwordEncoder.encode(form.getNewPassword()));
        appUserRepository.save(appUser);
    }

    /**
     * Tên đăng nhập lấy từ Authentication nên gần như luôn tồn tại. Trường hợp duy nhất
     * không tìm thấy là tài khoản bị Admin xoá trong lúc người dùng còn đang mở phiên.
     */
    private AppUser requireUser(String username) {
        return appUserRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy tài khoản đang đăng nhập."));
    }
}
