package vn.edu.hcmute.uteexpress.security;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import vn.edu.hcmute.uteexpress.entity.AppUser;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.entity.admin.StaffAccount;
import vn.edu.hcmute.uteexpress.repository.AppUserRepository;
import vn.edu.hcmute.uteexpress.repository.OrderRepository;
import vn.edu.hcmute.uteexpress.repository.admin.StaffAccountRepository;
import vn.edu.hcmute.uteexpress.security.admin.StaffPrincipal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class OrderAccessPolicyTest {
    private final OrderRepository orders = mock(OrderRepository.class);
    private final AppUserRepository users = mock(AppUserRepository.class);
    private final StaffAccountRepository staff = mock(StaffAccountRepository.class);
    private final OrderAccessPolicy policy = new OrderAccessPolicy(orders, users, staff);

    @Test void activeOwnerCanReadHisOrderByCodeAndId() {
        AppUser owner = user(1, "owner", AppUser.Role.USER);
        Order order = order(owner, null);
        when(users.findByUsername("owner")).thenReturn(Optional.of(owner));
        Authentication authentication = authentication("owner", "USER");
        assertThat(policy.canReadTrackingCode("UTE001", authentication)).isTrue();
        assertThat(policy.requireReadableOrder(7L, authentication)).isSameAs(order);
    }

    @Test void customerCannotReadAnotherCustomersOrder() {
        AppUser owner = user(1, "owner", AppUser.Role.USER);
        order(owner, null);
        AppUser other = user(2, "other", AppUser.Role.USER);
        when(users.findByUsername("other")).thenReturn(Optional.of(other));
        Authentication authentication = authentication("other", "USER");
        assertThat(policy.canReadTrackingCode("UTE001", authentication)).isFalse();
        assertThatThrownBy(() -> policy.requireReadableOrder(7L, authentication))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test void assignedShipperCanReadButOtherShippersCannot() {
        AppUser assigned = user(2, "assigned", AppUser.Role.SHIPPER);
        Order order = order(user(1, "owner", AppUser.Role.USER), assigned);
        when(users.findByUsername("assigned")).thenReturn(Optional.of(assigned));
        assertThat(policy.requireReadableOrder(7L, authentication("assigned", "SHIPPER"))).isSameAs(order);
        AppUser other = user(3, "other", AppUser.Role.SHIPPER);
        when(users.findByUsername("other")).thenReturn(Optional.of(other));
        assertThat(policy.canReadTrackingCode("UTE001", authentication("other", "SHIPPER"))).isFalse();
    }

    @Test void disabledOrRoleChangedSessionsLoseAccessImmediately() {
        AppUser owner = user(1, "owner", AppUser.Role.USER);
        order(owner, null);
        when(users.findByUsername("owner")).thenReturn(Optional.of(owner));
        Authentication session = authentication("owner", "USER");
        owner.setEnabled(false);
        assertThat(policy.canReadTrackingCode("UTE001", session)).isFalse();
        owner.setEnabled(true);
        owner.setRole(AppUser.Role.SHIPPER);
        assertThat(policy.canReadTrackingCode("UTE001", session)).isFalse();
    }

    @Test void matchingDatabaseOwnerStillNeedsTheCorrectSessionAuthority() {
        AppUser owner = user(1, "owner", AppUser.Role.USER);
        order(owner, null);
        when(users.findByUsername("owner")).thenReturn(Optional.of(owner));
        assertThat(policy.canReadTrackingCode("UTE001", authentication("owner", "SHIPPER"))).isFalse();
    }

    @ParameterizedTest @ValueSource(strings = {"ADMIN", "MANAGER"})
    void activeInternalPrincipalCanReadAnyOrder(String role) {
        Order order = order(user(1, "owner", AppUser.Role.USER), null);
        StaffAccount account = staffAccount(role);
        Authentication authentication = internal(account);
        when(staff.findByUserUsername("staff")).thenReturn(Optional.of(account));
        assertThat(policy.canReadTrackingCode("UTE001", authentication)).isTrue();
        assertThat(policy.requireReadableOrder(7L, authentication)).isSameAs(order);
        verifyNoInteractions(users);
    }

    @Test void revokedOrDisabledStaffPrincipalCannotReadPrivateOrders() {
        order(user(1, "owner", AppUser.Role.USER), null);
        StaffAccount account = staffAccount("MANAGER");
        Authentication session = internal(account);
        when(staff.findByUserUsername("staff")).thenReturn(Optional.of(account));
        account.revokeSessions();
        assertThat(policy.canReadTrackingCode("UTE001", session)).isFalse();
        Authentication current = internal(account);
        account.setActive(false);
        assertThat(policy.canReadTrackingCode("UTE001", current)).isFalse();
    }

    @Test void changingStaffPasswordOrRoleInvalidatesExistingAccess() {
        order(user(1, "owner", AppUser.Role.USER), null);
        StaffAccount account = staffAccount("MANAGER");
        Authentication session = internal(account);
        when(staff.findByUserUsername("staff")).thenReturn(Optional.of(account));
        account.getUser().setPassword("different-hash");
        assertThat(policy.canReadTrackingCode("UTE001", session)).isFalse();
        Authentication current = internal(account);
        account.getUser().setRole(AppUser.Role.USER);
        assertThat(policy.canReadTrackingCode("UTE001", current)).isFalse();
    }

    @Test void mandatoryStaffPasswordChangeAlsoRestrictsPrivateFileAndTopicReads() {
        order(user(1, "owner", AppUser.Role.USER), null);
        StaffAccount account = staffAccount("MANAGER");
        Authentication session = internal(account);
        account.setPasswordChangeRequired(true);
        when(staff.findByUserUsername("staff")).thenReturn(Optional.of(account));
        assertThat(policy.canReadTrackingCode("UTE001", session)).isFalse();
        assertThatThrownBy(() -> policy.requireReadableOrder(7L, session)).isInstanceOf(AccessDeniedException.class);
    }

    @Test void publicSessionCannotClaimInternalPrivilegesUsingAnAdminAuthority() {
        order(user(1, "owner", AppUser.Role.USER), null);
        AppUser administrator = user(9, "admin", AppUser.Role.ADMIN);
        when(users.findByUsername("admin")).thenReturn(Optional.of(administrator));
        assertThat(policy.canReadTrackingCode("UTE001", authentication("admin", "ADMIN"))).isFalse();
        verifyNoInteractions(staff);
    }

    @Test void anonymousUnauthenticatedAndMissingAccountsFailClosed() {
        order(user(1, "owner", AppUser.Role.USER), null);
        assertThat(policy.canReadTrackingCode("UTE001", null)).isFalse();
        assertThat(policy.canReadTrackingCode("UTE001", UsernamePasswordAuthenticationToken.unauthenticated("owner", null))).isFalse();
        Authentication anonymous = new AnonymousAuthenticationToken("key", "anonymousUser",
                List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
        assertThat(policy.canReadTrackingCode("UTE001", anonymous)).isFalse();
        assertThat(policy.canReadTrackingCode("UTE001", authentication("deleted", "USER"))).isFalse();
        assertThat(policy.canReadTrackingCode("MISSING", authentication("owner", "USER"))).isFalse();
    }

    private Order order(AppUser owner, AppUser shipper) {
        Order order = new Order();
        order.setId(7L);
        order.setTrackingCode("UTE001");
        order.setSender(owner);
        order.setShipper(shipper);
        when(orders.findByTrackingCode("UTE001")).thenReturn(Optional.of(order));
        when(orders.findById(7L)).thenReturn(Optional.of(order));
        return order;
    }

    private static AppUser user(long id, String username, AppUser.Role role) {
        AppUser user = new AppUser();
        user.setId(id); user.setUsername(username); user.setRole(role);
        user.setEnabled(true); user.setPassword("hashed-password");
        return user;
    }

    private static StaffAccount staffAccount(String role) {
        StaffAccount account = new StaffAccount();
        account.setUser(user(9, "staff", AppUser.Role.valueOf(role)));
        account.setActive(true); account.setPasswordChangeRequired(false);
        return account;
    }

    private static Authentication authentication(String username, String role) {
        return UsernamePasswordAuthenticationToken.authenticated(username, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    private static Authentication internal(StaffAccount account) {
        StaffPrincipal principal = new StaffPrincipal(account);
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
    }
}
