package vn.edu.hcmute.uteexpress.service.tracking;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import vn.edu.hcmute.uteexpress.entity.AppUser;
import vn.edu.hcmute.uteexpress.repository.*;
import vn.edu.hcmute.uteexpress.service.routing.BranchScope;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CodSettlementInputTest {
    static Stream<List<Long>> invalidLists() {
        return Stream.of(Collections.emptyList(), Arrays.asList(1L, null), List.of(0L), List.of(-1L),
                Collections.nCopies(CodSettlementService.MAX_ORDERS + 1, 1L));
    }

    @ParameterizedTest @MethodSource("invalidLists")
    void invalidRawIdsAreRejectedBeforeAnyCashDatabaseWork(List<Long> ids) {
        var access=mock(LogisticsAccess.class);
        var settlements=mock(CodSettlementRepository.class);
        var items=mock(CodSettlementItemRepository.class);
        var shipper=new AppUser("shipper", "test-only-hash", "shipper@test.invalid"); shipper.setId(1L);
        when(access.shipper("shipper")).thenReturn(shipper);
        var service=new CodSettlementService(access, settlements, items, Clock.systemUTC(), mock(BranchScope.class));
        assertThrows(IllegalArgumentException.class, () -> service.submit("shipper", ids, BigDecimal.ONE, "Phiếu kiểm thử"));
        verifyNoInteractions(settlements, items);
    }
}
