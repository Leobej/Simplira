package io.github.leobej.infrastructure.messaging.config;

import io.github.leobej.domain.repository.IssueRepository;
import io.github.leobej.domain.repository.UserWorkspaceRepository;
import io.github.leobej.domain.service.ProjectAuthorization;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

// Who may listen where (§8): the same rule as reading the thing over REST (§6). Anything not
// listed is refused, the way a new REST endpoint is private until someone opts it in - and a raw
// /queue/... name is refused because it could be used to listen in on another session's user queue.
//
// Checked at SUBSCRIBE only. Someone who loses access keeps an open subscription until they
// reconnect - which is why the events carry ids and a type, never content (LiveUpdate).
@Component
public class SubscriptionAuthorizer {

    static final String NOTIFICATIONS = "/user/queue/notifications";
    private static final String PROJECT = "/topic/project/";
    private static final String ISSUE = "/topic/issue/";
    private static final String WORKSPACE = "/topic/workspace/";

    private final ProjectAuthorization projectAuthorization;
    private final IssueRepository issueRepository;
    private final UserWorkspaceRepository userWorkspaceRepository;

    public SubscriptionAuthorizer(ProjectAuthorization projectAuthorization,
                                  IssueRepository issueRepository,
                                  UserWorkspaceRepository userWorkspaceRepository) {
        this.projectAuthorization = projectAuthorization;
        this.issueRepository = issueRepository;
        this.userWorkspaceRepository = userWorkspaceRepository;
    }

    public boolean maySubscribe(UUID userId, String destination) {
        if (destination == null) {
            return false;
        }
        if (destination.equals(NOTIFICATIONS)) {
            return true;    // a user destination only ever reaches its own user
        }
        if (destination.startsWith(PROJECT)) {
            return idAfter(PROJECT, destination)
                    .map(projectId -> projectAuthorization.hasAccess(projectId, userId))
                    .orElse(false);
        }
        if (destination.startsWith(ISSUE)) {
            return idAfter(ISSUE, destination)
                    .flatMap(issueRepository::findById)
                    .map(issue -> projectAuthorization.hasAccess(issue.getProjectId(), userId))
                    .orElse(false);
        }
        if (destination.startsWith(WORKSPACE)) {
            return idAfter(WORKSPACE, destination)
                    .map(workspaceId -> userWorkspaceRepository.find(workspaceId, userId).isPresent())
                    .orElse(false);
        }
        return false;
    }

    // Exactly one id after the prefix: /topic/project/{id}/anything-else is not a topic we publish.
    private static Optional<UUID> idAfter(String prefix, String destination) {
        try {
            return Optional.of(UUID.fromString(destination.substring(prefix.length())));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
