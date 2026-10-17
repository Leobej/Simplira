package io.github.leobej.domain.model.comment;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

// The comment owns its mentions: they are derived from its text and change only when it does.
@Getter
@AllArgsConstructor
public class Comment {
    private final UUID id;
    private final UUID issueId;
    private final UUID authorId;
    private String content;
    private Set<UUID> mentionedUserIds;
    private final Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;

    public static Comment write(UUID issueId, UUID authorId, String content, Set<UUID> mentionedUserIds) {
        Instant now = Instant.now();
        return new Comment(UUID.randomUUID(), issueId, authorId, content,
                new LinkedHashSet<>(mentionedUserIds), now, now, null);
    }

    public void edit(String newContent, Set<UUID> newMentions) {
        this.content = newContent;
        this.mentionedUserIds = new LinkedHashSet<>(newMentions);
        this.updatedAt = Instant.now();
    }

    public boolean isEdited() {
        return updatedAt.isAfter(createdAt);
    }

    public boolean isWrittenBy(UUID userId) {
        return authorId.equals(userId);
    }

    public void delete() {
        if (deletedAt == null) {
            this.deletedAt = Instant.now();
        }
    }
}
