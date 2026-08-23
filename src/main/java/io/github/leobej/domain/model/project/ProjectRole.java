package io.github.leobej.domain.model.project;

// ADMIN is absent on purpose: it is workspace-wide, so a project cannot grant or revoke it.
public enum ProjectRole {
    TEAM_LEADER,
    DEVELOPER;

    public boolean isAtLeast(ProjectRole required) {
        return ordinal() <= required.ordinal();   // declared most privileged first
    }
}
