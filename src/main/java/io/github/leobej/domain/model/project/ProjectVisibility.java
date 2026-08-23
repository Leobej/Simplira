package io.github.leobej.domain.model.project;

public enum ProjectVisibility {
    // Every workspace member can see it.
    PUBLIC,
    // Only workspace admins and people explicitly added to the project.
    PRIVATE
}
