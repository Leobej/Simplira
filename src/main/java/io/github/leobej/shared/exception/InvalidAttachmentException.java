package io.github.leobej.shared.exception;

import lombok.Getter;

// FILE_TOO_LARGE, INVALID_FILE_TYPE, EMPTY_FILE: what was wrong with the upload itself.
@Getter
public class InvalidAttachmentException extends RuntimeException {
    private final String code;

    public InvalidAttachmentException(String code, String message) {
        super(message);
        this.code = code;
    }
}
