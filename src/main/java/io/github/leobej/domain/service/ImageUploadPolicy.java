package io.github.leobej.domain.service;

import io.github.leobej.domain.port.ContentTypeDetector;
import io.github.leobej.shared.exception.InvalidAttachmentException;

import java.util.Set;

// What counts as an acceptable uploaded image, for attachments and avatars alike (§25).
//
// Pure domain: no Spring annotations, no infrastructure imports.
public final class ImageUploadPolicy {

    // Images a browser renders as images and nothing else. SVG is deliberately missing: it is XML
    // that can carry script, and opened from a link it runs.
    private static final Set<String> ALLOWED_TYPES = Set.of("image/png", "image/jpeg", "image/gif", "image/webp");

    private ImageUploadPolicy() {}

    // Returns the type read from the bytes - never the one the client claimed.
    public static String requireImage(byte[] content, long maxBytes, ContentTypeDetector detector) {
        if (content == null || content.length == 0) {
            throw new InvalidAttachmentException("EMPTY_FILE", "The file is empty");
        }
        if (content.length > maxBytes) {
            throw new InvalidAttachmentException("FILE_TOO_LARGE",
                    "Maximum file size is " + (maxBytes / (1024 * 1024)) + " MB");
        }
        String mimeType = detector.detect(content);
        if (!ALLOWED_TYPES.contains(mimeType)) {
            throw new InvalidAttachmentException("INVALID_FILE_TYPE", "Only PNG, JPEG, GIF and WebP images are allowed");
        }
        return mimeType;
    }
}
