package vn.edu.hcmute.uteexpress.payment;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import vn.edu.hcmute.uteexpress.service.impl.MomoStartupServiceImpl;
import vn.edu.hcmute.uteexpress.service.payment.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.awaitility.Awaitility.await;

class MomoStartupServiceTest {
    final MockEnvironment environment = new MockEnvironment().withProperty("MOMO_SIM_SECRET", MomoTicketCodecTest.KEY)
            .withProperty("MOMO_SIMULATOR_URL", "https://uteexpress-momo-test.onrender.com");
    final MomoHealthClient client = mock(MomoHealthClient.class);

    private MomoStartupServiceImpl service(MockEnvironment environment) {
        return new MomoStartupServiceImpl(new MomoSettings(environment), client, environment, Duration.ZERO);
    }
    @Test void springBootReadyTriggersOneBackgroundWorkflowAndNeverCreatesAPayment() throws Exception {
        when(client.isReachable(any())).thenReturn(true);
        try (var service = service(environment)) {
            service.onApplicationReady(); service.onApplicationReady();
            await().atMost(2, TimeUnit.SECONDS).until(() -> "ONLINE".equals(service.snapshot().state()));
            verify(client, times(1)).isReachable(URI.create("https://uteexpress-momo-test.onrender.com"));
            assertFalse(service.snapshot().message().contains(MomoTicketCodecTest.KEY));
        }
    }
    @Test void missingConfigurationDoesNotFailTheLocalAppOrSendAnyRequest() {
        try (var service = service(new MockEnvironment())) {
            assertDoesNotThrow(service::onApplicationReady);
            assertEquals("NOT_CONFIGURED", service.snapshot().state()); verifyNoInteractions(client);
        }
    }
    @Test void malformedOrUnsafeOriginCannotBeProbed() {
        environment.setProperty("MOMO_SIMULATOR_URL", "http://127.0.0.1:8080");
        try (var service = service(environment)) { service.onApplicationReady(); assertEquals("NOT_CONFIGURED", service.snapshot().state()); verifyNoInteractions(client); }
    }
    @Test void badSecretCannotBeReportedAsReady() {
        environment.setProperty("MOMO_SIM_SECRET", "CHANGE_ME");
        try (var service = service(environment)) { service.onApplicationReady(); assertEquals("NOT_CONFIGURED", service.snapshot().state()); verifyNoInteractions(client); }
    }
    @Test void isolatedTestsNeverContactRenderEvenWithAValidFixtureUrl() {
        environment.setActiveProfiles("isolated-logistics");
        try (var service = service(environment)) { service.onApplicationReady(); assertEquals("DISABLED", service.snapshot().state()); verifyNoInteractions(client); }
    }
    @Test void operatorCanDisableStartupCheck() {
        environment.setProperty("app.momo.startup-check.enabled", "false");
        try (var service = service(environment)) { service.onApplicationReady(); assertEquals("DISABLED", service.snapshot().state()); verifyNoInteractions(client); }
    }
    @Test void sleepingServiceGetsOnlyThreeAttemptsNotAnInfiniteKeepAlive() throws Exception {
        when(client.isReachable(any())).thenReturn(false, false, true);
        try (var service = service(environment)) {
            service.onApplicationReady(); await().atMost(2, TimeUnit.SECONDS).until(() -> "ONLINE".equals(service.snapshot().state()));
            verify(client, times(3)).isReachable(any());
        }
    }
    @Test void unavailableServiceDoesNotMarkItselfOnlineOrBlockLocalBoot() throws Exception {
        when(client.isReachable(any())).thenThrow(new IllegalStateException("private internal error"));
        try (var service = service(environment)) {
            service.onApplicationReady(); await().atMost(2, TimeUnit.SECONDS).until(() -> "UNREACHABLE".equals(service.snapshot().state()));
            verify(client, times(3)).isReachable(any()); assertFalse(service.snapshot().message().contains("private internal error"));
        }
    }
    @Test void healthCheckNeverBlocksTheApplicationReadyThreadAndStopsOnShutdown() throws Exception {
        var entered = new CountDownLatch(1); var interrupted = new CountDownLatch(1);
        when(client.isReachable(any())).thenAnswer(invocation -> {
            entered.countDown();
            try { Thread.sleep(30000); return true; } catch (InterruptedException exception) { interrupted.countDown(); throw exception; }
        });
        var service = service(environment);
        try {
            assertTimeout(Duration.ofSeconds(1), service::onApplicationReady);
            assertTrue(entered.await(2, TimeUnit.SECONDS)); assertEquals("CHECKING", service.snapshot().state());
        } finally { service.close(); }
        assertTrue(interrupted.await(2, TimeUnit.SECONDS)); assertNotEquals("ONLINE", service.snapshot().state());
    }
    @Test void openingPaymentPageWakesRenderAgainAfterItWasUnreachable() throws Exception {
        when(client.isReachable(any())).thenReturn(false, false, false, true);
        try (var service = service(environment)) {
            service.onApplicationReady(); await().atMost(2, TimeUnit.SECONDS).until(() -> "UNREACHABLE".equals(service.snapshot().state()));
            service.wakeForPayment();
            await().atMost(2, TimeUnit.SECONDS).until(() -> "ONLINE".equals(service.snapshot().state()));
            verify(client, times(4)).isReachable(any());
        }
    }
    @Test void recentlyOnlineRenderIsNotPingedOnEveryPageLoad() throws Exception {
        when(client.isReachable(any())).thenReturn(true);
        try (var service = service(environment)) {
            service.onApplicationReady(); await().atMost(2, TimeUnit.SECONDS).until(() -> "ONLINE".equals(service.snapshot().state()));
            service.wakeForPayment(); service.wakeForPayment();
            verify(client, times(1)).isReachable(any());
        }
    }
    @Test void reloadingPaymentPageDuringAWakeDoesNotStackRequests() throws Exception {
        var release = new CountDownLatch(1);
        when(client.isReachable(any())).thenAnswer(invocation -> { release.await(2, TimeUnit.SECONDS); return true; });
        try (var service = service(environment)) {
            service.onApplicationReady();
            for (int i = 0; i < 5; i++) service.wakeForPayment();
            release.countDown();
            await().atMost(3, TimeUnit.SECONDS).until(() -> "ONLINE".equals(service.snapshot().state()));
            verify(client, times(1)).isReachable(any());
        }
    }
    @Test void wakeDoesNothingWhenMomoIsNotConfiguredOrDisabled() {
        try (var service = service(new MockEnvironment())) { service.onApplicationReady(); service.wakeForPayment(); verifyNoInteractions(client); }
        environment.setActiveProfiles("isolated-logistics");
        try (var service = service(environment)) { service.onApplicationReady(); service.wakeForPayment(); verifyNoInteractions(client); }
    }
    @Test void closedApplicationDoesNotStartAProbe() {
        var service = service(environment); service.close(); service.onApplicationReady(); verifyNoInteractions(client);
    }
}
