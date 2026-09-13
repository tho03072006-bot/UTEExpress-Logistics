package vn.edu.hcmute.uteexpress.service.tracking.impl;

import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessageSendingOperations;

import vn.edu.hcmute.uteexpress.dto.tracking.OrderStatusChangedEvent;

@ExtendWith(MockitoExtension.class)
class OrderStatusWebSocketListenerTest {

    @Mock
    private SimpMessageSendingOperations messagingTemplate;

    @InjectMocks
    private OrderStatusWebSocketListener listener;

    @Test
    void sendStatusChanged_sendsEventToTrackingCodeTopic() {
        OrderStatusChangedEvent event = new OrderStatusChangedEvent(
                1L,
                "UTE64477413",
                "IN_TRANSIT",
                LocalDateTime.of(2026, 9, 13, 16, 30));

        listener.sendStatusChanged(event);

        verify(messagingTemplate).convertAndSend(
                "/topic/order/UTE64477413",
                event);
    }
}
