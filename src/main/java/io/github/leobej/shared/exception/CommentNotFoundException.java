package io.github.leobej.shared.exception;

import java.util.UUID;

public class CommentNotFoundException extends RuntimeException {
    public CommentNotFoundException(UUID commentId) {
        super("Comment " + commentId + " was not found");
    }
}
