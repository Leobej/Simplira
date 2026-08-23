package io.github.leobej.shared.exception;

import java.util.UUID;

public class ProjectMemberNotFoundException extends RuntimeException {
    public ProjectMemberNotFoundException(UUID userId) {
        super("User " + userId + " is not a member of this project");
    }
}
