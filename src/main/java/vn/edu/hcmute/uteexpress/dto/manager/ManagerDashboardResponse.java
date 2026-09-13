package vn.edu.hcmute.uteexpress.dto.manager;

/**
 * Du lieu tong hop ma Service gui sang giao dien tong quan Manager/Admin.
 * DTO giup Controller khong phai truyen truc tiep Entity sang giao dien.
 */
public class ManagerDashboardResponse {

    private final long totalUsers;
    private final long totalOrders;
    private final long totalShippers;
    private final long pendingOrders;

    public ManagerDashboardResponse(long totalUsers, long totalOrders,
                                    long totalShippers, long pendingOrders) {
        this.totalUsers = totalUsers;
        this.totalOrders = totalOrders;
        this.totalShippers = totalShippers;
        this.pendingOrders = pendingOrders;
    }

    public long getTotalUsers() {
        return totalUsers;
    }

    public long getTotalOrders() {
        return totalOrders;
    }

    public long getTotalShippers() {
        return totalShippers;
    }

    public long getPendingOrders() {
        return pendingOrders;
    }
}
