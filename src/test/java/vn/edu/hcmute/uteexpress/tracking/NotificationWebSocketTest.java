package vn.edu.hcmute.uteexpress.tracking;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.ExecutorSubscribableChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import vn.edu.hcmute.uteexpress.config.WebSocketConfig;
import static org.junit.jupiter.api.Assertions.*;

class NotificationWebSocketTest {
    private final ChannelInterceptor interceptor;
    private final ExecutorSubscribableChannel channel = new ExecutorSubscribableChannel();

    NotificationWebSocketTest() {
        var registration = new InspectableRegistration();
        new WebSocketConfig().configureClientInboundChannel(registration);
        interceptor = registration.registered().get(0);
    }

    @Test void onlyShipperCanSubscribeToTheirOwnNotificationDestination() {
        var own = frame(StompCommand.SUBSCRIBE, "/user/queue/shipper-notifications", "SHIPPER");
        assertSame(own, interceptor.preSend(own, channel));
        for (String role : new String[]{null, "USER", "MANAGER"}) {
            assertThrows(AccessDeniedException.class, () -> interceptor.preSend(
                    frame(StompCommand.SUBSCRIBE, "/user/queue/shipper-notifications", role), channel));
        }
    }

    @Test void evenShipperCannotSubscribeToAnotherUsersQueueOrRawBrokerQueue() {
        for (String destination : List.of("/queue/shipper-notifications-user123", "/queue/**",
                "/user/another-shipper/queue/shipper-notifications", "/user/queue/other")) {
            assertThrows(AccessDeniedException.class, () -> interceptor.preSend(
                    frame(StompCommand.SUBSCRIBE, destination, "SHIPPER"), channel));
        }
    }

    @Test void clientCannotForgeSystemMessagesAndExistingTrackingSubscriptionWorks() {
        for (String destination : List.of("/topic/order/UTE-TEST", "/queue/shipper-notifications",
                "/user/another-shipper/queue/shipper-notifications")) {
            assertThrows(AccessDeniedException.class, () -> interceptor.preSend(
                    frame(StompCommand.SEND, destination, "SHIPPER"), channel));
        }
        var tracking = frame(StompCommand.SUBSCRIBE, "/topic/order/UTE-TEST", "USER");
        assertSame(tracking, interceptor.preSend(tracking, channel));
    }

    @Test void subscriptionsCannotUseWildcardsOrUnknownTopics() {
        for (String destination : List.of("/topic/order/*", "/topic/order/**", "/topic/**", "/topic/order/../other", "/app/private")) {
            assertThrows(AccessDeniedException.class, () -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, destination, "USER"), channel));
        }
    }

    private Message<byte[]> frame(StompCommand command, String destination, String role) {
        var headers = StompHeaderAccessor.create(command);
        headers.setDestination(destination);
        if (role != null) headers.setUser(new UsernamePasswordAuthenticationToken("test-only", "unused",
                List.of(new SimpleGrantedAuthority("ROLE_" + role))));
        headers.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
    }

    private static class InspectableRegistration extends ChannelRegistration {
        List<ChannelInterceptor> registered() { return getInterceptors(); }
    }
}
