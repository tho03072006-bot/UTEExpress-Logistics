package vn.edu.hcmute.uteexpress.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.validation.DataBinder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceReviewRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void close() {
        factory.close();
    }

    @Test
    void rejectsShortReviewPaddedToFiftyCharactersByFormSubmission() {
        ServiceReviewRequest request = bindForm("A" + " ".repeat(49));

        assertTrue(hasContentViolation(request));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 49})
    void rejectsContentBelowMinimumAfterTrimming(int length) {
        ServiceReviewRequest request = bindForm(" \t\r\n" + "A".repeat(length) + " \t\r\n");

        assertTrue(hasContentViolation(request));
    }

    @ParameterizedTest
    @ValueSource(ints = {50, 1000})
    void acceptsContentAtBoundariesAfterTrimming(int length) {
        String content = "A".repeat(length);
        ServiceReviewRequest request = bindForm(" \t" + content + "\r\n ");

        assertTrue(validator.validate(request).isEmpty());
        assertEquals(content, request.getContent());
    }

    @Test
    void rejectsContentAboveMaximumAfterTrimming() {
        ServiceReviewRequest request = bindForm(" " + "A".repeat(1001) + " ");

        assertTrue(hasContentViolation(request));
    }

    @Test
    void rejectsMissingContent() {
        ServiceReviewRequest request = new ServiceReviewRequest();
        request.setRating(5);
        request.setContent(null);

        assertTrue(hasContentViolation(request));
    }

    private static boolean hasContentViolation(ServiceReviewRequest request) {
        return validator.validate(request).stream()
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("content"));
    }

    private static ServiceReviewRequest bindForm(String content) {
        ServiceReviewRequest request = new ServiceReviewRequest();
        DataBinder binder = new DataBinder(request);
        MutablePropertyValues values = new MutablePropertyValues();
        values.add("rating", "5");
        values.add("content", content);
        binder.bind(values);
        assertFalse(binder.getBindingResult().hasErrors());
        return request;
    }
}
