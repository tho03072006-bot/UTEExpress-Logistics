package vn.edu.hcmute.uteexpress.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.hcmute.uteexpress.entity.AppUser;
import vn.edu.hcmute.uteexpress.entity.SavedAddress;

import java.util.List;
import java.util.Optional;

/**
 * Truy vấn sổ địa chỉ. Mọi phương thức đều nhận thêm AppUser để chặn việc
 * người dùng này xem/sửa địa chỉ của người dùng khác chỉ bằng cách đổi id trên URL.
 */
public interface SavedAddressRepository extends JpaRepository<SavedAddress, Long> {

    /** Địa chỉ mặc định luôn nằm đầu danh sách, sau đó tới địa chỉ mới thêm gần đây. */
    List<SavedAddress> findByUserOrderByAddressTypeAscDefaultAddressDescCreatedAtDesc(AppUser user);

    List<SavedAddress> findByUserAndAddressTypeOrderByDefaultAddressDescCreatedAtDesc(
            AppUser user, SavedAddress.AddressType addressType);

    Optional<SavedAddress> findByIdAndUser(Long id, AppUser user);

    Optional<SavedAddress> findFirstByUserAndAddressTypeAndDefaultAddressTrue(
            AppUser user, SavedAddress.AddressType addressType);

    long countByUser(AppUser user);

    long countByUserAndAddressType(AppUser user, SavedAddress.AddressType addressType);

    boolean existsByUserAndAddressTypeAndLabelIgnoreCase(
            AppUser user, SavedAddress.AddressType addressType, String label);
}
