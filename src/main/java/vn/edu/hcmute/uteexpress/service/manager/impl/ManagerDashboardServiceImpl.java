package vn.edu.hcmute.uteexpress.service.manager.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.uteexpress.dto.manager.ManagerDashboardResponse;
import vn.edu.hcmute.uteexpress.entity.AppUser;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.repository.manager.ManagerOrderStatisticsRepository;
import vn.edu.hcmute.uteexpress.repository.manager.ManagerUserStatisticsRepository;
import vn.edu.hcmute.uteexpress.service.manager.ManagerDashboardService;

/**
 * Cai dat nghiep vu thong ke tong quan bang cac phep dem tu Repository.
 */
@Service
public class ManagerDashboardServiceImpl implements ManagerDashboardService {

    private final ManagerUserStatisticsRepository userStatisticsRepository;
    private final ManagerOrderStatisticsRepository orderStatisticsRepository;

    public ManagerDashboardServiceImpl(ManagerUserStatisticsRepository userStatisticsRepository,
                                       ManagerOrderStatisticsRepository orderStatisticsRepository) {
        this.userStatisticsRepository = userStatisticsRepository;
        this.orderStatisticsRepository = orderStatisticsRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public ManagerDashboardResponse getSummary() {
        long totalUsers = userStatisticsRepository.count();
        long totalOrders = orderStatisticsRepository.count();
        long totalShippers = userStatisticsRepository.countByRole(AppUser.Role.SHIPPER);
        long pendingOrders = orderStatisticsRepository.countByStatus(Order.OrderStatus.PENDING_PICKUP);

        return new ManagerDashboardResponse(
                totalUsers,
                totalOrders,
                totalShippers,
                pendingOrders
        );
    }
}
