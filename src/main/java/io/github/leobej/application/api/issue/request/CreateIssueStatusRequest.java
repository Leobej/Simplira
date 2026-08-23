package io.github.leobej.application.api.issue.request;

import io.github.leobej.domain.model.issue.IssueStatusCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateIssueStatusRequest(
        @NotBlank @Size(max = 50) String name,
        // The display name is yours; the category is what the system reads.
        @NotNull IssueStatusCategory category,
        @NotBlank @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "must be a hex colour such as #3B82F6")
        String color
) {}
