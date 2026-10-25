package io.github.leobej.shared.exception;

import java.util.UUID;

public class AttachmentNotFoundException extends RuntimeException {
    public AttachmentNotFoundException(UUID attachmentId) {
        super("Attachment " + attachmentId + " was not found");
    }
}
