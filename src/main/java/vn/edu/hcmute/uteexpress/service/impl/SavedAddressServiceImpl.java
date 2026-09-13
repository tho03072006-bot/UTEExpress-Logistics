package vn.edu.hcmute.uteexpress.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.uteexpress.dto.SavedAddressRequest;
import vn.edu.hcmute.uteexpress.entity.AppUser;
import vn.edu.hcmute.uteexpress.entity.SavedAddress;
import vn.edu.hcmute.uteexpress.repository.AppUserRepository;
import vn.edu.hcmute.uteexpress.repository.SavedAddressRepository;
import vn.edu.hcmute.uteexpress.service.SavedAddressService;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Transactional
public class SavedAddressServiceImpl implements SavedAddressService {

    /** Giới hạn số địa chỉ mỗi loại cho một tài khoản, tránh người dùng lưu rác quá nhiều. */
    private static final long MAX_ADDRESS_PER_TYPE = 20;

    private final SavedAddressRepository savedAddressRepository;
    private final AppUserRepository appUserRepository;

    public SavedAddressServiceImpl(SavedAddressRepository savedAddressRepository,
                                   AppUserRepository appUserRepository) {
        this.savedAddressRepository = savedAddressRepository;
        this.appUserRepository = appUserRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<SavedAddress.AddressType, List<SavedAddress>> findGroupedByType(String username) {
        AppUser owner = requireUser(username);
        Map<SavedAddress.AddressType, List<SavedAddress>> grouped = new LinkedHashMap<>();
        for (SavedAddress.AddressType type : SavedAddress.AddressType.values()) {
            grouped.put(type, savedAddressRepository
                    .findByUserAndAddressTypeOrderByDefaultAddressDescCreatedAtDesc(owner, type));
        }
        return grouped;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SavedAddress> findByType(String username, SavedAddress.AddressType addressType) {
        AppUser owner = requireUser(username);
        return savedAddressRepository
                .findByUserAndAddressTypeOrderByDefaultAddressDescCreatedAtDesc(owner, addressType);
    }

    @Override
    @Transactional(readOnly = true)
    public long countOfUser(String username) {
        return savedAddressRepository.countByUser(requireUser(username));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SavedAddress> findDefaultOfType(String username, SavedAddress.AddressType addressType) {
        AppUser owner = requireUser(username);
        return savedAddressRepository.findFirstByUserAndAddressTypeAndDefaultAddressTrue(owner, addressType);
    }

    @Override
    @Transactional(readOnly = true)
    public SavedAddress findOwned(Long id, String username) {
        AppUser owner = requireUser(username);
        return savedAddressRepository.findByIdAndUser(id, owner)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy địa chỉ này trong sổ địa chỉ của bạn"));
    }

    @Override
    public SavedAddress create(SavedAddressRequest request, String username) {
        AppUser owner = requireUser(username);
        SavedAddress.AddressType type = request.getAddressType();

        long currentCount = savedAddressRepository.countByUserAndAddressType(owner, type);
        if (currentCount >= MAX_ADDRESS_PER_TYPE) {
            throw new IllegalStateException("Mỗi loại địa chỉ chỉ lưu tối đa " + MAX_ADDRESS_PER_TYPE
                    + " địa chỉ. Vui lòng xoá bớt trước khi thêm mới.");
        }
        requireUniqueLabel(owner, type, request.getLabel());

        SavedAddress address = new SavedAddress();
        address.setUser(owner);
        copyFields(request, address);
        // Địa chỉ đầu tiên của một loại luôn là mặc định, người dùng không phải tự tick.
        address.setDefaultAddress(currentCount == 0 || request.isDefaultAddress());

        SavedAddress saved = savedAddressRepository.save(address);
        normalizeDefault(owner, type, saved);
        return saved;
    }

    @Override
    public SavedAddress update(Long id, SavedAddressRequest request, String username) {
        AppUser owner = requireUser(username);
        SavedAddress address = savedAddressRepository.findByIdAndUser(id, owner)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy địa chỉ này trong sổ địa chỉ của bạn"));

        SavedAddress.AddressType newType = request.getAddressType();
        SavedAddress.AddressType oldType = address.getAddressType();

        // Chỉ kiểm tra trùng nhãn khi người dùng thực sự đổi nhãn hoặc đổi loại địa chỉ.
        boolean labelChanged = !address.getLabel().equalsIgnoreCase(request.getLabel().trim());
        if (labelChanged || newType != oldType) {
            requireUniqueLabel(owner, newType, request.getLabel());
        }

        copyFields(request, address);
        address.setDefaultAddress(request.isDefaultAddress());
        address.setUpdatedAt(LocalDateTime.now());
        SavedAddress saved = savedAddressRepository.save(address);

        // Đổi loại địa chỉ thì nhóm cũ có thể mất địa chỉ mặc định, nên chuẩn hoá lại cả hai nhóm.
        normalizeDefault(owner, newType, saved);
        if (newType != oldType) {
            normalizeDefault(owner, oldType, null);
        }
        return saved;
    }

    @Override
    public void delete(Long id, String username) {
        AppUser owner = requireUser(username);
        SavedAddress address = savedAddressRepository.findByIdAndUser(id, owner)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy địa chỉ này trong sổ địa chỉ của bạn"));

        SavedAddress.AddressType type = address.getAddressType();
        savedAddressRepository.delete(address);
        savedAddressRepository.flush();

        // Nếu vừa xoá đúng địa chỉ mặc định thì đẩy địa chỉ còn lại mới nhất lên thay thế.
        normalizeDefault(owner, type, null);
    }

    @Override
    public void markAsDefault(Long id, String username) {
        AppUser owner = requireUser(username);
        SavedAddress address = savedAddressRepository.findByIdAndUser(id, owner)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy địa chỉ này trong sổ địa chỉ của bạn"));

        address.setDefaultAddress(true);
        address.setUpdatedAt(LocalDateTime.now());
        SavedAddress saved = savedAddressRepository.save(address);
        normalizeDefault(owner, saved.getAddressType(), saved);
    }

    // ----- Phần dùng chung trong service -----

    private AppUser requireUser(String username) {
        return appUserRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy người dùng: " + username));
    }

    /** Nhãn địa chỉ phải khác nhau trong cùng một loại, để danh sách chọn nhanh không bị rối. */
    private void requireUniqueLabel(AppUser owner, SavedAddress.AddressType type, String label) {
        if (savedAddressRepository.existsByUserAndAddressTypeAndLabelIgnoreCase(owner, type, label.trim())) {
            throw new IllegalStateException("Bạn đã có địa chỉ tên " + label.trim() + " trong mục "
                    + type.getDisplayName() + ". Hãy đặt nhãn khác cho dễ phân biệt.");
        }
    }

    private void copyFields(SavedAddressRequest request, SavedAddress address) {
        address.setLabel(request.getLabel().trim());
        address.setContactName(request.getContactName().trim());
        address.setContactPhone(request.getContactPhone().trim());
        address.setAddressLine(request.getAddressLine().trim());
        address.setAddressType(request.getAddressType());
    }

    /**
     * Bảo đảm mỗi loại địa chỉ của một người dùng có đúng một địa chỉ mặc định.
     *
     * @param preferred địa chỉ vừa được người dùng chọn làm mặc định; truyền null nghĩa là
     *                  không chỉ định - khi đó giữ nguyên địa chỉ mặc định cũ, nếu nhóm
     *                  không còn địa chỉ mặc định nào thì đẩy địa chỉ đầu danh sách lên thay.
     */
    private void normalizeDefault(AppUser owner, SavedAddress.AddressType type, SavedAddress preferred) {
        List<SavedAddress> sameType = savedAddressRepository
                .findByUserAndAddressTypeOrderByDefaultAddressDescCreatedAtDesc(owner, type);
        if (sameType.isEmpty()) {
            return;
        }

        SavedAddress target = null;
        if (preferred != null && preferred.isDefaultAddress()) {
            target = preferred;
        } else {
            target = sameType.stream()
                    .filter(SavedAddress::isDefaultAddress)
                    .findFirst()
                    .orElse(sameType.get(0));
        }

        for (SavedAddress item : sameType) {
            boolean shouldBeDefault = item.getId().equals(target.getId());
            if (item.isDefaultAddress() != shouldBeDefault) {
                item.setDefaultAddress(shouldBeDefault);
                item.setUpdatedAt(LocalDateTime.now());
                savedAddressRepository.save(item);
            }
        }
        target.setDefaultAddress(true);
    }
}
