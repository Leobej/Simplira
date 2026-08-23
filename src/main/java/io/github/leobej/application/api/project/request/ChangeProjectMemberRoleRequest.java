package io.github.leobej.application.api.project.request;

import io.github.leobej.domain.model.project.ProjectRole;
import jakarta.validation.constraints.NotNull;

public record ChangeProjectMemberRoleRequest(@NotNull ProjectRole role) {}
