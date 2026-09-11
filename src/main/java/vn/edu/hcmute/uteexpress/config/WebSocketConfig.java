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
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-tracking")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }
}
