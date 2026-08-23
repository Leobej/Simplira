package io.github.leobej.shared.exception;

import java.util.UUID;

public class SprintNotFoundException extends RuntimeException {
    public SprintNotFoundException(UUID sprintId) {
        super("Sprint " + sprintId + " was not found");
    }
}
