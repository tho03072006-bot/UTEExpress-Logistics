package vn.edu.hcmute.uteexpress.support;

import java.time.LocalDateTime;
import java.util.HashMap;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.edu.hcmute.uteexpress.dto.*;
import vn.edu.hcmute.uteexpress.entity.routing.*;
import vn.edu.hcmute.uteexpress.repository.routing.*;
import vn.edu.hcmute.uteexpress.tool.database.BranchSeedData;

/** Dữ liệu test địa bàn thực tế. Chặn tuyệt đối chạy ngoài H2 in-memory; không sửa cấu hình test. */
public final class RoutingFixtures {
    private RoutingFixtures() { }
    public static void seedH2(ApplicationContext context) {
        try (var connection = context.getBean(javax.sql.DataSource.class).getConnection()) {
            if (!connection.getMetaData().getURL().startsWith("jdbc:h2:mem:")) throw new IllegalStateException("Chỉ nạp fixture trên H2 in-memory.");
        } catch (java.sql.SQLException ex) { throw new IllegalStateException(ex); }
        new TransactionTemplate(context.getBean(PlatformTransactionManager.class)).executeWithoutResult(status -> {
            var catalog = BranchSeedData.load(); var branches = context.getBean(BranchRepository.class);
            var areas = context.getBean(ProvinceAreaRepository.class); var tariffs = context.getBean(RouteTariffRepository.class);
            var byCode = new HashMap<String, Branch>();
            for (var b : catalog.branches()) byCode.put(b.code(), branches.findByCode(b.code()).orElseGet(() ->
                    branches.save(new Branch(b.code(), b.name(), b.provinceCode(), b.hubAddress(), b.hubAreaCode(), LocalDateTime.now()))));
            for (var a : catalog.areas()) if (areas.findByAreaCode(a.code()).isEmpty())
                areas.save(new ProvinceArea(a.provinceCode(), a.code(), a.name(), byCode.get(a.branchCode()), a.lat(), a.lng()));
            for (var t : catalog.tariffs()) {
                var from = byCode.get(t.origin()); var to = byCode.get(t.destination());
                if (tariffs.findByOriginBranch_IdAndDestBranch_IdAndServiceType(from.getId(), to.getId(), t.service()).isEmpty())
                    tariffs.save(new RouteTariff(from, to, t.service(), t.surcharge(), t.leadTime()));
            }
        });
    }
    public static void fill(OrderCreateRequest form) {
        form.setPickupProvinceCode("79"); form.setPickupAreaCode("SGN-THUDUC");
        form.setDeliveryProvinceCode("79"); form.setDeliveryAreaCode("SGN-THUDUC");
    }
    public static void fill(SavedAddressRequest form) { form.setProvinceCode("79"); form.setAreaCode("SGN-THUDUC"); }
    public static void assignStaff(ApplicationContext context, vn.edu.hcmute.uteexpress.entity.AppUser actor,
            vn.edu.hcmute.uteexpress.entity.AppUser... users) {
        var branch = context.getBean(BranchRepository.class).findByCode("SGN").orElseThrow();
        var staff = context.getBean(StaffBranchRepository.class);
        for (var user : users) staff.save(new StaffBranch(user, branch, actor, LocalDateTime.now()));
    }
    public static void route(ApplicationContext context, vn.edu.hcmute.uteexpress.entity.Order order) {
        var area = context.getBean(ProvinceAreaRepository.class).findByAreaCode("SGN-THUDUC").orElseThrow();
        context.getBean(OrderRouteRepository.class).save(new OrderRoute(order, area, area));
    }
}
