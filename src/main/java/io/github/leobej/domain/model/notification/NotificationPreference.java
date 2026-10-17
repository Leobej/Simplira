package io.github.leobej.domain.model.notification;

import java.util.UUID;

// Per user, per workspace, per action (§4). Someone can mute status changes in a busy workspace
// and keep them in a quiet one.
public record NotificationPreference(UUID userId,
                                     UUID workspaceId,
                                     NotificationAction action,
                                     boolean inApp,
                                     boolean email) {

    // What applies when nothing is stored: both channels on.
    public static NotificationPreference defaults(UUID userId, UUID workspaceId, NotificationAction action) {
        return new NotificationPreference(userId, workspaceId, action, true, true);
    }

    // A null leaves that channel as it was, so a client can flip one switch without restating both.
    public NotificationPreference with(Boolean inApp, Boolean email) {
        return new NotificationPreference(userId, workspaceId, action,
                inApp == null ? this.inApp : inApp,
                email == null ? this.email : email);
    }
}
