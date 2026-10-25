package io.github.leobej.infrastructure.persistence.postgres.adapter;

import io.github.leobej.domain.model.user.PasswordResetToken;
import io.github.leobej.domain.repository.PasswordResetTokenRepository;
import io.github.leobej.infrastructure.persistence.postgres.entity.PasswordResetTokenEntity;
import io.github.leobej.infrastructure.persistence.postgres.jpa.JpaPasswordResetTokenRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class PasswordResetTokenRepositoryAdapter implements PasswordResetTokenRepository {

    private final JpaPasswordResetTokenRepository jpaRepository;

    public PasswordResetTokenRepositoryAdapter(JpaPasswordResetTokenRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public PasswordResetToken save(PasswordResetToken token) {
        boolean isNew = !jpaRepository.existsById(token.getId());
        PasswordResetTokenEntity entity = new PasswordResetTokenEntity();
        entity.setId(token.getId());
        entity.setUserId(token.getUserId());
        entity.setTokenHash(token.getTokenHash());
        entity.setExpiresAt(token.getExpiresAt());
        entity.setConsumedAt(token.getConsumedAt());
        entity.setCreatedAt(token.getCreatedAt());
        if (isNew) {
            entity.markNew();
        }
        return toDomain(jpaRepository.saveAndFlush(entity));
    }

    @Override
    public Optional<PasswordResetToken> findByTokenHash(String tokenHash) {
        return jpaRepository.findByTokenHash(tokenHash).map(PasswordResetTokenRepositoryAdapter::toDomain);
    }

    @Override
    @Transactional
    public void deleteUnconsumedForUser(UUID userId) {
        jpaRepository.deleteUnconsumedForUser(userId);
    }

    @Override
    public boolean existsCreatedSince(UUID userId, Instant since) {
        return jpaRepository.existsByUserIdAndCreatedAtAfter(userId, since);
    }

    @Override
    @Transactional
    public int deleteSpent(Instant now) {
        return jpaRepository.deleteSpent(now);
    }

    private static PasswordResetToken toDomain(PasswordResetTokenEntity entity) {
        return new PasswordResetToken(entity.getId(), entity.getUserId(), entity.getTokenHash(),
                entity.getExpiresAt(), entity.getConsumedAt(), entity.getCreatedAt());
    }
}
