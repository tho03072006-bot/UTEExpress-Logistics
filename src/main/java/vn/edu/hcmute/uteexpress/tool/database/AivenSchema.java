package vn.edu.hcmute.uteexpress.tool.database;

import java.io.StringWriter;
import java.util.Arrays;
import java.util.List;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import vn.edu.hcmute.uteexpress.entity.*;
import vn.edu.hcmute.uteexpress.entity.admin.StaffAccount;
import vn.edu.hcmute.uteexpress.entity.routing.*;

/** Sinh DDL từ toàn bộ entity hiện tại, không sửa code nghiệp vụ của thành viên khác. */
public final class AivenSchema {
    private AivenSchema() { }

    public static List<Class<?>> entityTypes() {
        return List.of(AppUser.class, Order.class, SavedAddress.class,
                DraftOrder.class, OrderFormSubmission.class, PromoCode.class, OrderPayment.class, ServiceReview.class,
                EmailChangeRequest.class, DeliveryProof.class, StaffAccount.class,
                OrderStatusHistory.class, ShipperNotification.class, OrderComplaint.class,
                CodSettlement.class, CodSettlementItem.class, UserProfileDetail.class,
                vn.edu.hcmute.uteexpress.entity.admin.ShippingTariff.class,
                vn.edu.hcmute.uteexpress.entity.admin.AuditEvent.class,
                vn.edu.hcmute.uteexpress.entity.admin.OperationsPolicy.class,
                vn.edu.hcmute.uteexpress.entity.admin.OperationsCase.class,
                vn.edu.hcmute.uteexpress.entity.admin.ComplaintActivity.class,
                vn.edu.hcmute.uteexpress.entity.admin.ShippingTariffVersion.class,
                Branch.class, ProvinceArea.class, AreaNeighbor.class, StaffBranch.class,
                ShipperZone.class, OrderRoute.class, LinehaulTrip.class, LinehaulItem.class,
                RouteTariff.class, SavedAddressArea.class, DraftOrderArea.class);
    }

    public static List<String> statements() {
        StringWriter script = new StringWriter();
        var registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.MySQLDialect")
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", "false")
                .applySetting("hibernate.connection.provider_class",
                        "org.hibernate.engine.jdbc.connections.internal.UserSuppliedConnectionProviderImpl")
                .applySetting("jakarta.persistence.schema-generation.database.action", "none")
                .applySetting("jakarta.persistence.schema-generation.scripts.action", "create")
                .applySetting("jakarta.persistence.schema-generation.scripts.create-target", script)
                .applySetting("hibernate.hbm2ddl.delimiter", ";")
                .build();
        try {
            var sources = new MetadataSources(registry);
            for (Class<?> entity : entityTypes()) {
                sources.addAnnotatedClass(entity);
            }
            var metadata = sources.getMetadataBuilder()
                    .applyPhysicalNamingStrategy(new CamelCaseToUnderscoresNamingStrategy()).build();
            try (var ignored = metadata.buildSessionFactory()) {
                return Arrays.stream(script.toString().split(";"))
                        .map(String::trim).filter(sql -> !sql.isEmpty())
                        // Hibernate ánh xạ @Nationalized sang utf8mb3; nâng charset trong DDL,
                        // không sửa entity của User/Shipper, để giữ cả Unicode 4 byte.
                        .map(sql -> sql.replace(" character set utf8", " character set utf8mb4"))
                        .map(sql -> sql.startsWith("create table ")
                                ? sql + " default charset=utf8mb4 collate=utf8mb4_0900_ai_ci" : sql)
                        .toList();
            }
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }
}
