package io.github.leobej.infrastructure.messaging.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.simp.user.SimpSession;
import org.springframework.messaging.simp.user.SimpSubscription;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// CONNECT and SUBSCRIBE are checked once, but a connection stays open for hours. Every so often
// this walks the open sessions and ends the ones that would not be let in again:
//   - the access token they connected with has expired, the same rule REST applies per request;
//   - one of their subscriptions is no longer allowed (removed from the workspace or project,
//     project made private, issue deleted).
//
// It ends the whole session rather than the one subscription: the simple broker cannot drop a
// subscription from the server side without the client knowing, and a client that silently stops
// hearing about a board is worse than one told to reconnect. Ending it is a STOMP ERROR frame,
// whose message says why, after which Spring closes the socket - the same thing a refused
// CONNECT or SUBSCRIBE gets. The client reconnects with a fresh token, resubscribes, and every
// check runs again; anything still refused comes back as "Cannot subscribe to ...".
@Component
public class ConnectionSweeper {

    static final String TOKEN_EXPIRED = "Access token expired";
    static final String ACCESS_WITHDRAWN = "No longer allowed to subscribe to ";

    private static final Logger log = LoggerFactory.getLogger(ConnectionSweeper.class);

    private final SimpUserRegistry userRegistry;
    private final StompAuthenticationInterceptor authenticationInterceptor;
    private final SubscriptionAuthorizer subscriptionAuthorizer;
    private final MessageChannel clientOutboundChannel;

    public ConnectionSweeper(SimpUserRegistry userRegistry,
                             StompAuthenticationInterceptor authenticationInterceptor,
                             SubscriptionAuthorizer subscriptionAuthorizer,
                             @Qualifier("clientOutboundChannel") MessageChannel clientOutboundChannel) {
        this.userRegistry = userRegistry;
        this.authenticationInterceptor = authenticationInterceptor;
        this.subscriptionAuthorizer = subscriptionAuthorizer;
        this.clientOutboundChannel = clientOutboundChannel;
    }

    @Scheduled(fixedDelayString = "${simplira.websocket.access-check-interval}")
    public void sweep() {
        sweep(Instant.now());
    }

    public void sweep(Instant now) {
        // One person with three tabs on the same board is one access check, not three.
        Map<String, Boolean> decided = new HashMap<>();
        int ended = 0;
        for (SimpUser user : userRegistry.getUsers()) {
            UUID userId = UUID.fromString(user.getName());
            for (SimpSession session : user.getSessions()) {
                Optional<String> reason = reasonToEnd(userId, session, now, decided);
                if (reason.isPresent()) {
                    end(session.getId(), reason.get());
                    ended++;
                }
            }
        }
        if (ended > 0) {
            log.info("Ended WebSocket sessions: count={}", ended);
        }
    }

    private Optional<String> reasonToEnd(UUID userId, SimpSession session, Instant now, Map<String, Boolean> decided) {
        boolean expired = authenticationInterceptor.tokenExpiry(session.getId())
                .map(expiresAt -> !expiresAt.isAfter(now))
                .orElse(false);
        if (expired) {
            return Optional.of(TOKEN_EXPIRED);
        }
        for (SimpSubscription subscription : session.getSubscriptions()) {
            String destination = subscription.getDestination();
            boolean allowed = decided.computeIfAbsent(userId + " " + destination,
                    key -> subscriptionAuthorizer.maySubscribe(userId, destination));
            if (!allowed) {
                return Optional.of(ACCESS_WITHDRAWN + destination);
            }
        }
        return Optional.empty();
    }

    private void end(String sessionId, String reason) {
        StompHeaderAccessor error = StompHeaderAccessor.create(StompCommand.ERROR);
        error.setMessage(reason);
        error.setSessionId(sessionId);
        clientOutboundChannel.send(MessageBuilder.createMessage(new byte[0], error.getMessageHeaders()));
    }
}
