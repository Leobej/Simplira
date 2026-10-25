package io.github.leobej.shared.exception;

public class EmailNotVerifiedException extends RuntimeException {
    public EmailNotVerifiedException() {
        super("Verify your email address first - check your inbox, or request a new link");
    }
}
