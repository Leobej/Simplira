package io.github.leobej.infrastructure.messaging.config;

import io.github.leobej.domain.repository.UserRepository;
import io.github.leobej.infrastructure.security.JwtService;
import io.github.leobej.infrastructure.security.JwtService.VerifiedToken;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// The WebSocket handshake is public - a browser cannot put a bearer token on it - so the access
// token travels on the STOMP CONNECT frame instead, as an Authorization header, and is checked
// here the same way JwtAuthenticationFilter checks it on REST.
//
// A connection outlives its 15-minute token, so the token's expiry is kept per session and
// ConnectionSweeper ends the connection once it passes. The client refreshes and reconnects.
@Component
public class StompAuthenticationInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final SubscriptionAuthorizer subscriptionAuthorizer;
    private final Map<String, Instant> tokenExpiries = new ConcurrentHashMap<>();

    public StompAuthenticationInterceptor(JwtService jwtService,
                                          UserRepository userRepository,
                                          SubscriptionAuthorizer subscriptionAuthorizer) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.subscriptionAuthorizer = subscriptionAuthorizer;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        if (accessor.getCommand() == StompCommand.CONNECT) {
            VerifiedToken token = authenticate(accessor.getFirstNativeHeader("Authorization"));
            // The principal's name is the user id, which is what convertAndSendToUser addresses.
            String name = token.userId().toString();
            accessor.setUser(() -> name);
            tokenExpiries.put(accessor.getSessionId(), token.expiresAt());
        } else if (accessor.getCommand() == StompCommand.SUBSCRIBE) {
            UUID userId = UUID.fromString(accessor.getUser().getName());
            if (!subscriptionAuthorizer.maySubscribe(userId, accessor.getDestination())) {
                throw new MessageDeliveryException("Cannot subscribe to " + accessor.getDestination());
            }
        } else if (accessor.getCommand() == StompCommand.SEND) {
            // Nothing is handled client-to-server yet; everything goes through REST.
            throw new MessageDeliveryException("Sending over the WebSocket is not supported");
        } else if (accessor.getCommand() == StompCommand.DISCONNECT) {
            // Spring sends one through here whenever a session ends, however it ended.
            tokenExpiries.remove(accessor.getSessionId());
        }
        return message;
    }

    // When the token a connection was opened with stops being good; empty for a session that
    // never got past CONNECT.
    public Optional<Instant> tokenExpiry(String sessionId) {
        return Optional.ofNullable(tokenExpiries.get(sessionId));
    }

    private VerifiedToken authenticate(String header) {
        if (header == null || !header.startsWith("Bearer ")) {
            throw new MessageDeliveryException("CONNECT needs an Authorization: Bearer header");
        }
        return jwtService.verify(header.substring(7))
                .filter(token -> userRepository.findById(token.userId()).isPresent())
                .orElseThrow(() -> new MessageDeliveryException("Invalid or expired access token"));
    }
}
