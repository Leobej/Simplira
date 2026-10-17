package io.github.leobej.infrastructure.persistence.postgres.adapter;

import io.github.leobej.domain.model.notification.Notification;
import io.github.leobej.domain.model.notification.NotificationPage;
import io.github.leobej.domain.repository.NotificationRepository;
import io.github.leobej.infrastructure.persistence.postgres.entity.NotificationEntity;
import io.github.leobej.infrastructure.persistence.postgres.jpa.JpaNotificationRepository;
import io.github.leobej.infrastructure.persistence.postgres.mapper.NotificationEntityMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class NotificationRepositoryAdapter implements NotificationRepository {

    private final JpaNotificationRepository jpaRepository;

    public NotificationRepositoryAdapter(JpaNotificationRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Notification save(Notification notification) {
        boolean isNew = !jpaRepository.existsById(notification.getId());
        NotificationEntity entity = NotificationEntityMapper.toEntity(notification, isNew);
        return NotificationEntityMapper.toDomain(jpaRepository.save(entity));
    }

    @Override
    public Optional<Notification> findById(UUID id) {
        return jpaRepository.findById(id).map(NotificationEntityMapper::toDomain);
    }

    @Override
    public NotificationPage findForRecipient(UUID recipientId, UUID workspaceId, boolean unreadOnly,
                                             int page, int pageSize) {
        Page<NotificationEntity> result = jpaRepository.findForRecipient(recipientId, workspaceId, unreadOnly,
                PageRequest.of(page, pageSize));
        return new NotificationPage(
                result.getContent().stream().map(NotificationEntityMapper::toDomain).toList(),
                result.getTotalElements(), page, pageSize);
    }

    @Override
    @Transactional
    public int markAllRead(UUID recipientId, UUID workspaceId, Instant readAt) {
        return jpaRepository.markAllRead(recipientId, workspaceId, readAt);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        jpaRepository.deleteById(id);
    }

    @Override
    @Transactional
    public int deleteExpired(Instant now) {
        return jpaRepository.deleteExpired(now);
    }
}
