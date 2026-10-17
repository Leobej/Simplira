package io.github.leobej.domain.model.issue;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

// Immutable: a link is created or removed, never edited. Changing its type is a new link.
@Getter
@AllArgsConstructor
public class IssueLink {
    private final UUID id;
    private final UUID sourceIssueId;
    private final UUID targetIssueId;
    private final IssueLinkType type;
    private final UUID createdBy;
    private final Instant createdAt;

    public static IssueLink create(UUID sourceIssueId, UUID targetIssueId, IssueLinkType type, UUID createdBy) {
        return new IssueLink(UUID.randomUUID(), sourceIssueId, targetIssueId, type, createdBy, Instant.now());
    }

    public boolean involves(UUID issueId) {
        return sourceIssueId.equals(issueId) || targetIssueId.equals(issueId);
    }

    // Seen from one end, the issue at the other.
    public UUID otherEnd(UUID issueId) {
        return sourceIssueId.equals(issueId) ? targetIssueId : sourceIssueId;
    }

    public boolean blocks() {
        return type == IssueLinkType.BLOCKS;
    }
}
