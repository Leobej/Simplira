# Simplira API — Bruno collection

Open Bruno → **Open Collection** → pick this `bruno` folder. Then select the **Local**
environment in the top-right dropdown, or `{{baseUrl}}` will not resolve.

## Running the API first

```
docker compose -f ../docker/docker-compose.yml up -d postgres
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```

The `dev` profile matters: `application.properties` has no fallback for the JWT secret,
so without it (or a `JWT_SECRET` environment variable) the app refuses to start.

## Order

Requests are numbered, and the sequence is meant to be run top to bottom:

| # | Request | Expects |
|---|---|---|
| Auth 1 | Register | 201 the first time, 409 on every run after — both pass |
| Auth 2 | Login | 200 — captures `data.accessToken` into the `accessToken` variable |
| Auth 3 | Register - duplicate email | 409 `EMAIL_ALREADY_EXISTS` |
| Auth 4 | Register - invalid payload | 400 `VALIDATION_FAILED` with per-field details |
| Auth 5 | Login - wrong password | 401 `INVALID_CREDENTIALS` |
| Auth 6 | Refresh | 200 — rotates the cookie, captures a fresh `accessToken` |
| Auth 7 | Logout | 204 — revokes the session; Refresh afterwards gives 401 |
| Users 1 | Get me | 200 — inherits the collection's bearer auth |
| Users 2 | Get me - no token | 401 `UNAUTHENTICATED` |
| Workspaces 1 | Create workspace | 201 — captures `workspaceId`; slug derived from the name |
| Workspaces 2 | List my workspaces | 200 — only workspaces you belong to |
| Workspaces 3 | Get workspace | 200 — a non-member gets 404, not 403 |
| Workspaces 4 | Update workspace | 200 — ADMIN only |
| Workspaces 5 | List members | 200 — grows once invitations are accepted |
| Workspaces 6 | Search members | 200 — empty under two characters |
| Workspaces 7 | Delete workspace | 204 — soft delete; frees the slug for reuse |
| Invitations 1 | Invite member | 201 — captures `invitationId`; token goes to the log, not the response |
| Invitations 2 | List invitations | 200 — never includes the token |
| Invitations 3 | Accept invitation | 200 — needs `invitationToken` pasted from the log |
| Invitations 4 | Cancel invitation | 204 — the token stops working immediately |
| Projects 1 | Create project | 201 — captures `projectId`; key derived from the name |
| Projects 2 | List projects | 200 — filtered by what you can see |
| Projects 3 | Get project | 200 — a private project answers 404 to outsiders |
| Projects 4 | Update project | 200 — workspace ADMIN only |
| Projects 5 | List project members | 200 — creator is TEAM_LEADER |
| Projects 6 | Add project member | 201 — needs `memberUserId`, already in the workspace |
| Projects 7 | Delete project | 204 — soft delete; frees the key |
| Sprints 1 | Create sprint | 201 — captures `sprintId`; starts PLANNED |
| Sprints 2 | List sprints | 200 — ordered by start date |
| Sprints 3 | Get sprint | 200 |
| Sprints 4 | Update sprint | 200 — dates stay in order |
| Sprints 5 | Start sprint | 200 — one ACTIVE sprint per project |
| Sprints 6 | Close sprint | 200 — issue movement lands with the issues slice |
| Sprints 7 | Delete sprint | 204 — close it first if ACTIVE |
| Board 1 | List issue types | 200 — Task, Bug, Story, seeded with the project |
| Board 2 | Create issue type | 201 — names unique per project |
| Board 3 | List statuses | 200 — To Do, In Progress, Done with categories |
| Board 4 | Create status | 201 — captures `statusId`; added at the end |
| Board 5 | Rename status | 200 — the label changes, the category is the meaning |
| Board 6 | Reorder statuses | 409 until you paste the full id list in |
| Board 7 | Delete status | 204 — not the last in a category, not the default |
| Issues 1 | Create issue | 201 — captures `issueId`; project defaults fill type and status |
| Issues 2 | List backlog | 200 — filters and paging, `meta` carries the totals |
| Issues 3 | Get issue by identifier | 200 — SB-1 rather than a UUID |
| Issues 4 | Update issue | 200 first time, 409 the second — version conflict |
| Issues 5 | Move to sprint | 200 — needs `sprintId` from the Sprints folder |
| Issues 6 | Reposition issue | 200 — drop between two cards, no renumbering |
| Issues 7 | Block issue | 200 — DELETE the same path to clear |
| Issues 8 | Create subtask | 201 — one level deep only |

Every request uses one fixed account, `userEmail` / `userPassword` from the **Local**
environment (`john@example.com` / `supersecret`). Change it there and the whole collection
follows — never in a request's Vars tab, because request-scoped variables outrank environment
ones and only affect the single request you edited.

The only ordering requirement is Register before the rest on a fresh database; `accessToken`
is set by Login and persists between runs.

Each request carries an `assert` block, so **Run Folder** on `Auth` or the whole collection
works as a smoke test rather than something you have to eyeball.

## Notes

- Tokens last 15 minutes. When `Get me` starts returning 401, re-run Login.
- Emails are normalized, so `DEV@SIMPLIRA.COM` and `dev@simplira.com` are the same account.
- `/actuator/health` currently returns 401 — every route except register and login requires
  authentication, actuator included. Worth changing before anything tries to health-check the app.
- There is no refresh or logout endpoint yet, so the collection stops at the access token.
