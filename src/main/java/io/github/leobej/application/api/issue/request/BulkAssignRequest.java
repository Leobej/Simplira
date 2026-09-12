package io.github.leobej.application.api.issue.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

// Additive: the named people are added to every listed issue, nobody is taken off. The cap is
// there so one request cannot turn into thousands of writes.
public record BulkAssignRequest(
        @NotEmpty @Size(max = 100) List<UUID> issueIds,
        @NotEmpty @Size(max = 20) List<UUID> userIds
) {}
