package vn.edu.hcmute.uteexpress.service.tracking;

import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import vn.edu.hcmute.uteexpress.entity.Order;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ShipperBulkInputTest {
    static Stream<List<Long>> invalidLists() {
        return Stream.of(Collections.emptyList(), Arrays.asList(1L,null),List.of(0L),List.of(-1L),Collections.nCopies(ShipperBulkService.MAX_ORDERS+1,1L));
    }
    @ParameterizedTest @MethodSource("invalidLists")
    void rejectMalformedRawListBeforeUpdatingAnyOrder(List<Long> ids) {
        var tracking=mock(TrackingService.class);
        assertThrows(IllegalArgumentException.class,()->new ShipperBulkService(tracking).update("shipper",ids,Order.OrderStatus.PICKED_UP));
        verifyNoInteractions(tracking);
    }
}
