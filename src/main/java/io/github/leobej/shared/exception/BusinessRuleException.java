package io.github.leobej.shared.exception;

import lombok.Getter;

// For rules that need their own error code without a class each: the last status in a category,
// the last issue type, and the ones the issues slice will add.
@Getter
public class BusinessRuleException extends RuntimeException {
    private final String code;

    public BusinessRuleException(String code, String message) {
        super(message);
        this.code = code;
    }
}
