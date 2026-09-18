package vn.edu.hcmute.uteexpress.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import vn.edu.hcmute.uteexpress.entity.AppUser;
import vn.edu.hcmute.uteexpress.repository.AppUserRepository;
import vn.edu.hcmute.uteexpress.service.AuthService;
import vn.edu.hcmute.uteexpress.util.MailFromNameSetter;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private JavaMailSender mailSender;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private AuthService authService;

    @BeforeEach
    void setUp() {
        // Dung instance that thay vi mock: lop nay chi ghep chuoi ten nguoi gui,
        // khong goi ra ngoai nen mock cung khong loi gi hon.
        authService = new AuthServiceImpl(
                appUserRepository, passwordEncoder, mailSender,
                new MailFromNameSetter("UTEExpress", "uteexpress@example.com"));
    }

    @Test
    void registerCreatesDisabledAccountAndSendsSixDigitOtp() {
        authService.register(
                "khachhang", "matkhau123", "uteexpress8@gmail.com", "Khách Hàng");

        ArgumentCaptor<AppUser> userCaptor = ArgumentCaptor.forClass(AppUser.class);
        verify(appUserRepository).save(userCaptor.capture());
        AppUser user = userCaptor.getValue();
        assertFalse(user.isEnabled());
        assertTrue(passwordEncoder.matches("matkhau123", user.getPassword()));
        assertTrue(user.getOtpCode().matches("[0-9]{6}"));
        assertTrue(user.getOtpExpiry().isAfter(LocalDateTime.now()));

        ArgumentCaptor<SimpleMailMessage> mailCaptor =
                ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(mailCaptor.capture());
        assertEquals("uteexpress8@gmail.com", mailCaptor.getValue().getTo()[0]);
        assertTrue(mailCaptor.getValue().getText().contains(user.getOtpCode()));
    }

    @Test
    void verifyOtpActivatesOnlyMatchingUnexpiredCodeAndConsumesIt() {
        AppUser user = unverifiedUser();
        when(appUserRepository.findByUsername("khachhang"))
                .thenReturn(Optional.of(user));

        assertFalse(authService.verifyOtp("khachhang", "000000"));
        assertFalse(user.isEnabled());
        assertTrue(authService.verifyOtp("khachhang", "123456"));
        assertTrue(user.isEnabled());
        assertNull(user.getOtpCode());
        assertNull(user.getOtpExpiry());
        assertFalse(authService.verifyOtp("khachhang", "123456"));
    }

    @Test
    void verifyOtpRejectsExpiredAndUnknownAccount() {
        AppUser user = unverifiedUser();
        user.setOtpExpiry(LocalDateTime.now().minusMinutes(1));
        when(appUserRepository.findByUsername("khachhang"))
                .thenReturn(Optional.of(user));

        assertFalse(authService.verifyOtp("khachhang", "123456"));
        assertFalse(authService.verifyOtp("khongco", "123456"));
        verify(appUserRepository, never()).save(any());
    }

    @Test
    void resetPasswordSendsOnlyForEnabledAccountAndUsesNewOtp() {
        AppUser user = enabledUser();
        when(appUserRepository.findByEmail(user.getEmail()))
                .thenReturn(Optional.of(user));

        authService.sendPasswordResetOtp(user.getEmail());

        assertNotNull(user.getOtpCode());
        assertTrue(user.getOtpCode().matches("[0-9]{6}"));
        verify(mailSender).send(any(SimpleMailMessage.class));

        String code = user.getOtpCode();
        assertFalse(authService.resetPassword(user.getEmail(), "000000x", "newPass123"));
        assertTrue(authService.resetPassword(user.getEmail(), code, "newPass123"));
        assertTrue(passwordEncoder.matches("newPass123", user.getPassword()));
        assertNull(user.getOtpCode());
        assertFalse(authService.resetPassword(user.getEmail(), code, "otherPass"));
    }

    @Test
    void resetPasswordDoesNotSendForUnknownOrDisabledAccount() {
        AppUser disabled = unverifiedUser();
        when(appUserRepository.findByEmail("unknown@example.com"))
                .thenReturn(Optional.empty());
        when(appUserRepository.findByEmail(disabled.getEmail()))
                .thenReturn(Optional.of(disabled));

        authService.sendPasswordResetOtp("unknown@example.com");
        authService.sendPasswordResetOtp(disabled.getEmail());

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
        assertFalse(authService.resetPassword(disabled.getEmail(), "123456", "newPass123"));
    }

    @Test
    void smtpFailureIsReportedInsteadOfLoggingOtpAsFallback() {
        doThrow(new MailSendException("SMTP unavailable"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        assertThrows(IllegalStateException.class,
                () -> authService.register(
                        "khachhang", "matkhau123", "uteexpress8@gmail.com", "Khách Hàng"));
    }

    private AppUser unverifiedUser() {
        AppUser user = new AppUser("khachhang", "oldHash", "uteexpress8@gmail.com");
        user.setOtpCode("123456");
        user.setOtpExpiry(LocalDateTime.now().plusMinutes(10));
        return user;
    }

    private AppUser enabledUser() {
        AppUser user = unverifiedUser();
        user.setEnabled(true);
        user.setOtpCode(null);
        user.setOtpExpiry(null);
        return user;
    }
}
