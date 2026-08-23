package io.github.leobej.shared.exception;

public class ProjectKeyAlreadyExistsException extends RuntimeException {
    public ProjectKeyAlreadyExistsException(String key) {
        super("A project with key '" + key + "' already exists in this workspace");
    }
}
