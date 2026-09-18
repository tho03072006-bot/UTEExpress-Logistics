package vn.edu.hcmute.uteexpress.tool.database;

import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AivenDatabaseToolsTest {
    @Test void allTeamMappingsSupportMySqlWithoutConnectingToDatabase() {
        List<String> schema = AivenSchema.statements();
        var tables = schema.stream().filter(sql -> sql.startsWith("create table "))
                .map(sql -> sql.split(" ")[2]).collect(java.util.stream.Collectors.toSet());
        assertEquals(Set.of("app_user", "shipment_order", "saved_address", "draft_order", "promo_code",
                "order_payment", "service_review", "email_change_request", "delivery_proof", "staff_account"), tables);
        assertEquals(12, schema.stream().filter(sql -> sql.contains("foreign key")).count());
        assertEquals(9, schema.stream().filter(sql -> sql.contains(" unique ")).count());
        assertTrue(schema.stream().filter(sql -> sql.startsWith("create table "))
                .allMatch(sql -> sql.contains("auto_increment") && sql.contains("charset=utf8mb4")));
        assertFalse(String.join("\n", schema).contains("character set utf8 "));
        assertTrue(schema.stream().noneMatch(sql -> sql.toLowerCase().matches(".*\\b(drop|delete|truncate)\\b.*")));
    }

    @Test void defaultConfigUsesAivenWithoutOpeningConnection() {
        new ApplicationContextRunner().withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues("AIVEN_HOST=test.aivencloud.com", "AIVEN_PORT=12345", "AIVEN_DATABASE=defaultdb",
                        "AIVEN_USER=test", "AIVEN_PASSWORD=test-only", "AIVEN_TRUSTSTORE_URL=file:/test.p12",
                        "AIVEN_TRUSTSTORE_PASSWORD=test-only")
                .run(context -> {
                    var env = context.getEnvironment();
                    assertEquals("jdbc:mysql://test.aivencloud.com:12345/defaultdb", env.getProperty("spring.datasource.url"));
                    assertEquals("com.mysql.cj.jdbc.Driver", env.getProperty("spring.datasource.driver-class-name"));
                    assertEquals("test-only", env.getProperty("spring.datasource.password"));
                    assertEquals("validate", env.getProperty("spring.jpa.hibernate.ddl-auto"));
                    assertEquals("VERIFY_IDENTITY", env.getProperty("spring.datasource.hikari.data-source-properties.sslMode"));
                    assertEquals("5", env.getProperty("spring.datasource.hikari.maximum-pool-size"));
                    assertEquals("false", env.getProperty("spring.jpa.show-sql"));
                });
    }

    @Test void toolConnectionRequiresVerifiedTls() {
        Properties config = config();
        assertEquals("jdbc:mysql://test.aivencloud.com:12345/defaultdb", AivenConnectionSettings.jdbcUrl(config));
        var options = AivenConnectionSettings.connectionProperties(config);
        assertEquals("VERIFY_IDENTITY", options.getProperty("sslMode"));
        assertEquals("false", options.getProperty("fallbackToSystemTrustStore"));
        assertEquals("false", options.getProperty("allowPublicKeyRetrieval"));
        assertEquals("PKCS12", options.getProperty("trustCertificateKeyStoreType"));
    }

    @ParameterizedTest @ValueSource(strings = {"0", "65536", "abc", "CHANGE_ME", ""})
    void rejectsInvalidPort(String port) {
        Properties config = config(); config.setProperty("AIVEN_PORT", port);
        assertThrows(IllegalArgumentException.class, () -> AivenConnectionSettings.jdbcUrl(config));
    }

    @Test void rejectsUrlInjectionAndLocalhost() {
        Properties config = config(); config.setProperty("AIVEN_DATABASE", "defaultdb?sslMode=DISABLED");
        assertThrows(IllegalArgumentException.class, () -> AivenConnectionSettings.jdbcUrl(config));
        config.setProperty("AIVEN_DATABASE", "defaultdb"); config.setProperty("AIVEN_HOST", "localhost");
        assertThrows(IllegalArgumentException.class, () -> AivenConnectionSettings.jdbcUrl(config));
    }

    @Test void rejectsPlaceholderPasswordAndRemoteTruststore() {
        Properties config = config(); config.setProperty("AIVEN_PASSWORD", "CHANGE_ME");
        assertThrows(IllegalArgumentException.class, () -> AivenConnectionSettings.connectionProperties(config));
        config.setProperty("AIVEN_PASSWORD", "test-only"); config.setProperty("AIVEN_TRUSTSTORE_URL", "https://example.com/test.p12");
        assertThrows(IllegalArgumentException.class, () -> AivenConnectionSettings.connectionProperties(config));
    }

    @Test void refusesExistingTablesEvenIfEmptyAndReleasesLock() throws Exception {
        Connection connection = connection(true);
        assertThrows(IllegalArgumentException.class, () -> InitializeAivenDatabase.initialize(connection, ddl()));
        verify(connection.createStatement(), never()).executeUpdate(anyString());
        verify(connection.createStatement()).execute(contains("RELEASE_LOCK"));
    }

    @Test void createsSchemaAfterEmptyDatabaseCheck() throws Exception {
        Connection connection = connection(false);
        InitializeAivenDatabase.initialize(connection, ddl());
        verify(connection.createStatement()).executeUpdate(ddl().get(0));
        verify(connection.createStatement()).execute(contains("RELEASE_LOCK"));
        verify(connection, never()).prepareStatement(anyString());
    }

    @Test void rejectsWrongEngineBeforeSql() throws Exception {
        Connection connection = connection(false);
        when(connection.getMetaData().getDatabaseProductName()).thenReturn("Microsoft SQL Server");
        assertThrows(IllegalArgumentException.class, () -> InitializeAivenDatabase.initialize(connection, ddl()));
        verify(connection.createStatement(), never()).executeQuery(anyString());
    }

    @Test void lockTimeoutDoesNotCreateTables() throws Exception {
        Connection connection = connection(false);
        when(connection.createStatement().executeQuery(anyString()).getInt(1)).thenReturn(0);
        assertThrows(IllegalArgumentException.class, () -> InitializeAivenDatabase.initialize(connection, ddl()));
        verify(connection.createStatement(), never()).executeUpdate(anyString());
    }

    @Test void failureReleasesLockWithoutDeletingPartialSchema() throws Exception {
        Connection connection = connection(false);
        when(connection.createStatement().executeUpdate(anyString())).thenThrow(new SQLException("test"));
        assertThrows(SQLException.class, () -> InitializeAivenDatabase.initialize(connection, ddl()));
        verify(connection.createStatement()).execute(contains("RELEASE_LOCK"));
        verify(connection.createStatement(), never()).executeUpdate(contains("drop"));
        verify(connection, never()).rollback();
    }

    private List<String> ddl() { return List.of("create table test (id bigint primary key)"); }

    private Connection connection(boolean populated) throws Exception {
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        Statement statement = mock(Statement.class);
        ResultSet lock = mock(ResultSet.class);
        ResultSet tables = mock(ResultSet.class);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getDatabaseProductName()).thenReturn("MySQL");
        when(metadata.getDatabaseMajorVersion()).thenReturn(8);
        when(connection.getCatalog()).thenReturn("defaultdb");
        when(metadata.getTables(eq("defaultdb"), isNull(), eq("%"), isNull())).thenReturn(tables);
        when(tables.next()).thenReturn(populated);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery(anyString())).thenReturn(lock);
        when(lock.next()).thenReturn(true); when(lock.getInt(1)).thenReturn(1);
        return connection;
    }

    private Properties config() {
        Properties config = new Properties();
        config.setProperty("AIVEN_HOST", "test.aivencloud.com"); config.setProperty("AIVEN_PORT", "12345");
        config.setProperty("AIVEN_DATABASE", "defaultdb"); config.setProperty("AIVEN_USER", "test");
        config.setProperty("AIVEN_PASSWORD", "test-only"); config.setProperty("AIVEN_TRUSTSTORE_URL", "file:/test.p12");
        config.setProperty("AIVEN_TRUSTSTORE_PASSWORD", "test-only"); return config;
    }
}
