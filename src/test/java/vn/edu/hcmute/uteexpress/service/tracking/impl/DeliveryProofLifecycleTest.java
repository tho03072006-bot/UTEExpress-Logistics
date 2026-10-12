package vn.edu.hcmute.uteexpress.service.tracking.impl;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Optional;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import vn.edu.hcmute.uteexpress.entity.AppUser;
import vn.edu.hcmute.uteexpress.entity.DeliveryProof;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.entity.OrderPayment;
import vn.edu.hcmute.uteexpress.repository.AppUserRepository;
import vn.edu.hcmute.uteexpress.repository.DeliveryProofRepository;
import vn.edu.hcmute.uteexpress.repository.OrderPaymentRepository;
import vn.edu.hcmute.uteexpress.repository.OrderRepository;
import vn.edu.hcmute.uteexpress.service.tracking.DeliveryProofService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Real temporary files and Spring transaction completion hooks, without a cloud database. */
class DeliveryProofLifecycleTest {
    @TempDir Path uploadRoot;
    private final DeliveryProofRepository proofs = mock(DeliveryProofRepository.class);
    private final AppUserRepository users = mock(AppUserRepository.class);
    private final OrderRepository orders = mock(OrderRepository.class);
    private final OrderPaymentRepository payments = mock(OrderPaymentRepository.class);
    private final RecordingTransactions transactions = new RecordingTransactions();
    private DeliveryProofService service;
    private DeliveryProofServiceImpl target;
    private DeliveryProofStorageServiceImpl storage;
    private Order order;

    @BeforeEach void setup() {
        storage = new DeliveryProofStorageServiceImpl(uploadRoot.toString());
        target = new DeliveryProofServiceImpl(proofs, users, orders, storage, payments);
        ProxyFactory factory = new ProxyFactory(target);
        factory.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        service = (DeliveryProofService) factory.getProxy();
        AppUser shipper = new AppUser();
        shipper.setUsername("shipper");
        shipper.setRole(AppUser.Role.SHIPPER);
        shipper.setEnabled(true);
        order = new Order();
        order.setId(7L);
        order.setShipper(shipper);
        order.setStatus(Order.OrderStatus.IN_TRANSIT);
        OrderPayment payment = new OrderPayment();
        payment.setMethod(OrderPayment.PaymentMethod.MOMO);
        payment.setStatus(OrderPayment.PaymentStatus.PAID);
        when(users.findByUsername("shipper")).thenReturn(Optional.of(shipper));
        when(orders.findByIdAndShipperForUpdate(7L, shipper)).thenReturn(Optional.of(order));
        when(payments.findByOrderForUpdate(order)).thenReturn(Optional.of(payment));
    }

    @Test void signatureFailureRemovesTheAlreadyStoredImage() throws Exception {
        assertThatThrownBy(() -> service.saveProof(7L, "shipper", image(), "bad signature"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(fileCount()).isZero();
        assertThat(transactions.rollbacks).isEqualTo(1);
        verify(proofs, never()).save(any());
    }

    @Test void signatureFailureAlsoCleansFilesForDirectServiceCalls() throws Exception {
        assertThatThrownBy(() -> target.saveProof(7L, "shipper", image(), "bad signature"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(fileCount()).isZero();
        verify(proofs, never()).save(any());
    }

    @Test void saveFailureRemovesBothNewFilesAndKeepsTheOldProof() throws Exception {
        DeliveryProof old = existingProof();
        String oldImage = old.getProofImagePath(), oldSignature = old.getSignaturePath();
        when(proofs.findByOrder(order)).thenReturn(Optional.of(old));
        when(proofs.save(any())).thenThrow(new IllegalStateException("persistence failed"));
        assertThatThrownBy(() -> service.saveProof(7L, "shipper", image(), signature()))
                .hasMessageContaining("persistence failed");
        assertThat(fileCount()).isEqualTo(2);
        assertThat(Files.exists(savedFile(oldImage))).isTrue();
        assertThat(Files.exists(savedFile(oldSignature))).isTrue();
    }

    @Test void deferredCommitFailureRemovesNewFilesWithoutDeletingOldFiles() throws Exception {
        DeliveryProof old = existingProof();
        String oldImage = old.getProofImagePath(), oldSignature = old.getSignaturePath();
        when(proofs.findByOrder(order)).thenReturn(Optional.of(old));
        when(proofs.save(any())).thenAnswer(call -> call.getArgument(0));
        transactions.failCommit = true;
        assertThatThrownBy(() -> service.saveProof(7L, "shipper", image(), signature()))
                .isInstanceOf(TransactionSystemException.class);
        assertThat(fileCount()).isEqualTo(2);
        assertThat(Files.exists(savedFile(oldImage))).isTrue();
        assertThat(Files.exists(savedFile(oldSignature))).isTrue();
    }

    @Test void replacementDeletesOldFilesOnlyAfterSuccessfulCommit() throws Exception {
        DeliveryProof old = existingProof();
        String oldImage = old.getProofImagePath(), oldSignature = old.getSignaturePath();
        when(proofs.findByOrder(order)).thenReturn(Optional.of(old));
        when(proofs.save(any())).thenAnswer(call -> {
            assertThat(Files.exists(savedFile(oldImage))).isTrue();
            assertThat(Files.exists(savedFile(oldSignature))).isTrue();
            assertThat(fileCount()).isEqualTo(4);
            return call.getArgument(0);
        });
        DeliveryProof saved = service.saveProof(7L, "shipper", image(), signature());
        assertThat(transactions.commits).isEqualTo(1);
        assertThat(Files.exists(savedFile(oldImage))).isFalse();
        assertThat(Files.exists(savedFile(oldSignature))).isFalse();
        assertThat(Files.exists(savedFile(saved.getProofImagePath()))).isTrue();
        assertThat(Files.exists(savedFile(saved.getSignaturePath()))).isTrue();
        assertThat(fileCount()).isEqualTo(2);
    }

    private DeliveryProof existingProof() throws Exception {
        DeliveryProof proof = new DeliveryProof();
        proof.setOrder(order);
        proof.setProofImagePath(storage.storeProofImage(image()));
        proof.setSignaturePath(storage.storeSignature(signature()));
        return proof;
    }

    private long fileCount() throws Exception {
        try (var stream = Files.walk(uploadRoot)) { return stream.filter(Files::isRegularFile).count(); }
    }

    private Path savedFile(String url) {
        return uploadRoot.resolve("giao-hang").resolve(Path.of(url).getFileName().toString());
    }

    private static MockMultipartFile image() throws Exception {
        return new MockMultipartFile("proofImage", "proof.png", "image/png", png());
    }

    private static String signature() throws Exception {
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(png());
    }

    private static byte[] png() throws Exception {
        try (var bytes = new ByteArrayOutputStream()) {
            ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", bytes);
            return bytes.toByteArray();
        }
    }

    private static class RecordingTransactions extends AbstractPlatformTransactionManager {
        boolean failCommit;
        int commits, rollbacks;
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) { }
        @Override protected void doCommit(DefaultTransactionStatus status) {
            if (failCommit) throw new TransactionSystemException("DB flush failed");
            commits++;
        }
        @Override protected void doRollback(DefaultTransactionStatus status) { rollbacks++; }
    }
}
