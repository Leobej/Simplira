package io.github.leobej.application.api.project.request;

import io.github.leobej.domain.model.project.ProjectRole;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AddProjectMemberRequest(@NotNull UUID userId, @NotNull ProjectRole role) {}
