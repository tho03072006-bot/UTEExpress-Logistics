package vn.edu.hcmute.uteexpress.service.tracking.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import vn.edu.hcmute.uteexpress.entity.AppUser;
import vn.edu.hcmute.uteexpress.entity.DeliveryProof;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.repository.AppUserRepository;
import vn.edu.hcmute.uteexpress.repository.DeliveryProofRepository;
import vn.edu.hcmute.uteexpress.repository.OrderRepository;
import vn.edu.hcmute.uteexpress.service.tracking.DeliveryProofStorageService;

@ExtendWith(MockitoExtension.class)
class DeliveryProofServiceImplTest {

    @Mock
    private DeliveryProofRepository deliveryProofRepository;

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private DeliveryProofStorageService storageService;

    @InjectMocks
    private DeliveryProofServiceImpl deliveryProofService;

    @Test
    void saveProof_savesFilesAndProofForAssignedShipper() {
        AppUser shipper = createShipper();
        Order order = createOrder(shipper, Order.OrderStatus.IN_TRANSIT);
        MockMultipartFile image = new MockMultipartFile(
                "proofImage",
                "proof.png",
                "image/png",
                new byte[] { 1 });

        when(appUserRepository.findByUsername("nguyentai"))
                .thenReturn(Optional.of(shipper));
        when(orderRepository.findByIdAndShipper(1L, shipper))
                .thenReturn(Optional.of(order));
        when(storageService.storeProofImage(image))
                .thenReturn("/uploads/giao-hang/proof.png");
        when(storageService.storeSignature("signature-data"))
                .thenReturn("/uploads/giao-hang/signature.png");
        when(deliveryProofRepository.findByOrder(order))
                .thenReturn(Optional.empty());
        when(deliveryProofRepository.save(any(DeliveryProof.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DeliveryProof result = deliveryProofService.saveProof(
                1L,
                "nguyentai",
                image,
                "signature-data");

        assertSame(order, result.getOrder());
        assertSame(shipper, result.getShipper());
        assertTrue(result.getProofImagePath().endsWith("proof.png"));
        assertTrue(result.getSignaturePath().endsWith("signature.png"));
        verify(deliveryProofRepository).save(result);
    }

    @Test
    void saveProof_rejectsOrderAssignedToAnotherShipper() {
        AppUser shipper = createShipper();

        when(appUserRepository.findByUsername("nguyentai"))
                .thenReturn(Optional.of(shipper));
        when(orderRepository.findByIdAndShipper(99L, shipper))
                .thenReturn(Optional.empty());

        assertThrows(
                IllegalStateException.class,
                () -> deliveryProofService.saveProof(
                        99L,
                        "nguyentai",
                        new MockMultipartFile(
                                "proofImage",
                                new byte[] { 1 }),
                        "signature-data"));

        verify(storageService, never()).storeProofImage(any());
    }

    @Test
    void saveProof_rejectsOrderThatIsNotInTransit() {
        AppUser shipper = createShipper();
        Order order = createOrder(
                shipper, Order.OrderStatus.PENDING_PICKUP);

        when(appUserRepository.findByUsername("nguyentai"))
                .thenReturn(Optional.of(shipper));
        when(orderRepository.findByIdAndShipper(1L, shipper))
                .thenReturn(Optional.of(order));

        assertThrows(
                IllegalStateException.class,
                () -> deliveryProofService.saveProof(
                        1L,
                        "nguyentai",
                        new MockMultipartFile(
                                "proofImage",
                                new byte[] { 1 }),
                        "signature-data"));

        verify(storageService, never()).storeProofImage(any());
    }

    @Test
    void hasCompleteProof_returnsTrueWhenBothPathsExist() {
        Order order = new Order();
        DeliveryProof proof = new DeliveryProof();
        proof.setProofImagePath("/uploads/giao-hang/proof.png");
        proof.setSignaturePath("/uploads/giao-hang/signature.png");

        when(deliveryProofRepository.findByOrder(order))
                .thenReturn(Optional.of(proof));

        assertTrue(deliveryProofService.hasCompleteProof(order));
    }

    @Test
    void hasCompleteProof_returnsFalseWhenSignatureIsMissing() {
        Order order = new Order();
        DeliveryProof proof = new DeliveryProof();
        proof.setProofImagePath("/uploads/giao-hang/proof.png");
        proof.setSignaturePath(" ");

        when(deliveryProofRepository.findByOrder(order))
                .thenReturn(Optional.of(proof));

        assertFalse(deliveryProofService.hasCompleteProof(order));
    }

    private AppUser createShipper() {
        AppUser shipper = new AppUser();
        shipper.setUsername("nguyentai");
        shipper.setRole(AppUser.Role.SHIPPER);
        return shipper;
    }

    private Order createOrder(
            AppUser shipper,
            Order.OrderStatus status) {
        Order order = new Order();
        order.setId(1L);
        order.setShipper(shipper);
        order.setStatus(status);
        return order;
    }
}
