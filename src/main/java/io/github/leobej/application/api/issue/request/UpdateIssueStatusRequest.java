package io.github.leobej.application.api.issue.request;

import io.github.leobej.domain.model.issue.IssueStatusCategory;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateIssueStatusRequest(
        @Size(max = 50) String name,
        IssueStatusCategory category,
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "must be a hex colour such as #3B82F6")
        String color,
        Boolean isDefault
) {}
