package vn.edu.hcmute.uteexpress.service.tracking;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import vn.edu.hcmute.uteexpress.entity.AppUser;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.repository.AppUserRepository;
import vn.edu.hcmute.uteexpress.repository.admin.StaffAccountRepository;

@Component
public class LogisticsAccess {
    private final AppUserRepository users;
    private final StaffAccountRepository staff;
    public LogisticsAccess(AppUserRepository users, StaffAccountRepository staff) {
        this.users = users; this.staff = staff;
    }
    public AppUser shipper(String username) { return requireRole(username, AppUser.Role.SHIPPER); }
    public AppUser customer(String username) { return requireRole(username, AppUser.Role.USER); }
    public AppUser internal(String username) {
        var account = staff.findByUserUsername(username).orElseThrow(LogisticsAccess::denied);
        var user = account.getUser();
        if (!account.isActive() || !user.isEnabled()
                || (user.getRole() != AppUser.Role.MANAGER && user.getRole() != AppUser.Role.ADMIN)) throw denied();
        return user;
    }
    public void checkOrder(Order order, String username) {
        AppUser user = users.findByUsername(username).orElseThrow(LogisticsAccess::denied);
        if (!user.isEnabled()) throw denied();
        if (user.getRole() == AppUser.Role.USER && user.getId().equals(order.getSender().getId())) return;
        if (user.getRole() == AppUser.Role.SHIPPER && order.getShipper() != null
                && user.getId().equals(order.getShipper().getId())) return;
        if (user.getRole() == AppUser.Role.ADMIN || user.getRole() == AppUser.Role.MANAGER) { internal(username); return; }
        throw denied();
    }
    private AppUser requireRole(String username, AppUser.Role role) {
        var user = users.findByUsername(username).orElseThrow(LogisticsAccess::denied);
        if (!user.isEnabled() || user.getRole() != role) throw denied();
        return user;
    }
    private static AccessDeniedException denied() { return new AccessDeniedException("Bạn không có quyền truy cập dữ liệu này."); }
}
