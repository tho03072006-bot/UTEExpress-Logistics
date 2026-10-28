package vn.edu.hcmute.uteexpress.service.tracking.impl;

import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import vn.edu.hcmute.uteexpress.dto.tracking.OrderStatusChangedEvent;

@Component
public class OrderStatusWebSocketListener {

    private static final String ORDER_TOPIC_PREFIX = "/topic/order/";

    private final SimpMessageSendingOperations messagingTemplate;

    public OrderStatusWebSocketListener(
            SimpMessageSendingOperations messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Chi gui sau khi transaction commit de trinh duyet khong nhan mot
     * trang thai ma co so du lieu da rollback.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendStatusChanged(OrderStatusChangedEvent event) {
        messagingTemplate.convertAndSend(
                ORDER_TOPIC_PREFIX + event.trackingCode(),
                event);
    }
}
