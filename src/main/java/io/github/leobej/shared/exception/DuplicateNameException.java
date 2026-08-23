package io.github.leobej.shared.exception;

// Issue types and statuses are both named uniquely within a project.
public class DuplicateNameException extends RuntimeException {
    public DuplicateNameException(String name) {
        super("'" + name + "' already exists in this project");
    }
}
