package vn.edu.hcmute.uteexpress.service.tracking;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import vn.edu.hcmute.uteexpress.entity.*;
import vn.edu.hcmute.uteexpress.entity.admin.StaffAccount;
import vn.edu.hcmute.uteexpress.entity.routing.*;
import vn.edu.hcmute.uteexpress.repository.AppUserRepository;
import vn.edu.hcmute.uteexpress.repository.admin.StaffAccountRepository;
import vn.edu.hcmute.uteexpress.repository.routing.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LogisticsAccessTest {
    @Test void managerCannotUseBatchCheckerToAccessAnotherBranchOrder() {
        var users=mock(AppUserRepository.class);var staff=mock(StaffAccountRepository.class);
        var assignments=mock(StaffBranchRepository.class);var routes=mock(OrderRouteRepository.class);
        var access=new LogisticsAccess(users,staff,assignments,routes);
        var manager=new AppUser("managerqa","hash","manager@test.invalid");manager.setId(1L);manager.setRole(AppUser.Role.MANAGER);manager.setEnabled(true);
        var account=new StaffAccount();account.setUser(manager);account.setPasswordChangeRequired(false);
        when(users.findByUsername("managerqa")).thenReturn(Optional.of(manager));
        when(staff.findByUserUsername("managerqa")).thenReturn(Optional.of(account));
        var branch=mock(Branch.class);when(branch.getId()).thenReturn(7L);
        when(assignments.findByUser_Id(1L)).thenReturn(Optional.of(new StaffBranch(manager,branch,manager,java.time.LocalDateTime.now())));
        var mine=new Order();mine.setId(10L);var other=new Order();other.setId(20L);
        when(routes.idsInScope(List.of(10L,20L),7L)).thenReturn(List.of(10L));
        assertThrows(AccessDeniedException.class,()->access.checkOrders(List.of(mine,other),"managerqa"));
    }
}
