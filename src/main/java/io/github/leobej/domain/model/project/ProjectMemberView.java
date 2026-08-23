package io.github.leobej.domain.model.project;

import io.github.leobej.domain.model.user.User;

import java.time.Instant;

// A project membership joined with the person behind it, for member lists and the picker.
public record ProjectMemberView(User user, ProjectRole role, Instant addedAt) {
}
