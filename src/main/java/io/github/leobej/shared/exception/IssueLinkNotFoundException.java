package io.github.leobej.shared.exception;

import java.util.UUID;

public class IssueLinkNotFoundException extends RuntimeException {
    public IssueLinkNotFoundException(UUID linkId) {
        super("Issue link " + linkId + " was not found");
    }
}
