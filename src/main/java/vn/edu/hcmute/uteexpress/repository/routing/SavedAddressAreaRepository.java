package vn.edu.hcmute.uteexpress.repository.routing;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.hcmute.uteexpress.entity.routing.SavedAddressArea;

/** Truy vấn dữ liệu SavedAddressArea; quyền và nghiệp vụ được kiểm tra tại tầng service. */
public interface SavedAddressAreaRepository extends JpaRepository<SavedAddressArea, Long> {
    List<SavedAddressArea> findAllBySavedAddress_User_Username(String username);
    Optional<SavedAddressArea> findBySavedAddress_Id(Long addressId);
}
