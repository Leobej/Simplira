package io.github.leobej.infrastructure.persistence.postgres.adapter;

import io.github.leobej.domain.model.invitation.Invitation;
import io.github.leobej.domain.model.invitation.InvitationStatus;
import io.github.leobej.domain.repository.InvitationRepository;
import io.github.leobej.infrastructure.persistence.postgres.entity.InvitationEntity;
import io.github.leobej.infrastructure.persistence.postgres.jpa.JpaInvitationRepository;
import io.github.leobej.infrastructure.persistence.postgres.mapper.InvitationEntityMapper;
import io.github.leobej.shared.exception.DuplicateInvitationException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Repository
public class InvitationRepositoryAdapter implements InvitationRepository {

    private static final String PENDING_UNIQUE_INDEX = "uq_invitations_pending_email";

    private final JpaInvitationRepository jpaRepository;

    public InvitationRepositoryAdapter(JpaInvitationRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Invitation save(Invitation invitation) {
        boolean isNew = !jpaRepository.existsById(invitation.getId());
        InvitationEntity entity = InvitationEntityMapper.toEntity(invitation, isNew);
        try {
            return InvitationEntityMapper.toDomain(jpaRepository.saveAndFlush(entity));
        } catch (DataIntegrityViolationException ex) {
            // Two admins inviting the same address at once both pass the pending check.
            if (isDuplicatePending(ex)) {
                throw new DuplicateInvitationException(invitation.getEmail());
            }
            throw ex;
        }
    }

    @Override
    public Optional<Invitation> findById(UUID id) {
        return jpaRepository.findById(id).map(InvitationEntityMapper::toDomain);
    }

    @Override
    public Optional<Invitation> findByTokenHash(String tokenHash) {
        return jpaRepository.findByTokenHash(tokenHash).map(InvitationEntityMapper::toDomain);
    }

    @Override
    public List<Invitation> findByWorkspaceId(UUID workspaceId) {
        return jpaRepository.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId).stream()
                .map(InvitationEntityMapper::toDomain)
                .toList();
    }

    @Override
    public boolean existsPending(UUID workspaceId, String email) {
        return jpaRepository.existsByWorkspaceIdAndEmailAndStatus(
                workspaceId, email, InvitationStatus.PENDING);
    }

    @Override
    public void delete(UUID invitationId) {
        jpaRepository.deleteById(invitationId);
    }

    private static boolean isDuplicatePending(DataIntegrityViolationException ex) {
        if (ex.getCause() instanceof ConstraintViolationException cause && cause.getConstraintName() != null) {
            return cause.getConstraintName().equalsIgnoreCase(PENDING_UNIQUE_INDEX);
        }
        String message = ex.getMostSpecificCause().getMessage();
        return message != null && message.toLowerCase(Locale.ROOT).contains(PENDING_UNIQUE_INDEX);
    }
}
