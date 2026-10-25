package io.github.leobej.infrastructure.messaging.publisher;

import io.github.leobej.domain.model.live.LiveUpdate;
import io.github.leobej.domain.port.LiveUpdatePublisher;
import io.github.leobej.infrastructure.messaging.event.LiveUpdateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

// §8's topics. An issue-level change goes to its project's board and to its own page; a project's
// sprints and members go to the board; workspace-level changes (renames, members) to the workspace.
@Component
public class StompLiveUpdatePublisher implements LiveUpdatePublisher {

    private static final Logger log = LoggerFactory.getLogger(StompLiveUpdatePublisher.class);

    private final SimpMessagingTemplate messagingTemplate;

    public StompLiveUpdatePublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    // Logged, never thrown: the change is saved and the request that made it has succeeded.
    @Override
    public void publish(LiveUpdate update) {
        LiveUpdateEvent event = LiveUpdateEvent.of(update);
        try {
            if (update.projectId() != null) {
                messagingTemplate.convertAndSend("/topic/project/" + update.projectId(), event);
            } else if (update.workspaceId() != null) {
                messagingTemplate.convertAndSend("/topic/workspace/" + update.workspaceId(), event);
            }
            if (update.issueId() != null) {
                messagingTemplate.convertAndSend("/topic/issue/" + update.issueId(), event);
            }
        } catch (RuntimeException ex) {
            log.warn("Live update push failed: type={}", update.type(), ex);
        }
    }
}
