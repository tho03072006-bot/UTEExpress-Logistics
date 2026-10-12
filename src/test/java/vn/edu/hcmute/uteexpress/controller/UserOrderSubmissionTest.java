package vn.edu.hcmute.uteexpress.controller;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.validation.BeanPropertyBindingResult;
import vn.edu.hcmute.uteexpress.controller.user.UserOrderController;
import vn.edu.hcmute.uteexpress.dto.OrderCreateRequest;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.service.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserOrderSubmissionTest {
    @Test void repeatedSubmissionReturnsSameOrderAndCannotBeReusedByDifferentAccount() {
        OrderService orders = mock(OrderService.class);
        var controller = new UserOrderController(orders, mock(SavedAddressService.class), mock(OrderDraftService.class),
                mock(OrderPaymentService.class), mock(ServiceReviewService.class));
        var authentication = new TestingAuthenticationToken("thotest", "ignored", "ROLE_USER");
        var session = new MockHttpSession();
        var request = new OrderCreateRequest();
        var errors = new BeanPropertyBindingResult(request, "form");
        var created = new Order(); created.setId(42L);
        when(orders.createOrder(request, "thotest")).thenReturn(created);
        String token = controller.submissionToken(authentication, session);
        assertEquals("redirect:/nguoi-dung/don-hang/42", controller.createOrder(request, errors, authentication, new ExtendedModelMap(), token, session));
        assertEquals("redirect:/nguoi-dung/don-hang/42", controller.createOrder(request, errors, authentication, new ExtendedModelMap(), token, session));
        verify(orders, times(1)).createOrder(request, "thotest");
        var another = new TestingAuthenticationToken("another", "ignored", "ROLE_USER");
        assertEquals("user/order-form", controller.createOrder(request, errors, another, new ExtendedModelMap(), token, session));
        verifyNoMoreInteractions(orders);
    }
}
