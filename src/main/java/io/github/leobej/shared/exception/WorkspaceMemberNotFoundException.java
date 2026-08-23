package io.github.leobej.shared.exception;

import java.util.UUID;

public class WorkspaceMemberNotFoundException extends RuntimeException {
    public WorkspaceMemberNotFoundException(UUID userId) {
        super("User " + userId + " is not a member of this workspace");
    }
}
