package io.github.leobej.domain.model.issue;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class IssueType {
    private final UUID id;
    private final UUID projectId;
    private String name;
    private String icon;
    private String color;
    private boolean isDefault;      // the type new issues get when none is chosen
    private int sortOrder;
    private final Instant createdAt;
    private Instant deletedAt;
    private UUID deletedBy;

    public static IssueType create(UUID projectId, String name, String icon, String color,
                                   boolean isDefault, int sortOrder) {
        return new IssueType(UUID.randomUUID(), projectId, name, icon, color, isDefault, sortOrder,
                Instant.now(), null, null);
    }

    public static IssueType from(UUID projectId, DefaultIssueType template) {
        return create(projectId, template.name(), template.icon(), template.color(),
                template.isDefault(), template.sortOrder());
    }

    public void rename(String newName) {
        this.name = newName;
    }

    public void restyle(String newIcon, String newColor) {
        if (newIcon != null) {
            this.icon = newIcon;
        }
        if (newColor != null) {
            this.color = newColor;
        }
    }

    public void makeDefault() {
        this.isDefault = true;
    }

    public void clearDefault() {
        this.isDefault = false;
    }

    public void reorder(int newSortOrder) {
        this.sortOrder = newSortOrder;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public void delete(UUID actorId) {
        if (deletedAt == null) {
            this.deletedAt = Instant.now();
            this.deletedBy = actorId;
        }
    }
}
