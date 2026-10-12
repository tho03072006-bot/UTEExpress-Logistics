package vn.edu.hcmute.uteexpress.controller;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import vn.edu.hcmute.uteexpress.service.MomoPaymentService;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MomoCallbackControllerTest {
    @Test void browserResultCodeZeroOnlyNavigatesAndCannotSettlePayment() {
        var service = mock(MomoPaymentService.class);
        when(service.orderIdFromReference("UTE-12-Q-aabbccdd")).thenReturn(12L);
        var controller = new MomoCallbackController(service);
        String path = controller.browserReturn(Map.of("orderId", "UTE-12-Q-aabbccdd", "resultCode", "0",
                "amount", "1", "signature", "forged"), new RedirectAttributesModelMap());
        assertThat(path).isEqualTo("redirect:/nguoi-dung/thanh-toan/12");
        verify(service, never()).handleIpn(any()); verify(service, never()).check(any(), any());
    }

    @Test void forgedIpnReturnsBadRequestNotSuccessAcknowledgment() {
        var service = mock(MomoPaymentService.class);
        when(service.handleIpn(any())).thenThrow(new IllegalStateException("bad signature"));
        assertThat(new MomoCallbackController(service).ipn(Map.of("resultCode", 0)).getStatusCode().value()).isEqualTo(400);
    }
}
