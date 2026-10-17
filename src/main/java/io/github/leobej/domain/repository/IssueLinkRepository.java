package io.github.leobej.domain.repository;

import io.github.leobej.domain.model.issue.IssueLink;
import io.github.leobej.domain.model.issue.IssueLinkType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IssueLinkRepository {

    // Throws IssueLinkAlreadyExistsException when the pair already has a link of this type,
    // either way round — the race the service's own check cannot close.
    IssueLink save(IssueLink link);

    Optional<IssueLink> findById(UUID id);

    // Where the issue is either end, oldest first.
    List<IssueLink> findInvolving(UUID issueId);

    // Either direction.
    boolean existsBetween(UUID firstIssueId, UUID secondIssueId, IssueLinkType type);

    // Whether any BLOCKS link points at the issue.
    boolean isBlockedByLinks(UUID issueId);

    void delete(IssueLink link);

    // Removes every link the issue is either end of, and returns what was removed.
    List<IssueLink> deleteInvolving(UUID issueId);
}
