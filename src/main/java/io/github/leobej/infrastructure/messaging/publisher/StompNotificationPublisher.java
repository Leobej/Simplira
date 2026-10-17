package io.github.leobej.infrastructure.messaging.publisher;

import io.github.leobej.domain.model.notification.Notification;
import io.github.leobej.domain.port.NotificationPublisher;
import io.github.leobej.infrastructure.messaging.event.NotificationCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class StompNotificationPublisher implements NotificationPublisher {

    static final String DESTINATION = "/queue/notifications";

    private static final Logger log = LoggerFactory.getLogger(StompNotificationPublisher.class);

    private final SimpMessagingTemplate messagingTemplate;

    public StompNotificationPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    // Every session the recipient has open gets it; with none open it goes nowhere, which is fine.
    // A failure is logged, never thrown: the notification is saved, and the request that caused it
    // has already succeeded.
    @Override
    public void publish(Notification notification) {
        try {
            messagingTemplate.convertAndSendToUser(notification.getRecipientId().toString(), DESTINATION,
                    NotificationCreatedEvent.of(notification));
        } catch (RuntimeException ex) {
            log.warn("Notification push failed: notificationId={}", notification.getId(), ex);
        }
    }
}
