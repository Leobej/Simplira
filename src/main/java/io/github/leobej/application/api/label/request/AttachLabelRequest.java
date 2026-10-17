package io.github.leobej.application.api.label.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AttachLabelRequest(@NotNull UUID labelId) {}
