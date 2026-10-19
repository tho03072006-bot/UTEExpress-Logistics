package vn.edu.hcmute.uteexpress.support;

import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import vn.edu.hcmute.uteexpress.UteexpressApplication;
import vn.edu.hcmute.uteexpress.config.AivenOnlyConfig;

/** Chỉ test: H2 in-memory riêng, không phải fallback/local database của ứng dụng. Guard Aiven được test riêng. */
@Configuration(proxyBeanMethods = false) @Profile("isolated-logistics") @EnableAutoConfiguration
@EntityScan("vn.edu.hcmute.uteexpress.entity")
@EnableJpaRepositories("vn.edu.hcmute.uteexpress.repository")
@ComponentScan(basePackages = "vn.edu.hcmute.uteexpress", excludeFilters = {
    @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {UteexpressApplication.class, AivenOnlyConfig.class}),
    @ComponentScan.Filter(type = FilterType.CUSTOM, classes = org.springframework.boot.context.TypeExcludeFilter.class)
})
public class IsolatedLogisticsTestConfiguration {
    @Bean @Primary
    vn.edu.hcmute.uteexpress.service.media.CloudMediaGateway isolatedCloudMedia(javax.sql.DataSource source) throws Exception {
        try (var connection = source.getConnection()) {
            if (!connection.getMetaData().getURL().startsWith("jdbc:h2:mem:"))
                throw new IllegalStateException("Không dùng cloud gateway giả ngoài test H2 in-memory.");
        }
        return new InMemoryCloudMediaGateway();
    }
    @Bean ApplicationRunner seedIsolatedPolicy(JdbcTemplate jdbc, org.springframework.transaction.PlatformTransactionManager manager) {
        return args -> {
            try (var connection = jdbc.getDataSource().getConnection()) {
                if (!connection.getMetaData().getURL().startsWith("jdbc:h2:mem:")) throw new IllegalStateException("Chỉ seed test H2 in-memory.");
            }
            new TransactionTemplate(manager).executeWithoutResult(status -> {
                jdbc.update("INSERT INTO operations_policy(id,revision,pickup_hours,standard_hours,express_hours,super_express_hours,"
                        + "max_delivery_attempts,complaint_response_hours,complaint_resolve_hours,updated_by,updated_at) "
                        + "VALUES(1,0,24,120,48,4,3,24,168,'test-only',CURRENT_TIMESTAMP)");
                for (String type : new String[]{"STANDARD", "EXPRESS", "SUPER_EXPRESS"}) {
                    int base = type.equals("STANDARD") ? 15000 : type.equals("EXPRESS") ? 25000 : 40000;
                    jdbc.update("INSERT INTO shipping_tariff(service_type,base_fee,fee_per_kg,revision,updated_at) VALUES(?,?,3000,0,CURRENT_TIMESTAMP)", type, base);
                }
            });
        };
    }
}
