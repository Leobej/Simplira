package com.simplira.simplira.application.api.auth.response;

import com.simplira.simplira.domain.model.user.User;
import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String fullName,
        String avatarUrl,
        boolean emailVerified,
        Instant createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getAvatarUrl(),
                user.isEmailVerified(),
                user.getCreatedAt()
        );
    }
}
