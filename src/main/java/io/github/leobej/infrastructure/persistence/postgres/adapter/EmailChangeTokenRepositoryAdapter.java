package io.github.leobej.infrastructure.persistence.postgres.adapter;

import io.github.leobej.domain.model.user.EmailChangeToken;
import io.github.leobej.domain.repository.EmailChangeTokenRepository;
import io.github.leobej.infrastructure.persistence.postgres.entity.EmailChangeTokenEntity;
import io.github.leobej.infrastructure.persistence.postgres.jpa.JpaEmailChangeTokenRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class EmailChangeTokenRepositoryAdapter implements EmailChangeTokenRepository {

    private final JpaEmailChangeTokenRepository jpaRepository;

    public EmailChangeTokenRepositoryAdapter(JpaEmailChangeTokenRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public EmailChangeToken save(EmailChangeToken token) {
        boolean isNew = !jpaRepository.existsById(token.getId());
        EmailChangeTokenEntity entity = new EmailChangeTokenEntity();
        entity.setId(token.getId());
        entity.setUserId(token.getUserId());
        entity.setNewEmail(token.getNewEmail());
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
    public Optional<EmailChangeToken> findByTokenHash(String tokenHash) {
        return jpaRepository.findByTokenHash(tokenHash).map(EmailChangeTokenRepositoryAdapter::toDomain);
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

    private static EmailChangeToken toDomain(EmailChangeTokenEntity entity) {
        return new EmailChangeToken(entity.getId(), entity.getUserId(), entity.getNewEmail(), entity.getTokenHash(),
                entity.getExpiresAt(), entity.getConsumedAt(), entity.getCreatedAt());
    }
}
