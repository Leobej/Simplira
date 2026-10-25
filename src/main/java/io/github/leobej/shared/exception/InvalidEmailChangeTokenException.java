package io.github.leobej.shared.exception;

// Unknown, used or expired - one error, as for the other emailed links.
public class InvalidEmailChangeTokenException extends RuntimeException {
    public InvalidEmailChangeTokenException() {
        super("This confirmation link is invalid or has expired. Request the change again.");
    }
}
