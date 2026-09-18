package vn.edu.hcmute.uteexpress.repository.admin;

import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;
import vn.edu.hcmute.uteexpress.entity.AppUser;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.entity.admin.StaffAccount;
import vn.edu.hcmute.uteexpress.repository.manager.ManagerOrderReadRepository;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class StaffQueryValidationTest {
    @Test void validatesMappingsAndRepositoryQueriesWithoutDatabaseConnection() {
        var registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.MySQLDialect")
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", "false")
                .applySetting("hibernate.hbm2ddl.auto", "none")
                .applySetting("hibernate.connection.provider_class",
                        "org.hibernate.engine.jdbc.connections.internal.UserSuppliedConnectionProviderImpl")
                .build();
        try (var factory = new MetadataSources(registry).addAnnotatedClass(AppUser.class)
                .addAnnotatedClass(Order.class).addAnnotatedClass(StaffAccount.class)
                .buildMetadata().buildSessionFactory(); var session = factory.openSession()) {
            for (Class<?> repository : java.util.List.of(StaffAccountRepository.class, ManagerOrderReadRepository.class)) {
                for (var method : repository.getMethods()) {
                    Query query = method.getAnnotation(Query.class);
                    if (query != null) {
                        assertDoesNotThrow(() -> session.createQuery(query.value(), Object.class), method.getName());
                    }
                }
            }
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }
}
