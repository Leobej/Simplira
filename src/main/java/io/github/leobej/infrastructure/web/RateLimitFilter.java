package io.github.leobej.infrastructure.web;

import io.github.leobej.application.shared.response.ApiResponse;
import io.github.leobej.application.shared.response.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

// Per-IP limits on the public auth endpoints: the ones anyone can call without an account, where
// guessing passwords or sending email on someone else's behalf would happen. Ahead of Spring
// Security, so a flood is turned away before any password is hashed.
//
// The client IP is request.getRemoteAddr(). Behind a proxy or load balancer that is the proxy's
// address - every user would share one bucket - so set server.forward-headers-strategy=NATIVE
// (FORWARD_HEADERS_STRATEGY) there, and only there: trusting X-Forwarded-For from the open
// internet would let anyone pick their own IP.
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
@ConditionalOnProperty(name = "simplira.rate-limit.enabled", havingValue = "true", matchIfMissing = true)
public class RateLimitFilter extends OncePerRequestFilter {

    record Rule(String name, int capacity, Duration window) {}

    private static final String AUTH = "/api/v1/auth/";

    // Login allows a person a few typos and nothing like a dictionary. The email-sending endpoints
    // are the tightest: each request may put mail in someone's inbox.
    static final Map<String, Rule> RULES = Map.of(
            AUTH + "login", new Rule("login", 10, Duration.ofMinutes(1)),
            AUTH + "register", new Rule("register", 10, Duration.ofHours(1)),
            AUTH + "refresh", new Rule("refresh", 60, Duration.ofMinutes(1)),
            AUTH + "forgot-password", new Rule("forgot-password", 5, Duration.ofMinutes(15)),
            AUTH + "resend-verification", new Rule("resend-verification", 5, Duration.ofMinutes(15)),
            AUTH + "reset-password", new Rule("reset-password", 10, Duration.ofMinutes(15)),
            AUTH + "verify-email", new Rule("verify-email", 20, Duration.ofMinutes(15)),
            AUTH + "confirm-email-change", new Rule("confirm-email-change", 20, Duration.ofMinutes(15)));

    private static final Duration LONGEST_WINDOW = Duration.ofHours(1);

    private final RateLimiter limiter;
    private final ObjectMapper objectMapper;

    @Autowired
    public RateLimitFilter(ObjectMapper objectMapper) {
        this(new RateLimiter(System::nanoTime), objectMapper);
    }

    RateLimitFilter(RateLimiter limiter, ObjectMapper objectMapper) {
        this.limiter = limiter;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !RULES.containsKey(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        Rule rule = RULES.get(request.getRequestURI());
        long retryAfter = limiter.tryAcquire(rule.name() + "|" + request.getRemoteAddr(), rule.capacity(), rule.window());
        if (retryAfter == 0) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfter));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), ApiResponse.failure(ErrorResponse.of("RATE_LIMITED",
                "Too many requests. Try again in " + retryAfter + " seconds.")));
    }

    // Keeps memory bounded: an address that stopped calling is forgotten once its bucket is full.
    @Scheduled(fixedDelay = 10, timeUnit = TimeUnit.MINUTES)
    public void evictIdleBuckets() {
        limiter.evictIdle(LONGEST_WINDOW);
    }
}
