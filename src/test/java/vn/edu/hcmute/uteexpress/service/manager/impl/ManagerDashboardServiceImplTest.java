package vn.edu.hcmute.uteexpress.service.manager.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.edu.hcmute.uteexpress.dto.manager.ManagerDashboardResponse;
import vn.edu.hcmute.uteexpress.entity.AppUser;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.repository.manager.ManagerOrderStatisticsRepository;
import vn.edu.hcmute.uteexpress.repository.manager.ManagerUserStatisticsRepository;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManagerDashboardServiceImplTest {

    @Mock
    private ManagerUserStatisticsRepository userStatisticsRepository;

    @Mock
    private ManagerOrderStatisticsRepository orderStatisticsRepository;

    @InjectMocks
    private ManagerDashboardServiceImpl managerDashboardService;

    @Test
    void getSummaryReturnsCountsFromRepositories() {
        when(userStatisticsRepository.count()).thenReturn(12L);
        when(orderStatisticsRepository.count()).thenReturn(30L);
        when(userStatisticsRepository.countByRole(AppUser.Role.SHIPPER)).thenReturn(4L);
        when(orderStatisticsRepository.countByStatus(Order.OrderStatus.PENDING_PICKUP)).thenReturn(6L);

        ManagerDashboardResponse summary = managerDashboardService.getSummary();

        assertAll(
                () -> assertEquals(12L, summary.getTotalUsers()),
                () -> assertEquals(30L, summary.getTotalOrders()),
                () -> assertEquals(4L, summary.getTotalShippers()),
                () -> assertEquals(6L, summary.getPendingOrders())
        );
    }
}
