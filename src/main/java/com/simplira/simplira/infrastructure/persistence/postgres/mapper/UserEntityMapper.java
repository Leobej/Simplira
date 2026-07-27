package com.simplira.simplira.infrastructure.persistence.postgres.mapper;

import com.simplira.simplira.domain.model.user.User;
import com.simplira.simplira.infrastructure.persistence.postgres.entity.UserEntity;

public final class UserEntityMapper {

    private UserEntityMapper() {}  // static-only, never instantiated

    // Database → Domain
    public static User toDomain(UserEntity entity) {
        return new User(
                entity.getId(),
                entity.getEmail(),
                entity.getPassword(),
                entity.getFullName(),
                entity.getAvatarUrl(),
                entity.isEmailVerified(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    // Domain → Database
    public static UserEntity toEntity(User user) {
        UserEntity entity = new UserEntity();
        entity.setId(user.getId());
        entity.setEmail(user.getEmail());
        entity.setPassword(user.getPassword());
        entity.setFullName(user.getFullName());
        entity.setAvatarUrl(user.getAvatarUrl());
        entity.setEmailVerified(user.isEmailVerified());
        entity.setCreatedAt(user.getCreatedAt());
        entity.setUpdatedAt(user.getUpdatedAt());
        return entity;
    }
}
