package io.github.leobej.domain.port;

// Keeps the hashing algorithm (and Spring Security) out of the domain.
public interface PasswordHasher {
    String hash(String rawPassword);
    boolean matches(String rawPassword, String hashedPassword);
}
