package io.github.leobej.shared.exception;

public class InvalidProjectKeyException extends RuntimeException {
    public InvalidProjectKeyException(String key) {
        super("Project key '" + key + "' must be 2-10 characters, start with a letter, "
                + "and contain only uppercase letters and digits");
    }
}
