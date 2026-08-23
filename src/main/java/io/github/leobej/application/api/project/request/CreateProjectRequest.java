package io.github.leobej.application.api.project.request;

import io.github.leobej.domain.model.project.ProjectVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateProjectRequest(
        @NotBlank @Size(max = 100) String name,
        // Optional: derived from the name when omitted ("Simplira Backend" -> "SB").
        @Pattern(regexp = "^[A-Z][A-Z0-9]{1,9}$",
                message = "must be 2-10 characters, start with a letter, uppercase letters and digits only")
        String key,
        @Size(max = 2000) String description,
        // Defaults to PUBLIC: visible to the whole workspace.
        ProjectVisibility visibility
) {}
