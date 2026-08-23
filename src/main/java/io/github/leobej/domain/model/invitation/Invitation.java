package io.github.leobej.domain.model.invitation;

import io.github.leobej.domain.model.workspace.WorkspaceRole;
import io.github.leobej.shared.exception.InvitationNotUsableException;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class Invitation {
    private final UUID id;
    private final UUID workspaceId;
    private final String email;
    private final WorkspaceRole role;
    private final UUID invitedBy;
    private final String tokenHash;   // hashed like a refresh token: a leaked row must not be usable
    private InvitationStatus status;
    private final Instant expiresAt;
    private UUID acceptedBy;
    private Instant acceptedAt;
    private final Instant createdAt;

    public static Invitation create(UUID workspaceId,
                                    String email,
                                    WorkspaceRole role,
                                    UUID invitedBy,
                                    String tokenHash,
                                    Duration lifetime) {
        Instant now = Instant.now();
        return new Invitation(
                UUID.randomUUID(),   // domain owns identity
                workspaceId,
                email,
                role,
                invitedBy,
                tokenHash,
                InvitationStatus.PENDING,
                now.plus(lifetime),
                null,
                null,
                now
        );
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public void accept(UUID userId) {
        if (status == InvitationStatus.ACCEPTED) {
            throw new InvitationNotUsableException("This invitation has already been accepted");
        }
        if (isExpired()) {
            // Recorded rather than just reported, so a stale row stops occupying the pending index.
            this.status = InvitationStatus.EXPIRED;
            throw new InvitationNotUsableException("This invitation has expired");
        }
        this.status = InvitationStatus.ACCEPTED;
        this.acceptedBy = userId;
        this.acceptedAt = Instant.now();
    }

    // True when the person accepting is the person who was invited, which is what makes
    // acceptance proof that they control the address.
    public boolean wasSentTo(String candidateEmail) {
        return email.equalsIgnoreCase(candidateEmail);
    }
}
