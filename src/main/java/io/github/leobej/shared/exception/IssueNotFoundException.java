package io.github.leobej.shared.exception;

import java.util.UUID;

public class IssueNotFoundException extends RuntimeException {
    public IssueNotFoundException(UUID issueId) {
        super("Issue " + issueId + " was not found");
    }

    public IssueNotFoundException(String identifier) {
        super("Issue " + identifier + " was not found");
    }
}
