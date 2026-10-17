package io.github.leobej.application.api.issue.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

// Capped so one request cannot become an unbounded loop of writes.
public record BulkAssignRequest(
        @NotEmpty @Size(max = 100) List<UUID> issueIds,
        @NotNull UUID userId
) {}
