package io.github.leobej.shared.exception;

import java.util.UUID;

public class WorkspaceNotFoundException extends RuntimeException {
    public WorkspaceNotFoundException(UUID workspaceId) {
        super("Workspace " + workspaceId + " was not found");
    }
}
