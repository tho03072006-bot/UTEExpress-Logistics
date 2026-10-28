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
    private final jakarta.validation.Validator validator;
    private final jakarta.persistence.EntityManager entityManager;

    public UserProfileServiceImpl(AppUserRepository appUserRepository, PasswordEncoder passwordEncoder,
                                  EmailChangeService emailChangeService,jakarta.validation.Validator validator,
                                  jakarta.persistence.EntityManager entityManager) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailChangeService = emailChangeService;
        this.validator=validator;
        this.entityManager=entityManager;
    }

    @Override
    @Transactional(readOnly = true)
    public AppUser findByUsername(String username) {
        return requireUser(username);
    }

    @Override
    public boolean updateProfile(String username, ProfileUpdateRequest form) {
        if(form==null)throw new IllegalStateException("Thiếu dữ liệu tài khoản.");
        var errors=validator.validate(form);if(!errors.isEmpty())throw new IllegalStateException(errors.iterator().next().getMessage());
        AppUser appUser = appUserRepository.findLockedByUsername(username)
            .orElseThrow(()->new IllegalStateException("Không tìm thấy tài khoản."));
        entityManager.refresh(appUser, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if(!appUser.isEnabled() || appUser.isLockedByAdmin() || (appUser.getRole()!=AppUser.Role.USER && appUser.getRole()!=AppUser.Role.SHIPPER))
            throw new org.springframework.security.access.AccessDeniedException("Tài khoản không được cập nhật ở khu vực này.");
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
        if (form==null || !form.isPasswordConfirmed()) throw new IllegalStateException("Hai mật khẩu mới không khớp.");
        vn.edu.hcmute.uteexpress.validation.PublicAccountValidation.requirePassword(form.getNewPassword());
        AppUser appUser = appUserRepository.findLockedByUsername(username)
            .orElseThrow(() -> new IllegalStateException("Không tìm thấy tài khoản đang đăng nhập."));
        entityManager.refresh(appUser, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if(!appUser.isEnabled() || appUser.isLockedByAdmin() || (appUser.getRole()!=AppUser.Role.USER && appUser.getRole()!=AppUser.Role.SHIPPER))
            throw new org.springframework.security.access.AccessDeniedException("Tài khoản không được đổi mật khẩu ở khu vực này.");

        // Mật khẩu trong DB là chuỗi băm BCrypt, không thể so sánh bằng equals.
        // matches() băm lại chuỗi người dùng vừa nhập rồi mới đối chiếu.
        if (form.getCurrentPassword()==null || form.getCurrentPassword().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72
                || !passwordEncoder.matches(form.getCurrentPassword(), appUser.getPassword())) {
            throw new IllegalStateException("Mật khẩu hiện tại không đúng.");
        }
        if (passwordEncoder.matches(form.getNewPassword(), appUser.getPassword())) {
            throw new IllegalStateException("Mật khẩu mới phải khác mật khẩu hiện tại.");
        }

        appUser.setPassword(passwordEncoder.encode(form.getNewPassword()));
        appUser.setOtpCode(null); appUser.setOtpExpiry(null); appUser.setOtpAttempts(0); appUser.revokeSessions();
        if(appUser.getRole()==AppUser.Role.USER) emailChangeService.cancel(username);
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
