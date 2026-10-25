package io.github.leobej.domain.model.attachment;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class Attachment {
    private final UUID id;
    private final UUID issueId;
    private final UUID uploadedBy;
    private final String fileName;    // as uploaded, for display and download - never part of the key
    private final String fileKey;     // where the bytes live in the bucket (§27)
    private final long fileSize;
    private final String mimeType;    // detected from the content, not taken from the client
    private final Instant createdAt;
    private Instant deletedAt;
    private UUID deletedBy;

    public static Attachment upload(UUID issueId, UUID uploadedBy, String fileName, long fileSize, String mimeType) {
        UUID id = UUID.randomUUID();
        // §27 suggests {uuid}_{originalFilename}; the name stays out of the key so nothing a user
        // typed ends up in a storage path.
        return new Attachment(id, issueId, uploadedBy, fileName, "attachments/" + issueId + "/" + id, fileSize,
                mimeType, Instant.now(), null, null);
    }

    public boolean isUploadedBy(UUID userId) {
        return uploadedBy.equals(userId);
    }

    public void delete(UUID actorId) {
        if (deletedAt == null) {
            this.deletedAt = Instant.now();
            this.deletedBy = actorId;
        }
    }
}
