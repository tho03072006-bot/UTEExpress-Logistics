package vn.edu.hcmute.uteexpress.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.config.ChannelRegistration;
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

    private final ObjectProvider<vn.edu.hcmute.uteexpress.security.OrderAccessPolicy> access;
    private final String[] origins;

    public WebSocketConfig(ObjectProvider<vn.edu.hcmute.uteexpress.security.OrderAccessPolicy> access,
            @Value("${app.websocket.allowed-origins:http://localhost:8080,http://127.0.0.1:8080}") String origins) {
        this.access = access;
        this.origins = java.util.Arrays.stream(origins.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toArray(String[]::new);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new vn.edu.hcmute.uteexpress.security.TrackingMessageInterceptor(access.getIfAvailable()));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-tracking")
                .setAllowedOrigins(origins)
                .withSockJS();
    }
}
