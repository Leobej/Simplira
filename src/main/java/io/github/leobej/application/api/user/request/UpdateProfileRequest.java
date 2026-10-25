package io.github.leobej.application.api.user.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Email is not here: changing it would need the new address verified first, which is its own flow.
public record UpdateProfileRequest(@NotBlank @Size(max = 100) String fullName) {}
