package io.github.leobej.infrastructure.persistence.postgres.mapper;

import io.github.leobej.domain.model.token.RefreshToken;
import io.github.leobej.infrastructure.persistence.postgres.entity.RefreshTokenEntity;

public final class RefreshTokenEntityMapper {

    private RefreshTokenEntityMapper() {}  // static-only, never instantiated

    // Database → Domain
    public static RefreshToken toDomain(RefreshTokenEntity entity) {
        return new RefreshToken(
                entity.getId(),
                entity.getUserId(),
                entity.getTokenHash(),
                entity.getDeviceInfo(),
                entity.getIpAddress(),
                entity.getExpiresAt(),
                entity.getCreatedAt(),
                entity.getRevokedAt(),
                entity.getRevokedReason()
        );
    }

    // Domain → Database. isNew drives Persistable#isNew(): true means INSERT, false means UPDATE.
    public static RefreshTokenEntity toEntity(RefreshToken token, boolean isNew) {
        RefreshTokenEntity entity = new RefreshTokenEntity();
        entity.setId(token.getId());
        entity.setUserId(token.getUserId());
        entity.setTokenHash(token.getTokenHash());
        entity.setDeviceInfo(token.getDeviceInfo());
        entity.setIpAddress(token.getIpAddress());
        entity.setExpiresAt(token.getExpiresAt());
        entity.setCreatedAt(token.getCreatedAt());
        entity.setRevokedAt(token.getRevokedAt());
        entity.setRevokedReason(token.getRevokedReason());
        if (isNew) {
            entity.markNew();
        }
        return entity;
    }
}
