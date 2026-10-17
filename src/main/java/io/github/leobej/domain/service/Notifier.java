package io.github.leobej.domain.service;

import io.github.leobej.domain.model.comment.Comment;
import io.github.leobej.domain.model.issue.Issue;
import io.github.leobej.domain.model.issue.IssueAssignee;
import io.github.leobej.domain.model.issue.IssueLinkType;
import io.github.leobej.domain.model.issue.IssueStatus;
import io.github.leobej.domain.model.notification.Notification;
import io.github.leobej.domain.model.notification.NotificationAction;
import io.github.leobej.domain.model.notification.NotificationEntityType;
import io.github.leobej.domain.model.notification.NotificationPreference;
import io.github.leobej.domain.model.project.Project;
import io.github.leobej.domain.model.sprint.Sprint;
import io.github.leobej.domain.model.user.User;
import io.github.leobej.domain.port.EmailService;
import io.github.leobej.domain.repository.IssueAssigneeRepository;
import io.github.leobej.domain.repository.NotificationPreferenceRepository;
import io.github.leobej.domain.repository.NotificationRepository;
import io.github.leobej.domain.repository.ProjectRepository;
import io.github.leobej.domain.repository.UserRepository;
import io.github.leobej.shared.exception.ProjectNotFoundException;
import io.github.leobej.shared.util.MentionParser;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

// Turns something that happened into notifications: who hears about it, in what words, on which
// channels. The service that made the change calls in once it is saved.
//
// Each person hears about one event once, in its most specific form: a mention rather than "new
// comment", ISSUE_CLOSED rather than STATUS_CHANGED, ISSUE_BLOCKED rather than LINKED.
//
// Pure domain: no Spring annotations, no infrastructure imports.
// Wired as a bean by infrastructure.config.DomainServiceConfig.
public class Notifier {

    private static final int EXCERPT_LENGTH = 200;

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final IssueAssigneeRepository assigneeRepository;
    private final ProjectAuthorization projectAuthorization;
    private final EmailService emailService;
    private final Duration retention;

    public Notifier(NotificationRepository notificationRepository,
                    NotificationPreferenceRepository preferenceRepository,
                    UserRepository userRepository,
                    ProjectRepository projectRepository,
                    IssueAssigneeRepository assigneeRepository,
                    ProjectAuthorization projectAuthorization,
                    EmailService emailService,
                    Duration retention) {
        this.notificationRepository = notificationRepository;
        this.preferenceRepository = preferenceRepository;
        this.userRepository = userRepository;
        this.projectRepository = projectRepository;
        this.assigneeRepository = assigneeRepository;
        this.projectAuthorization = projectAuthorization;
        this.emailService = emailService;
        this.retention = retention;
    }

    public void issueAssigned(Issue issue, UUID assigneeId, UUID actorId) {
        Project project = projectOf(issue.getProjectId());
        deliver(project, NotificationAction.ASSIGNED, actorId, List.of(assigneeId), issue,
                nameOf(actorId) + " assigned you to " + identifier(project, issue), issue.getTitle());
    }

    public void issueUnassigned(Issue issue, UUID assigneeId, UUID actorId) {
        Project project = projectOf(issue.getProjectId());
        deliver(project, NotificationAction.UNASSIGNED, actorId, List.of(assigneeId), issue,
                nameOf(actorId) + " removed you from " + identifier(project, issue), issue.getTitle());
    }

    // The people mentioned hear MENTIONED; everyone else involved hears COMMENT_ADDED.
    public void commentAdded(Issue issue, Comment comment, UUID actorId) {
        Project project = projectOf(issue.getProjectId());
        String actor = nameOf(actorId);
        String excerpt = excerpt(comment.getContent());

        deliver(project, NotificationAction.MENTIONED, actorId, comment.getMentionedUserIds(), issue,
                actor + " mentioned you on " + identifier(project, issue), excerpt);

        Set<UUID> others = involvedIn(issue);
        others.removeAll(comment.getMentionedUserIds());
        deliver(project, NotificationAction.COMMENT_ADDED, actorId, others, issue,
                actor + " commented on " + identifier(project, issue), excerpt);
    }

    // An edit reaches only the people it newly mentions: everyone else was told the first time.
    public void commentEdited(Issue issue, Comment comment, Set<UUID> previouslyMentioned, UUID actorId) {
        Set<UUID> newlyMentioned = new LinkedHashSet<>(comment.getMentionedUserIds());
        newlyMentioned.removeAll(previouslyMentioned);
        if (newlyMentioned.isEmpty()) {
            return;
        }
        Project project = projectOf(issue.getProjectId());
        deliver(project, NotificationAction.MENTIONED, actorId, newlyMentioned, issue,
                nameOf(actorId) + " mentioned you on " + identifier(project, issue), excerpt(comment.getContent()));
    }

    // closed is true only on the move into DONE: "Done" to "Shipped" is a status change, not a
    // second closing.
    public void statusChanged(Issue issue, IssueStatus status, boolean closed, UUID actorId) {
        Project project = projectOf(issue.getProjectId());
        String actor = nameOf(actorId);
        String identifier = identifier(project, issue);
        if (closed) {
            deliver(project, NotificationAction.ISSUE_CLOSED, actorId, involvedIn(issue), issue,
                    actor + " closed " + identifier, issue.getTitle());
        } else {
            deliver(project, NotificationAction.STATUS_CHANGED, actorId, involvedIn(issue), issue,
                    actor + " moved " + identifier + " to " + status.getName(), issue.getTitle());
        }
    }

