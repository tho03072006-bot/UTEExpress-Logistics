package vn.edu.hcmute.uteexpress.repository.routing;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.hcmute.uteexpress.entity.routing.ProvinceArea;

/** Truy vấn dữ liệu ProvinceArea; quyền và nghiệp vụ được kiểm tra tại tầng service. */
public interface ProvinceAreaRepository extends JpaRepository<ProvinceArea, Long> {
    Optional<ProvinceArea> findByAreaCode(String areaCode);
    List<ProvinceArea> findAllByBranch_IdOrderByAreaName(Long branchId);
}
