package io.github.leobej.shared.exception;

import java.util.UUID;

public class IssueAssigneeNotFoundException extends RuntimeException {
    public IssueAssigneeNotFoundException(UUID userId) {
        super("User with id " + userId + " is not assigned to this issue");
    }
}
