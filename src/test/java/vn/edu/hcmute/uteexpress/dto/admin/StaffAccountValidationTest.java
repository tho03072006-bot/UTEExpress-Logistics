package vn.edu.hcmute.uteexpress.dto.admin;

import jakarta.validation.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import vn.edu.hcmute.uteexpress.entity.AppUser;
import static org.junit.jupiter.api.Assertions.*;

class StaffAccountValidationTest {
    private static ValidatorFactory factory;
    private static Validator validator;
    @BeforeAll static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }
    @AfterAll static void close() { factory.close(); }
    @Test void acceptsVietnameseNameAndNormalizesIdentity() {
        var form = validForm();
        form.setUsername("  Manager01  "); form.setEmail(" WORK@Example.Com ");
        assertEquals("manager01", form.getUsername());
        assertEquals("work@example.com", form.getEmail());
        assertTrue(validator.validate(form).isEmpty());
    }
    @ParameterizedTest
    @ValueSource(strings = {"short", "alllowercase123!", "ALLUPPERCASE123!", "NoDigitsHere!!", "NoSpecial12345", "Spaces Here@123", ""})
    void rejectsWeakPasswords(String password) {
        var form = validForm();
        form.setPassword(password); form.setConfirmPassword(password);
        assertFalse(validator.validate(form).isEmpty());
    }
    @Test void rejectsPasswordsOverBcryptByteLimit() {
        var form = validForm();
        form.setPassword("Ab1!" + "ế".repeat(24)); form.setConfirmPassword(form.getPassword());
        assertFalse(form.isPasswordWithinByteLimit());
        assertFalse(validator.validate(form).isEmpty());
    }
    @Test void rejectsMismatchedConfirmation() {
        var form = validForm(); form.setConfirmPassword("DifferentPass@2026");
        assertFalse(validator.validate(form).isEmpty());
    }
    @ParameterizedTest
    @ValueSource(strings = {"abc", "0admin", "admin name", "<script>", "đăngnhập"})
    void rejectsInvalidUsername(String username) {
        var form = validForm(); form.setUsername(username);
        assertFalse(validator.validate(form).isEmpty());
    }
    @ParameterizedTest
    @ValueSource(strings = {"bad", "x@y", "a b@example.com", "@example.com"})
    void rejectsInvalidEmail(String email) {
        var form = validForm(); form.setEmail(email);
        assertFalse(validator.validate(form).isEmpty());
    }
    @Test void rejectsOversizedFieldsAndUnprivilegedRole() {
        var form = validForm(); form.setFullName("A".repeat(101));
        form.setRole(AppUser.Role.USER); form.setEmail("a".repeat(95) + "@example.com");
        assertTrue(validator.validate(form).size() >= 3);
    }
    @Test void rejectsNullRoleStatusAndRevisionInUpdates() {
        assertFalse(validator.validate(new StaffAccountUpdateRequest()).isEmpty());
    }
    private StaffAccountCreateRequest validForm() {
        var form = new StaffAccountCreateRequest();
        form.setUsername("manager01"); form.setFullName("Nguyễn Văn Thắng");
        form.setEmail("manager@example.com"); form.setPassword("StrongPassword@2026");
        form.setConfirmPassword("StrongPassword@2026"); form.setRole(AppUser.Role.MANAGER);
        return form;
    }
}

