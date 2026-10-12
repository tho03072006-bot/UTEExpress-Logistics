package vn.edu.hcmute.uteexpress.service.tracking.impl;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import vn.edu.hcmute.uteexpress.entity.AppUser;
import vn.edu.hcmute.uteexpress.entity.DeliveryProof;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.entity.OrderPayment;
import vn.edu.hcmute.uteexpress.repository.AppUserRepository;
import vn.edu.hcmute.uteexpress.repository.DeliveryProofRepository;
import vn.edu.hcmute.uteexpress.repository.OrderRepository;
import vn.edu.hcmute.uteexpress.repository.OrderPaymentRepository;
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
    private final OrderPaymentRepository paymentRepository;

    public DeliveryProofServiceImpl(
            DeliveryProofRepository deliveryProofRepository,
            AppUserRepository appUserRepository,
            OrderRepository orderRepository,
            DeliveryProofStorageService storageService) {
        this(deliveryProofRepository, appUserRepository, orderRepository, storageService, null);
    }

    @Autowired
    public DeliveryProofServiceImpl(
            DeliveryProofRepository deliveryProofRepository,
            AppUserRepository appUserRepository,
            OrderRepository orderRepository,
            DeliveryProofStorageService storageService,
            OrderPaymentRepository paymentRepository) {
        this.deliveryProofRepository = deliveryProofRepository;
        this.appUserRepository = appUserRepository;
        this.orderRepository = orderRepository;
        this.storageService = storageService;
        this.paymentRepository = paymentRepository;
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
                .findByIdAndShipperForUpdate(orderId, shipper)
                .orElseThrow(() -> new IllegalStateException(
                        "Không tìm thấy đơn hoặc đơn không được "
                                + "phân công cho bạn."));

        if (order.getStatus() != Order.OrderStatus.IN_TRANSIT) {
            throw new IllegalStateException(
                    "Chỉ được lưu bằng chứng khi đơn đang giao.");
        }

        requireValidPayment(order);
        DeliveryProof proof = deliveryProofRepository
                .findByOrder(order)
                .orElseGet(DeliveryProof::new);
        String oldImage = proof.getProofImagePath();
        String oldSignature = proof.getSignaturePath();
        String proofImagePath = null;
        String signaturePath = null;
        try {
            proofImagePath = storageService.storeProofImage(proofImage);
            signaturePath = storageService.storeSignature(signatureData);
            proof.setOrder(order);
            proof.setShipper(shipper);
            proof.setProofImagePath(proofImagePath);
            proof.setSignaturePath(signaturePath);
            proof.setUpdatedAt(LocalDateTime.now());
            DeliveryProof saved = deliveryProofRepository.save(proof);
            removeReplacedFilesAfterCommit(
                    java.util.Objects.equals(oldImage, proofImagePath) ? null : oldImage,
                    java.util.Objects.equals(oldSignature, signaturePath) ? null : oldSignature);
            return saved;
        } catch (RuntimeException failure) {
            // Also prevents orphan files if called directly without a transaction proxy.
            storageService.deleteStored(proofImagePath);
            storageService.deleteStored(signaturePath);
            throw failure;
        }
    }

    private void requireValidPayment(Order order) {
        if (paymentRepository == null) {
            throw new IllegalStateException("Không kiểm tra được thông tin thanh toán của đơn hàng.");
        }
        OrderPayment payment = paymentRepository.findByOrderForUpdate(order)
                .orElseThrow(() -> new IllegalStateException("Đơn hàng chưa có thông tin thanh toán hợp lệ."));
        if (payment.getMethod() == null || (payment.getMethod().isOnlineWallet()
                && payment.getStatus() != OrderPayment.PaymentStatus.PAID)) {
            throw new IllegalStateException("Đơn thanh toán qua ví phải thanh toán cước trước khi lưu bằng chứng giao hàng.");
        }
    }

    private void removeReplacedFilesAfterCommit(String oldImage, String oldSignature) {
        Runnable cleanup = () -> {
            storageService.deleteStored(oldImage);
            storageService.deleteStored(oldSignature);
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { cleanup.run(); }
            });
        } else {
            cleanup.run();
        }
    }

    private AppUser findShipper(String username) {
        AppUser shipper = appUserRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException(
                        "Không tìm thấy tài khoản Shipper."));

        if (shipper.getRole() != AppUser.Role.SHIPPER || !shipper.isEnabled()) {
            throw new IllegalStateException(
                    "Tài khoản hiện tại không có vai trò SHIPPER.");
        }

        return shipper;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
