package io.github.leobej.domain.model.issue;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class IssueStatus {
    private final UUID id;
    private final UUID projectId;
    private String name;
    private IssueStatusCategory category;
    private String color;
    private int position;           // display order on the board
    private boolean isDefault;      // where new issues land
    private final Instant createdAt;
    private Instant deletedAt;
    private UUID deletedBy;

    public static IssueStatus create(UUID projectId, String name, IssueStatusCategory category,
                                     String color, int position, boolean isDefault) {
        return new IssueStatus(UUID.randomUUID(), projectId, name, category, color, position,
                isDefault, Instant.now(), null, null);
    }

    public static IssueStatus from(UUID projectId, DefaultIssueStatus template) {
        return create(projectId, template.name(), template.category(), template.color(),
                template.position(), template.isDefault());
    }

    public void rename(String newName) {
        this.name = newName;
    }

    // The category can be changed, but it is the meaning rather than the label: moving a status
    // out of DONE changes what counts as finished in every chart and sprint close.
    public void recategorise(IssueStatusCategory newCategory) {
        this.category = newCategory;
    }

    public void recolour(String newColor) {
        this.color = newColor;
    }

    public void moveTo(int newPosition) {
        this.position = newPosition;
    }

    public void makeDefault() {
        this.isDefault = true;
    }

    public void clearDefault() {
        this.isDefault = false;
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
