package vn.edu.hcmute.uteexpress.service.assistant;

import static org.junit.jupiter.api.Assertions.*;
import java.time.*;
import org.junit.jupiter.api.Test;
import vn.edu.hcmute.uteexpress.config.AssistantConfig;

class AssistantRateGuardTest {
    final Clock clock=Clock.fixed(Instant.parse("2026-10-19T00:00:00Z"),ZoneOffset.UTC);
    final AssistantConfig.Settings settings=new AssistantConfig.Settings(true,"CHANGE_ME","openai/gpt-oss-20b",18,1600,3);
    @Test void accountLimitDoesNotBlockAnotherAccount() throws Exception {
        var guard=new AssistantRateGuard(clock,settings);
        for (int i=0;i<3;i++) guard.acquire(1L).close();
        var failure=assertThrows(AssistantFailure.class,() -> guard.acquire(1L));
        assertEquals("ACCOUNT_RATE_LIMIT",failure.getCode()); assertEquals(60,failure.getRetryAfter());
        guard.acquire(2L).close();
    }
    @Test void concurrentSessionsOfOneAccountAreLockedAndProviderCooldownIsGlobal() throws Exception {
        var guard=new AssistantRateGuard(clock,settings); var lease=guard.acquire(1L);
        assertEquals("BUSY",assertThrows(AssistantFailure.class,() -> guard.acquire(1L)).getCode()); lease.close();
        guard.blockProvider(120);
        assertEquals(120,assertThrows(AssistantFailure.class,() -> guard.acquire(2L)).getRetryAfter());
    }
}
