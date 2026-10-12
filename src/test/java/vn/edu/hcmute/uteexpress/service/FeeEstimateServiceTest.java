package vn.edu.hcmute.uteexpress.service;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import vn.edu.hcmute.uteexpress.entity.Order;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class FeeEstimateServiceTest {
    private final OrderService orders = mock(OrderService.class);
    private final FeeEstimateService service = new FeeEstimateService(orders);

    @Test void distanceTiersAndServiceFactors() {
        assertEquals(0, FeeEstimateService.distanceFee(5, Order.ServiceType.STANDARD).compareTo(BigDecimal.ZERO));
        assertEquals(new BigDecimal("7000"), FeeEstimateService.distanceFee(12, Order.ServiceType.STANDARD));
        assertEquals(new BigDecimal("25000"), FeeEstimateService.distanceFee(30, Order.ServiceType.STANDARD));
        assertEquals(new BigDecimal("31000"), FeeEstimateService.distanceFee(50, Order.ServiceType.STANDARD));
        assertEquals(new BigDecimal("106000"), FeeEstimateService.distanceFee(300, Order.ServiceType.STANDARD));
        assertEquals(new BigDecimal("276000"), FeeEstimateService.distanceFee(2000, Order.ServiceType.STANDARD));
        assertEquals(new BigDecimal("9100"), FeeEstimateService.distanceFee(12, Order.ServiceType.EXPRESS), "x1,3 làm tròn 100 đ");
        assertEquals(new BigDecimal("11200"), FeeEstimateService.distanceFee(12, Order.ServiceType.SUPER_EXPRESS), "x1,6");
    }

    @Test void quotesAddDistanceToTheRealBaseFeeAndRespectSuperExpressRange() {
        when(orders.estimateFee(eq(Order.ServiceType.STANDARD), anyDouble())).thenReturn(new BigDecimal("22500"));
        when(orders.estimateFee(eq(Order.ServiceType.EXPRESS), anyDouble())).thenReturn(new BigDecimal("32500"));
        when(orders.estimateFee(eq(Order.ServiceType.SUPER_EXPRESS), anyDouble())).thenReturn(new BigDecimal("47500"));
        var near = service.quotes(2.5, 12.0, null);
        assertEquals(3, near.size());
        assertEquals(new BigDecimal("29500"), near.get(0).total());
        assertEquals("1 - 2 ngày", near.get(0).deliveryTime());
        assertTrue(near.get(2).available());
        var far = service.quotes(2.5, 120.0, Order.ServiceType.SUPER_EXPRESS);
        assertEquals(1, far.size());
        assertFalse(far.get(0).available());
        assertNull(far.get(0).total());
        var noDistance = service.quotes(2.5, null, Order.ServiceType.STANDARD).get(0);
        assertEquals(new BigDecimal("22500"), noDistance.total(), "không nhập khoảng cách thì đúng bằng cước khi tạo đơn");
        assertEquals("3 - 5 ngày", noDistance.deliveryTime());
        assertThrows(IllegalArgumentException.class, () -> service.quotes(0, 10.0, null));
        assertThrows(IllegalArgumentException.class, () -> service.quotes(2, 2001.0, null));
        assertThrows(IllegalArgumentException.class, () -> service.quotes(2, -1.0, null));
    }
}
