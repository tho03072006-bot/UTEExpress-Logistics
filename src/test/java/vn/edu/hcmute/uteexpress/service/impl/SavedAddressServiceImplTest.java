package vn.edu.hcmute.uteexpress.service.impl;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.edu.hcmute.uteexpress.dto.SavedAddressRequest;
import vn.edu.hcmute.uteexpress.entity.AppUser;
import vn.edu.hcmute.uteexpress.entity.SavedAddress;
import vn.edu.hcmute.uteexpress.repository.AppUserRepository;
import vn.edu.hcmute.uteexpress.repository.SavedAddressRepository;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SavedAddressServiceImplTest {
    private SavedAddressRepository addresses;
    private AppUserRepository users;
    private SavedAddressServiceImpl service;
    private AppUser owner;
    private SavedAddress address;

    @BeforeEach void setUp() {
        addresses = mock(SavedAddressRepository.class); users = mock(AppUserRepository.class);
        service = new SavedAddressServiceImpl(addresses, users);
        owner = new AppUser("customer", "hash", "customer@example.com"); owner.setId(1L);
        address = new SavedAddress(); address.setId(10L); address.setUser(owner);
        address.setAddressType(SavedAddress.AddressType.SENDER); address.setLabel("Nhà");
        when(users.findByUsernameForUpdate("customer")).thenReturn(Optional.of(owner));
        when(addresses.findByIdAndUser(10L, owner)).thenReturn(Optional.of(address));
        when(addresses.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test void movingIntoFullGroupIsRejectedBeforeMutatingRow() {
        when(addresses.countByUserAndAddressType(owner, SavedAddress.AddressType.RECEIVER)).thenReturn(20L);
        assertThrows(IllegalStateException.class, () -> service.update(10L, request(), "customer"));
        assertEquals(SavedAddress.AddressType.SENDER, address.getAddressType());
        verify(addresses, never()).save(any());
    }

    @Test void movingIntoGroupWithNineteenRowsIsAllowedAndNormalizesFields() {
        when(addresses.countByUserAndAddressType(owner, SavedAddress.AddressType.RECEIVER)).thenReturn(19L);
        when(addresses.findByUserAndAddressTypeOrderByDefaultAddressDescCreatedAtDesc(owner,
                SavedAddress.AddressType.RECEIVER)).thenReturn(List.of(address));
        SavedAddress saved = service.update(10L, request(), "customer");
        assertEquals(SavedAddress.AddressType.RECEIVER, saved.getAddressType());
        assertEquals("Nhà", saved.getLabel()); assertEquals("Người Nhận", saved.getContactName());
        assertEquals("0901234567", saved.getContactPhone()); assertEquals("1 Đường số 1", saved.getAddressLine());
        assertTrue(saved.isDefaultAddress());
        verify(users).findByUsernameForUpdate("customer");
    }

    @Test void editingWithinFullGroupDoesNotConsumeAnotherSlot() {
        address.setAddressType(SavedAddress.AddressType.RECEIVER);
        SavedAddress saved = service.update(10L, request(), "customer");
        assertEquals(SavedAddress.AddressType.RECEIVER, saved.getAddressType());
        verify(addresses, never()).countByUserAndAddressType(any(), any());
        verify(addresses).save(address);
    }

    @Test void createAtTwentyIsRejected() {
        when(addresses.countByUserAndAddressType(owner, SavedAddress.AddressType.RECEIVER)).thenReturn(20L);
        assertThrows(IllegalStateException.class, () -> service.create(request(), "customer"));
        verify(addresses, never()).save(any());
    }

    @Test void ownershipIsRequiredForUpdateDeleteAndDefault() {
        when(addresses.findByIdAndUser(99L, owner)).thenReturn(Optional.empty());
        assertThrows(IllegalStateException.class, () -> service.update(99L, request(), "customer"));
        assertThrows(IllegalStateException.class, () -> service.delete(99L, "customer"));
        assertThrows(IllegalStateException.class, () -> service.markAsDefault(99L, "customer"));
        verify(addresses, never()).save(any()); verify(addresses, never()).delete(any());
    }

    @Test void directServiceCallerCannotBypassPhoneTypeOrSchemaValidation() {
        SavedAddressRequest request = request(); request.setContactPhone("abc");
        assertThrows(IllegalStateException.class, () -> service.create(request, "customer"));
        request.setContactPhone("0901234567"); request.setAddressLine("a".repeat(256));
        assertThrows(IllegalStateException.class, () -> service.update(10L, request, "customer"));
        request.setAddressLine("Địa chỉ"); request.setAddressType(null);
        assertThrows(IllegalStateException.class, () -> service.create(request, "customer"));
        verify(addresses, never()).save(any());
    }

    private SavedAddressRequest request() {
        SavedAddressRequest request = new SavedAddressRequest();
        request.setLabel(" Nhà "); request.setContactName(" Người Nhận ");
        request.setContactPhone(" 0901234567 "); request.setAddressLine(" 1 Đường số 1 ");
        request.setAddressType(SavedAddress.AddressType.RECEIVER); return request;
    }
}
