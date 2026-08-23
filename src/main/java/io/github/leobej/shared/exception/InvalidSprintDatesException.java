package io.github.leobej.shared.exception;

public class InvalidSprintDatesException extends RuntimeException {
    public InvalidSprintDatesException() {
        super("A sprint cannot end before it starts");
    }
}
