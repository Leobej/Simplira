package io.github.leobej.shared.exception;

import java.util.UUID;

public class IssueTypeNotFoundException extends RuntimeException {
    public IssueTypeNotFoundException(UUID typeId) {
        super("Issue type " + typeId + " was not found");
    }
}
