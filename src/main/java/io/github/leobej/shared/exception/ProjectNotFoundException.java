package io.github.leobej.shared.exception;

import java.util.UUID;

public class ProjectNotFoundException extends RuntimeException {
    public ProjectNotFoundException(UUID projectId) {
        super("Project " + projectId + " was not found");
    }
}
