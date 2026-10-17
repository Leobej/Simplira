package io.github.leobej.domain.service;

import io.github.leobej.domain.model.audit.AuditAction;
import io.github.leobej.domain.model.comment.Comment;
import io.github.leobej.domain.model.comment.CommentView;
import io.github.leobej.domain.model.issue.Issue;
import io.github.leobej.domain.model.user.User;
import io.github.leobej.domain.repository.CommentRepository;
import io.github.leobej.domain.repository.IssueRepository;
import io.github.leobej.domain.repository.UserRepository;
import io.github.leobej.domain.service.ProjectAuthorization.ProjectAccess;
import io.github.leobej.shared.exception.CommentNotFoundException;
import io.github.leobej.shared.exception.ForbiddenException;
import io.github.leobej.shared.exception.IssueNotFoundException;
import io.github.leobej.shared.exception.ProjectNotFoundException;
import io.github.leobej.shared.util.MentionParser;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

// Pure domain: no Spring annotations, no infrastructure imports.
// Wired as a bean by infrastructure.config.DomainServiceConfig.
public class CommentService {

    private final CommentRepository commentRepository;
    private final IssueRepository issueRepository;
    private final UserRepository userRepository;
    private final ProjectAuthorization projectAuthorization;
    private final Notifier notifier;
    private final AuditTrail auditTrail;

    public CommentService(CommentRepository commentRepository,
                          IssueRepository issueRepository,
                          UserRepository userRepository,
                          ProjectAuthorization projectAuthorization,
                          Notifier notifier,
                          AuditTrail auditTrail) {
        this.commentRepository = commentRepository;
        this.issueRepository = issueRepository;
        this.userRepository = userRepository;
        this.projectAuthorization = projectAuthorization;
        this.notifier = notifier;
        this.auditTrail = auditTrail;
    }

    public List<CommentView> list(UUID issueId, UUID actorId) {
        Issue issue = loadIssue(issueId);
        requireAccess(issue, actorId, () -> new IssueNotFoundException(issueId));
        return views(commentRepository.findByIssueId(issueId));
    }

    public CommentView create(UUID issueId, String content, UUID actorId) {
        Issue issue = loadIssue(issueId);
        requireAccess(issue, actorId, () -> new IssueNotFoundException(issueId));

        Comment comment = Comment.write(issueId, actorId, content, validMentions(issue.getProjectId(), content));
        Comment saved = commentRepository.save(comment);
        auditTrail.onComment(issue, saved.getId(), AuditAction.CREATED, null,
                AuditTrail.values("content", saved.getContent()), actorId);
        notifier.commentAdded(issue, saved, actorId);
        return views(List.of(saved)).get(0);
    }

    // Nobody can put words in someone else's mouth, admins included.
    public CommentView edit(UUID commentId, String content, UUID actorId) {
        Comment comment = loadComment(commentId);
        Issue issue = loadIssue(comment.getIssueId());
        requireAccess(issue, actorId, () -> new CommentNotFoundException(commentId));

        if (!comment.isWrittenBy(actorId)) {
            throw new ForbiddenException("Only the author can edit a comment");
        }
        Set<UUID> previouslyMentioned = Set.copyOf(comment.getMentionedUserIds());
        String previousContent = comment.getContent();
        comment.edit(content, validMentions(issue.getProjectId(), content));
        Comment saved = commentRepository.save(comment);
        auditTrail.onComment(issue, saved.getId(), AuditAction.UPDATED,
                AuditTrail.values("content", previousContent), AuditTrail.values("content", saved.getContent()), actorId);
        notifier.commentEdited(issue, saved, previouslyMentioned, actorId);
        return views(List.of(saved)).get(0);
    }

    // The author, or a workspace admin removing something that should not be there. §7 says
    // "delete own"; moderation is the one reason to go beyond it.
    public void delete(UUID commentId, UUID actorId) {
        Comment comment = loadComment(commentId);
        Issue issue = loadIssue(comment.getIssueId());
        ProjectAccess access = requireAccess(issue, actorId, () -> new CommentNotFoundException(commentId));

        if (!comment.isWrittenBy(actorId) && !access.workspaceAdmin()) {
            throw new ForbiddenException("Only the author or a workspace admin can delete a comment");
        }
        comment.delete();
        commentRepository.save(comment);
        // The author is the point when it is someone else doing the deleting: that is moderation.
        auditTrail.onComment(issue, commentId, AuditAction.DELETED,
                AuditTrail.values("authorId", comment.getAuthorId()), null, actorId);
    }

    // A mention of someone who cannot see the project is dropped rather than failing the comment:
    // the text still reads as written, but nobody is pointed at work they cannot open. The same
    // rule as assignees, for the same reason.
    private Set<UUID> validMentions(UUID projectId, String content) {
        return MentionParser.mentionedUserIds(content).stream()
                .filter(userId -> projectAuthorization.hasAccess(projectId, userId))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    // An invisible project makes its issues and comments invisible: PROJECT_NOT_FOUND would
    // confirm the id is real while hiding only the project.
    private ProjectAccess requireAccess(Issue issue, UUID actorId,
                                        java.util.function.Supplier<RuntimeException> notFound) {
        try {
            return projectAuthorization.requireAccess(issue.getProjectId(), actorId);
        } catch (ProjectNotFoundException ex) {
            throw notFound.get();
        }
    }

    private List<CommentView> views(List<Comment> comments) {
        Map<UUID, User> authors = userRepository
                .findAllById(comments.stream().map(Comment::getAuthorId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        return comments.stream()
                .map(comment -> new CommentView(comment, authors.get(comment.getAuthorId())))
                .toList();
    }

    private Comment loadComment(UUID commentId) {
        return commentRepository.findById(commentId)
                .orElseThrow(() -> new CommentNotFoundException(commentId));
    }

    private Issue loadIssue(UUID issueId) {
        return issueRepository.findById(issueId)
                .orElseThrow(() -> new IssueNotFoundException(issueId));
    }
}
