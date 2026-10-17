package io.github.leobej.domain.model.issue;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class Issue {
    private final UUID id;
    private final UUID projectId;
    private UUID sprintId;              // null = backlog
    private final UUID parentId;        // null = not a subtask; a subtask never moves parents
    private UUID typeId;
    private UUID statusId;
    private String title;
    private String description;
    private IssuePriority priority;
    private boolean blocked;
    private String blockedReason;
    private final UUID reporterId;
    private UUID closedBy;
    private Instant closedAt;
    private LocalDate dueDate;
    private final int number;           // per project; the identifier is key + "-" + number (§24)
    private Double backlogPosition;
    private Double sprintPosition;
    private long version;               // optimistic locking (§36)
    private final Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;

    public static Issue create(UUID projectId,
                               UUID typeId,
                               UUID statusId,
                               String title,
                               String description,
                               IssuePriority priority,
                               UUID reporterId,
                               UUID parentId,
                               LocalDate dueDate,
                               int number,
                               double backlogPosition) {
        Instant now = Instant.now();
        return new Issue(
                UUID.randomUUID(),   // domain owns identity
                projectId,
                null,                // created in the backlog; move-to-sprint puts it on a board
                parentId,
                typeId,
                statusId,
                title,
                description,
                priority == null ? IssuePriority.MEDIUM : priority,
                false,
                null,
                reporterId,
                null,
                null,
                dueDate,
                number,
                backlogPosition,
                null,
                0L,
                now,
                now,
                null
        );
    }

    public boolean isSubtask() {
        return parentId != null;
    }

    public boolean isInBacklog() {
        return sprintId == null;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public void rename(String newTitle) {
        this.title = newTitle;
        this.touch();
    }

    public void describe(String newDescription) {
        this.description = newDescription;
        this.touch();
    }

    public void changeType(UUID newTypeId) {
        this.typeId = newTypeId;
        this.touch();
    }

    public void changePriority(IssuePriority newPriority) {
        this.priority = newPriority;
        this.touch();
    }

    public void changeDueDate(LocalDate newDueDate) {
        this.dueDate = newDueDate;
        this.touch();
    }

    // Closing is a side effect of reaching a DONE status rather than its own action, so the
    // caller passes what the new status means.
    public void changeStatus(UUID newStatusId, boolean isDoneCategory, UUID actorId) {
        this.statusId = newStatusId;
        if (isDoneCategory && closedAt == null) {
            this.closedBy = actorId;
            this.closedAt = Instant.now();
        } else if (!isDoneCategory) {
            this.closedBy = null;
            this.closedAt = null;
        }
        this.touch();
    }

    // The flag has two sources: a manual block, which always has a reason, and BLOCKS links (§4).
    // It is up while either is, and each side clears only its own half.
    public void block(String reason) {
        this.blocked = true;
        this.blockedReason = reason;
        this.touch();
    }

    // Lifts the manual block only. A BLOCKS link still pointing here keeps the flag up until the
    // link goes, so the flag never reads "clear" while something is in the way.
    public void unblock(boolean blockedByLinks) {
        this.blocked = blockedByLinks;
        this.blockedReason = null;
        this.touch();
    }

    // The link-driven half. Returns whether the flag moved, so callers only write when it did.
    public boolean applyLinkBlocking(boolean blockedByLinks) {
        boolean nowBlocked = blockedReason != null || blockedByLinks;
        if (nowBlocked == blocked) {
            return false;
        }
        this.blocked = nowBlocked;
        this.touch();
        return true;
    }

    public void moveToSprint(UUID targetSprintId, double position) {
        this.sprintId = targetSprintId;
        this.sprintPosition = position;
        this.touch();
    }

    public void moveToBacklog(double position) {
        this.sprintId = null;
        this.sprintPosition = null;
        this.backlogPosition = position;
        this.touch();
    }

    // Which column the position applies to depends on where the issue currently lives.
    public void reposition(double position) {
        if (isInBacklog()) {
            this.backlogPosition = position;
        } else {
            this.sprintPosition = position;
        }
        this.touch();
    }

    public void delete() {
        if (deletedAt == null) {
            this.deletedAt = Instant.now();
            this.touch();
        }
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }
}
