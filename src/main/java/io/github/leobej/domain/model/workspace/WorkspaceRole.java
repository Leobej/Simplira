package io.github.leobej.domain.model.workspace;

// Roles are per workspace, never global - the same user can be ADMIN in one and DEVELOPER in
// another. They are also never carried in the JWT (§6); they are read per request.
public enum WorkspaceRole {
    ADMIN,
    TEAM_LEADER,
    DEVELOPER;

    public boolean isAtLeast(WorkspaceRole required) {
        return ordinal() <= required.ordinal();   // declared most privileged first
    }
}
