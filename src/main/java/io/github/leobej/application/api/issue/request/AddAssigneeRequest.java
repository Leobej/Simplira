package io.github.leobej.application.api.issue.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AddAssigneeRequest(@NotNull UUID userId) {}
