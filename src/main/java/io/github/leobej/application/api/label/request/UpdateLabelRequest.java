package io.github.leobej.application.api.label.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateLabelRequest(
        @Size(max = 50) String name,
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "must be a hex colour such as #FF5733")
        String color
) {}
