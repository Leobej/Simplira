package io.github.leobej.application.api.auth.request;

import jakarta.validation.constraints.NotBlank;

public record ConfirmEmailChangeRequest(@NotBlank String token) {}
