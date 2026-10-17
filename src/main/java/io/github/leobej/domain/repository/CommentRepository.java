package io.github.leobej.domain.repository;

import io.github.leobej.domain.model.comment.Comment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommentRepository {

    // Persists the comment and brings its mention rows in line with mentionedUserIds.
    Comment save(Comment comment);

    // Soft-deleted comments are invisible to both of these.
    Optional<Comment> findById(UUID id);
    List<Comment> findByIssueId(UUID issueId);
}
