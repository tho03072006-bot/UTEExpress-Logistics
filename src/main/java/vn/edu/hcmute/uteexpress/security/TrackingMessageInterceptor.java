package vn.edu.hcmute.uteexpress.security;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AnonymousAuthenticationToken;

/** Chỉ máy chủ phát sự kiện; từng subscription phải có quyền trên vận đơn. */
public class TrackingMessageInterceptor implements ChannelInterceptor {
    private final OrderAccessPolicy access;
    public TrackingMessageInterceptor(OrderAccessPolicy access) { this.access = access; }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor headers = StompHeaderAccessor.wrap(message);
        StompCommand command = headers.getCommand();
        if (command == StompCommand.SEND || headers.getMessageType() == SimpMessageType.MESSAGE) {
            throw denied();
        }
        if (command == StompCommand.CONNECT || command == StompCommand.STOMP || command == StompCommand.SUBSCRIBE) {
            if (!(headers.getUser() instanceof Authentication authentication) || !authentication.isAuthenticated()
                    || authentication instanceof AnonymousAuthenticationToken) {
                throw denied();
            }
            if (command == StompCommand.SUBSCRIBE) {
                String destination = headers.getDestination();
                String prefix = "/topic/order/";
                if (access == null || destination == null || !destination.startsWith(prefix)) { throw denied(); }
                String code = destination.substring(prefix.length());
                if (!code.matches("[A-Z0-9][A-Z0-9-]{0,19}") || !access.canReadTrackingCode(code, authentication)) {
                    throw denied();
                }
            }
        }
        return message;
    }

    private AccessDeniedException denied() { return new AccessDeniedException("Không có quyền sử dụng kênh cập nhật này."); }
}
