package io.github.leobej.shared.exception;

public class SlugAlreadyExistsException extends RuntimeException {
    public SlugAlreadyExistsException(String slug) {
        super("A workspace with slug '" + slug + "' already exists");
    }
}
