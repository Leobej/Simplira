package io.github.leobej.application.api.link.request;

import io.github.leobej.domain.model.issue.IssueLinkType;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

// The issue in the path is the source, so { targetIssueId: B, type: BLOCKS } on A reads "A blocks B".
public record CreateIssueLinkRequest(@NotNull UUID targetIssueId, @NotNull IssueLinkType type) {}
