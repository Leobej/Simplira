package io.github.leobej.shared.exception;

// Expired, already accepted, or otherwise no longer acceptable.
public class InvitationNotUsableException extends RuntimeException {
    public InvitationNotUsableException(String reason) {
        super(reason);
    }
}
