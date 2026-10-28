package vn.edu.hcmute.uteexpress.security.admin;

import java.time.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StaffLoginAttemptServiceTest {
    @Test void blocksAfterFiveFailuresAndExpiresWithoutExtension() {
        Clock clock = mock(Clock.class);
        Instant now = Instant.parse("2026-09-18T00:00:00Z");
        when(clock.instant()).thenReturn(now);
        var service = new StaffLoginAttemptService(clock);
        for (int i = 0; i < 4; i++) { service.recordFailure("Admin01"); }
        assertFalse(service.isBlocked("admin01"));
        service.recordFailure(" admin01 "); assertTrue(service.isBlocked("ADMIN01"));
        when(clock.instant()).thenReturn(now.plusSeconds(899)); service.recordFailure("admin01");
        when(clock.instant()).thenReturn(now.plusSeconds(900)); assertFalse(service.isBlocked("admin01"));
    }
    @Test void successClearsFailures() {
        var service = new StaffLoginAttemptService(Clock.systemUTC());
        service.recordFailure("admin01"); service.clearFailures("admin01");
        for (int i = 0; i < 4; i++) { service.recordFailure("admin01"); }
        assertFalse(service.isBlocked("admin01"));
    }
}

