package io.github.leobej.domain.service;

import io.github.leobej.domain.model.attachment.Attachment;
import io.github.leobej.domain.model.attachment.AttachmentView;
import io.github.leobej.domain.model.audit.AuditAction;
import io.github.leobej.domain.model.issue.Issue;
import io.github.leobej.domain.port.ContentTypeDetector;
import io.github.leobej.domain.port.FileStorage;
import io.github.leobej.domain.repository.AttachmentRepository;
import io.github.leobej.domain.repository.IssueRepository;
import io.github.leobej.domain.service.ProjectAuthorization.ProjectAccess;
import io.github.leobej.shared.exception.AttachmentNotFoundException;
import io.github.leobej.shared.exception.ForbiddenException;
import io.github.leobej.shared.exception.InvalidAttachmentException;
import io.github.leobej.shared.exception.IssueNotFoundException;
import io.github.leobej.shared.exception.ProjectNotFoundException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

// Image attachments on issues (§4, §25, §27).
//
// Pure domain: no Spring annotations, no infrastructure imports.
// Wired as a bean by infrastructure.config.DomainServiceConfig.
public class AttachmentService {

    public static final long MAX_SIZE = 10L * 1024 * 1024;     // §25
    static final Duration URL_LIFETIME = Duration.ofHours(1);  // §27

    // Images a browser renders as images and nothing else. SVG is deliberately missing: it is XML
    // that can carry script, and opened from a link it runs.
    private static final Set<String> ALLOWED_TYPES = Set.of("image/png", "image/jpeg", "image/gif", "image/webp");
    private static final int MAX_NAME_LENGTH = 255;

    private final AttachmentRepository attachmentRepository;
    private final IssueRepository issueRepository;
    private final ProjectAuthorization projectAuthorization;
    private final FileStorage fileStorage;
    private final ContentTypeDetector contentTypeDetector;
    private final AuditTrail auditTrail;

    public AttachmentService(AttachmentRepository attachmentRepository,
                             IssueRepository issueRepository,
                             ProjectAuthorization projectAuthorization,
                             FileStorage fileStorage,
                             ContentTypeDetector contentTypeDetector,
                             AuditTrail auditTrail) {
        this.attachmentRepository = attachmentRepository;
        this.issueRepository = issueRepository;
        this.projectAuthorization = projectAuthorization;
        this.fileStorage = fileStorage;
        this.contentTypeDetector = contentTypeDetector;
        this.auditTrail = auditTrail;
    }

    public List<AttachmentView> list(UUID issueId, UUID actorId) {
        requireVisibleIssue(issueId, actorId);
        return attachmentRepository.findByIssueId(issueId).stream().map(this::view).toList();
    }

    public AttachmentView upload(UUID issueId, String fileName, byte[] content, UUID actorId) {
        Issue issue = requireVisibleIssue(issueId, actorId);

        if (content == null || content.length == 0) {
            throw new InvalidAttachmentException("EMPTY_FILE", "The file is empty");
        }
        if (content.length > MAX_SIZE) {
            throw new InvalidAttachmentException("FILE_TOO_LARGE", "Maximum file size is 10 MB");
        }
        String mimeType = contentTypeDetector.detect(content);
        if (!ALLOWED_TYPES.contains(mimeType)) {
            throw new InvalidAttachmentException("INVALID_FILE_TYPE", "Only PNG, JPEG, GIF and WebP images are allowed");
        }

        Attachment attachment = Attachment.upload(issueId, actorId, displayName(fileName), content.length, mimeType);
        // Bytes first: a row pointing at nothing would show a broken image, while bytes nobody
        // points at are only wasted space.
        fileStorage.store(attachment.getFileKey(), content, mimeType);
        Attachment saved = attachmentRepository.save(attachment);

        auditTrail.onAttachment(issue, saved.getId(), AuditAction.CREATED, null, AuditTrail.values(
                "fileName", saved.getFileName(), "fileSize", saved.getFileSize(), "mimeType", saved.getMimeType()),
                actorId);
        return view(saved);
    }

    // The uploader, or a workspace admin - the same rule as comments. The row is kept for the
    // record, but the bytes are deleted now: whoever removes a screenshot with a password in it
    // means for it to be gone, not hidden.
    public void delete(UUID attachmentId, UUID actorId) {
        Attachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new AttachmentNotFoundException(attachmentId));
        Issue issue = issueRepository.findById(attachment.getIssueId())
                .orElseThrow(() -> new AttachmentNotFoundException(attachmentId));
        ProjectAccess access;
        try {
            access = projectAuthorization.requireAccess(issue.getProjectId(), actorId);
        } catch (ProjectNotFoundException ex) {
            throw new AttachmentNotFoundException(attachmentId);
        }
        if (!attachment.isUploadedBy(actorId) && !access.workspaceAdmin()) {
            throw new ForbiddenException("Only the uploader or a workspace admin can delete an attachment");
        }

        attachment.delete(actorId);
        attachmentRepository.save(attachment);
        fileStorage.delete(attachment.getFileKey());
        auditTrail.onAttachment(issue, attachmentId, AuditAction.DELETED, AuditTrail.values(
                "fileName", attachment.getFileName(), "uploadedBy", attachment.getUploadedBy()), null, actorId);
    }

    private AttachmentView view(Attachment attachment) {
        Instant expiresAt = Instant.now().plus(URL_LIFETIME);
        return new AttachmentView(attachment,
                fileStorage.temporaryUrl(attachment.getFileKey(), URL_LIFETIME, attachment.getFileName()),
                expiresAt);
    }

    // Only the last path segment, so "../../etc/passwd.png" is shown and downloaded as "passwd.png".
    private static String displayName(String fileName) {
        String name = fileName == null ? "" : fileName.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).strip();
        if (name.isEmpty()) {
            name = "image";
        }
        return name.length() > MAX_NAME_LENGTH ? name.substring(name.length() - MAX_NAME_LENGTH) : name;
    }

    private Issue requireVisibleIssue(UUID issueId, UUID actorId) {
        Issue issue = issueRepository.findById(issueId)
                .orElseThrow(() -> new IssueNotFoundException(issueId));
        try {
            projectAuthorization.requireAccess(issue.getProjectId(), actorId);
        } catch (ProjectNotFoundException ex) {
            throw new IssueNotFoundException(issueId);
        }
        return issue;
    }
}
