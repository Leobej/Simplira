package io.github.leobej.domain.model.workspace;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class Workspace {
    private final UUID id;
    private String name;
    private String slug;
    private final UUID ownerId;
    private final Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;
    private UUID deletedBy;

    public static Workspace create(String name, String slug, UUID ownerId) {
        Instant now = Instant.now();
        return new Workspace(
                UUID.randomUUID(),   // domain owns identity
                name,
                slug,
                ownerId,
                now,
                now,
                null,
                null
        );
    }

    public void rename(String newName) {
        this.name = newName;
        this.touch();
    }

    public void changeSlug(String newSlug) {
        this.slug = newSlug;
        this.touch();
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    // Soft delete: the row stays for the audit trail, and the partial index frees the slug.
    public void delete(UUID actorId) {
        if (deletedAt == null) {
            this.deletedAt = Instant.now();
            this.deletedBy = actorId;
            this.touch();
        }
    }

    public boolean isOwnedBy(UUID userId) {
        return ownerId.equals(userId);
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }
}
