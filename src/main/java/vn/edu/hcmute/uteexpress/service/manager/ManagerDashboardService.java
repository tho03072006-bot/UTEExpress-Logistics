package vn.edu.hcmute.uteexpress.service.manager;

import vn.edu.hcmute.uteexpress.dto.manager.ManagerDashboardResponse;

/**
 * Khai bao nghiep vu cua trang tong quan Manager/Admin.
 * Controller chi goi interface nay, khong tu truy van database.
 */
public interface ManagerDashboardService {

    ManagerDashboardResponse getSummary();
    /** Manager: đơn và Shipper của chi nhánh mình; Admin: toàn hệ thống như getSummary(). */
    default ManagerDashboardResponse getSummary(String actor) { return getSummary(); }
}
