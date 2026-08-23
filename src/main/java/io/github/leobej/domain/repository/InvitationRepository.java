package io.github.leobej.domain.repository;

import io.github.leobej.domain.model.invitation.Invitation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvitationRepository {
    Invitation save(Invitation invitation);
    Optional<Invitation> findById(UUID id);
    Optional<Invitation> findByTokenHash(String tokenHash);
    List<Invitation> findByWorkspaceId(UUID workspaceId);
    boolean existsPending(UUID workspaceId, String email);
    void delete(UUID invitationId);
}
