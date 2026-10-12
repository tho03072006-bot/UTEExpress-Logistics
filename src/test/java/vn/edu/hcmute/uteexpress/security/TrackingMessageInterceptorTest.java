package vn.edu.hcmute.uteexpress.security;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class TrackingMessageInterceptorTest {
    private final OrderAccessPolicy access = mock(OrderAccessPolicy.class);
    private final TrackingMessageInterceptor interceptor = new TrackingMessageInterceptor(access);

    @ParameterizedTest @ValueSource(strings = {"/topic/order/UTE001", "/queue/test", "/app/test", ""})
    void clientSendIsAlwaysDenied(String destination) {
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SEND, destination, authenticated()), null))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(access);
    }

    @Test void nativeSimpMessageCannotBypassTheStompSendCheck() {
        SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        headers.setUser(authenticated());
        headers.setDestination("/topic/order/UTE001");
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
        assertThatThrownBy(() -> interceptor.preSend(message, null)).isInstanceOf(AccessDeniedException.class);
    }

    @Test void connectAndSubscribeRequireRealAuthentication() {
        Authentication anonymous = new AnonymousAuthenticationToken("key", "anonymousUser",
                List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
        for (StompCommand command : List.of(StompCommand.CONNECT, StompCommand.STOMP, StompCommand.SUBSCRIBE)) {
            assertThatThrownBy(() -> interceptor.preSend(frame(command, "/topic/order/UTE001", null), null))
                    .isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> interceptor.preSend(frame(command, "/topic/order/UTE001", anonymous), null))
                    .isInstanceOf(AccessDeniedException.class);
        }
        verifyNoInteractions(access);
    }

    @Test void authenticatedConnectIsAllowed() {
        Message<byte[]> message = frame(StompCommand.CONNECT, null, authenticated());
        assertThat(interceptor.preSend(message, null)).isSameAs(message);
    }

    @Test void unauthorizedOrForeignTrackingSubscriptionIsDenied() {
        Authentication authentication = authenticated();
        when(access.canReadTrackingCode("UTE001", authentication)).thenReturn(false);
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE,
                "/topic/order/UTE001", authentication), null)).isInstanceOf(AccessDeniedException.class);
        verify(access).canReadTrackingCode("UTE001", authentication);
    }

    @Test void allowedOrderSubscriptionIsPreserved() {
        Authentication authentication = authenticated();
        when(access.canReadTrackingCode("UTE001", authentication)).thenReturn(true);
        Message<byte[]> message = frame(StompCommand.SUBSCRIBE, "/topic/order/UTE001", authentication);
        assertThat(interceptor.preSend(message, null)).isSameAs(message);
    }

    @ParameterizedTest @ValueSource(strings = {"/topic/order/", "/topic/order/*", "/topic/order/ute001",
            "/topic/order/UTE001/other", "/topic/order/UTE001\n", "/topic/order/ABCDEFGHIJKLMNOPQRSTUVWXYZ",
            "/topic/other/UTE001", "/user/queue/order", "/topic/order/UTE001?x=1"})
    void onlyOneCanonicalOrderDestinationCanBeSubscribedTo(String destination) {
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, destination, authenticated()), null))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(access);
    }

    @Test void missingPolicyFailsClosedForSubscriptions() {
        TrackingMessageInterceptor missing = new TrackingMessageInterceptor(null);
        assertThatThrownBy(() -> missing.preSend(frame(StompCommand.SUBSCRIBE,
                "/topic/order/UTE001", authenticated()), null)).isInstanceOf(AccessDeniedException.class);
    }

    private static Authentication authenticated() {
        return UsernamePasswordAuthenticationToken.authenticated("customer", null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    private static Message<byte[]> frame(StompCommand command, String destination, Authentication authentication) {
        StompHeaderAccessor headers = StompHeaderAccessor.create(command);
        if (destination != null) headers.setDestination(destination);
        if (authentication != null) headers.setUser(authentication);
        return MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
    }
}
