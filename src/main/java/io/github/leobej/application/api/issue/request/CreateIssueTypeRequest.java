package io.github.leobej.application.api.issue.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateIssueTypeRequest(
        @NotBlank @Size(max = 50) String name,
        @Size(max = 50) String icon,
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "must be a hex colour such as #4A90D9")
        String color,
        Boolean isDefault
) {}
