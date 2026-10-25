package io.github.leobej.infrastructure.persistence.postgres.adapter;

import io.github.leobej.domain.model.user.EmailVerificationToken;
import io.github.leobej.domain.repository.EmailVerificationTokenRepository;
import io.github.leobej.infrastructure.persistence.postgres.entity.EmailVerificationTokenEntity;
import io.github.leobej.infrastructure.persistence.postgres.jpa.JpaEmailVerificationTokenRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class EmailVerificationTokenRepositoryAdapter implements EmailVerificationTokenRepository {

    private final JpaEmailVerificationTokenRepository jpaRepository;

    public EmailVerificationTokenRepositoryAdapter(JpaEmailVerificationTokenRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public EmailVerificationToken save(EmailVerificationToken token) {
        boolean isNew = !jpaRepository.existsById(token.getId());
        EmailVerificationTokenEntity entity = new EmailVerificationTokenEntity();
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
    public Optional<EmailVerificationToken> findByTokenHash(String tokenHash) {
        return jpaRepository.findByTokenHash(tokenHash).map(EmailVerificationTokenRepositoryAdapter::toDomain);
    }

    @Override
    @Transactional
    public void deleteUnconsumedForUser(UUID userId) {
        jpaRepository.deleteUnconsumedForUser(userId);
    }

    @Override
    @Transactional
    public int deleteSpent(Instant now) {
        return jpaRepository.deleteSpent(now);
    }

    private static EmailVerificationToken toDomain(EmailVerificationTokenEntity entity) {
        return new EmailVerificationToken(entity.getId(), entity.getUserId(), entity.getTokenHash(),
                entity.getExpiresAt(), entity.getConsumedAt(), entity.getCreatedAt());
    }
}
