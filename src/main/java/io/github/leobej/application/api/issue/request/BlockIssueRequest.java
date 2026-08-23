package io.github.leobej.application.api.issue.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BlockIssueRequest(@NotBlank @Size(max = 500) String reason) {}
