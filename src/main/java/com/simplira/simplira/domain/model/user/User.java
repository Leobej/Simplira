package com.simplira.simplira.domain.model.user;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class User {
    private final UUID id;
    private String email;
    private String password;
    private String fullName;
    private String avatarUrl;
    private boolean emailVerified;
    private final Instant createdAt;
    private Instant updatedAt;

    public static User register(String email, String hashedPassword, String fullName) {
        Instant now = Instant.now();
        return new User(
                UUID.randomUUID(),   // domain owns identity
                email,
                hashedPassword,
                fullName,
                null,                // no avatar yet
                false,               // not verified yet
                now,
                now
        );
    }

    public void markEmailVerified() {
        this.emailVerified = true;
        this.touch();
    }

    public void changeName(String newName) {
        this.fullName = newName;
        this.touch();
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }
}