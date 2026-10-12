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
        when(appUserRepository.findByUsernameForUpdate("khachhang"))
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
        when(appUserRepository.findByUsernameForUpdate("khachhang"))
                .thenReturn(Optional.of(user));

        assertFalse(authService.verifyOtp("khachhang", "123456"));
        assertFalse(authService.verifyOtp("khongco", "123456"));
        assertNull(user.getOtpCode());
        verify(appUserRepository).save(user);
    }

    @Test
    void resetPasswordSendsOnlyForEnabledAccountAndUsesNewOtp() {
        AppUser user = enabledUser();
        when(appUserRepository.findByEmailForUpdate(user.getEmail()))
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
        when(appUserRepository.findByEmailForUpdate("unknown@example.com"))
                .thenReturn(Optional.empty());
        when(appUserRepository.findByEmailForUpdate(disabled.getEmail()))
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

    @Test
    void activationFifthFailureConsumesCodeAndNewServiceCannotReuseIt() {
        AppUser user = unverifiedUser();
        when(appUserRepository.findByUsernameForUpdate("khachhang")).thenReturn(Optional.of(user));
        for (int i = 0; i < 5; i++) assertFalse(authService.verifyOtp("khachhang", "000000"));
        assertNull(user.getOtpCode());
        assertNull(user.getOtpExpiry());
        setUp(); // Simulate a new application instance after the code was consumed in DB.
        assertFalse(authService.verifyOtp("khachhang", "123456"));
    }

    @Test
    void activationCorrectCodeOnFifthAttemptIsAllowed() {
        AppUser user = unverifiedUser();
        when(appUserRepository.findByUsernameForUpdate("khachhang")).thenReturn(Optional.of(user));
        for (int i = 0; i < 4; i++) assertFalse(authService.verifyOtp("khachhang", "000000"));
        assertTrue(authService.verifyOtp("khachhang", "123456"));
    }

    @Test
    void resetFifthFailureConsumesCodeWithoutChangingPassword() {
        AppUser user = unverifiedUser();
        user.setEnabled(true);
        when(appUserRepository.findByEmailForUpdate(user.getEmail())).thenReturn(Optional.of(user));
        for (int i = 0; i < 5; i++) assertFalse(authService.resetPassword(user.getEmail(), "000000", "newPass123"));
        assertNull(user.getOtpCode());
        assertEquals("oldHash", user.getPassword());
        assertFalse(authService.resetPassword(user.getEmail(), "123456", "newPass123"));
    }

    @Test
    void resendActivationRecoversExpiredAccountAndEnforcesCooldown() {
        AppUser user = unverifiedUser();
        user.setOtpExpiry(LocalDateTime.now().minusMinutes(1));
        when(appUserRepository.findByUsernameForUpdate("khachhang")).thenReturn(Optional.of(user));
        authService.resendActivationOtp("khachhang");
        assertTrue(user.getOtpExpiry().isAfter(LocalDateTime.now()));
        assertThrows(IllegalStateException.class, () -> authService.resendActivationOtp("khachhang"));
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void resetSendCooldownSurvivesServiceRestartUsingIssueTimeInDatabase() {
        AppUser user = enabledUser();
        when(appUserRepository.findByEmailForUpdate(user.getEmail())).thenReturn(Optional.of(user));
        authService.sendPasswordResetOtp(user.getEmail());
        setUp();
        assertThrows(IllegalStateException.class, () -> authService.sendPasswordResetOtp(user.getEmail()));
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void registrationNormalizesBeforeUniqueChecksAndPreservesSixDigitDemoPolicy() {
        authService.register(" khachhang ", "123456", " CUSTOMER@EXAMPLE.COM ", " Khách Hàng ");
        ArgumentCaptor<AppUser> userCaptor = ArgumentCaptor.forClass(AppUser.class);
        verify(appUserRepository).save(userCaptor.capture());
        assertEquals("khachhang", userCaptor.getValue().getUsername());
        assertEquals("customer@example.com", userCaptor.getValue().getEmail());
        assertEquals("Khách Hàng", userCaptor.getValue().getFullName());
        assertTrue(passwordEncoder.matches("123456", userCaptor.getValue().getPassword()));
        verify(appUserRepository).existsByUsername("khachhang");
        verify(appUserRepository).existsByEmail("customer@example.com");
    }

    @Test
    void serviceRejectsOversizedOrShortPasswordBeforeEncodingOrPersistence() {
        assertThrows(IllegalStateException.class,
                () -> authService.register("khachhang", "1", "customer@example.com", "Khách"));
        assertThrows(IllegalStateException.class,
                () -> authService.register("khachhang", "á".repeat(37), "customer@example.com", "Khách"));
        assertThrows(IllegalStateException.class,
                () -> authService.resetPassword("customer@example.com", "123456", "á".repeat(37)));
        verify(appUserRepository, never()).save(any());
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
