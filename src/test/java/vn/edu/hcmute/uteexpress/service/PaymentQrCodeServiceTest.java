package vn.edu.hcmute.uteexpress.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class PaymentQrCodeServiceTest {
    @Test void providerPayloadIsEncodedAsPixelsInsteadOfInterpretedAsMarkup() {
        String svg = new PaymentQrCodeService().toSvg("<script>alert('x')</script>");
        assertThat(svg).startsWith("<svg").contains("<path d=\"M").doesNotContain("<script").doesNotContain("alert");
    }
}
