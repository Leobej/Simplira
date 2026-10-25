package io.github.leobej.domain.model.attachment;

import java.time.Instant;

// An attachment with a freshly signed link to its bytes (§27). The link is generated on every read
// and stops working at urlExpiresAt; nothing about it is stored.
public record AttachmentView(Attachment attachment, String url, Instant urlExpiresAt) {}
