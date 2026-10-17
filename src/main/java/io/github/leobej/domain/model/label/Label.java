package io.github.leobej.domain.model.label;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class Label {
    private final UUID id;
    private final UUID projectId;   // labels are scoped per project, like issue types
    private String name;
    private String color;
    private final UUID createdBy;
    private final Instant createdAt;
    private Instant deletedAt;
    private UUID deletedBy;

    public static Label create(UUID projectId, String name, String color, UUID createdBy) {
        return new Label(UUID.randomUUID(), projectId, name, color, createdBy, Instant.now(), null, null);
    }

    public void rename(String newName) {
        this.name = newName;
    }

    public void recolour(String newColor) {
        this.color = newColor;
    }

    public void delete(UUID actorId) {
        if (deletedAt == null) {
            this.deletedAt = Instant.now();
            this.deletedBy = actorId;
        }
    }
}
