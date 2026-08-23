package io.github.leobej.shared.exception;

import java.util.UUID;

public class IssueStatusNotFoundException extends RuntimeException {
    public IssueStatusNotFoundException(UUID statusId) {
        super("Issue status " + statusId + " was not found");
    }
}
