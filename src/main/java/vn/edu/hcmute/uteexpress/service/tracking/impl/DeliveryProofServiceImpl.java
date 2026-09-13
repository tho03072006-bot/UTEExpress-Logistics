package vn.edu.hcmute.uteexpress.service.tracking.impl;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import vn.edu.hcmute.uteexpress.entity.AppUser;
import vn.edu.hcmute.uteexpress.entity.DeliveryProof;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.repository.AppUserRepository;
import vn.edu.hcmute.uteexpress.repository.DeliveryProofRepository;
import vn.edu.hcmute.uteexpress.repository.OrderRepository;
import vn.edu.hcmute.uteexpress.service.tracking.DeliveryProofService;
import vn.edu.hcmute.uteexpress.service.tracking.DeliveryProofStorageService;

@Service
@Transactional(readOnly = true)
public class DeliveryProofServiceImpl
        implements DeliveryProofService {

    private final DeliveryProofRepository deliveryProofRepository;
    private final AppUserRepository appUserRepository;
    private final OrderRepository orderRepository;
    private final DeliveryProofStorageService storageService;

    public DeliveryProofServiceImpl(
            DeliveryProofRepository deliveryProofRepository,
            AppUserRepository appUserRepository,
            OrderRepository orderRepository,
            DeliveryProofStorageService storageService) {
        this.deliveryProofRepository = deliveryProofRepository;
        this.appUserRepository = appUserRepository;
        this.orderRepository = orderRepository;
        this.storageService = storageService;
    }

    @Override
    public Optional<DeliveryProof> findByOrder(Order order) {
        return deliveryProofRepository.findByOrder(order);
    }

    @Override
    public boolean hasCompleteProof(Order order) {
        return deliveryProofRepository.findByOrder(order)
                .filter(proof ->
                        hasText(proof.getProofImagePath())
                        && hasText(proof.getSignaturePath()))
                .isPresent();
    }

    @Override
    @Transactional
    public DeliveryProof saveProof(
            Long orderId,
            String shipperUsername,
            MultipartFile proofImage,
            String signatureData) {
        AppUser shipper = findShipper(shipperUsername);

        Order order = orderRepository
                .findByIdAndShipper(orderId, shipper)
                .orElseThrow(() -> new IllegalStateException(
                        "Không tìm thấy đơn hoặc đơn không được "
                                + "phân công cho bạn."));

        if (order.getStatus() != Order.OrderStatus.IN_TRANSIT) {
            throw new IllegalStateException(
                    "Chỉ được lưu bằng chứng khi đơn đang giao.");
        }

        String proofImagePath =
                storageService.storeProofImage(proofImage);
        String signaturePath =
                storageService.storeSignature(signatureData);

        DeliveryProof proof = deliveryProofRepository
                .findByOrder(order)
                .orElseGet(DeliveryProof::new);

        proof.setOrder(order);
        proof.setShipper(shipper);
        proof.setProofImagePath(proofImagePath);
        proof.setSignaturePath(signaturePath);
        proof.setUpdatedAt(LocalDateTime.now());

        return deliveryProofRepository.save(proof);
    }

    private AppUser findShipper(String username) {
        AppUser shipper = appUserRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException(
                        "Không tìm thấy tài khoản Shipper."));

        if (shipper.getRole() != AppUser.Role.SHIPPER) {
            throw new IllegalStateException(
                    "Tài khoản hiện tại không có vai trò SHIPPER.");
        }

        return shipper;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}