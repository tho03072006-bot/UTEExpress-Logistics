package vn.edu.hcmute.uteexpress.dto.admin;

import jakarta.validation.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import vn.edu.hcmute.uteexpress.entity.AppUser;
import static org.junit.jupiter.api.Assertions.*;

class AdminAccountValidationTest {
    private static ValidatorFactory factory;
    private static Validator validator;
    @BeforeAll static void setUp() { factory = Validation.buildDefaultValidatorFactory(); validator = factory.getValidator(); }
    @AfterAll static void close() { factory.close(); }
    @ParameterizedTest @ValueSource(strings = {"", "abc", "1user", "user admin", "<script>", "user\nname"})
    void invalidUsernameIsRejected(String value) { var f = valid(); f.setUsername(value); assertFalse(validator.validate(f).isEmpty()); }
    @ParameterizedTest @ValueSource(strings = {"", "a", "1234", "<script>alert(1)</script>", "Name\nControl"})
    void invalidNameIsRejected(String value) { var f = valid(); f.setFullName(value); assertFalse(validator.validate(f).isEmpty()); }
    @ParameterizedTest @ValueSource(strings = {"", "abc", "a@localhost", "a b@example.com", "a@example.com\r\nBcc:x@example.com",
        "audit@example.c", ".name@example.com", "name.@example.com", "name..x@example.com", "name@-example.com", "name@example-.com", "name@example..com", "name@example.123"})
    void invalidEmailIsRejected(String value) { var f = valid(); f.setEmail(value); assertFalse(validator.validate(f).isEmpty()); }
    @ParameterizedTest @ValueSource(strings = {"audit@example.c", ".name@example.com", "name..x@example.com", "name@-example.com", "name@example..com", "name@example.123"})
    void internalAndPublicEmailPoliciesRejectTheSameInvalidInputs(String email) {
        var staff = new StaffAccountCreateRequest(); staff.setEmail(email);
        var edit = new vn.edu.hcmute.uteexpress.dto.ProfileUpdateRequest(); edit.setEmail(email);
        var profile = new AdminAccountProfileRequest(); profile.setEmail(email);
        var reset = new vn.edu.hcmute.uteexpress.dto.PasswordResetRequest(); reset.setEmail(email);
        for (Object form : java.util.List.of(staff, edit, profile, reset))
            assertFalse(validator.validateProperty(form, "email").isEmpty());
    }
    @ParameterizedTest @ValueSource(strings = {" USER+demo@EXAMPLE.COM ", "name.o'hara@example.com", "name@sub.example.com"})
    void acceptedEmailsStayCompatibleWithPublicRecovery(String email) {
        var form = valid(); form.setEmail(email); assertTrue(validator.validateProperty(form, "email").isEmpty());
        assertDoesNotThrow(() -> vn.edu.hcmute.uteexpress.validation.PublicAccountValidation.requireEmail(form.getEmail()));
    }
    @ParameterizedTest @ValueSource(strings = {"", "0123456789", "123", "09012345678", "090abc4567", "+1 901234567", "090\t1234567"})
    void invalidPhoneIsRejected(String value) { var f = valid(); f.setPhone(value); assertFalse(validator.validate(f).isEmpty()); }
    @Test void phoneLengthAndFormattingStayConsistentAcrossAllAccountAndOrderForms() {
        for(String value:java.util.List.of("+84 901 234 567", "090 123-45.67", " ".repeat(21)+"0901234567")) {
            var admin=new AdminAccountProfileRequest();admin.setPhone(value);
            var profile=new vn.edu.hcmute.uteexpress.dto.ProfileUpdateRequest();profile.setPhone(value);
            var address=new vn.edu.hcmute.uteexpress.dto.SavedAddressRequest();address.setContactPhone(value);
            var order=new vn.edu.hcmute.uteexpress.dto.OrderCreateRequest();order.setReceiverPhone(value);
            boolean expected=value.length()<=20;
            assertEquals(expected,validator.validateProperty(admin,"phone").isEmpty());
            assertEquals(expected,validator.validateProperty(profile,"phone").isEmpty());
            assertEquals(expected,validator.validateProperty(address,"contactPhone").isEmpty());
            assertEquals(expected,validator.validateProperty(order,"receiverPhone").isEmpty());
        }
    }
    @ParameterizedTest @ValueSource(strings = {"", "short", "onlylowercase123!", "NoNumbersHere!", "NoSpecial12345", "Has Space@2026"})
    void invalidPasswordIsRejected(String value) {
        var f = valid(); f.getCredentials().setPassword(value); f.getCredentials().setConfirmPassword(value);
        assertFalse(validator.validate(f).isEmpty());
    }
    @Test void byteLimitAndMismatchedNestedConfirmationAreChecked() {
        var f = valid(); var oversized = "Aa1!" + "ắ".repeat(24);
        f.getCredentials().setPassword(oversized); f.getCredentials().setConfirmPassword(oversized);
        assertFalse(validator.validate(f).isEmpty());
        f.getCredentials().setPassword("StrongPass@2026"); f.getCredentials().setConfirmPassword("Different@2026");
        assertFalse(validator.validate(f).isEmpty());
    }
    @Test void passwordControlCharactersCannotReachBcrypt() {
        var f = valid(); f.getCredentials().setPassword("StrongPass@2026\u0000");
        f.getCredentials().setConfirmPassword(f.getCredentials().getPassword());
        assertFalse(validator.validate(f).isEmpty());
    }
    @Test void missingNestedProfilePasswordAndRoleAreRejected() {
        var f = valid(); f.setRole(null); assertFalse(validator.validate(f).isEmpty());
        f.setRole(AppUser.Role.USER); f.setCredentials(null); assertFalse(validator.validate(f).isEmpty());
        var edit = new AdminAccountUpdateRequest(); edit.setRevision(0L); edit.setCurrentPassword("StrongPass@2026");
        edit.setReason("Kiểm thử hợp lệ"); edit.setActive(true); edit.setProfile(null);
        assertFalse(validator.validate(edit).isEmpty());
    }
    @Test void deleteRequiresRevisionReasonPasswordAndTypedUsername() {
        var f = new AdminAccountDeleteRequest(); assertFalse(validator.validate(f).isEmpty());
        f.setRevision(-1L); f.setCurrentPassword("StrongPass@2026"); f.setReason("Lý do hợp lệ"); f.setConfirmedUsername("userdemo");
        assertFalse(validator.validate(f).isEmpty()); f.setRevision(0L); f.setReason("Lý do\nđiều khiển");
        assertFalse(validator.validate(f).isEmpty()); f.setReason(" "); assertFalse(validator.validate(f).isEmpty());
        f.setReason("Lý do hợp lệ"); assertTrue(validator.validate(f).isEmpty());
    }
    @Test void vietnameseNamePlus84PhoneAndCaseNormalizationAreAccepted() {
        var f = valid(); f.setUsername(" USERDEMO "); f.setEmail(" USER@EXAMPLE.COM "); f.setPhone("+84 901 234 567");
        assertTrue(validator.validate(f).isEmpty()); assertEquals("userdemo", f.getUsername());
        assertEquals("user@example.com", f.getEmail()); assertEquals("0901234567", f.getPhone());
    }
    private AdminAccountCreateRequest valid() {
        var f = new AdminAccountCreateRequest(); f.setUsername("userdemo"); f.setFullName("Nguyễn Văn Thắng");
        f.setEmail("user@example.com"); f.setPhone("0901234567"); f.setRole(AppUser.Role.USER);
        f.getCredentials().setPassword("StrongPass@2026"); f.getCredentials().setConfirmPassword("StrongPass@2026"); return f;
    }
}
