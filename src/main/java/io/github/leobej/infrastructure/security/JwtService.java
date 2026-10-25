package io.github.leobej.infrastructure.security;

import io.github.leobej.domain.model.user.User;
import io.github.leobej.domain.port.AccessTokenIssuer;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService implements AccessTokenIssuer {

    private final SecretKey key;
    private final long accessTokenExpirySeconds;

    public JwtService(
            @Value("${simplira.jwt.secret}") String secret,
            @Value("${simplira.jwt.access-token-expiry}") long accessTokenExpirySeconds) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpirySeconds = accessTokenExpirySeconds;
    }

    @Override
    public String issueAccessToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("fullName", user.getFullName())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(accessTokenExpirySeconds)))
                .signWith(key)
                .compact();
    }

    @Override
    public long accessTokenExpirySeconds() {
        return accessTokenExpirySeconds;
    }

    // Validation and subject extraction in one parse — empty means the token is unusable.
    public Optional<UUID> resolveUserId(String token) {
        return verify(token).map(VerifiedToken::userId);
    }

    // The same, keeping the expiry: a WebSocket connection outlives the request that opened it,
    // so it has to know when its token stops being good.
    public Optional<VerifiedToken> verify(String token) {
        try {
            Claims claims = parseClaims(token);
            return Optional.of(new VerifiedToken(UUID.fromString(claims.getSubject()),
                    claims.getExpiration().toInstant()));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public record VerifiedToken(UUID userId, Instant expiresAt) {}

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
