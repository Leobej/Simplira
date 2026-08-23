package io.github.leobej.shared.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

public final class TokenGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;   // 256 bits of entropy

    private TokenGenerator() {}

    public static String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // SHA-256 rather than BCrypt, for two reasons: the token is 256 random bits, so there is no
    // weak secret to slow an attacker down on, and lookups are by hash - a per-row salt could
    // not be searched for without reading every row.
    public static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every JVM is required to ship SHA-256", e);
        }
    }
}
