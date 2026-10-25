package io.github.leobej.domain.repository;

import io.github.leobej.domain.model.attachment.Attachment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AttachmentRepository {

    Attachment save(Attachment attachment);

    // Deleted attachments are invisible to both reads.
    Optional<Attachment> findById(UUID id);

    // Oldest first, the order they were added.
    List<Attachment> findByIssueId(UUID issueId);
}
