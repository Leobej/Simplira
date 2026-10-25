package io.github.leobej.infrastructure.persistence.postgres.adapter;

import io.github.leobej.domain.model.attachment.Attachment;
import io.github.leobej.domain.repository.AttachmentRepository;
import io.github.leobej.infrastructure.persistence.postgres.entity.AttachmentEntity;
import io.github.leobej.infrastructure.persistence.postgres.jpa.JpaAttachmentRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class AttachmentRepositoryAdapter implements AttachmentRepository {

    private final JpaAttachmentRepository jpaRepository;

    public AttachmentRepositoryAdapter(JpaAttachmentRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Attachment save(Attachment attachment) {
        boolean isNew = !jpaRepository.existsById(attachment.getId());
        AttachmentEntity entity = new AttachmentEntity();
        entity.setId(attachment.getId());
        entity.setIssueId(attachment.getIssueId());
        entity.setUploadedBy(attachment.getUploadedBy());
        entity.setFileName(attachment.getFileName());
        entity.setFileKey(attachment.getFileKey());
        entity.setFileSize(attachment.getFileSize());
        entity.setMimeType(attachment.getMimeType());
        entity.setCreatedAt(attachment.getCreatedAt());
        entity.setDeletedAt(attachment.getDeletedAt());
        entity.setDeletedBy(attachment.getDeletedBy());
        if (isNew) {
            entity.markNew();
        }
        return toDomain(jpaRepository.saveAndFlush(entity));
    }

    @Override
    public Optional<Attachment> findById(UUID id) {
        return jpaRepository.findByIdAndDeletedAtIsNull(id).map(AttachmentRepositoryAdapter::toDomain);
    }

    @Override
    public List<Attachment> findByIssueId(UUID issueId) {
        return jpaRepository.findByIssueIdAndDeletedAtIsNullOrderByCreatedAt(issueId).stream()
                .map(AttachmentRepositoryAdapter::toDomain)
                .toList();
    }

    private static Attachment toDomain(AttachmentEntity entity) {
        return new Attachment(entity.getId(), entity.getIssueId(), entity.getUploadedBy(), entity.getFileName(),
                entity.getFileKey(), entity.getFileSize(), entity.getMimeType(), entity.getCreatedAt(),
                entity.getDeletedAt(), entity.getDeletedBy());
    }
}
