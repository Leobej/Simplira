package io.github.leobej.application.api.project.request;

import io.github.leobej.domain.model.project.ProjectVisibility;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// PATCH semantics: a null field is left alone. Description is the exception - sending "" clears it.
public record UpdateProjectRequest(
        @Size(max = 100) String name,
        @Pattern(regexp = "^[A-Z][A-Z0-9]{1,9}$",
                message = "must be 2-10 characters, start with a letter, uppercase letters and digits only")
        String key,
        @Size(max = 2000) String description,
        ProjectVisibility visibility
) {}
