package io.github.leobej.application.api.workspace.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateWorkspaceRequest(
        @NotBlank @Size(max = 100) String name,
        // Optional: derived from the name when omitted, and collisions are resolved silently.
        @Size(max = 60) String slug
) {}
