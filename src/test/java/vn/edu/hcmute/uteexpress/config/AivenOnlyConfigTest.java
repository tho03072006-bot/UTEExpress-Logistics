package vn.edu.hcmute.uteexpress.config;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.junit.jupiter.api.Assertions.*;
class AivenOnlyConfigTest {
    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner().withUserConfiguration(AivenOnlyConfig.class).withPropertyValues(
            "spring.datasource.url=jdbc:mysql://test.aivencloud.com:12345/defaultdb",
            "spring.jpa.hibernate.ddl-auto=validate",
            "spring.datasource.hikari.data-source-properties.sslMode=VERIFY_IDENTITY",
            "spring.datasource.hikari.data-source-properties.fallbackToSystemTrustStore=false");
    }
    @Test void acceptsOnlyVerifiedAiven() { runner().run(context -> assertNull(context.getStartupFailure())); }
    @Test void localhostCannotBeInjectedByAnOldProfile() {
        runner().withPropertyValues("spring.datasource.url=jdbc:mysql://localhost:3306/uteexpress")
            .run(context -> assertNotNull(context.getStartupFailure()));
    }
    @Test void disabledTlsAndAutomaticSchemaChangesFailClosed() {
        runner().withPropertyValues("spring.datasource.hikari.data-source-properties.sslMode=REQUIRED")
            .run(context -> assertNotNull(context.getStartupFailure()));
        runner().withPropertyValues("spring.jpa.hibernate.ddl-auto=update")
            .run(context -> assertNotNull(context.getStartupFailure()));
    }
    @Test void hikariOverrideCannotSilentlyUseLocalDatabase() {
        runner().withPropertyValues("spring.datasource.hikari.jdbc-url=jdbc:mysql://localhost:3306/uteexpress")
            .run(context -> assertNotNull(context.getStartupFailure()));
    }
}