    public void issueBlocked(Issue issue, String reason, UUID actorId) {
        Project project = projectOf(issue.getProjectId());
        deliver(project, NotificationAction.ISSUE_BLOCKED, actorId, involvedIn(issue), issue,
                nameOf(actorId) + " blocked " + identifier(project, issue), reason);
    }

    public void issueBlockedByLink(Issue blocked, Issue blocker, UUID actorId) {
        Project project = projectOf(blocked.getProjectId());
        deliver(project, NotificationAction.ISSUE_BLOCKED, actorId, involvedIn(blocked), blocked,
                nameOf(actorId) + " blocked " + identifier(project, blocked),
                "Blocked by " + identifier(project, blocker) + ": " + blocker.getTitle());
    }

    // Told to the people on the target: the one doing the linking is usually working the source.
    public void issueLinked(Issue source, Issue target, IssueLinkType type, UUID actorId) {
        Project project = projectOf(target.getProjectId());
        deliver(project, NotificationAction.LINKED, actorId, involvedIn(target), target,
                nameOf(actorId) + " linked " + identifier(project, source) + " to " + identifier(project, target),
                identifier(project, source) + " " + phrase(type) + " " + identifier(project, target)
                        + ": " + source.getTitle());
    }

    // Sprint events go to whoever has work in the sprint. For a PUBLIC project "every member"
    // would be the whole workspace, which is how notifications get muted.
    public void sprintStarted(Sprint sprint, Collection<UUID> issueIds, UUID actorId) {
        Project project = projectOf(sprint.getProjectId());
        deliverForSprint(project, NotificationAction.SPRINT_STARTED, actorId, sprint, issueIds,
                nameOf(actorId) + " started " + sprint.getName());
    }

    public void sprintClosed(Sprint sprint, Collection<UUID> issueIds, UUID actorId) {
        Project project = projectOf(sprint.getProjectId());
        deliverForSprint(project, NotificationAction.SPRINT_CLOSED, actorId, sprint, issueIds,
                nameOf(actorId) + " closed " + sprint.getName());
    }

    private void deliverForSprint(Project project, NotificationAction action, UUID actorId, Sprint sprint,
                                  Collection<UUID> issueIds, String title) {
        Set<UUID> assignees = assigneeRepository.findAssigneeIds(issueIds).values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        deliver(project, action, actorId, assignees, NotificationEntityType.SPRINT, sprint.getId(),
                title, project.getName());
    }

    private void deliver(Project project, NotificationAction action, UUID actorId, Collection<UUID> candidates,
                         Issue issue, String title, String body) {
        deliver(project, action, actorId, candidates, NotificationEntityType.ISSUE, issue.getId(), title, body);
    }

    private void deliver(Project project,
                         NotificationAction action,
                         UUID actorId,
                         Collection<UUID> candidates,
                         NotificationEntityType entityType,
                         UUID entityId,
                         String title,
                         String body) {
        // Nobody hears about their own action, or about work they can no longer open: a reporter
        // who has left the workspace is still the issue's reporter.
        List<UUID> recipients = candidates.stream()
                .distinct()
                .filter(userId -> !userId.equals(actorId))
                .filter(userId -> projectAuthorization.hasAccess(project.getId(), userId))
                .toList();
        if (recipients.isEmpty()) {
            return;
        }

        Map<UUID, NotificationPreference> stored = preferenceRepository
                .findForAction(project.getWorkspaceId(), action, recipients).stream()
                .collect(Collectors.toMap(NotificationPreference::userId, Function.identity()));

        List<UUID> byEmail = new ArrayList<>();
        for (UUID recipient : recipients) {
            NotificationPreference preference = stored.getOrDefault(recipient,
                    NotificationPreference.defaults(recipient, project.getWorkspaceId(), action));
            if (preference.inApp()) {
                notificationRepository.save(Notification.create(project.getWorkspaceId(), recipient, actorId,
                        entityType, entityId, action, title, body, retention));
            }
            if (preference.email()) {
                byEmail.add(recipient);
            }
        }
        for (User user : userRepository.findAllById(byEmail)) {
            emailService.sendNotification(user.getEmail(), title, body == null ? "" : body);
        }
    }

    // Who follows an issue without having to ask: whoever reported it and whoever is on it.
    private Set<UUID> involvedIn(Issue issue) {
        Set<UUID> people = new LinkedHashSet<>();
        people.add(issue.getReporterId());
        assigneeRepository.findByIssueId(issue.getId()).stream()
                .map(IssueAssignee::getUserId)
                .forEach(people::add);
        return people;
    }

    private Project projectOf(UUID projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));
    }

    private String nameOf(UUID userId) {
        return userRepository.findById(userId).map(User::getFullName).orElse("Someone");
    }

    private static String identifier(Project project, Issue issue) {
        return project.getKey() + "-" + issue.getNumber();
    }

    private static String excerpt(String content) {
        String text = MentionParser.plainText(content);
        if (text == null || text.length() <= EXCERPT_LENGTH) {
            return text;
        }
        return text.substring(0, EXCERPT_LENGTH - 1) + "…";
    }

    private static String phrase(IssueLinkType type) {
        return switch (type) {
            case RELATED_TO -> "relates to";
            case DUPLICATE_OF -> "duplicates";
            case BLOCKS -> "blocks";
            case DEPENDS_ON -> "depends on";
        };
    }
}
