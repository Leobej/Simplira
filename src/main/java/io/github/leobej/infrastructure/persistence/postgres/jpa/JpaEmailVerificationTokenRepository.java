package io.github.leobej.infrastructure.persistence.postgres.jpa;

import io.github.leobej.infrastructure.persistence.postgres.entity.EmailVerificationTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface JpaEmailVerificationTokenRepository extends JpaRepository<EmailVerificationTokenEntity, UUID> {

    Optional<EmailVerificationTokenEntity> findByTokenHash(String tokenHash);

    boolean existsByUserIdAndCreatedAtAfter(UUID userId, Instant since);

    @Modifying
    @Query("delete from EmailVerificationTokenEntity t where t.userId = :userId and t.consumedAt is null")
    int deleteUnconsumedForUser(@Param("userId") UUID userId);

    @Modifying
    @Query("delete from EmailVerificationTokenEntity t where t.consumedAt is not null or t.expiresAt < :now")
    int deleteSpent(@Param("now") Instant now);
}
