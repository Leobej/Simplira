package io.github.leobej.application.api.user.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Email is not here: it changes through POST /users/me/email and a link sent to the new address.
public record UpdateProfileRequest(@NotBlank @Size(max = 100) String fullName) {}
