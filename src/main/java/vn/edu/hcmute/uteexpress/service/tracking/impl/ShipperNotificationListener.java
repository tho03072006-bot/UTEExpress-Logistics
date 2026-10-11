package vn.edu.hcmute.uteexpress.service.tracking.impl;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;
import vn.edu.hcmute.uteexpress.dto.tracking.ShipperNotificationEvent;

@Component
public class ShipperNotificationListener {
    private final SimpMessagingTemplate messages;
    public ShipperNotificationListener(SimpMessagingTemplate messages) { this.messages = messages; }
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void send(ShipperNotificationEvent event) {
        messages.convertAndSendToUser(event.username(), "/queue/shipper-notifications", event);
    }
}
