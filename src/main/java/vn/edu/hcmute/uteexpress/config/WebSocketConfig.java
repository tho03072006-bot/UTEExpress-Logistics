package vn.edu.hcmute.uteexpress.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Cau hinh WebSocket (STOMP) dung cho tinh nang sang tao: cap nhat trang thai
 * van don theo thoi gian thuc tu Shipper sang User (muc 03 - TV2 phu trach).
 *
 * Frontend se ket noi toi endpoint "/ws-tracking" (qua SockJS), lang nghe
 * kenh "/topic/order/{trackingCode}" de nhan cap nhat trang thai.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-tracking")
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(org.springframework.messaging.simp.config.ChannelRegistration registration) {
        registration.interceptors(new org.springframework.messaging.support.ChannelInterceptor() {
            @Override
            public org.springframework.messaging.Message<?> preSend(org.springframework.messaging.Message<?> message,
                    org.springframework.messaging.MessageChannel channel) {
                var headers = org.springframework.messaging.support.MessageHeaderAccessor.getAccessor(
                        message, org.springframework.messaging.simp.stomp.StompHeaderAccessor.class);
                if (headers == null || headers.getDestination() == null) return message;
                String destination = headers.getDestination();
                if (headers.getCommand() == org.springframework.messaging.simp.stomp.StompCommand.SEND
                        && (destination.startsWith("/topic/") || destination.startsWith("/queue/") || destination.startsWith("/user/")))
                    throw new org.springframework.security.access.AccessDeniedException("Không được tự gửi thông báo hệ thống.");
                if (headers.getCommand() == org.springframework.messaging.simp.stomp.StompCommand.SUBSCRIBE
                        && !destination.matches("/topic/order/[A-Za-z0-9-]{1,40}")
                        && !destination.equals("/user/queue/shipper-notifications"))
                    throw new org.springframework.security.access.AccessDeniedException("Kênh theo dõi không hợp lệ; không được đăng ký wildcard.");
                if (headers.getCommand() == org.springframework.messaging.simp.stomp.StompCommand.SUBSCRIBE
                        && (destination.startsWith("/queue/") || destination.startsWith("/user/"))) {
                    if (!destination.equals("/user/queue/shipper-notifications")
                            || !(headers.getUser() instanceof org.springframework.security.core.Authentication auth)
                            || auth.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals("ROLE_SHIPPER")))
                        throw new org.springframework.security.access.AccessDeniedException("Không có quyền nhận thông báo này.");
                }
                return message;
            }
        });
    }
}
