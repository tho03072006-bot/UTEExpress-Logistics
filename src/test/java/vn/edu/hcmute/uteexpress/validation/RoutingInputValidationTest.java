package vn.edu.hcmute.uteexpress.validation;

import jakarta.validation.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import vn.edu.hcmute.uteexpress.dto.*;
import vn.edu.hcmute.uteexpress.dto.admin.BranchUpdateRequest;
import static org.junit.jupiter.api.Assertions.*;

class RoutingInputValidationTest {
    static ValidatorFactory factory;
    static Validator validator;
    @BeforeAll static void open(){factory=Validation.buildDefaultValidatorFactory();validator=factory.getValidator();}
    @AfterAll static void close(){factory.close();}
    @ParameterizedTest @ValueSource(strings={"-----","....."," ' - . ","Địa\nchỉ thử"})
    void meaninglessOrControlCharacterAddressesAreRejectedEverywhere(String address) {
        assertThrows(IllegalArgumentException.class,()->ShippingInputValidation.requireAddress(address));
        var create=new OrderCreateRequest();create.setSenderAddress(address);create.setReceiverAddress(address);
        var quote=new OrderQuoteRequest();quote.setSenderAddress(address);quote.setReceiverAddress(address);
        var saved=new SavedAddressRequest();saved.setAddressLine(address);
        assertFalse(validator.validateProperty(create,"senderAddress").isEmpty());assertFalse(validator.validateProperty(create,"receiverAddress").isEmpty());
        assertFalse(validator.validateProperty(quote,"senderAddress").isEmpty());assertFalse(validator.validateProperty(quote,"receiverAddress").isEmpty());
        assertFalse(validator.validateProperty(saved,"addressLine").isEmpty());
    }
    @ParameterizedTest @ValueSource(strings={"123/4A đường số 5","Chung cư Hòa Bình","1A/12 ngõ 5"})
    void commonVietnameseStreetFormatsRemainValid(String address) {
        assertDoesNotThrow(()->ShippingInputValidation.requireAddress(address));
    }
    @Test void explicitMissingPickupModeIsInvalidForQuoteAsForCreate() {
        var quote=new OrderQuoteRequest();quote.setPickupMode(null);
        var create=new OrderCreateRequest();create.setPickupMode(null);
        assertFalse(validator.validateProperty(quote,"pickupMode").isEmpty());assertFalse(validator.validateProperty(create,"pickupMode").isEmpty());
    }
    @Test void branchMetadataCannotContainControlCharactersOrPunctuationOnlyName() {
        var branch=new BranchUpdateRequest();branch.setName("---");branch.setHubAddress("-----");branch.setReason("Thay\nđịa chỉ");
        assertFalse(validator.validateProperty(branch,"name").isEmpty());assertFalse(validator.validateProperty(branch,"hubAddress").isEmpty());
        assertFalse(validator.validateProperty(branch,"reason").isEmpty());
        branch.setName("Kho SGN 1");branch.setHubAddress("Số 1 Võ Văn Ngân");branch.setReason("Cập nhật địa chỉ kho mới.");
        assertTrue(validator.validateProperty(branch,"name").isEmpty());assertTrue(validator.validateProperty(branch,"hubAddress").isEmpty());
        assertTrue(validator.validateProperty(branch,"reason").isEmpty());
    }
}
