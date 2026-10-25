package io.github.leobej.infrastructure.persistence.postgres.adapter;

import io.github.leobej.domain.model.token.RefreshToken;
import io.github.leobej.domain.model.token.RevocationReason;
import io.github.leobej.domain.repository.RefreshTokenRepository;
import io.github.leobej.infrastructure.persistence.postgres.entity.RefreshTokenEntity;
import io.github.leobej.infrastructure.persistence.postgres.jpa.JpaRefreshTokenRepository;
import io.github.leobej.infrastructure.persistence.postgres.mapper.RefreshTokenEntityMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class RefreshTokenRepositoryAdapter implements RefreshTokenRepository {

    private final JpaRefreshTokenRepository jpaRepository;

    public RefreshTokenRepositoryAdapter(JpaRefreshTokenRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public RefreshToken save(RefreshToken refreshToken) {
        // Same trap as UserEntityMapper: the domain assigns the id, so newness has to be looked up.
        boolean isNew = !jpaRepository.existsById(refreshToken.getId());
        RefreshTokenEntity entity = RefreshTokenEntityMapper.toEntity(refreshToken, isNew);
        return RefreshTokenEntityMapper.toDomain(jpaRepository.saveAndFlush(entity));
    }

    @Override
    public Optional<RefreshToken> findByTokenHash(String tokenHash) {
        return jpaRepository.findByTokenHash(tokenHash)
                .map(RefreshTokenEntityMapper::toDomain);
    }

    @Override
    @Transactional
    public void revokeAllForUser(UUID userId) {
        jpaRepository.revokeAllForUser(userId, Instant.now(), RevocationReason.SIGNED_OUT_EVERYWHERE);
    }

    @Override
    @Transactional
    public int deleteExpired(Instant now) {
        return jpaRepository.deleteExpired(now);
    }
}
