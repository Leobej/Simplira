package io.github.leobej.shared.exception;

public class SprintAlreadyActiveException extends RuntimeException {
    public SprintAlreadyActiveException() {
        super("This project already has an active sprint; close it before starting another");
    }
}
