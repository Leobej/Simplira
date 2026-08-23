package io.github.leobej.shared.exception;

public class InvitationNotFoundException extends RuntimeException {
    public InvitationNotFoundException() {
        super("Invitation was not found");
    }
}
