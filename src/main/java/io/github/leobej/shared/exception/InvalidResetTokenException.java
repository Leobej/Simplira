package io.github.leobej.shared.exception;

// Unknown, used or expired - one error, as for verification links: the answer to all three is
// "request a new link".
public class InvalidResetTokenException extends RuntimeException {
    public InvalidResetTokenException() {
        super("This reset link is invalid or has expired. Request a new one.");
    }
}
