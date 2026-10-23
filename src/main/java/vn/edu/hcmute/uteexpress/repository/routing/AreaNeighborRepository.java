package vn.edu.hcmute.uteexpress.repository.routing;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.hcmute.uteexpress.entity.routing.AreaNeighbor;
import vn.edu.hcmute.uteexpress.entity.routing.AreaNeighborId;

/** Truy vấn dữ liệu AreaNeighbor; quyền và nghiệp vụ được kiểm tra tại tầng service. */
public interface AreaNeighborRepository extends JpaRepository<AreaNeighbor, AreaNeighborId> {
    List<AreaNeighbor> findAllById_AreaCode(String areaCode);
}
