# Simplira API — Bruno collection

Open Bruno → **Open Collection** → pick this `bruno` folder. Then select the **Local**
environment in the top-right dropdown, or `{{baseUrl}}` will not resolve.

## Running the API first

```
docker compose -f ../docker/docker-compose.yml up -d postgres s3mock
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```

The `dev` profile matters: `application.properties` has no fallback for the JWT secret,
so without it (or a `JWT_SECRET` environment variable) the app refuses to start. It also points
attachments at the `s3mock` container, which is why that starts too.

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
| Auth 8 | Register teammate | 201 the first time, 409 after — both pass |
| Auth 9 | Login teammate | 200 — captures `teammateToken` and `memberUserId` |
| Auth 10 | Verify email | 200 — needs `verificationToken` pasted from the log; unlocks creating workspaces |
| Auth 11 | Resend verification | 200 — always, whoever the address belongs to |
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
| Invitations 3 | Accept invitation | 200 — as the teammate; needs `invitationToken` pasted from the log |
| Invitations 4 | Cancel invitation | 204 — the token stops working immediately |
| Projects 1 | Create project | 201 — captures `projectId`; key derived from the name |
| Projects 2 | List projects | 200 — filtered by what you can see |
| Projects 3 | Get project | 200 — a private project answers 404 to outsiders |
| Projects 4 | Update project | 200 — workspace ADMIN only |
| Projects 5 | List project members | 200 — creator is TEAM_LEADER |
| Projects 6 | Add project member | 201 — the teammate, once they have joined the workspace |
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
| Assignees 1 | Assign | 200 — idempotent; assigns the teammate, who is notified |
| Assignees 2 | List assignees | 200 — names and avatars, in assignment order |
| Assignees 3 | Unassign | 204 — idempotent |
| Assignees 4 | Bulk assign | 200 — adds, never replaces; all or nothing |
| Comments 1 | Add comment | 201 — `@[Name](id)` mentions; captures `commentId` |
| Comments 2 | List comments | 200 — authors resolved, oldest first |
| Comments 3 | Edit comment | 200 — author only; mentions re-read |
| Comments 4 | Delete comment | 204 — author or workspace ADMIN |
| Labels 1 | Create label | 201 — leads only; captures `labelId` |
| Labels 2 | List labels | 200 — by name, deleted ones hidden |
| Labels 3 | Update label | 200 — name, colour or both |
| Labels 4 | Attach to issue | 200 — idempotent; the issue's full label list |
| Labels 5 | List issue labels | 200 — names and colours |
| Labels 6 | Filter issues by label | 200 — repeat `labelId` to require all of them |
| Labels 7 | Detach from issue | 204 — idempotent |
| Labels 8 | Delete label | 204 — off every issue; frees the name, so the folder re-runs |
| Links 1 | Create blocker issue | 201 — captures `blockerIssueId`, fresh each run |
| Links 2 | Add blocking link | 201 — blocker BLOCKS issue; captures `linkId`, issue becomes blocked |
| Links 3 | List links | 200 — both directions, with the other issue's identifier and title |
| Links 4 | Remove link | 204 — the last BLOCKS link going clears the flag |
| Notifications 1 | Teammate mentions you | 201 — runs as the teammate; puts a mention in your inbox |
| Notifications 2 | List notifications | 200 — newest first; captures `notificationId` |
| Notifications 3 | Unread count | 200 — `meta.total` is the bell's number |
| Notifications 4 | Mark as read | 200 — idempotent; keeps the first `readAt` |
| Notifications 5 | Mark all as read | 204 |
| Notifications 6 | Dismiss notification | 204 — deleted for good |
| Notifications 7 | Get preferences | 200 — all 10 actions, defaults filled in |
| Notifications 8 | Update preferences | 200 — only what you list changes |
| Audit Log 1 | Issue history | 200 — newest first; only the fields that changed |
| Audit Log 2 | Project audit log | 200 — TEAM_LEADER or ADMIN |
| Audit Log 3 | Workspace audit log | 200 — ADMIN only; `actorId` narrows to one person |
| Attachments 1 | Upload attachment | 201 — `fixtures/sample.png`; type read from the bytes; captures `attachmentId` |
| Attachments 2 | List attachments | 200 — each with a signed link valid for an hour |
| Attachments 3 | Delete attachment | 204 — uploader or ADMIN; the bytes go too |

You are `userEmail` / `userPassword` from the **Local** environment (`john@example.com` /
`supersecret`), and every request runs as you unless it says otherwise. Change it there and the
whole collection follows — never in a request's Vars tab, because request-scoped variables
outrank environment ones and only affect the single request you edited.

A second account, the **teammate** at `inviteEmail` / `teammatePassword`, exists so there is
someone to invite, assign and be notified by. Auth 8 and 9 create it and capture `teammateToken`
and `memberUserId`. The few requests that act as the teammate (Invitations 3, Notifications 1)
say so with their own bearer auth; nothing else changes accounts.

On a fresh database: Register, Login, Register teammate and Login teammate, then Verify email,
then the rest in order. Two tokens have to be pasted from the application log, because each
exists only in an email, by design: `verificationToken` (the `[stub email] verify email` line,
needed before you can create a workspace) and `invitationToken` (for Accept invitation). An
account created before verification existed has no link yet — run Resend verification first.

Each request carries an `assert` block, so **Run Folder** on `Auth` or the whole collection
works as a smoke test rather than something you have to eyeball.

## Notes

- Live updates are STOMP over WebSocket at `ws://localhost:8080/ws`, which Bruno does not speak.
  Any STOMP client works: send `Authorization: Bearer <accessToken>` on CONNECT, then SUBSCRIBE to
  `/topic/project/{projectId}` and run requests here to watch events arrive (`ISSUE_CREATED`,
  `ISSUE_REPOSITIONED`, `COMMENT_CREATED`...). `/user/queue/notifications` carries your own
  notifications.
- Nobody is notified about their own actions — that is why Notifications 1 runs as the
  teammate. Assigning `memberUserId` (Assignees 1) works the other way round: log in as the
  teammate to see it arrive.
- Notification emails are queued and sent by a poller every 10 seconds, so the
  `[stub email] notification` log line appears shortly after the request, not during it.
- Tokens last 15 minutes. When `Get me` starts returning 401, re-run Login (or Refresh).
- Emails are normalized, so `DEV@SIMPLIRA.COM` and `dev@simplira.com` are the same account.
