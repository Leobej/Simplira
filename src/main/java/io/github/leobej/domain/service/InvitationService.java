package io.github.leobej.domain.service;

import io.github.leobej.domain.model.audit.AuditAction;
import io.github.leobej.domain.model.invitation.Invitation;
import io.github.leobej.domain.model.user.User;
import io.github.leobej.domain.model.workspace.UserWorkspace;
import io.github.leobej.domain.model.workspace.Workspace;
import io.github.leobej.domain.model.workspace.WorkspaceRole;
import io.github.leobej.domain.port.EmailService;
import io.github.leobej.domain.repository.InvitationRepository;
import io.github.leobej.domain.repository.UserRepository;
import io.github.leobej.domain.repository.UserWorkspaceRepository;
import io.github.leobej.shared.exception.AlreadyMemberException;
import io.github.leobej.shared.exception.DuplicateInvitationException;
import io.github.leobej.shared.exception.InvitationNotFoundException;
import io.github.leobej.shared.exception.InvitationNotUsableException;
import io.github.leobej.shared.util.TokenGenerator;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// Pure domain: no Spring annotations, no infrastructure imports.
// Wired as a bean by infrastructure.config.DomainServiceConfig.
public class InvitationService {

    private final InvitationRepository invitationRepository;
    private final UserWorkspaceRepository userWorkspaceRepository;
    private final UserRepository userRepository;
    private final WorkspaceAuthorization authorization;
    private final EmailService emailService;
    private final Duration invitationLifetime;
    private final AuditTrail auditTrail;
    private final VerifiedEmailGate verifiedEmailGate;

    public InvitationService(InvitationRepository invitationRepository,
                             UserWorkspaceRepository userWorkspaceRepository,
                             UserRepository userRepository,
                             WorkspaceAuthorization authorization,
                             EmailService emailService,
                             Duration invitationLifetime,
                             AuditTrail auditTrail,
                             VerifiedEmailGate verifiedEmailGate) {
        this.invitationRepository = invitationRepository;
        this.userWorkspaceRepository = userWorkspaceRepository;
        this.userRepository = userRepository;
        this.authorization = authorization;
        this.emailService = emailService;
        this.invitationLifetime = invitationLifetime;
        this.auditTrail = auditTrail;
        this.verifiedEmailGate = verifiedEmailGate;
    }

    public Invitation invite(UUID workspaceId, String email, WorkspaceRole role, UUID actorId) {
        authorization.requireAdmin(workspaceId, actorId);
        Workspace workspace = authorization.requireWorkspace(workspaceId);
        String normalizedEmail = User.normalizeEmail(email);

        userRepository.findByEmail(normalizedEmail).ifPresent(existing -> {
            if (userWorkspaceRepository.find(workspaceId, existing.getId()).isPresent()) {
                throw new AlreadyMemberException(normalizedEmail);
            }
        });
        if (invitationRepository.existsPending(workspaceId, normalizedEmail)) {
            throw new DuplicateInvitationException(normalizedEmail);
        }

        // Only the hash is stored. The raw token exists in the email and nowhere else, so a
        // leaked database row cannot be used to join a workspace.
        String rawToken = TokenGenerator.generate();
        Invitation invitation = invitationRepository.save(Invitation.create(
                workspaceId, normalizedEmail, role, actorId, TokenGenerator.hash(rawToken), invitationLifetime));

        emailService.sendInvitation(normalizedEmail, workspace.getName(), rawToken);
        return invitation;
    }

    public List<Invitation> list(UUID workspaceId, UUID actorId) {
        authorization.requireAdmin(workspaceId, actorId);
        return invitationRepository.findByWorkspaceId(workspaceId);
    }

    public void cancel(UUID workspaceId, UUID invitationId, UUID actorId) {
        authorization.requireAdmin(workspaceId, actorId);
        Invitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(InvitationNotFoundException::new);

        // Scoped deliberately: an admin of one workspace must not be able to cancel another's.
        if (!invitation.getWorkspaceId().equals(workspaceId)) {
            throw new InvitationNotFoundException();
        }
        invitationRepository.delete(invitationId);
    }

    // §28. Acceptance already refuses an expired invitation; this is what frees the address so the
    // same person can be invited again without someone first trying the dead link.
    public int expireOutdated() {
        return invitationRepository.expireOutdated(Instant.now());
    }

    public AcceptResult accept(String rawToken, User actor) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvitationNotFoundException();
        }
        Invitation invitation = invitationRepository.findByTokenHash(TokenGenerator.hash(rawToken))
                .orElseThrow(InvitationNotFoundException::new);

        // Accepting an invitation sent to your own address proves that address - it verifies you,
        // below - so it needs no verification first. One forwarded from someone else's address
        // proves nothing, so the account has to be verified already. Checked before the
        // invitation is used, so a refusal leaves it usable.
        if (!invitation.wasSentTo(actor.getEmail())) {
            verifiedEmailGate.require(actor);
        }

        try {
            invitation.accept(actor.getId());
        } catch (InvitationNotUsableException ex) {
            // accept() flips a lapsed invitation to EXPIRED; persist that so it stops occupying
            // the pending index and the same person can be invited again.
            invitationRepository.save(invitation);
            throw ex;
        }
        invitationRepository.save(invitation);

        // Accepting twice from two tabs should not create two memberships.
        Optional<UserWorkspace> existing = userWorkspaceRepository.find(invitation.getWorkspaceId(), actor.getId());
        UserWorkspace membership = existing
                .orElseGet(() -> userWorkspaceRepository.save(UserWorkspace.join(
                        actor.getId(),
                        invitation.getWorkspaceId(),
                        invitation.getRole(),
                        invitation.getInvitedBy())));
        if (existing.isEmpty()) {
            auditTrail.onWorkspaceMember(invitation.getWorkspaceId(), actor.getId(), AuditAction.CREATED, null,
                    AuditTrail.values("role", membership.getRole(), "invitedBy", invitation.getInvitedBy()),
                    actor.getId());
        }

        // Clicking a link sent to an address proves control of it, which is exactly what the
        // separate verification flow would prove. Only when the addresses actually match.
        boolean justVerified = false;
        if (invitation.wasSentTo(actor.getEmail()) && !actor.isEmailVerified()) {
            actor.markEmailVerified();
            userRepository.save(actor);
            justVerified = true;
        }

        return new AcceptResult(
                authorization.requireWorkspace(invitation.getWorkspaceId()),
                membership.getRole(),
                justVerified);
    }

    public record AcceptResult(Workspace workspace, WorkspaceRole role, boolean emailVerified) {
    }
}
