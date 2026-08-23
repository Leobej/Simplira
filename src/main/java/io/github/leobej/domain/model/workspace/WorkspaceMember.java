package io.github.leobej.domain.model.workspace;

import io.github.leobej.domain.model.user.User;

import java.time.Instant;

// A membership joined with the person behind it, which is what every member list needs.
public record WorkspaceMember(User user, WorkspaceRole role, Instant joinedAt) {
}
