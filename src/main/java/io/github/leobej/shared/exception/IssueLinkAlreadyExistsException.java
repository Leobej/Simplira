package io.github.leobej.shared.exception;

// Raised from two places, the service's check and the adapter's unique index, so it carries its
// own message rather than each repeating it.
public class IssueLinkAlreadyExistsException extends BusinessRuleException {
    public IssueLinkAlreadyExistsException() {
        super("ISSUE_LINK_ALREADY_EXISTS",
                "These issues already have a link of this type, in one direction or the other");
    }
}
