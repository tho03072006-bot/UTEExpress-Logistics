package vn.edu.hcmute.uteexpress.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.uteexpress.entity.AppUser;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.repository.AppUserRepository;
import vn.edu.hcmute.uteexpress.repository.OrderRepository;
import vn.edu.hcmute.uteexpress.repository.admin.StaffAccountRepository;
import vn.edu.hcmute.uteexpress.security.admin.StaffPrincipal;

@Service
@Transactional(readOnly = true)
public class OrderAccessPolicy {
    private final OrderRepository orders;
    private final AppUserRepository users;
    private final StaffAccountRepository staff;

    public OrderAccessPolicy(OrderRepository orders, AppUserRepository users, StaffAccountRepository staff) {
        this.orders = orders; this.users = users; this.staff = staff;
    }

    public boolean canReadTrackingCode(String code, Authentication authentication) {
        return orders.findByTrackingCode(code).map(order -> canRead(order, authentication)).orElse(false);
    }

    public Order requireReadableOrder(long id, Authentication authentication) {
        Order order = orders.findById(id).orElseThrow(this::denied);
        if (!canRead(order, authentication)) { throw denied(); }
        return order;
    }

    private boolean canRead(Order order, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) { return false; }
        if (authentication.getPrincipal() instanceof StaffPrincipal principal) {
            return staff.findByUserUsername(principal.getUsername())
                    .filter(principal::matchesAccount)
                    .filter(account -> !account.isPasswordChangeRequired())
                    .filter(account -> account.getUser().getRole() == AppUser.Role.ADMIN
                            || account.getUser().getRole() == AppUser.Role.MANAGER).isPresent();
        }
        AppUser requester = users.findByUsername(authentication.getName()).orElse(null);
        if (requester == null || !requester.isEnabled()) { return false; }
        if (requester.getRole() == AppUser.Role.USER && hasRole(authentication, "USER")) {
            return order.getSender() != null && requester.getId().equals(order.getSender().getId());
        }
        return requester.getRole() == AppUser.Role.SHIPPER && hasRole(authentication, "SHIPPER")
                && order.getShipper() != null && requester.getId().equals(order.getShipper().getId());
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_" + role));
    }

    private org.springframework.security.access.AccessDeniedException denied() {
        return new org.springframework.security.access.AccessDeniedException("Không được truy cập vận đơn này.");
    }
}
