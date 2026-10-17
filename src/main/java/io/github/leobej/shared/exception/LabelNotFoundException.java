package io.github.leobej.shared.exception;

import java.util.UUID;

public class LabelNotFoundException extends RuntimeException {
    public LabelNotFoundException(UUID labelId) {
        super("Label " + labelId + " was not found");
    }
}
