package io.github.leobej.infrastructure.persistence.postgres.jpa;

import io.github.leobej.domain.model.invitation.InvitationStatus;
import io.github.leobej.infrastructure.persistence.postgres.entity.InvitationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaInvitationRepository extends JpaRepository<InvitationEntity, UUID> {

    Optional<InvitationEntity> findByTokenHash(String tokenHash);

    List<InvitationEntity> findByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId);

    boolean existsByWorkspaceIdAndEmailAndStatus(UUID workspaceId, String email, InvitationStatus status);
}
