package vn.edu.hcmute.uteexpress.service.routing;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import vn.edu.hcmute.uteexpress.dto.admin.StaffBranchRequest;
import vn.edu.hcmute.uteexpress.entity.*;
import vn.edu.hcmute.uteexpress.entity.routing.*;
import vn.edu.hcmute.uteexpress.repository.admin.AdminUserRepository;
import vn.edu.hcmute.uteexpress.repository.routing.*;
import vn.edu.hcmute.uteexpress.service.admin.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StaffBranchServiceTest {
    @Mock StaffBranchRepository staff;
    @Mock ShipperZoneRepository zones;
    @Mock BranchRepository branches;
    @Mock ProvinceAreaRepository areas;
    @Mock AdminUserRepository users;
    @Mock StaffAccessPolicy access;
    @Mock AuditService audit;
    StaffBranchService service;
    AppUser shipper, admin;
    Branch sgn, han;
    final LocalDateTime now = LocalDateTime.of(2026, 10, 23, 12, 0);
    @BeforeEach void setup() {
        service = new StaffBranchService(staff, zones, branches, areas, users, access, audit, Clock.systemUTC());
        shipper = new AppUser("shipperqa", "hash", "qa@example.com"); shipper.setId(5L); shipper.setRole(AppUser.Role.SHIPPER);
        admin = new AppUser("adminqa", "hash", "adminqa@example.com"); admin.setId(1L); admin.setRole(AppUser.Role.ADMIN);
        sgn = branch(10L, "SGN"); han = branch(20L, "HAN");
    }
    @Test void nonAdminCannotAssignOrReadSelection() {
        doThrow(new AccessDeniedException("Chỉ Admin.")).when(access).requireAdmin("other");
        assertThrows(AccessDeniedException.class, () -> service.assign("other", shipper, form()));
        assertThrows(AccessDeniedException.class, () -> service.selection("other", 5L));
        verifyNoInteractions(users, staff, zones, branches, areas, audit);
    }
    @ParameterizedTest @EnumSource(value = AppUser.Role.class, names = {"MANAGER", "SHIPPER"})
    void branchIsMandatory(AppUser.Role role) {
        shipper.setRole(role); lock();
        var form = form(); form.setBranchId(null);
        assertThrows(IllegalArgumentException.class, () -> service.assign("adminqa", shipper, form));
        verifyNoInteractions(branches, areas, audit);
    }
    @Test void unknownBranchIsRejected() {
        lock(); assertThrows(IllegalArgumentException.class, () -> service.assign("adminqa", shipper, form()));
        verifyNoInteractions(areas, audit);
    }
    @Test void disabledBranchCannotAcceptNewStaff() {
        lock(); sgn.configure("Kho", "Địa chỉ", "SGN-A", false, now);
        when(branches.findById(10L)).thenReturn(Optional.of(sgn));
        assertThrows(IllegalArgumentException.class, () -> service.assign("adminqa", shipper, form()));
        verifyNoInteractions(areas, audit);
    }
    @Test void branchChangeWithUnfinishedOrdersIsRejectedBeforeChangingZones() {
        lock(); when(branches.findById(10L)).thenReturn(Optional.of(sgn));
        when(staff.findByUser_Id(5L)).thenReturn(Optional.of(new StaffBranch(shipper, han, admin, now)));
        when(users.countUnfinishedAssignments(eq(5L), any())).thenReturn(1L);
        var error = assertThrows(IllegalArgumentException.class, () -> service.assign("adminqa", shipper, form()));
        assertTrue(error.getMessage().contains("còn đơn")); verifyNoInteractions(areas, zones, audit);
        verify(staff, never()).save(any());
    }
    @Test void shipperRequiresOnePrimaryArea() {
        lock(); when(branches.findById(10L)).thenReturn(Optional.of(sgn)); var form = form(); form.setPrimaryAreaCode(null);
        assertThrows(IllegalArgumentException.class, () -> service.assign("adminqa", shipper, form));
        verifyNoInteractions(areas, audit);
    }
    @Test void areaOutsideBranchIsRejected() {
        lock(); when(branches.findById(10L)).thenReturn(Optional.of(sgn));
        when(areas.findByAreaCode("SGN-A")).thenReturn(Optional.of(area("SGN-A", han)));
        assertThrows(IllegalArgumentException.class, () -> service.assign("adminqa", shipper, form()));
        verifyNoInteractions(audit); verify(zones, never()).deleteAll(any());
    }
    @Test void duplicatePrimaryAndSecondaryAreaIsRejected() {
        validPrimary(); var form = form(); form.setSecondaryAreaCodes(List.of("SGN-A"));
        assertThrows(IllegalArgumentException.class, () -> service.assign("adminqa", shipper, form));
        verifyNoInteractions(audit); verify(zones, never()).deleteAll(any());
    }
    @Test void validShipperGetsOnePrimaryAndSecondaryInTheSameBranch() {
        validPrimary(); when(areas.findByAreaCode("SGN-B")).thenReturn(Optional.of(area("SGN-B", sgn)));
        when(users.findByUsername("adminqa")).thenReturn(Optional.of(admin));
        var form = form(); form.setSecondaryAreaCodes(List.of("SGN-B")); service.assign("adminqa", shipper, form);
        var saved = ArgumentCaptor.forClass(ShipperZone.class); verify(zones, times(2)).save(saved.capture());
        assertEquals(1, saved.getAllValues().stream().filter(ShipperZone::isPrimary).count());
        assertTrue(saved.getAllValues().stream().allMatch(z -> z.getArea().getBranch() == sgn));
        verify(staff).save(any()); verify(audit).record(eq("adminqa"), eq("STAFF_BRANCH_ASSIGN"), eq("user:5"), anyString());
        assertEquals(0, shipper.getAccessVersion(), "Service tài khoản chịu trách nhiệm thu hồi phiên một lần");
    }
    @Test void managerNeedsBranchButNotShipperZones() {
        lock(); shipper.setRole(AppUser.Role.MANAGER);
        when(branches.findById(10L)).thenReturn(Optional.of(sgn)); when(users.findByUsername("adminqa")).thenReturn(Optional.of(admin));
        var form = form(); form.setPrimaryAreaCode(null); service.assign("adminqa", shipper, form);
        verify(staff).save(any()); verify(zones, never()).save(any()); verifyNoInteractions(areas);
    }
    @Test void sameBranchAndZonesDoNotGenerateAnotherAuditOrBlockAnActiveShipper() {
        validPrimary(); when(staff.findByUser_Id(5L)).thenReturn(Optional.of(new StaffBranch(shipper, sgn, admin, now)));
        when(zones.findAllByShipper_Id(5L)).thenReturn(List.of(new ShipperZone(shipper, area("SGN-A", sgn), true)));
        service.assign("adminqa", shipper, form());
        verify(users, never()).countUnfinishedAssignments(any(), any()); verifyNoInteractions(audit);
        verify(staff, never()).save(any()); verify(zones, never()).deleteAll(any());
    }
    @Test void routeHistoryAndOperatorHistoryProtectDeletionButOwnConfigurationDoesNot() {
        assertFalse(service.hasReferences(5L));
        when(users.countRouteResponsibilities(5L)).thenReturn(1L); assertTrue(service.hasReferences(5L));
        when(users.countRouteResponsibilities(5L)).thenReturn(0L);
        when(users.countLinehaulActions(5L)).thenReturn(1L); assertTrue(service.hasReferences(5L));
        when(users.countLinehaulActions(5L)).thenReturn(0L);
        assertFalse(service.hasReferences(5L));
        when(staff.existsByAssignedBy_Id(5L)).thenReturn(true); assertTrue(service.hasReferences(5L));
        verifyNoInteractions(zones);
    }
    private void lock() { when(users.lockUser(5L)).thenReturn(Optional.of(shipper)); }
    private void validPrimary() {
        lock(); when(branches.findById(10L)).thenReturn(Optional.of(sgn));
        when(areas.findByAreaCode("SGN-A")).thenReturn(Optional.of(area("SGN-A", sgn)));
    }
    private StaffBranchRequest form() {
        var form = new StaffBranchRequest(); form.setBranchId(10L); form.setPrimaryAreaCode("SGN-A"); return form;
    }
    private Branch branch(Long id, String code) {
        var branch = new Branch(code, "Kho " + code, code, "Địa chỉ kho", code + "-A", now);
        ReflectionTestUtils.setField(branch, "id", id); return branch;
    }
    private ProvinceArea area(String code, Branch branch) {
        return new ProvinceArea(branch.getProvinceCode(), code, "Khu vực " + code, branch, BigDecimal.TEN, BigDecimal.TEN);
    }
}
