package io.github.leobej.infrastructure.messaging.config;

import io.github.leobej.domain.model.user.User;
import io.github.leobej.domain.repository.UserRepository;
import io.github.leobej.infrastructure.security.JwtService;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.Set;
import java.util.UUID;

// The WebSocket handshake is public - a browser cannot put a bearer token on it - so the access
// token travels on the STOMP CONNECT frame instead, as an Authorization header, and is checked
// here the same way JwtAuthenticationFilter checks it on REST.
//
// It is checked once, at CONNECT: a connection outlives the 15-minute token, and logging out does
// not close it. A reconnect needs a fresh token.
@Component
public class StompAuthenticationInterceptor implements ChannelInterceptor {

    // Everything a client may subscribe to. Anything else is refused, the way a new REST endpoint
    // is private until someone opts it in - and a raw /queue/... name could otherwise be used to
    // listen in on another session's user queue.
    private static final Set<String> SUBSCRIBABLE = Set.of("/user/queue/notifications");

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public StompAuthenticationInterceptor(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        if (accessor.getCommand() == StompCommand.CONNECT) {
            accessor.setUser(authenticate(accessor.getFirstNativeHeader("Authorization")));
        } else if (accessor.getCommand() == StompCommand.SUBSCRIBE
                && !SUBSCRIBABLE.contains(accessor.getDestination())) {
            throw new MessageDeliveryException("Cannot subscribe to " + accessor.getDestination());
        } else if (accessor.getCommand() == StompCommand.SEND) {
            // Nothing is handled client-to-server yet; everything goes through REST.
            throw new MessageDeliveryException("Sending over the WebSocket is not supported");
        }
        return message;
    }

    // The principal's name is the user id, which is what convertAndSendToUser addresses.
    private Principal authenticate(String header) {
        if (header == null || !header.startsWith("Bearer ")) {
            throw new MessageDeliveryException("CONNECT needs an Authorization: Bearer header");
        }
        UUID userId = jwtService.resolveUserId(header.substring(7))
                .flatMap(userRepository::findById)
                .map(User::getId)
                .orElseThrow(() -> new MessageDeliveryException("Invalid or expired access token"));
        String name = userId.toString();
        return () -> name;
    }
}
