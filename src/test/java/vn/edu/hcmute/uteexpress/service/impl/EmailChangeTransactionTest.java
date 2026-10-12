package vn.edu.hcmute.uteexpress.service.impl;

import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import vn.edu.hcmute.uteexpress.entity.AppUser;
import vn.edu.hcmute.uteexpress.entity.EmailChangeRequest;
import vn.edu.hcmute.uteexpress.repository.AppUserRepository;
import vn.edu.hcmute.uteexpress.repository.EmailChangeRequestRepository;
import vn.edu.hcmute.uteexpress.service.EmailChangeService;
import vn.edu.hcmute.uteexpress.service.OtpRejectedException;
import vn.edu.hcmute.uteexpress.util.MailFromNameSetter;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Uses the real Spring interceptor; a direct service unit test cannot detect rollback. */
class EmailChangeTransactionTest {
    private AppUserRepository users;
    private EmailChangeRequestRepository requests;
    private JavaMailSender mail;
    private AppUser user;
    private EmailChangeRequest pending;
    private SnapshotTransactions transactions;
    private EmailChangeService service;

    @BeforeEach void setUp() {
        users = mock(AppUserRepository.class);
        requests = mock(EmailChangeRequestRepository.class);
        mail = mock(JavaMailSender.class);
        user = new AppUser("customer", "hash", "old@example.com"); user.setId(1L);
        pending = new EmailChangeRequest(user, "new@example.com", "123456", LocalDateTime.now().plusMinutes(8));
        when(users.findByUsernameForUpdate("customer")).thenReturn(Optional.of(user));
        when(requests.findByUser(user)).thenAnswer(invocation -> Optional.ofNullable(pending));
        when(requests.save(any())).thenAnswer(invocation -> { pending = invocation.getArgument(0); return pending; });
        doAnswer(invocation -> { pending = null; return null; }).when(requests).delete(any());
        transactions = new SnapshotTransactions();
        EmailChangeServiceImpl target = new EmailChangeServiceImpl(requests, users, mail,
                new MailFromNameSetter("UTEExpress", "sender@example.com"));
        ProxyFactory factory = new ProxyFactory(target);
        factory.setInterfaces(EmailChangeService.class);
        factory.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        service = (EmailChangeService) factory.getProxy();
    }

    @Test void wrongOtpPersistsCounterAfterExceptionAndFifthFailureDeletesRequest() {
        for (int count = 1; count <= 4; count++) {
            assertThrows(OtpRejectedException.class, () -> service.confirm("customer", "000000"));
            assertEquals(count, pending.getAttemptCount());
            assertEquals(count, transactions.commits);
            assertEquals(0, transactions.rollbacks);
        }
        assertThrows(OtpRejectedException.class, () -> service.confirm("customer", "000000"));
        assertNull(pending);
        assertEquals(5, transactions.commits);
        assertEquals("old@example.com", user.getEmail());
        assertThrows(IllegalStateException.class, () -> service.confirm("customer", "123456"));
        assertEquals(1, transactions.rollbacks);
    }

    @Test void expiredCodeDeletionIsCommitted() {
        pending.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        assertThrows(OtpRejectedException.class, () -> service.confirm("customer", "123456"));
        assertNull(pending);
        assertEquals(1, transactions.commits);
        assertEquals(0, transactions.rollbacks);
    }

    @Test void correctFifthAttemptChangesEmailAndInvalidatesPreviousRecoveryOtp() {
        pending.setAttemptCount(4);
        user.setOtpCode("654321"); user.setOtpExpiry(LocalDateTime.now().plusMinutes(8));
        service.confirm("customer", "123456");
        assertEquals("new@example.com", user.getEmail());
        assertNull(pending);
        assertNull(user.getOtpCode());
        assertNull(user.getOtpExpiry());
        assertEquals(1, transactions.commits);
        verify(users).save(user);
    }

    @Test void uniqueConflictIsStillRolledBackInsteadOfBeingTreatedAsOtpRejection() {
        AppUser other = new AppUser(); other.setId(2L);
        when(users.findByEmail("new@example.com")).thenReturn(Optional.of(other));
        assertThrows(IllegalStateException.class, () -> service.confirm("customer", "123456"));
        assertEquals(0, transactions.commits);
        assertEquals(1, transactions.rollbacks);
        assertEquals("old@example.com", user.getEmail());
        assertNotNull(pending);
    }

    @Test void smtpFailureRollsBackFreshCodeButDoesNotLogItOrChangeEmail() {
        doThrow(new MailSendException("test failure")).when(mail).send(any(SimpleMailMessage.class));
        String previousCode = pending.getOtpCode();
        LocalDateTime previousExpiry = pending.getExpiresAt();
        assertThrows(IllegalStateException.class, () -> service.resendOtp("customer"));
        assertEquals(previousCode, pending.getOtpCode());
        assertEquals(previousExpiry, pending.getExpiresAt());
        assertEquals(0, transactions.commits);
        assertEquals(1, transactions.rollbacks);
        assertEquals("old@example.com", user.getEmail());
    }

    @Test void cooldownBlocksResendAndReplacementBeforeGeneratingAnotherCode() {
        pending.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        assertThrows(IllegalStateException.class, () -> service.resendOtp("customer"));
        assertThrows(IllegalStateException.class, () -> service.requestChange("customer", "next@example.com"));
        verify(mail, never()).send(any(SimpleMailMessage.class));
        assertEquals("new@example.com", pending.getNewEmail());
    }

    /** Minimal transactional store: rollback restores row contents, commit retains them. */
    private final class SnapshotTransactions extends AbstractPlatformTransactionManager {
        private EmailChangeRequest snapshot;
        private String otp, email, oldUserEmail;
        private LocalDateTime expiry, updated;
        private int attempts, commits, rollbacks;
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) {
            snapshot = pending;
            oldUserEmail = user.getEmail();
            if (snapshot != null) {
                attempts = snapshot.getAttemptCount(); otp = snapshot.getOtpCode(); email = snapshot.getNewEmail();
                expiry = snapshot.getExpiresAt(); updated = snapshot.getUpdatedAt();
            }
        }
        @Override protected void doCommit(DefaultTransactionStatus status) { commits++; }
        @Override protected void doRollback(DefaultTransactionStatus status) {
            rollbacks++;
            pending = snapshot; user.setEmail(oldUserEmail);
            if (pending != null) {
                pending.setAttemptCount(attempts); pending.setOtpCode(otp); pending.setNewEmail(email);
                pending.setExpiresAt(expiry); pending.setUpdatedAt(updated);
            }
        }
    }
}
