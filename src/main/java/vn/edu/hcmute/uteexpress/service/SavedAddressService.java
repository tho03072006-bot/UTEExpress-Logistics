package vn.edu.hcmute.uteexpress.service;

import vn.edu.hcmute.uteexpress.dto.SavedAddressRequest;
import vn.edu.hcmute.uteexpress.entity.SavedAddress;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Nghiệp vụ sổ địa chỉ đã lưu của người gửi hàng (TV1).
 * Toàn bộ logic (kiểm tra quyền sở hữu, xử lý địa chỉ mặc định) nằm ở đây,
 * Controller chỉ điều hướng - đúng quy ước kiến trúc 3 tầng của nhóm.
 */
public interface SavedAddressService {

    /**
     * Toàn bộ sổ địa chỉ, gom nhóm theo loại để hiển thị thành từng khối trên giao diện.
     * Luôn trả về đủ mọi loại địa chỉ, loại nào chưa có thì kèm danh sách rỗng.
     */
    Map<SavedAddress.AddressType, List<SavedAddress>> findGroupedByType(String username);

    List<SavedAddress> findByType(String username, SavedAddress.AddressType addressType);

    /** Địa chỉ mặc định của một loại, dùng để điền sẵn khi tạo đơn hàng mới. */
    Optional<SavedAddress> findDefaultOfType(String username, SavedAddress.AddressType addressType);

    /**
     * Lấy một địa chỉ và bảo đảm nó thuộc về đúng người đang đăng nhập.
     * Ném IllegalStateException nếu không tìm thấy hoặc địa chỉ của người khác.
     */
    SavedAddress findOwned(Long id, String username);

    SavedAddress create(SavedAddressRequest request, String username);

    SavedAddress update(Long id, SavedAddressRequest request, String username);

    void delete(Long id, String username);

    /** Đặt một địa chỉ làm mặc định, đồng thời bỏ mặc định của các địa chỉ cùng loại. */
    void markAsDefault(Long id, String username);
}
