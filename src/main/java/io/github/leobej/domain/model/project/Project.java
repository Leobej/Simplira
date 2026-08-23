package io.github.leobej.domain.model.project;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class Project {
    private final UUID id;
    private final UUID workspaceId;
    private String name;
    private String key;              // half of every issue identifier: KEY-42 (§24)
    private String description;
    private ProjectVisibility visibility;
    private final UUID createdBy;
    private final Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;
    private UUID deletedBy;

    public static Project create(UUID workspaceId,
                                 String name,
                                 String key,
                                 String description,
                                 ProjectVisibility visibility,
                                 UUID createdBy) {
        Instant now = Instant.now();
        return new Project(
                UUID.randomUUID(),   // domain owns identity
                workspaceId,
                name,
                key,
                description,
                visibility,
                createdBy,
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

    public void changeDescription(String newDescription) {
        this.description = newDescription;
        this.touch();
    }

    public void changeVisibility(ProjectVisibility newVisibility) {
        this.visibility = newVisibility;
        this.touch();
    }

    // Changing the key rewrites how every existing issue is referred to - old links stop
    // resolving - so callers should treat it as a rename, not a correction.
    public void changeKey(String newKey) {
        this.key = newKey;
        this.touch();
    }

    public boolean isPublic() {
        return visibility == ProjectVisibility.PUBLIC;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    // Soft delete (§32): hard-deleting a project would destroy its issues, sprints and history.
    public void delete(UUID actorId) {
        if (deletedAt == null) {
            this.deletedAt = Instant.now();
            this.deletedBy = actorId;
            this.touch();
        }
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }
}
