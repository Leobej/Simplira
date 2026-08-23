package io.github.leobej.application.api.workspace.request;

import jakarta.validation.constraints.Size;

// PATCH semantics: a null field means "leave it alone", never "clear it".
public record UpdateWorkspaceRequest(
        @Size(max = 100) String name,
        @Size(max = 60) String slug
) {}
