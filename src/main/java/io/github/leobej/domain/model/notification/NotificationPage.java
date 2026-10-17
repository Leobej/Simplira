package io.github.leobej.domain.model.notification;

import java.util.List;

public record NotificationPage(List<Notification> items, long total, int page, int pageSize) {
}
