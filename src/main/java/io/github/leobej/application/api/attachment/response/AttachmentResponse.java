package io.github.leobej.application.api.attachment.response;

import io.github.leobej.domain.model.attachment.Attachment;
import io.github.leobej.domain.model.attachment.AttachmentView;

import java.time.Instant;
import java.util.UUID;

// §27's shape, plus when the link stops working so a client knows to refetch rather than guess.
public record AttachmentResponse(
        UUID id,
        UUID issueId,
        String fileName,
        long fileSize,
        String mimeType,
        String url,
        Instant urlExpiresAt,
        UUID uploadedBy,
        Instant createdAt
) {
    public static AttachmentResponse from(AttachmentView view) {
        Attachment attachment = view.attachment();
        return new AttachmentResponse(attachment.getId(), attachment.getIssueId(), attachment.getFileName(),
                attachment.getFileSize(), attachment.getMimeType(), view.url(), view.urlExpiresAt(),
                attachment.getUploadedBy(), attachment.getCreatedAt());
    }
}
