package vn.edu.hcmute.uteexpress.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockServletContext;
import static org.junit.jupiter.api.Assertions.*;

class SessionTimeoutConfigTest {
    @ParameterizedTest @ValueSource(ints = {-1,0,14,121,10000})
    void rejectsUnreasonableIdleTimeout(int minutes) {
        assertThrows(IllegalStateException.class, () -> new SessionTimeoutConfig().configureSessionTimeout(minutes));
    }
    @Test void containerEnforcesSixtyMinuteIdleTimeout() throws Exception {
        var context = new MockServletContext();
        new SessionTimeoutConfig().configureSessionTimeout(60).onStartup(context);
        assertEquals(60, context.getSessionTimeout());
    }
}
