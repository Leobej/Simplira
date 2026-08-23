package io.github.leobej.application.api.workspace.request;

import io.github.leobej.domain.model.workspace.WorkspaceRole;
import jakarta.validation.constraints.NotNull;

public record ChangeMemberRoleRequest(@NotNull WorkspaceRole role) {}
