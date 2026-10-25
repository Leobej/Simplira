package io.github.leobej.infrastructure.persistence.postgres.jpa;

import io.github.leobej.domain.model.invitation.InvitationStatus;
import io.github.leobej.infrastructure.persistence.postgres.entity.InvitationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaInvitationRepository extends JpaRepository<InvitationEntity, UUID> {

    Optional<InvitationEntity> findByTokenHash(String tokenHash);

    List<InvitationEntity> findByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId);

    boolean existsByWorkspaceIdAndEmailAndStatus(UUID workspaceId, String email, InvitationStatus status);

    @Modifying
    @Query("update InvitationEntity i set i.status = :expired where i.status = :pending and i.expiresAt < :now")
    int expireOutdated(@Param("now") Instant now,
                       @Param("pending") InvitationStatus pending,
                       @Param("expired") InvitationStatus expired);
}
