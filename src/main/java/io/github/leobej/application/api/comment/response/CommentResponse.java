package io.github.leobej.application.api.comment.response;

import io.github.leobej.domain.model.comment.CommentView;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CommentResponse(
        UUID id,
        UUID issueId,
        UUID authorId,
        // Resolved here rather than left to the client: a comment outlives its author's
        // membership, so the current member list cannot always name them.
        String authorName,
        String authorAvatarUrl,
        String content,
        List<UUID> mentionedUserIds,
        boolean edited,
        Instant createdAt,
        Instant updatedAt
) {
    public static CommentResponse from(CommentView view) {
        var comment = view.comment();
        return new CommentResponse(
                comment.getId(),
                comment.getIssueId(),
                comment.getAuthorId(),
                view.author() == null ? null : view.author().getFullName(),
                view.author() == null ? null : view.author().getAvatarUrl(),
                comment.getContent(),
                List.copyOf(comment.getMentionedUserIds()),
                comment.isEdited(),
                comment.getCreatedAt(),
                comment.getUpdatedAt()
        );
    }
}
