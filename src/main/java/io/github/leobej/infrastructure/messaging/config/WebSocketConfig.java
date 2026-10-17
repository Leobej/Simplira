package io.github.leobej.infrastructure.messaging.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

// STOMP over a plain WebSocket at /ws (§8).
//
// The in-memory simple broker, not the RabbitMQ relay §8 describes: it is enough for one instance,
// and nothing here depends on which broker it is. Going multi-instance means swapping
// enableSimpleBroker for enableStompBrokerRelay (plus reactor-netty on the classpath and
// setUserDestinationBroadcast / setUserRegistryBroadcast, so a /user destination reaches sessions
// on other instances) - no client or publisher changes.
//
// No SockJS: every client §8 and §13 plan for speaks WebSocket natively, and SockJS's fallback
// transports cannot carry the Authorization header the CONNECT frame relies on.
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthenticationInterceptor authenticationInterceptor;
    private final String appBaseUrl;

    public WebSocketConfig(StompAuthenticationInterceptor authenticationInterceptor,
                           @Value("${simplira.app.base-url}") String appBaseUrl) {
        this.authenticationInterceptor = authenticationInterceptor;
        this.appBaseUrl = appBaseUrl;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    // Browsers send an Origin header and only the frontend's is accepted. Native clients send
    // none, which Spring lets through - they authenticate on CONNECT like everyone else.
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns(appBaseUrl);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authenticationInterceptor);
    }
}
