package vn.edu.hcmute.uteexpress.service.tracking.impl;

import java.time.LocalDateTime;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import vn.edu.hcmute.uteexpress.dto.tracking.SenderOrderUpdateEvent;
import vn.edu.hcmute.uteexpress.util.MailFromNameSetter;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SenderOrderMailListenerTest {
    private final JavaMailSender sender = mock(JavaMailSender.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
    private final MailFromNameSetter from = new MailFromNameSetter("UTEExpress", "shop@gmail.com");

    private SenderOrderUpdateEvent event(String email) {
        return new SenderOrderUpdateEvent(email, "Nguyễn\r\nBcc: x@y.z", "UTE123", SenderOrderUpdateEvent.Kind.PICKUP_FAILED,
                "Người gửi không có mặt", LocalDateTime.of(2026, 10, 12, 8, 30));
    }

    @Test void sendsPlainTextMailWithoutHeaderInjection() {
        when(provider.getIfAvailable()).thenReturn(sender);
        var listener = new SenderOrderMailListener(provider, from, true, Runnable::run);
        listener.onOrderUpdate(event("khach@gmail.com"));
        var captor = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(captor.capture());
        var message = captor.getValue();
        assertArrayEquals(new String[]{"khach@gmail.com"}, message.getTo());
        assertEquals("[UTEExpress] Vận đơn UTE123 chưa lấy được hàng", message.getSubject());
        assertTrue(message.getText().startsWith("Chào Nguyễn Bcc: x@y.z,"), "xuống dòng trong tên bị gộp thành một dòng");
        assertTrue(message.getText().contains("Lý do: Người gửi không có mặt"));
        assertTrue(message.getText().contains("Hẹn lấy hàng lại: 12/10/2026 08:30"));
        assertEquals("UTEExpress <shop@gmail.com>", message.getFrom());
    }

    @Test void hubUpdateMailNamesTheBranchHoldingTheParcel() {
        when(provider.getIfAvailable()).thenReturn(sender);
        var listener = new SenderOrderMailListener(provider, from, true, Runnable::run);
        listener.onOrderUpdate(new SenderOrderUpdateEvent("khach@gmail.com", "Nguyễn Hữu Tài", "UTE456", SenderOrderUpdateEvent.Kind.AT_ORIGIN_HUB,
                null, null, "Tại kho Chi nhánh TP. Hồ Chí Minh, chờ xếp chuyến (tuyến Chi nhánh TP. Hồ Chí Minh → Chi nhánh Hà Nội)"));
        var captor = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(captor.capture());
        assertEquals("[UTEExpress] Vận đơn UTE456 đã nhập kho gửi", captor.getValue().getSubject());
        assertTrue(captor.getValue().getText().contains("Vị trí hiện tại: Tại kho Chi nhánh TP. Hồ Chí Minh, chờ xếp chuyến (tuyến Chi nhánh TP. Hồ Chí Minh → Chi nhánh Hà Nội)"));
        // Đơn cũ chưa có tuyến: không có dòng vị trí.
        assertFalse(listener.message(event("khach@gmail.com")).getText().contains("Vị trí hiện tại"));
    }

    @Test void skipsWhenDisabledUnconfiguredOrTestAddress() {
        when(provider.getIfAvailable()).thenReturn(sender);
        new SenderOrderMailListener(provider, from, false, Runnable::run).onOrderUpdate(event("khach@gmail.com"));
        var enabled = new SenderOrderMailListener(provider, from, true, Runnable::run);
        for (String address : new String[]{null, "", "abc", "a@b.test", "x@test.invalid", "y@example.com", "z@sub.example.org",
                "a b@gmail.com", "@gmail.com", "khach@"})
            enabled.onOrderUpdate(event(address));
        verifyNoInteractions(sender);
        assertFalse(SenderOrderMailListener.configured("CHANGE_ME@gmail.com"));
        assertFalse(SenderOrderMailListener.configured(""));
        assertTrue(SenderOrderMailListener.configured("uteexpress8@gmail.com"));
    }

    @Test void skipsSenderWhoTurnedOffOrderEmails() {
        when(provider.getIfAvailable()).thenReturn(sender);
        new SenderOrderMailListener(provider, from, true, Runnable::run, email -> !email.equals("khach@gmail.com"))
                .onOrderUpdate(event("khach@gmail.com"));
        verifyNoInteractions(sender);
        new SenderOrderMailListener(provider, from, true, Runnable::run, email -> true).onOrderUpdate(event("khach@gmail.com"));
        verify(sender).send(any(SimpleMailMessage.class));
    }

    @Test void smtpErrorsAndFullQueueNeverPropagate() {
        when(provider.getIfAvailable()).thenReturn(sender);
        doThrow(new MailSendException("down")).when(sender).send(any(SimpleMailMessage.class));
        assertDoesNotThrow(() -> new SenderOrderMailListener(provider, from, true, Runnable::run).onOrderUpdate(event("khach@gmail.com")));
        var full = new SenderOrderMailListener(provider, from, true, task -> { throw new RejectedExecutionException("full"); });
        assertDoesNotThrow(() -> full.onOrderUpdate(event("khach@gmail.com")));
        when(provider.getIfAvailable()).thenReturn(null);
        assertDoesNotThrow(() -> new SenderOrderMailListener(provider, from, true, Runnable::run).onOrderUpdate(event("khach@gmail.com")));
    }
}
