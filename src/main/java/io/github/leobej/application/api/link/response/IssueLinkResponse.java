package io.github.leobej.application.api.link.response;

import io.github.leobej.domain.model.issue.Issue;
import io.github.leobej.domain.model.issue.IssueLinkType;
import io.github.leobej.domain.model.issue.IssueLinkView;

import java.time.Instant;
import java.util.UUID;

// sourceIssueId and targetIssueId give the direction: when the issue being viewed is the target
// of a BLOCKS link, the client renders it as "blocked by" linkedIssue.
public record IssueLinkResponse(
        UUID id,
        IssueLinkType type,
        UUID sourceIssueId,
        UUID targetIssueId,
        LinkedIssue linkedIssue,
        UUID createdBy,
        Instant createdAt
) {
    // Enough to render the line without a second request: SB-12, its title, struck through if closed.
    public record LinkedIssue(UUID id, String identifier, String title, boolean closed) {}

    public static IssueLinkResponse from(IssueLinkView view, String projectKey) {
        Issue linked = view.linkedIssue();
        return new IssueLinkResponse(
                view.link().getId(),
                view.link().getType(),
                view.link().getSourceIssueId(),
                view.link().getTargetIssueId(),
                new LinkedIssue(linked.getId(), projectKey + "-" + linked.getNumber(), linked.getTitle(),
                        linked.getClosedAt() != null),
                view.link().getCreatedBy(),
                view.link().getCreatedAt()
        );
    }
}
