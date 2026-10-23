package vn.edu.hcmute.uteexpress.service.routing;

import java.math.BigDecimal;
import java.time.*;
import java.util.Optional;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import vn.edu.hcmute.uteexpress.dto.admin.BranchUpdateRequest;
import vn.edu.hcmute.uteexpress.entity.routing.*;
import vn.edu.hcmute.uteexpress.exception.StaffConflictException;
import vn.edu.hcmute.uteexpress.repository.routing.*;
import vn.edu.hcmute.uteexpress.service.admin.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BranchAdminServiceTest {
    @Mock BranchRepository branches;
    @Mock ProvinceAreaRepository areas;
    @Mock StaffAccessPolicy access;
    @Mock AuditService audit;
    BranchAdminService service;
    Branch branch;
    @BeforeEach void setup() {
        service = new BranchAdminService(branches, areas, access, audit, Clock.systemUTC());
        branch = new Branch("SGN", "Kho SGN", "SGN", "Thủ Đức", "SGN-A", LocalDateTime.now());
        ReflectionTestUtils.setField(branch, "id", 1L);
    }
    @Test void nonAdminCannotReadOrUpdateBranches() {
        doThrow(new AccessDeniedException("Chỉ Admin.")).when(access).requireAdmin("manager");
        assertThrows(AccessDeniedException.class, () -> service.list("manager"));
        assertThrows(AccessDeniedException.class, () -> service.get("manager", 1L));
        assertThrows(AccessDeniedException.class, () -> service.update("manager", 1L, form()));
        verifyNoInteractions(branches, areas, audit);
    }
    @Test void staleRevisionCannotOverwrite() {
        when(branches.lockById(1L)).thenReturn(Optional.of(branch)); var form = form(); form.setRevision(4L);
        assertThrows(StaffConflictException.class, () -> service.update("admin", 1L, form));
        verifyNoInteractions(areas, audit); assertEquals("Kho SGN", branch.getName());
    }
    @Test void hubMustBelongToItsOwnBranch() {
        when(branches.lockById(1L)).thenReturn(Optional.of(branch));
        var other = new Branch("HAN", "Kho HAN", "HAN", "Cầu Giấy", "HAN-A", LocalDateTime.now());
        ReflectionTestUtils.setField(other, "id", 2L);
        when(areas.findByAreaCode("SGN-A")).thenReturn(Optional.of(area(other)));
        assertThrows(IllegalArgumentException.class, () -> service.update("admin", 1L, form()));
        verifyNoInteractions(audit); assertEquals("Kho SGN", branch.getName());
    }
    @Test void unknownHubIsRejected() {
        when(branches.lockById(1L)).thenReturn(Optional.of(branch));
        assertThrows(IllegalArgumentException.class, () -> service.update("admin", 1L, form()));
        verifyNoInteractions(audit);
    }
    @Test void unchangedFormDoesNotWrite() {
        validHub(); var form = BranchUpdateRequest.from(branch); form.setReason("Kiểm thử giữ nguyên");
        assertThrows(IllegalArgumentException.class, () -> service.update("admin", 1L, form));
        verify(branches, never()).flush(); verifyNoInteractions(audit);
    }
    @Test void canPauseNewOrdersWithoutChangingFixedCodeOrProvince() {
        validHub(); var form = form(); form.setActive(false); service.update("admin", 1L, form);
        assertFalse(branch.isActive()); assertEquals("SGN", branch.getCode()); assertEquals("SGN", branch.getProvinceCode());
        assertEquals("Kho SGN mới", branch.getName()); verify(branches).flush();
        verify(audit).record(eq("admin"), eq("BRANCH_UPDATE"), eq("branch:1"), contains("nhận đơn=false"));
    }
    @Test void reasonAndRevisionAreValidated() {
        try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            var form = form(); assertTrue(factory.getValidator().validate(form).isEmpty());
            form.setRevision(null); assertFalse(factory.getValidator().validate(form).isEmpty());
            form.setRevision(0L); form.setReason("a"); assertFalse(factory.getValidator().validate(form).isEmpty());
            form.setReason("a".repeat(301)); assertFalse(factory.getValidator().validate(form).isEmpty());
        }
    }
    private void validHub() {
        when(branches.lockById(1L)).thenReturn(Optional.of(branch));
        when(areas.findByAreaCode("SGN-A")).thenReturn(Optional.of(area(branch)));
    }
    private ProvinceArea area(Branch branch) {
        return new ProvinceArea(branch.getProvinceCode(), "SGN-A", "Khu vực kho", branch, BigDecimal.TEN, BigDecimal.TEN);
    }
    private BranchUpdateRequest form() {
        var form = BranchUpdateRequest.from(branch); form.setName("Kho SGN mới"); form.setReason("Kiểm thử đổi thông tin"); return form;
    }
}
