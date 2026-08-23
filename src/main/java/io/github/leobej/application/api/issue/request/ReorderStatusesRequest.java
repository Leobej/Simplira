package io.github.leobej.application.api.issue.request;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

// Every status in the project, in the order they should appear on the board.
public record ReorderStatusesRequest(@NotEmpty List<UUID> statusIds) {}
