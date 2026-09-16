package io.github.leobej.domain.model.issue;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

// An issue carries many of these: Simplira assigns work to people, not to a single person.
@Getter
@AllArgsConstructor
public class IssueAssignee {
    private final UUID id;
    private final UUID issueId;
    private final UUID userId;
    private final Instant assignedAt;
    private final UUID assignedBy;

    public static IssueAssignee assign(UUID issueId, UUID userId, UUID assignedBy) {
        return new IssueAssignee(UUID.randomUUID(), issueId, userId, Instant.now(), assignedBy);
    }
}
