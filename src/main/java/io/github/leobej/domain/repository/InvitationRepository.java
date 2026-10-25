package io.github.leobej.domain.repository;

import io.github.leobej.domain.model.invitation.Invitation;

import java.time.Instant;
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

    // PENDING past its expiry becomes EXPIRED, which frees the address for a fresh invitation
    // (one pending invitation per address is a partial unique index).
    int expireOutdated(Instant now);
}
