package io.github.leobej.infrastructure.scheduling;

import io.github.leobej.domain.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// §28. Only the notification purge so far; the refresh-token purge and invitation expiry from the
// same section belong here when they are built.
@Component
public class ScheduledJobs {

    private static final Logger log = LoggerFactory.getLogger(ScheduledJobs.class);

    private final NotificationService notificationService;

    public ScheduledJobs(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    // Daily at 02:00, server time.
    @Scheduled(cron = "0 0 2 * * *")
    public void purgeExpiredNotifications() {
        int deleted = notificationService.purgeExpired();
        log.info("Purged expired notifications: count={}", deleted);
    }
}
