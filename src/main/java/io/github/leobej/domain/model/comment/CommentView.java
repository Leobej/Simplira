package io.github.leobej.domain.model.comment;

import io.github.leobej.domain.model.user.User;

// A comment with its author resolved. Comments outlive membership, so the client cannot rely on
// the current member list to name whoever wrote one.
public record CommentView(Comment comment, User author) {
}
