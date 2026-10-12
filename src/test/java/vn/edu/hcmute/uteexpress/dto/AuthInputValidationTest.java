package vn.edu.hcmute.uteexpress.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import vn.edu.hcmute.uteexpress.controller.auth.AuthController.RegisterForm;
import vn.edu.hcmute.uteexpress.entity.SavedAddress;

import static org.junit.jupiter.api.Assertions.*;

class AuthInputValidationTest {
    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();

    @AfterAll static void closeFactory() { FACTORY.close(); }

    @Test void registrationAcceptsSchemaLimitsAndNormalizesIdentifiers() {
        RegisterForm form = registration();
        form.setUsername(" " + "a".repeat(50) + " ");
        form.setFullName("Tên " + "a".repeat(96));
        form.setEmail(" CUSTOMER@EXAMPLE.COM ");
        form.setPassword("a".repeat(72));
        assertTrue(VALIDATOR.validate(form).isEmpty());
        assertEquals("customer@example.com", form.getEmail());
    }

    @Test void registrationRejectsWhitespaceInvalidUsernameAndSchemaOverflow() {
        RegisterForm form = registration();
        form.setUsername("a".repeat(51));
        assertFalse(VALIDATOR.validate(form).isEmpty());
        form.setUsername("bad username");
        assertFalse(VALIDATOR.validate(form).isEmpty());
        form.setUsername("customer");
        form.setFullName("a".repeat(101));
        assertFalse(VALIDATOR.validate(form).isEmpty());
        form.setFullName("  ");
        form.setEmail("a".repeat(89) + "@example.com");
        assertFalse(VALIDATOR.validate(form).isEmpty());
        form.setEmail(" ");
        assertFalse(VALIDATOR.validate(form).isEmpty());
    }

    @Test void bcryptByteLimitIsCheckedOnRegisterChangeAndReset() {
        RegisterForm registration = registration();
        registration.setPassword("á".repeat(36)); // Exactly 72 UTF-8 bytes.
        assertTrue(VALIDATOR.validate(registration).isEmpty());
        registration.setPassword("á".repeat(36) + "a");
        assertFalse(VALIDATOR.validate(registration).isEmpty());
        PasswordChangeRequest change = new PasswordChangeRequest();
        change.setCurrentPassword("123456");
        change.setNewPassword("á".repeat(36));
        change.setConfirmPassword(change.getNewPassword());
        assertTrue(VALIDATOR.validate(change).isEmpty());
        change.setCurrentPassword("á".repeat(37));
        assertFalse(VALIDATOR.validate(change).isEmpty());
        PasswordResetRequest reset = reset();
        reset.setNewPassword("á".repeat(37));
        reset.setConfirmPassword(reset.getNewPassword());
        assertFalse(VALIDATOR.validate(reset).isEmpty());
    }

    @Test void resetRequiresSixDigitsAndMatchingPasswords() {
        PasswordResetRequest form = reset();
        assertTrue(VALIDATOR.validate(form).isEmpty());
        form.setOtp("12345");
        assertFalse(VALIDATOR.validate(form).isEmpty());
        form.setOtp(" 123456 ");
        form.setConfirmPassword("654321");
        assertFalse(VALIDATOR.validate(form).isEmpty());
        form.setConfirmPassword("123456");
        assertTrue(VALIDATOR.validate(form).isEmpty());
    }

    @Test void profileNormalizesAndRejectsInvalidPhoneAndControlCharacters() {
        ProfileUpdateRequest form = new ProfileUpdateRequest();
        form.setFullName(" Người Gửi ");
        form.setPhone(" 0901234567 ");
        form.setEmail(" CUSTOMER@EXAMPLE.COM ");
        assertTrue(VALIDATOR.validate(form).isEmpty());
        assertEquals("Người Gửi", form.getFullName());
        assertEquals("customer@example.com", form.getEmail());
        form.setPhone("abc");
        assertFalse(VALIDATOR.validate(form).isEmpty());
        form.setPhone("0901234567");
        form.setFullName("Tên\nẩn");
        assertFalse(VALIDATOR.validate(form).isEmpty());
    }

    @Test void savedAddressRejectsOversizeFieldsAndMissingType() {
        SavedAddressRequest form = address();
        assertTrue(VALIDATOR.validate(form).isEmpty());
        form.setAddressLine("a".repeat(256));
        assertFalse(VALIDATOR.validate(form).isEmpty());
        form.setAddressLine("a".repeat(255));
        assertTrue(VALIDATOR.validate(form).isEmpty());
        form.setAddressType(null);
        assertFalse(VALIDATOR.validate(form).isEmpty());
    }

    private RegisterForm registration() {
        RegisterForm form = new RegisterForm();
        form.setUsername("customer"); form.setEmail("customer@example.com");
        form.setPassword("123456"); form.setFullName("Người Gửi"); return form;
    }
    private PasswordResetRequest reset() {
        PasswordResetRequest form = new PasswordResetRequest();
        form.setEmail("customer@example.com"); form.setOtp("123456");
        form.setNewPassword("123456"); form.setConfirmPassword("123456"); return form;
    }
    private SavedAddressRequest address() {
        SavedAddressRequest form = new SavedAddressRequest();
        form.setLabel("Nhà"); form.setContactName("Người Nhận"); form.setContactPhone("0901234567");
        form.setAddressLine("1 Đường số 1"); form.setAddressType(SavedAddress.AddressType.RECEIVER); return form;
    }
}
