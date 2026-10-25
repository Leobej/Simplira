package io.github.leobej.shared.exception;

// Unknown, used or expired - deliberately one error: the answer to all three is "request a new link".
public class InvalidVerificationTokenException extends RuntimeException {
    public InvalidVerificationTokenException() {
        super("This verification link is invalid or has expired. Request a new one.");
    }
}
