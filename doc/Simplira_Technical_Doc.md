# Simplira — Complete Technical Documentation

> A lightweight issue tracker for small software teams who want Jira's core
> functionality without the complexity, configuration overhead, or price tag.

---

## Table of Contents

1. [Product Overview](#1-product-overview)
2. [Infrastructure Stack](#2-infrastructure-stack)
3. [Architectural Principles](#3-architectural-principles)
4. [Database Schema](#4-database-schema)
5. [Entity Relationship Summary](#5-entity-relationship-summary)
6. [Authentication & Security](#6-authentication--security)
7. [API Layer](#7-api-layer)
8. [WebSocket & Real-Time](#8-websocket--real-time)
9. [Spring Boot Project Structure](#9-spring-boot-project-structure)
10. [Key Architectural Patterns](#10-key-architectural-patterns)
    11–36. Frontend, Android, Testing, CI/CD, and later design decisions — see section headers.
37. [Implementation Patterns — Persistence & Auth](#37-implementation-patterns--persistence--auth)

---

## 1. Product Overview

### Description

Simplira is a lightweight issue tracker for small software teams who want Jira's
core functionality without the complexity, configuration overhead, or price tag.

### Core Hierarchy

```
Workspace → Projects → Sprints → Issues → Subtasks
```

### Issue Properties

| Property | Type | Notes |
|---|---|---|
| Title | VARCHAR | Required |
| Description | TEXT | Basic formatting, nullable |
| Type | FK → IssueType | Bug, Task, Story etc. |
| Status | ENUM | TODO, IN_PROGRESS, DONE |
| Priority | ENUM | LOW, MEDIUM, HIGH, CRITICAL |
| Blocked flag | BOOLEAN | Auto-managed from IssueLink |
| Blocked reason | TEXT | Optional reason text |
| Assignees | Multiple | Many users per issue |
| Reporter | FK → User | Who created it |
| Closed by | FK → User | Who closed it |
| Due date | DATE | Optional |
| Backlog position | FLOAT | Drag-and-drop ordering |
| Sprint position | FLOAT | Drag-and-drop ordering |
| Labels | Many-to-many | Scoped per project |
| Image attachments | Files | Images only, stored in object storage |
| Linked issues | IssueLink | RELATED_TO, DUPLICATE_OF, BLOCKS, DEPENDS_ON |
| Comments | Many | Supports @mentions |

### Roles & Permissions

| Action | Developer | Team Leader | Admin |
|---|---|---|---|
| Create/edit issues | ✅ | ✅ | ✅ |
| Close own issues | ✅ | ✅ | ✅ |
| Close others' issues | ⚠️ confirm | ⚠️ confirm | ⚠️ confirm |
| Bulk assign/status | ❌ | ✅ | ✅ |
| Start/close sprint | ❌ | ✅ | ✅ |
| Manage users/settings | ❌ | ❌ | ✅ |

### Sprints

- Manual start/end date — no fixed duration enforced
- One active sprint per project at a time (enforced at service layer)
- Backlog for issues not assigned to any sprint (sprint_id = null)
- Sprint close flow: incomplete issues move to backlog or next sprint (team leader decides)

### Charts

- Burndown chart — issues completed vs remaining over sprint duration
- Velocity chart — work completed per sprint historically
- Cumulative flow — issue status distribution over time

### Workspace Model

- One user can belong to many workspaces
- One workspace can have many users
- A user can have different roles in different workspaces
- Workspace identified by a unique slug (e.g. `simplira.com/acme-tech`)
- Seamless workspace switching (no re-login required)

### Project Visibility

- **PUBLIC** — all workspace members can see it
- **PRIVATE** — only explicitly added members can see it

Permission resolution logic:
1. Is user Admin in the workspace? → Allow
2. Is project PUBLIC? → Allow
3. Is user in ProjectMember for this project? → Allow
4. Otherwise → Deny

### Notifications

- Stored for 90 days then auto-purged via background job
- Pre-rendered title and body at creation time
- Full permanent history available via AuditLog
- Configurable per user per workspace per action type
- Real-time delivery via WebSocket (RabbitMQ-backed)

---

## 2. Infrastructure Stack

| Component | Technology |
|---|---|
| Backend | Java Spring Boot |
| Frontend | React |
| Database | PostgreSQL 18 (via Spring Data JPA) |
| Message Broker | RabbitMQ |
| WebSocket Protocol | STOMP over WebSocket (via RabbitMQ relay) |
| Object Storage | AWS S3 or Cloudflare R2 (image attachments) |
| DB Migrations | Flyway |
| Local Dev | Docker Compose |
| DB Admin UI | pgAdmin 4 (browser-based, http://localhost:5050) |
| Authentication | JWT (Access + Refresh token strategy) |

### Docker Compose (Local Development)

> **Note:** PostgreSQL 18+ changed the data directory structure. The volume must be mounted at
> `/var/lib/postgresql` (not `/var/lib/postgresql/data` as in older versions) to avoid
> initialization errors on first run.

> **Note:** If you have a local PostgreSQL installation running on port 5432, stop it before
> starting Docker Compose to avoid port conflicts.
> Windows: `net stop postgresql-x64-18`

```yaml
services:
  postgres:
    image: postgres:18
    ports:
      - "5432:5432"
    environment:
      POSTGRES_DB: simplira
      POSTGRES_USER: simplira
      POSTGRES_PASSWORD: simplira
    volumes:
      - postgres_data:/var/lib/postgresql  # note: /var/lib/postgresql, not /data — required for Postgres 18+

  rabbitmq:
    image: rabbitmq:3-management
    ports:
      - "5672:5672"
      - "15672:15672"  # Management UI
      - "61613:61613"  # STOMP port
    environment:
      RABBITMQ_DEFAULT_USER: simplira
      RABBITMQ_DEFAULT_PASS: simplira
    command: >
      bash -c "rabbitmq-plugins enable rabbitmq_stomp &&
               rabbitmq-server"
    volumes:
      - rabbitmq_data:/var/lib/rabbitmq

  pgadmin:
    image: dpage/pgadmin4
    ports:
      - "5050:80"
    environment:
      PGADMIN_DEFAULT_EMAIL: admin@simplira.com
      PGADMIN_DEFAULT_PASSWORD: admin

volumes:
  postgres_data:
  rabbitmq_data:
```

### pgAdmin Setup

After running `docker compose up -d`, open `http://localhost:5050` in the browser.

Login with:
- **Email:** admin@simplira.com
- **Password:** admin

Register the server:
- Right click **Servers** → **Register** → **Server**
- **General tab** → Name: `Simplira`
- **Connection tab:**
    - Host: `postgres` (Docker service name — not localhost)
    - Port: `5432`
    - Database: `simplira`
    - Username: `simplira`
    - Password: `simplira`

### Verifying Flyway Migrations

To check which migrations have run, connect via pgAdmin or directly inside the container:

```bash
docker exec -it docker-postgres-1 psql -U simplira -d simplira
```

Then:
```sql
SELECT * FROM flyway_schema_history;
```

---

## 3. Architectural Principles

### Hexagonal Architecture (Ports and Adapters)

```
┌─────────────────────────────────────┐
│           Application Core          │
│                                     │
│  Service → Domain Repository Port   │
└─────────────────┬───────────────────┘
                  │
     ┌────────────┼────────────┐
     ↓            ↓            ↓
PostgreSQL    SQL Server    Firebase
 Adapter       Adapter      Adapter
  (now)       (future)     (future)
```

### Layer Dependency Rules

```
application  → domain        ✅ allowed
infrastructure → domain      ✅ allowed
domain       → nothing       ✅ no outward dependencies
shared       → nothing       ✅ utilities only

application  → infrastructure ❌ FORBIDDEN
domain       → infrastructure ❌ FORBIDDEN
```

### Key Decisions

- **UUID over auto-increment IDs** — globally unique, harder to guess, multi-tenant safe
- **Soft deletes** on Issue, Comment, Attachment — audit trail preserved
- **Float positions** for drag-and-drop ordering — midpoint insertion without reordering
- **Pre-rendered notifications** — title/body generated at creation, not at query time
- **AuditLog as permanent history** — notifications are temporary (90 days), audit log is forever
- **No roles in JWT** — roles fetched from DB per request to avoid stale data
- **RabbitMQ from day one** — no painful refactor needed when scaling to multiple instances
- **Flyway for migrations** — schema never managed manually

---

## 4. Database Schema

> **Reading note — this is the original design.** Several columns and tables were added or
> changed in later sections; where they differ, the later section wins:
> - Issue `status` enum → `status_id` FK to IssueStatus (§33)
> - Project `key`, Issue `number` for human identifiers (§24)
> - Workspace / Project / IssueType / Label soft-delete columns (§21, §32)
> - User `email_verified` + EmailVerificationToken (table below; flow in §6)
> - Issue `version` for optimistic locking (§36)
>
> **Timezone convention:** every `TIMESTAMP` column in this document is created as `TIMESTAMPTZ`
> (timestamp *with* time zone). Plain `timestamp` is never used — it drops offset information and
> corrupts cross-timezone ordering. Store UTC, render per-user on the client.

### User

```sql
User
- id                UUID, PK
- email             VARCHAR, UNIQUE, NOT NULL
- password          VARCHAR, NOT NULL              -- hashed
- full_name         VARCHAR, NOT NULL
- avatar_url        VARCHAR, nullable
- email_verified    BOOLEAN, NOT NULL, DEFAULT false
- created_at        TIMESTAMPTZ, NOT NULL
- updated_at        TIMESTAMPTZ, NOT NULL
```

### RefreshToken

```sql
RefreshToken
- id                UUID, PK
- user_id           UUID, FK → User, NOT NULL
- token_hash        VARCHAR, NOT NULL              -- hashed, not plain text
- device_info       VARCHAR, nullable
- ip_address        VARCHAR, nullable
- expires_at        TIMESTAMPTZ, NOT NULL
- created_at        TIMESTAMPTZ, NOT NULL
- revoked_at        TIMESTAMPTZ, nullable            -- null = still valid

UNIQUE (token_hash)
```

### EmailVerificationToken

```sql
EmailVerificationToken
- id                UUID, PK
- user_id           UUID, FK → User, NOT NULL
- token_hash        VARCHAR, NOT NULL              -- hashed, single-use
- expires_at        TIMESTAMPTZ, NOT NULL            -- 24 hours default
- consumed_at       TIMESTAMPTZ, nullable            -- null = unused
- created_at        TIMESTAMPTZ, NOT NULL

UNIQUE (token_hash)
```

### Workspace

```sql
Workspace
- id                UUID, PK
- name              VARCHAR, NOT NULL
- slug              VARCHAR, UNIQUE, NOT NULL      -- e.g. "acme-tech"
- owner_id          UUID, FK → User, NOT NULL
- created_at        TIMESTAMPTZ, NOT NULL
- updated_at        TIMESTAMPTZ, NOT NULL
```

### UserWorkspace

```sql
UserWorkspace
- id                UUID, PK
- user_id           UUID, FK → User, NOT NULL
- workspace_id      UUID, FK → Workspace, NOT NULL
- role              ENUM(ADMIN, TEAM_LEADER, DEVELOPER), NOT NULL
- joined_at         TIMESTAMPTZ, NOT NULL
- invited_by        UUID, FK → User, nullable

UNIQUE (user_id, workspace_id)
```

### Project

```sql
Project
- id                UUID, PK
- workspace_id      UUID, FK → Workspace, NOT NULL
- name              VARCHAR, NOT NULL
- key               VARCHAR(10), NOT NULL                -- e.g. "SIMP"; UNIQUE per workspace (§24)
- description       TEXT, nullable
- visibility        ENUM(PUBLIC, PRIVATE), NOT NULL
- created_by        UUID, FK → User, NOT NULL
- created_at        TIMESTAMPTZ, NOT NULL
- updated_at        TIMESTAMPTZ, NOT NULL
```

### ProjectMember

```sql
ProjectMember
- id                UUID, PK
- project_id        UUID, FK → Project, NOT NULL
- user_id           UUID, FK → User, NOT NULL
- role              ENUM(TEAM_LEADER, DEVELOPER), NOT NULL  -- Admin excluded (workspace-wide)
- added_at          TIMESTAMPTZ, NOT NULL

UNIQUE (project_id, user_id)
```

### Sprint

```sql
Sprint
- id                UUID, PK
- project_id        UUID, FK → Project, NOT NULL
- name              VARCHAR, NOT NULL
- status            ENUM(PLANNED, ACTIVE, COMPLETED), NOT NULL
- start_date        DATE, NOT NULL
- end_date          DATE, NOT NULL
- created_by        UUID, FK → User, NOT NULL
- created_at        TIMESTAMPTZ, NOT NULL
- updated_at        TIMESTAMPTZ, NOT NULL
```

> Only one ACTIVE sprint per project enforced at service layer.

### IssueType

```sql
IssueType
- id                UUID, PK
- project_id        UUID, FK → Project, NOT NULL  -- scoped per project
- name              VARCHAR, NOT NULL              -- e.g. Bug, Task, Story
- icon              VARCHAR, nullable
- color             VARCHAR, nullable              -- hex code
- is_default        BOOLEAN, NOT NULL, DEFAULT false
- created_at        TIMESTAMPTZ, NOT NULL

UNIQUE (project_id, name)
```

> Only one default per project enforced at service layer.

### Issue

```sql
Issue
- id                UUID, PK
- project_id        UUID, FK → Project, NOT NULL
- sprint_id         UUID, FK → Sprint, nullable        -- null = backlog
- parent_id         UUID, FK → Issue, nullable         -- null = not a subtask
- type_id           UUID, FK → IssueType, NOT NULL
- title             VARCHAR, NOT NULL
- description       TEXT, nullable
- status_id         UUID, FK → IssueStatus, NOT NULL     -- replaces old status enum (§33)
- priority          ENUM(LOW, MEDIUM, HIGH, CRITICAL), NOT NULL
- is_blocked        BOOLEAN, NOT NULL, DEFAULT false    -- auto-managed by service layer
- blocked_reason    TEXT, nullable
- reporter_id       UUID, FK → User, NOT NULL
- closed_by         UUID, FK → User, nullable
- closed_at         TIMESTAMPTZ, nullable
- due_date          DATE, nullable
- number            INTEGER, NOT NULL                   -- per-project sequence; identifier = key + '-' + number (§24)
- backlog_position  FLOAT, nullable
- sprint_position   FLOAT, nullable
- version           BIGINT, NOT NULL, DEFAULT 0          -- optimistic locking / @Version (§36)
- created_at        TIMESTAMPTZ, NOT NULL
- updated_at        TIMESTAMPTZ, NOT NULL
- deleted_at        TIMESTAMPTZ, nullable                 -- soft delete

UNIQUE (project_id, number)
```

> Subtask constraint: parent_id can only reference issues where parent_id IS NULL.
> is_blocked is auto-set when a BLOCKS link is created and auto-cleared when removed.
> Position ordering uses float midpoint insertion to avoid full reorder.

### IssueAssignee

```sql
IssueAssignee
- id                UUID, PK
- issue_id          UUID, FK → Issue, NOT NULL
- user_id           UUID, FK → User, NOT NULL
- assigned_at       TIMESTAMPTZ, NOT NULL
- assigned_by       UUID, FK → User, NOT NULL

UNIQUE (issue_id, user_id)
```

### IssueLink

```sql
IssueLink
- id                UUID, PK
- source_issue_id   UUID, FK → Issue, NOT NULL
- target_issue_id   UUID, FK → Issue, NOT NULL
- link_type         ENUM(RELATED_TO, DUPLICATE_OF, BLOCKS, DEPENDS_ON), NOT NULL
- created_by        UUID, FK → User, NOT NULL
- created_at        TIMESTAMPTZ, NOT NULL

UNIQUE (source_issue_id, target_issue_id, link_type)
CHECK (source_issue_id <> target_issue_id)
```

> BLOCKED_BY excluded — directionality of BLOCKS covers it.
> RELATED_TO is bidirectional: query both source and target directions.
> UNIQUE includes link_type so A BLOCKS B and A DEPENDS_ON B are both valid.
> CHECK prevents an issue linking to itself.
>
> BLOCKS link side effects (handled in service layer):
> - Creating A BLOCKS B → sets B.is_blocked = true
> - Deleting A BLOCKS B → if no other BLOCKS remain on B, sets B.is_blocked = false

### Comment

```sql
Comment
- id                UUID, PK
- issue_id          UUID, FK → Issue, NOT NULL
- author_id         UUID, FK → User, NOT NULL
- content           TEXT, NOT NULL
- created_at        TIMESTAMPTZ, NOT NULL
- updated_at        TIMESTAMPTZ, NOT NULL
- deleted_at        TIMESTAMPTZ, nullable            -- soft delete
```

### CommentMention

```sql
CommentMention
- id                UUID, PK
- comment_id        UUID, FK → Comment, NOT NULL
- mentioned_user_id UUID, FK → User, NOT NULL
- created_at        TIMESTAMPTZ, NOT NULL

UNIQUE (comment_id, mentioned_user_id)
```

> Parsed from @username syntax on comment save.
> Stored separately for efficient mention queries without scanning comment text.

### Label

```sql
Label
- id                UUID, PK
- project_id        UUID, FK → Project, NOT NULL
- name              VARCHAR, NOT NULL
- color             VARCHAR, NOT NULL              -- hex code e.g. #FF5733
- created_by        UUID, FK → User, NOT NULL
- created_at        TIMESTAMPTZ, NOT NULL

UNIQUE (project_id, name)
```

### IssueLabel

```sql
IssueLabel
- issue_id          UUID, FK → Issue, NOT NULL
- label_id          UUID, FK → Label, NOT NULL

PRIMARY KEY (issue_id, label_id)
```

### Attachment

```sql
Attachment
- id                UUID, PK
- issue_id          UUID, FK → Issue, NOT NULL
- uploaded_by       UUID, FK → User, NOT NULL
- file_name         VARCHAR, NOT NULL
- file_url          VARCHAR, NOT NULL              -- points to object storage
- file_size         BIGINT, NOT NULL               -- bytes
- mime_type         VARCHAR, NOT NULL              -- images only (enforced in service)
- created_at        TIMESTAMPTZ, NOT NULL
- deleted_at        TIMESTAMPTZ, nullable            -- soft delete
- deleted_by        UUID, FK → User, nullable
```

### Invitation

```sql
Invitation
- id                UUID, PK
- workspace_id      UUID, FK → Workspace, NOT NULL
- email             VARCHAR, NOT NULL
- role              ENUM(ADMIN, TEAM_LEADER, DEVELOPER), NOT NULL
- invited_by        UUID, FK → User, NOT NULL
- token             VARCHAR, UNIQUE, NOT NULL      -- sent in invitation email URL
- status            ENUM(PENDING, ACCEPTED, EXPIRED), NOT NULL
- expires_at        TIMESTAMPTZ, NOT NULL            -- 7 days default
- accepted_by       UUID, FK → User, nullable
- accepted_at       TIMESTAMPTZ, nullable
- created_at        TIMESTAMPTZ, NOT NULL
```

### Notification

```sql
Notification
- id                UUID, PK
- workspace_id      UUID, FK → Workspace, NOT NULL
- recipient_id      UUID, FK → User, NOT NULL
- actor_id          UUID, FK → User, NOT NULL
- entity_type       ENUM(ISSUE, SPRINT, PROJECT, COMMENT), NOT NULL
- entity_id         UUID, NOT NULL
- action            ENUM(ASSIGNED, UNASSIGNED, MENTIONED, ISSUE_BLOCKED,
                         COMMENT_ADDED, STATUS_CHANGED, SPRINT_STARTED,
                         SPRINT_CLOSED, ISSUE_CLOSED, LINKED), NOT NULL
- title             VARCHAR, NOT NULL              -- pre-rendered
- body              TEXT, nullable                 -- pre-rendered
- is_read           BOOLEAN, NOT NULL, DEFAULT false
- read_at           TIMESTAMPTZ, nullable
- created_at        TIMESTAMPTZ, NOT NULL
- expires_at        TIMESTAMPTZ, NOT NULL            -- 90 days from created_at
```

### NotificationPreference

```sql
NotificationPreference
- id                UUID, PK
- user_id           UUID, FK → User, NOT NULL
- workspace_id      UUID, FK → Workspace, NOT NULL
- action            ENUM(same as Notification.action), NOT NULL
- in_app            BOOLEAN, NOT NULL, DEFAULT true
- email             BOOLEAN, NOT NULL, DEFAULT true
- updated_at        TIMESTAMPTZ, NOT NULL

UNIQUE (user_id, workspace_id, action)
```

### AuditLog

```sql
AuditLog
- id                UUID, PK
- workspace_id      UUID, FK → Workspace, NOT NULL
- project_id        UUID, FK → Project, nullable
- issue_id          UUID, FK → Issue, nullable
- actor_id          UUID, FK → User, NOT NULL
- entity_type       ENUM(ISSUE, SPRINT, PROJECT, COMMENT, MEMBER), NOT NULL
- entity_id         UUID, NOT NULL
- action            ENUM(CREATED, UPDATED, DELETED, ASSIGNED, UNASSIGNED,
                         LINKED, STATUS_CHANGED, SPRINT_STARTED,
                         SPRINT_CLOSED, ISSUE_CLOSED), NOT NULL
- old_value         JSON, nullable                 -- flexible JSON snapshot
- new_value         JSON, nullable                 -- flexible JSON snapshot
- created_at        TIMESTAMPTZ, NOT NULL
```

> JSON snapshots example:
> old_value: { "status": "TODO" }
> new_value: { "status": "IN_PROGRESS" }

### Schema Stats

| Tables | 24 |
|---|---|
| Enums | 13 |
| Soft deletes | Issue, Comment, Attachment, Workspace, Project, IssueType, IssueStatus, Label |
| CHECK constraints | IssueLink self-link prevention |
| Audit trail | AuditLog covers all meaningful actions |
| Real-time | Notifications + WebSocket via RabbitMQ |
| Multi-tenancy | Workspace scoped, role per workspace |
| DB portability | Hexagonal architecture, JPA abstraction |

---

## 5. Entity Relationship Summary

```
User ──< UserWorkspace >── Workspace
User ──< RefreshToken
Workspace ──< Project
Workspace ──< Invitation
Workspace ──< Notification
Workspace ──< AuditLog
Project ──< ProjectMember >── User
Project ──< Sprint
Project ──< Issue
Project ──< Label
Project ──< IssueType
Sprint ──< Issue
Issue ──< Issue (subtasks via parent_id)
Issue ──< IssueAssignee >── User
Issue ──< IssueLink >── Issue
Issue ──< IssueLabel >── Label
Issue ──< Comment
Issue ──< Attachment
Comment ──< CommentMention >── User
User ──< NotificationPreference >── Workspace
```

---

## 6. Authentication & Security

### Token Strategy

| Token | Lifetime | Storage | Purpose |
|---|---|---|---|
| Access Token | 15 minutes | JS memory (variable) | API authorization header |
| Refresh Token | 30 days | HttpOnly cookie | Obtain new access token |

### JWT Payload (Access Token)

```json
{
  "sub": "user-uuid",
  "email": "john@example.com",
  "fullName": "John Doe",
  "iat": 1716000000,
  "exp": 1716000900
}
```

> Roles are NOT stored in the JWT — fetched from DB per request to avoid stale data.

### Token Rotation

Every refresh generates a new refresh token. The old one is immediately revoked.
If a revoked token is used, all sessions for that user are terminated (theft detection).

### Password Reset Flow

1. `POST /auth/forgot-password` — generate secure token, store hash, send email
2. Always return 200 even if email not found (prevents user enumeration)
3. `POST /auth/reset-password` — validate token, hash new password, revoke ALL refresh tokens
4. Revoking all refresh tokens forces re-login everywhere (security on compromise)

### Email Verification Flow

1. On `POST /auth/register`, create the user with `email_verified = false`, generate a
   single-use token, store its **hash** in EmailVerificationToken (24h expiry), and email the link.
2. `POST /auth/verify-email` — look up by token hash, check it is not expired and not consumed,
   set `users.email_verified = true`, set `consumed_at = now()`.
3. `POST /auth/resend-verification` — invalidate any outstanding tokens for the user, issue a
   fresh one. Always return 200 (no account enumeration).
4. Gate sensitive actions (creating/joining workspaces, accepting invitations) behind
   `email_verified = true`. Login itself may be allowed, but with reduced capability until verified.

> Tokens are hashed at rest exactly like refresh tokens — a leaked DB row must not be replayable.

### Authorization Logic

```
Can user access project?
  1. Is user Admin in workspace?     → YES → Allow
  2. Is project PUBLIC?              → YES → Allow (if workspace member)
  3. Is user in ProjectMember?       → YES → Allow
  4. Otherwise                       → Deny
```

### Spring Security Configuration

- Stateless sessions (STATELESS session creation policy)
- CSRF disabled (JWT-based, no sessions)
- JWT filter runs before UsernamePasswordAuthenticationFilter
- Public endpoints: register, login, forgot-password, reset-password, verify-email, accept-invitation, WebSocket handshake

### Dependencies (pom.xml)

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.5</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.5</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.5</version>
</dependency>
```

---

## 7. API Layer

### Conventions

- Base path: `/api/v1`
- Plural nouns, never verbs: `/issues` not `/getIssues`
- Nested resources for ownership: `/projects/{id}/issues`
- PATCH for updates (partial, not full replacement)
- Actions as sub-resources: `/sprints/{id}/start`

### Standard Response Envelope

**Success:**
```json
{
  "success": true,
  "data": { },
  "meta": { "page": 1, "pageSize": 20, "total": 150 }
}
```

**Error:**
```json
{
  "success": false,
  "error": {
    "code": "ISSUE_NOT_FOUND",
    "message": "Issue with id X was not found",
    "details": []
  }
}
```

### Pagination Query Params

```
?page=1&pageSize=20&sortBy=sprint_position&sortDir=asc
```

### Role Legend

| Symbol | Meaning |
|---|---|
| 🌐 | Public (no auth) |
| 🔓 | Any authenticated user |
| 👤 | Workspace/project member |
| 👥 | Team Leader or above |
| 🔑 | Admin only |

---

### Auth Endpoints

```
POST   /api/v1/auth/register          🌐 Register new user
POST   /api/v1/auth/login             🌐 Login, returns JWT
POST   /api/v1/auth/logout            🔓 Invalidate token
POST   /api/v1/auth/refresh           🔓 Refresh JWT token
POST   /api/v1/auth/forgot-password   🌐 Send reset email
POST   /api/v1/auth/reset-password    🌐 Reset with token
POST   /api/v1/auth/verify-email          🌐 Verify email address
POST   /api/v1/auth/resend-verification   🌐 Resend verification email
```

### User Endpoints

```
GET    /api/v1/users/me               🔓 Get current user profile
PATCH  /api/v1/users/me               🔓 Update profile
PATCH  /api/v1/users/me/password      🔓 Change password
POST   /api/v1/users/me/avatar        🔓 Upload avatar
DELETE /api/v1/users/me/avatar        🔓 Remove avatar
```

### Workspace Endpoints

```
GET    /api/v1/workspaces                           🔓 List my workspaces
POST   /api/v1/workspaces                           🔓 Create workspace
GET    /api/v1/workspaces/{workspaceId}             👤 Get workspace details
PATCH  /api/v1/workspaces/{workspaceId}             🔑 Update workspace
DELETE /api/v1/workspaces/{workspaceId}             🔑 Delete workspace
```

### Workspace Member Endpoints

```
GET    /api/v1/workspaces/{workspaceId}/members                     👤 List members
PATCH  /api/v1/workspaces/{workspaceId}/members/{userId}/role       🔑 Change role
DELETE /api/v1/workspaces/{workspaceId}/members/{userId}            🔑 Remove member
DELETE /api/v1/workspaces/{workspaceId}/members/me                  👤 Leave workspace
```

### Invitation Endpoints

```
POST   /api/v1/workspaces/{workspaceId}/invitations                 🔑 Send invitation
GET    /api/v1/workspaces/{workspaceId}/invitations                 🔑 List invitations
DELETE /api/v1/workspaces/{workspaceId}/invitations/{invitationId}  🔑 Cancel invitation
POST   /api/v1/invitations/accept                                   🌐 Accept (token in body)
```

> Accept is public — user may not be registered yet when clicking email link.

### Project Endpoints

```
GET    /api/v1/workspaces/{workspaceId}/projects                    👤 List projects
POST   /api/v1/workspaces/{workspaceId}/projects                    🔑 Create project
GET    /api/v1/projects/{projectId}                                 👤 Get project
PATCH  /api/v1/projects/{projectId}                                 🔑 Update project
DELETE /api/v1/projects/{projectId}                                 🔑 Delete project
```

### Project Member Endpoints

```
GET    /api/v1/projects/{projectId}/members                         👤 List members
POST   /api/v1/projects/{projectId}/members                         👥 Add member
PATCH  /api/v1/projects/{projectId}/members/{userId}/role           👥 Change role
DELETE /api/v1/projects/{projectId}/members/{userId}                👥 Remove member
```

### Issue Type Endpoints

```
GET    /api/v1/projects/{projectId}/issue-types                     👤 List issue types
POST   /api/v1/projects/{projectId}/issue-types                     👥 Create issue type
PATCH  /api/v1/projects/{projectId}/issue-types/{typeId}            👥 Update issue type
DELETE /api/v1/projects/{projectId}/issue-types/{typeId}            👥 Delete issue type
```

### Sprint Endpoints

```
GET    /api/v1/projects/{projectId}/sprints                         👤 List sprints
POST   /api/v1/projects/{projectId}/sprints                         👥 Create sprint
GET    /api/v1/sprints/{sprintId}                                   👤 Get sprint
PATCH  /api/v1/sprints/{sprintId}                                   👥 Update sprint
DELETE /api/v1/sprints/{sprintId}                                   👥 Delete sprint
POST   /api/v1/sprints/{sprintId}/start                             👥 Start sprint
POST   /api/v1/sprints/{sprintId}/close                             👥 Close sprint
```

> `start` and `close` are POST sub-resources because they carry significant side effects
> (notifications, audit log, incomplete issue handling). Not simple PATCH operations.

### Issue Endpoints

```
GET    /api/v1/projects/{projectId}/issues                          👤 List backlog issues
GET    /api/v1/sprints/{sprintId}/issues                            👤 List sprint issues
POST   /api/v1/projects/{projectId}/issues                          👤 Create issue
GET    /api/v1/issues/{issueId}                                     👤 Get issue
PATCH  /api/v1/issues/{issueId}                                     👤 Update issue
DELETE /api/v1/issues/{issueId}                                     👤 Delete issue (soft)
POST   /api/v1/issues/{issueId}/block                               👤 Set blocked flag
DELETE /api/v1/issues/{issueId}/block                               👤 Clear blocked flag
PATCH  /api/v1/issues/{issueId}/position                            👤 Update position
POST   /api/v1/issues/{issueId}/move-to-sprint                      👥 Move to sprint
POST   /api/v1/issues/{issueId}/move-to-backlog                     👥 Move to backlog
POST   /api/v1/projects/{projectId}/issues/bulk-assign              👥 Bulk assign issues
```

### Subtask Endpoints

```
GET    /api/v1/issues/{issueId}/subtasks                            👤 List subtasks
POST   /api/v1/issues/{issueId}/subtasks                            👤 Create subtask
```

> Individual subtask operations (GET, PATCH, DELETE) reuse `/issues/{issueId}` endpoints.

### Issue Assignee Endpoints

```
GET    /api/v1/issues/{issueId}/assignees                           👤 List assignees
POST   /api/v1/issues/{issueId}/assignees                           👤 Add assignee
DELETE /api/v1/issues/{issueId}/assignees/{userId}                  👤 Remove assignee
```

### Issue Link Endpoints

```
GET    /api/v1/issues/{issueId}/links                               👤 List links
POST   /api/v1/issues/{issueId}/links                               👤 Create link
DELETE /api/v1/issues/{issueId}/links/{linkId}                      👤 Delete link
```

### Label Endpoints

```
GET    /api/v1/projects/{projectId}/labels                          👤 List labels
POST   /api/v1/projects/{projectId}/labels                          👥 Create label
PATCH  /api/v1/projects/{projectId}/labels/{labelId}                👥 Update label
DELETE /api/v1/projects/{projectId}/labels/{labelId}                👥 Delete label
POST   /api/v1/issues/{issueId}/labels                              👤 Add label to issue
DELETE /api/v1/issues/{issueId}/labels/{labelId}                    👤 Remove from issue
```

### Comment Endpoints

```
GET    /api/v1/issues/{issueId}/comments                            👤 List comments
POST   /api/v1/issues/{issueId}/comments                            👤 Create comment
PATCH  /api/v1/comments/{commentId}                                 👤 Update own comment
DELETE /api/v1/comments/{commentId}                                 👤 Delete own (soft)
```

### Attachment Endpoints

```
GET    /api/v1/issues/{issueId}/attachments                         👤 List attachments
POST   /api/v1/issues/{issueId}/attachments                         👤 Upload attachment
DELETE /api/v1/attachments/{attachmentId}                           👤 Delete (soft)
```

### Notification Endpoints

```
GET    /api/v1/notifications                                        🔓 List my notifications
PATCH  /api/v1/notifications/{notificationId}/read                  🔓 Mark as read
POST   /api/v1/notifications/read-all                               🔓 Mark all as read
DELETE /api/v1/notifications/{notificationId}                       🔓 Dismiss notification
```

### Notification Preference Endpoints

```
GET    /api/v1/workspaces/{workspaceId}/notification-preferences    👤 Get preferences
PATCH  /api/v1/workspaces/{workspaceId}/notification-preferences    👤 Update preferences
```

### Audit Log Endpoints

```
GET    /api/v1/workspaces/{workspaceId}/audit-log                   🔑 Workspace audit log
GET    /api/v1/projects/{projectId}/audit-log                       👥 Project audit log
GET    /api/v1/issues/{issueId}/audit-log                           👤 Issue history
```

### Chart Endpoints

```
GET    /api/v1/sprints/{sprintId}/charts/burndown                   👤 Burndown chart data
GET    /api/v1/projects/{projectId}/charts/velocity                 👤 Velocity chart data
GET    /api/v1/projects/{projectId}/charts/cumulative-flow          👤 Cumulative flow data
```

> Charts return pre-aggregated data. Backend does the heavy lifting, not the frontend.

### Endpoint Count

> **Canonical endpoint count — single source of truth.** This table already includes every
> endpoint added by later sections (member search §26, identifier resolution §24, issue statuses
> §33, resend-verification §6). The running totals quoted in §32/§33 (92, 97) were cumulative
> drafts and also inherited a base off-by-one; this table supersedes them.

| Resource Group | Count |
|---|---|
| Auth | 8 |
| Users | 5 |
| Workspaces | 5 |
| Workspace Members (incl. search) | 5 |
| Invitations | 4 |
| Projects | 5 |
| Project Members (incl. search) | 5 |
| Issue Types | 4 |
| Issue Statuses | 5 |
| Sprints | 7 |
| Issues (incl. resolve-by-identifier) | 13 |
| Subtasks | 2 |
| Issue Assignees | 3 |
| Issue Links | 3 |
| Labels | 6 |
| Comments | 4 |
| Attachments | 3 |
| Notifications | 4 |
| Notification Preferences | 2 |
| Audit Log | 3 |
| Charts | 3 |
| **Total** | **99** |

---

## 8. WebSocket & Real-Time

### Protocol

STOMP over WebSocket, relayed through RabbitMQ.

### Why RabbitMQ as Relay

Spring's built-in simple broker only works on a single server instance. With RabbitMQ
as the relay, messages published by any server instance are received by all connected
clients regardless of which instance they're connected to.

### Topic Structure

```
/topic/workspace/{workspaceId}          ← workspace-wide events
/topic/project/{projectId}              ← board updates
/topic/issue/{issueId}                  ← single issue detail view
/topic/user/{userId}/notifications      ← personal notification bell
```

### WebSocket Configuration

```java
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableStompBrokerRelay("/topic")
            .setRelayHost(relayHost)
            .setRelayPort(61613)
            .setSystemLogin(login)
            .setSystemPasscode(passcode)
            .setClientLogin(login)
            .setClientPasscode(passcode);

        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
            .setAllowedOriginPatterns("*")
            .withSockJS();               // fallback for older browsers
    }
}
```

### Service Layer Pattern

Every meaningful action follows this four-step pattern:

```java
public Issue updateStatus(UUID issueId, UUID newStatusId, UUID actorId) {
    // 1. Persist to database (status is an IssueStatus FK now, not an enum — see §33)
    Issue issue = issueRepository.findById(issueId)...
    issue.setStatusId(newStatusId);
    Issue saved = issueRepository.save(issue);

    // 2. Write audit log
    auditLogService.logStatusChange(saved, oldStatus, newStatus, actorId);

    // 3. Create notifications
    notificationService.notifyStatusChange(saved, actorId);

    // 4. Push WebSocket event
    messagingTemplate.convertAndSend(
        "/topic/project/" + issue.getProjectId(),
        new IssueStatusChangedEvent(issueId, newStatusId)
    );

    return saved;
}
```

### WebSocket Event Payload (lean, not full object)

```json
{
  "type": "ISSUE_STATUS_CHANGED",
  "issueId": "uuid",
  "newStatus": "IN_PROGRESS",
  "actorId": "uuid",
  "actorName": "John Doe",
  "timestamp": "2026-05-17T10:30:00Z"
}
```

### Security

Channel interceptor validates subscription authorization on STOMP SUBSCRIBE command.
Same permission logic as REST API — reused via AuthorizationService.

### Events That Trigger WebSocket Push

| Event | Topic |
|---|---|
| Issue status changed | /topic/project/{projectId} |
| Issue assigned | /topic/project/{projectId} + /topic/user/{userId}/notifications |
| Issue blocked | /topic/project/{projectId} + /topic/user/{userId}/notifications |
| Comment added | /topic/issue/{issueId} |
| Sprint started/closed | /topic/project/{projectId} |
| Notification created | /topic/user/{userId}/notifications |

### RabbitMQ Additional Uses

Beyond WebSocket relay, RabbitMQ can also handle:

- **Async email sending** — publish EmailEvent to queue, consumer sends email independently
- **Async audit log writing** — publish AuditEvent to queue, faster response times

---

## 9. Spring Boot Project Structure

### Top Level

```
simplira-backend/
├── src/
│   ├── main/
│   │   ├── java/com/simplira/
│   │   │   ├── SimpliraApplication.java
│   │   │   ├── domain/
│   │   │   ├── application/
│   │   │   ├── infrastructure/
│   │   │   └── shared/
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── application-dev.yml
│   │       ├── application-prod.yml
│   │       └── db/migration/
│   └── test/java/com/simplira/
├── docker-compose.yml
├── Dockerfile
├── pom.xml
└── README.md
```

### Domain Layer

Pure Java — zero Spring annotations. Framework-independent.

```
domain/
├── model/
│   ├── user/
│   │   ├── User.java
│   │   ├── UserRole.java
│   │   └── RefreshToken.java
│   ├── workspace/
│   │   ├── Workspace.java
│   │   └── UserWorkspace.java
│   ├── project/
│   │   ├── Project.java
│   │   ├── ProjectMember.java
│   │   └── ProjectVisibility.java
│   ├── sprint/
│   │   ├── Sprint.java
│   │   └── SprintStatus.java
│   ├── issue/
│   │   ├── Issue.java
│   │   ├── IssueType.java
│   │   ├── IssueStatus.java
│   │   ├── IssuePriority.java
│   │   ├── IssueLink.java
│   │   ├── IssueLinkType.java
│   │   └── IssueAssignee.java
│   ├── comment/
│   │   ├── Comment.java
│   │   └── CommentMention.java
│   ├── label/
│   │   ├── Label.java
│   │   └── IssueLabel.java
│   ├── attachment/
│   │   └── Attachment.java
│   ├── notification/
│   │   ├── Notification.java
│   │   ├── NotificationAction.java
│   │   └── NotificationPreference.java
│   ├── invitation/
│   │   ├── Invitation.java
│   │   └── InvitationStatus.java
│   └── audit/
│       ├── AuditLog.java
│       └── AuditAction.java
├── repository/
│   ├── UserRepository.java
│   ├── RefreshTokenRepository.java
│   ├── WorkspaceRepository.java
│   ├── UserWorkspaceRepository.java
│   ├── ProjectRepository.java
│   ├── ProjectMemberRepository.java
│   ├── SprintRepository.java
│   ├── IssueRepository.java
│   ├── IssueTypeRepository.java
│   ├── IssueLinkRepository.java
│   ├── IssueAssigneeRepository.java
│   ├── CommentRepository.java
│   ├── CommentMentionRepository.java
│   ├── LabelRepository.java
│   ├── AttachmentRepository.java
│   ├── NotificationRepository.java
│   ├── NotificationPreferenceRepository.java
│   ├── InvitationRepository.java
│   └── AuditLogRepository.java
└── service/
    ├── AuthService.java
    ├── UserService.java
    ├── WorkspaceService.java
    ├── ProjectService.java
    ├── SprintService.java
    ├── IssueService.java
    ├── IssueTypeService.java
    ├── IssueLinkService.java
    ├── CommentService.java
    ├── LabelService.java
    ├── AttachmentService.java
    ├── NotificationService.java
    ├── InvitationService.java
    ├── AuditLogService.java
    └── ChartService.java
```

### Application Layer

Spring lives here. Controllers, DTOs, request/response mapping.

```
application/
├── api/
│   ├── auth/
│   │   ├── AuthController.java
│   │   ├── request/
│   │   │   ├── RegisterRequest.java
│   │   │   ├── LoginRequest.java
│   │   │   ├── RefreshTokenRequest.java
│   │   │   ├── ForgotPasswordRequest.java
│   │   │   └── ResetPasswordRequest.java
│   │   └── response/
│   │       ├── AuthResponse.java
│   │       └── UserResponse.java
│   ├── workspace/
│   ├── project/
│   ├── sprint/
│   ├── issue/
│   │   ├── IssueController.java
│   │   ├── IssueAssigneeController.java
│   │   ├── IssueLinkController.java
│   │   ├── request/
│   │   └── response/
│   ├── comment/
│   ├── label/
│   ├── attachment/
│   ├── notification/
│   ├── invitation/
│   ├── audit/
│   └── chart/
│       └── response/
│           ├── BurndownChartResponse.java
│           ├── VelocityChartResponse.java
│           └── CumulativeFlowResponse.java
└── shared/
    ├── response/
    │   ├── ApiResponse.java
    │   ├── PageResponse.java
    │   └── ErrorResponse.java
    └── validation/
        ├── ValidUUID.java
        └── ValidHexColor.java
```

### Infrastructure Layer

Everything external — database, messaging, email, storage, security.

```
infrastructure/
├── persistence/
│   └── postgres/
│       ├── entity/             ← JPA entities (annotated)
│       ├── jpa/                ← Spring Data JPA interfaces
│       ├── adapter/            ← implements domain repositories
│       └── mapper/             ← JPA entity ↔ domain model
├── messaging/
│   ├── config/
│   │   ├── RabbitMQConfig.java
│   │   └── WebSocketConfig.java
│   ├── publisher/
│   │   ├── IssueEventPublisher.java
│   │   ├── SprintEventPublisher.java
│   │   └── NotificationEventPublisher.java
│   └── event/
│       ├── IssueStatusChangedEvent.java
│       ├── IssueAssignedEvent.java
│       ├── SprintStartedEvent.java
│       └── SprintClosedEvent.java
├── storage/
│   ├── StorageService.java     ← interface
│   └── S3StorageService.java   ← implementation
├── email/
│   ├── EmailService.java       ← interface
│   ├── SmtpEmailService.java   ← implementation
│   └── template/
│       ├── invitation.html
│       ├── password-reset.html
│       └── notification.html
└── security/
    ├── JwtService.java
    ├── JwtAuthenticationFilter.java
    ├── SecurityConfig.java
    ├── SimpliraUserDetails.java
    └── AuthorizationService.java
```

### Shared Layer

```
shared/
├── exception/
│   ├── SimpliraException.java
│   ├── IssueNotFoundException.java
│   ├── WorkspaceNotFoundException.java
│   ├── UnauthorizedException.java
│   ├── ForbiddenException.java
│   ├── InvalidStatusTransitionException.java
│   ├── SprintAlreadyActiveException.java
│   └── GlobalExceptionHandler.java       ← @ControllerAdvice, single error handling
├── util/
│   ├── SlugGenerator.java
│   ├── TokenGenerator.java
│   └── MentionParser.java                ← parses @username from comments
└── constant/
    ├── SimpliraConstants.java
    └── SecurityConstants.java
```

### Database Migrations (Flyway)

```
resources/db/migration/   ← CANONICAL LIST (supersedes the drafts in §32 and §33)
├── V1__create_users.sql                       -- includes email_verified
├── V2__create_refresh_tokens.sql
├── V3__create_email_verification_tokens.sql
├── V4__create_workspaces.sql                  -- slug uses a partial unique index (§21)
├── V5__create_user_workspaces.sql
├── V6__create_invitations.sql
├── V7__create_projects.sql                    -- includes key, soft-delete columns
├── V8__create_project_members.sql
├── V9__create_default_issue_types_table.sql
├── V10__create_issue_types.sql
├── V11__create_default_issue_statuses_table.sql
├── V12__create_issue_statuses.sql
├── V13__create_sprints.sql
├── V14__create_labels.sql
├── V15__create_issues.sql                     -- status_id FK, number, version
├── V16__create_issue_assignees.sql
├── V17__create_issue_links.sql
├── V18__create_issue_labels.sql
├── V19__create_comments.sql
├── V20__create_comment_mentions.sql
├── V21__create_attachments.sql
├── V22__create_notifications.sql
├── V23__create_notification_preferences.sql
├── V24__create_audit_log.sql
├── V25__seed_default_issue_types.sql
├── V26__seed_default_issue_statuses.sql
└── V27__add_indexes.sql
```

> Each file runs exactly once, never edited retroactively.
> Rolling back means writing a new migration.
>
> **Notes on the canonical list:**
> - FK-ordered: every table is created after the tables it references (e.g. issues after
    >   projects/sprints/issue_types/issue_statuses, join tables after both parents).
> - The five join tables that earlier drafts omitted — user_workspaces, project_members,
    >   issue_labels, comment_mentions, notification_preferences — each have their own migration here.
> - Because this is a greenfield schema, soft-delete columns and partial unique indexes (§21, §32)
    >   are written **into** the relevant CREATE TABLE statements rather than added later by ALTER.
> - Specific V-numbers cited in the older correction sections (§21, §24, §31–§33) refer to the
    >   pre-canonical numbering and are illustrative only — this list is authoritative.

### application.yml

```yaml
spring:
  application:
    name: simplira

  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/simplira}
    username: ${DB_USERNAME:simplira}
    password: ${DB_PASSWORD:simplira}

  jpa:
    hibernate:
      ddl-auto: validate          # Never auto-create — Flyway handles schema
    show-sql: false

  flyway:
    enabled: true
    locations: classpath:db/migration

  rabbitmq:
    host: ${RABBITMQ_HOST:localhost}
    port: ${RABBITMQ_PORT:5672}
    username: ${RABBITMQ_USERNAME:simplira}
    password: ${RABBITMQ_PASSWORD:simplira}

simplira:
  jwt:
    secret: ${JWT_SECRET}
    access-token-expiry: 900       # 15 minutes
    refresh-token-expiry: 2592000  # 30 days

  websocket:
    relay-host: ${RABBITMQ_HOST:localhost}
    relay-port: 61613

  storage:
    bucket: ${S3_BUCKET:simplira-attachments}
    region: ${S3_REGION:eu-central-1}

  notifications:
    expiry-days: 90
```

---

## 10. Key Architectural Patterns

### Repository Pattern

Services talk to interfaces, never to JPA directly.

```java
// Domain repository — pure Java interface
public interface IssueRepository {
    Optional<Issue> findById(UUID id);
    Issue save(Issue issue);
    void delete(UUID id);
    List<Issue> findByProjectId(UUID projectId);
}

// Infrastructure adapter — Spring @Repository, implements interface
@Repository
public class IssueRepositoryAdapter implements IssueRepository {
    private final JpaIssueRepository jpaRepository;
    private final IssueEntityMapper mapper;

    @Override
    public Optional<Issue> findById(UUID id) {
        return jpaRepository.findByIdAndDeletedAtIsNull(id)
            .map(mapper::toDomain);
    }
}
```

### Soft Delete Pattern

```java
// Never hard-delete: set deleted_at
@Override
public void delete(UUID id) {
    jpaRepository.findById(id).ifPresent(entity -> {
        entity.setDeletedAt(LocalDateTime.now());
        jpaRepository.save(entity);
    });
}

// All queries exclude soft-deleted records
List<Issue> findByProjectIdAndDeletedAtIsNull(UUID projectId);
```

### Float Position Ordering

```
Issues at positions 1.0 and 2.0
Insert between → 1.5 (no full reorder)
Insert between 1.0 and 1.5 → 1.25

Periodically normalize back to clean integers when precision runs low.
```

### Audit Log via Spring AOP

Write audit entries automatically without cluttering business logic:

```java
@Aspect
@Component
public class AuditAspect {
    @AfterReturning(pointcut = "@annotation(Auditable)", returning = "result")
    public void audit(JoinPoint joinPoint, Object result) {
        // Extract actor, entity, action, old/new values
        // Write to AuditLog asynchronously via RabbitMQ queue
    }
}
```

### Business Rule Enforcement (Service Layer)

```java
// Only one active sprint per project
public Sprint startSprint(UUID sprintId, UUID actorId) {
    if (sprintRepository.existsByProjectIdAndStatus(projectId, ACTIVE)) {
        throw new SprintAlreadyActiveException(projectId);
    }
    // proceed
}

// Subtask depth limit
public Issue createSubtask(UUID parentId, ...) {
    Issue parent = issueRepository.findById(parentId)...;
    if (parent.getParentId() != null) {
        throw new SubtaskDepthExceededException();
    }
    // proceed
}

// BLOCKS link auto-manages is_blocked flag
public IssueLink createLink(UUID sourceId, UUID targetId, IssueLinkType type) {
    IssueLink link = issueLinkRepository.save(...);
    if (type == BLOCKS) {
        Issue target = issueRepository.findById(targetId)...;
        target.setBlocked(true);
        issueRepository.save(target);
    }
    return link;
}
```

### Global Exception Handling

```java
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IssueNotFoundException.class)
    public ResponseEntity<ApiResponse<?>> handleNotFound(IssueNotFoundException ex) {
        return ResponseEntity.status(NOT_FOUND)
                .body(ApiResponse.error(new ErrorResponse("ISSUE_NOT_FOUND", ex.getMessage())));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<?>> handleValidation(MethodArgumentNotValidException ex) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .toList();
        return ResponseEntity.status(BAD_REQUEST)
                .body(ApiResponse.error(new ErrorResponse("VALIDATION_ERROR", "Invalid request", details)));
    }
}
```

### Layer Summary

| Layer | Responsibility | Spring? |
|---|---|---|
| domain/model | Pure business entities | No |
| domain/repository | Interfaces only | No |
| domain/service | Business logic | No |
| application/api | Controllers, DTOs, mapping | Yes |
| infrastructure/persistence | JPA entities, adapters | Yes |
| infrastructure/messaging | RabbitMQ, WebSocket | Yes |
| infrastructure/security | JWT, Spring Security | Yes |
| shared | Exceptions, utilities | Minimal |

---

## 11. React Frontend Architecture

### Tech Stack

| Concern | Technology |
|---|---|
| Framework | React 18 |
| Language | TypeScript |
| Build Tool | Vite |
| Routing | React Router v6 |
| Server State | TanStack React Query v5 |
| Client State | Zustand |
| UI Components | shadcn/ui + Radix UI |
| Styling | Tailwind CSS |
| WebSocket | STOMP.js + SockJS |
| HTTP Client | Axios |
| Forms | React Hook Form + Zod |
| Charts | Recharts |
| Icons | Lucide React |
| Date Handling | date-fns |

### Key Concept — Server vs Client State

**Server state** (React Query) — data that lives on the backend: issues, sprints, projects, notifications. React Query handles fetching, caching, background refetching, loading and error states automatically.

**Client state** (Zustand) — UI state that lives only in the browser: current access token, active workspace, sidebar open/closed, modal visibility.

Never mix them — no API calls in Zustand, no UI state in React Query.

---

### Project Structure

```
simplira-frontend/
├── public/
├── src/
│   ├── main.tsx                    ← entry point
│   ├── App.tsx                     ← root component + providers
│   ├── api/                        ← all API calls
│   ├── components/                 ← shared reusable UI
│   ├── features/                   ← vertical feature slices
│   ├── hooks/                      ← React Query hooks
│   ├── lib/                        ← third-party config
│   ├── pages/                      ← route-level components
│   ├── store/                      ← Zustand stores
│   ├── types/                      ← TypeScript types
│   └── utils/                      ← pure utility functions
├── index.html
├── vite.config.ts
├── tailwind.config.ts
├── tsconfig.json
└── package.json
```

**pages vs features vs components:**
- **pages** — one per route, thin orchestration only, no business logic
- **features** — self-contained vertical slices (issues, sprints, notifications)
- **components** — shared UI pieces used across multiple features

---

### api/

Every API call lives here. Nothing talks to the backend outside this folder.

```
api/
├── client.ts                       ← Axios instance + interceptors
├── auth.ts
├── workspaces.ts
├── projects.ts
├── sprints.ts
├── issues.ts
├── comments.ts
├── labels.ts
├── attachments.ts
├── notifications.ts
├── invitations.ts
├── audit.ts
└── charts.ts
```

**client.ts — Axios setup with auto token refresh:**
```typescript
export const apiClient = axios.create({
    baseURL: import.meta.env.VITE_API_URL,
    withCredentials: true,            // sends HttpOnly cookie for refresh token
});

apiClient.interceptors.request.use((config) => {
    const token = useAuthStore.getState().accessToken;
    if (token) config.headers.Authorization = `Bearer ${token}`;
    return config;
});

apiClient.interceptors.response.use(
    (response) => response,
    async (error) => {
        if (error.response?.status === 401) {
            try {
                await useAuthStore.getState().refreshToken();
                return apiClient(error.config);         // retry original request
            } catch {
                useAuthStore.getState().logout();
                window.location.href = '/login';
            }
        }
        return Promise.reject(error);
    }
);
```

---

### types/

TypeScript types mirroring backend DTOs exactly.

```
types/
├── index.ts                        ← re-exports everything
├── common.ts                       ← ApiResponse, PageResponse, PaginationParams
├── auth.ts
├── user.ts
├── workspace.ts
├── project.ts
├── sprint.ts
├── issue.ts
├── comment.ts
├── label.ts
├── notification.ts
├── audit.ts
└── chart.ts
```

---

### store/

Zustand stores for client-only state.

```
store/
├── authStore.ts                    ← access token, current user
├── workspaceStore.ts               ← active workspace (persisted to localStorage)
└── uiStore.ts                      ← sidebar, modals, toasts
```

---

### hooks/

Custom hooks combining React Query + API calls. All data fetching logic lives here.

```
hooks/
├── useAuth.ts
├── useWorkspaces.ts
├── useProjects.ts
├── useSprints.ts
├── useIssues.ts
├── useIssueDetail.ts
├── useComments.ts
├── useNotifications.ts
├── useWebSocket.ts
└── useDebounce.ts
```

Each hook file exports:
- Query keys (centralised for consistent cache invalidation)
- useQuery hooks for reading data
- useMutation hooks for writing data

---

### lib/

Third-party library configuration.

```
lib/
├── queryClient.ts                  ← React Query client config
├── websocket.ts                    ← STOMP client setup + subscribe helpers
└── axios.ts                        ← re-export of configured client
```

WebSocket connects on authenticated page load, subscribes to relevant topics
(project board, personal notifications), and invalidates React Query cache on
incoming events — UI updates automatically without manual refresh.

---

### features/

Self-contained vertical slices — each owns its own components.

```
features/
├── auth/
│   ├── LoginForm.tsx
│   ├── RegisterForm.tsx
│   └── ForgotPasswordForm.tsx
├── workspace/
│   ├── WorkspaceSwitcher.tsx
│   ├── WorkspaceSettings.tsx
│   └── InviteMemberModal.tsx
├── project/
│   ├── ProjectList.tsx
│   ├── ProjectCard.tsx
│   └── CreateProjectModal.tsx
├── sprint/
│   ├── SprintHeader.tsx
│   ├── SprintBoard.tsx             ← Kanban board
│   ├── SprintBacklog.tsx
│   ├── StartSprintModal.tsx
│   └── CloseSprintModal.tsx
├── issue/
│   ├── IssueCard.tsx               ← board card (compact)
│   ├── IssueRow.tsx                ← backlog row view
│   ├── IssueDetail.tsx             ← full detail panel
│   ├── CreateIssueModal.tsx
│   ├── IssueStatusSelect.tsx
│   ├── IssuePriorityBadge.tsx
│   ├── IssueBlockedBanner.tsx
│   ├── IssueLinks.tsx
│   ├── IssueAssignees.tsx
│   └── BulkActionBar.tsx
├── comment/
│   ├── CommentList.tsx
│   ├── CommentItem.tsx
│   └── CommentEditor.tsx           ← supports @mentions
├── notification/
│   ├── NotificationBell.tsx        ← header icon + unread count
│   ├── NotificationPanel.tsx
│   └── NotificationItem.tsx
├── label/
│   ├── LabelBadge.tsx
│   ├── LabelPicker.tsx
│   └── ManageLabelsModal.tsx
├── chart/
│   ├── BurndownChart.tsx
│   ├── VelocityChart.tsx
│   └── CumulativeFlowChart.tsx
└── audit/
    └── AuditLogList.tsx
```

---

### components/

Shared UI — no business logic.

```
components/
├── ui/                             ← shadcn/ui components
│   ├── button.tsx
│   ├── input.tsx
│   ├── dialog.tsx
│   ├── dropdown-menu.tsx
│   ├── badge.tsx
│   ├── avatar.tsx
│   ├── tooltip.tsx
│   ├── select.tsx
│   └── ...
├── layout/
│   ├── AppLayout.tsx               ← sidebar + header + content area
│   ├── Sidebar.tsx
│   ├── Header.tsx
│   └── PageContainer.tsx
└── common/
    ├── LoadingSpinner.tsx
    ├── ErrorBoundary.tsx
    ├── EmptyState.tsx
    ├── ConfirmDialog.tsx           ← reusable confirmation prompt
    ├── UserAvatar.tsx
    └── DateDisplay.tsx
```

---

### pages/

Route-level components — thin orchestration only.

```
pages/
├── LoginPage.tsx
├── RegisterPage.tsx
├── WorkspacePage.tsx
├── ProjectPage.tsx                 ← sprint board + backlog
├── IssuePage.tsx                   ← direct URL to issue detail
├── NotificationsPage.tsx
├── AuditLogPage.tsx
├── NotFoundPage.tsx
└── settings/
    ├── WorkspaceSettingsPage.tsx
    ├── ProjectSettingsPage.tsx
    └── ProfileSettingsPage.tsx
```

---

### Routing

```typescript
const router = createBrowserRouter([
  {
    path: '/',
    element: <AuthGuard><AppLayout /></AuthGuard>,
    children: [
      { index: true, element: <Navigate to="/workspaces" /> },
      { path: 'workspaces', element: <WorkspacePage /> },
      { path: ':workspaceSlug', children: [
        { index: true, element: <WorkspacePage /> },
        { path: 'projects/:projectId', element: <ProjectPage /> },
        { path: 'projects/:projectId/issues/:issueId', element: <IssuePage /> },
        { path: 'notifications', element: <NotificationsPage /> },
        { path: 'audit', element: <AuditLogPage /> },
        { path: 'settings', element: <SettingsPage /> },
      ]},
    ],
  },
  { path: '/login',          element: <LoginPage /> },
  { path: '/register',       element: <RegisterPage /> },
  { path: '/invite',         element: <AcceptInvitePage /> },
  { path: '/reset-password', element: <ResetPasswordPage /> },
  { path: '*',               element: <NotFoundPage /> },
]);
```

AuthGuard redirects unauthenticated users to /login before any protected route renders.

---

### Forms — React Hook Form + Zod

Zod defines validation schema. React Hook Form handles form state. No manual validation logic needed.

```typescript
const createIssueSchema = z.object({
  title:    z.string().min(1, 'Title is required').max(255),
  priority: z.enum(['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']),
  typeId:   z.string().uuid(),
  dueDate:  z.string().optional(),
});

type CreateIssueFormData = z.infer<typeof createIssueSchema>;

const { register, handleSubmit, formState: { errors } } =
  useForm<CreateIssueFormData>({ resolver: zodResolver(createIssueSchema) });
```

---

### App.tsx — Provider Stack

```typescript
const App = () => (
  <QueryClientProvider client={queryClient}>
    <RouterProvider router={router} />
  </QueryClientProvider>
);
```

One provider. No Redux, no Context nesting.

---

### Environment Variables

```bash
# .env.development
VITE_API_URL=http://localhost:8080/api/v1

# .env.production
VITE_API_URL=https://api.simplira.com/api/v1
```

---

### Key Dependencies

```json
{
  "dependencies": {
    "react": "^18.3.0",
    "react-dom": "^18.3.0",
    "react-router-dom": "^6.23.0",
    "@tanstack/react-query": "^5.40.0",
    "zustand": "^4.5.0",
    "axios": "^1.7.0",
    "@stomp/stompjs": "^7.0.0",
    "sockjs-client": "^1.6.1",
    "react-hook-form": "^7.51.0",
    "@hookform/resolvers": "^3.4.0",
    "zod": "^3.23.0",
    "recharts": "^2.12.0",
    "lucide-react": "^0.383.0",
    "date-fns": "^3.6.0",
    "tailwindcss": "^3.4.0",
    "clsx": "^2.1.0",
    "tailwind-merge": "^2.3.0"
  },
  "devDependencies": {
    "typescript": "^5.4.0",
    "vite": "^5.2.0",
    "@vitejs/plugin-react": "^4.3.0",
    "@types/react": "^18.3.0",
    "@types/react-dom": "^18.3.0"
  }
}
```

---

## 12. Android Companion App

### Overview

The Android app is a mobile client for Simplira — same backend API, different surface.
It is a separate project built after the web app is stable, consuming the existing REST
and WebSocket API with no additional backend work required.

Mobile is not a feature-complete mirror of the web app. The focus is on what developers
actually need on the go, not full project management.

### Feature Scope

**Included in mobile:**

| Feature | Notes |
|---|---|
| View assigned issues | Filtered to current user |
| Update issue status | TODO → IN_PROGRESS → DONE |
| Set/clear blocked flag | With optional reason |
| Add comments | Including @mention support |
| View issue detail | Full detail, read-heavy |
| View notifications | Real-time bell, mark as read |
| Switch workspaces | Seamless switcher |
| View sprint board | Read-only board view |

**Web only (excluded from mobile):**

| Feature | Reason |
|---|---|
| Sprint management | Create/start/close sprints |
| Project settings | Configuration, visibility |
| Issue type configuration | Admin-level setup |
| Charts and reporting | Poor mobile UX |
| Bulk operations | Requires multi-select UI |
| Admin functions | User/workspace management |
| Audit log | Too data-heavy for mobile |

### Tech Stack

| Component | Technology |
|---|---|
| Language | Kotlin |
| UI Framework | Jetpack Compose |
| HTTP Client | Retrofit + OkHttp |
| WebSocket | OkHttp WebSocket client |
| Local Cache | Room (offline reads) |
| DI | Hilt |
| Navigation | Jetpack Navigation Compose |
| State Management | ViewModel + StateFlow |
| Image Loading | Coil |
| Auth Token Storage | EncryptedSharedPreferences |

### Project Structure

```
simplira-android/
├── app/
│   └── src/main/java/com/simplira/android/
│       ├── SimpliraApplication.kt
│       ├── data/
│       │   ├── api/
│       │   │   ├── SimpliraApi.kt          ← Retrofit interface
│       │   │   ├── dto/                    ← API response models
│       │   │   └── interceptor/
│       │   │       ├── AuthInterceptor.kt  ← attaches JWT to requests
│       │   │       └── TokenRefreshInterceptor.kt
│       │   ├── local/
│       │   │   ├── SimpliraDatabase.kt     ← Room database
│       │   │   ├── dao/                    ← Room DAOs
│       │   │   └── entity/                 ← Room entities
│       │   ├── websocket/
│       │   │   ├── SimpliraWebSocketClient.kt
│       │   │   └── WebSocketEvent.kt
│       │   └── repository/
│       │       ├── IssueRepository.kt
│       │       ├── NotificationRepository.kt
│       │       └── AuthRepository.kt
│       ├── domain/
│       │   ├── model/                      ← domain models (no framework deps)
│       │   └── usecase/
│       │       ├── GetMyIssuesUseCase.kt
│       │       ├── UpdateIssueStatusUseCase.kt
│       │       └── AddCommentUseCase.kt
│       ├── ui/
│       │   ├── theme/
│       │   │   ├── Color.kt
│       │   │   ├── Typography.kt
│       │   │   └── Theme.kt
│       │   ├── navigation/
│       │   │   └── SimpliraNavGraph.kt
│       │   ├── screen/
│       │   │   ├── login/
│       │   │   │   ├── LoginScreen.kt
│       │   │   │   └── LoginViewModel.kt
│       │   │   ├── issues/
│       │   │   │   ├── IssueListScreen.kt
│       │   │   │   ├── IssueListViewModel.kt
│       │   │   │   ├── IssueDetailScreen.kt
│       │   │   │   └── IssueDetailViewModel.kt
│       │   │   ├── board/
│       │   │   │   ├── BoardScreen.kt
│       │   │   │   └── BoardViewModel.kt
│       │   │   ├── notifications/
│       │   │   │   ├── NotificationScreen.kt
│       │   │   │   └── NotificationViewModel.kt
│       │   │   └── workspace/
│       │   │       ├── WorkspaceSwitcherScreen.kt
│       │   │       └── WorkspaceSwitcherViewModel.kt
│       │   └── component/
│       │       ├── IssueCard.kt
│       │       ├── StatusChip.kt
│       │       ├── PriorityBadge.kt
│       │       ├── CommentItem.kt
│       │       └── NotificationItem.kt
│       └── di/
│           ├── NetworkModule.kt
│           ├── DatabaseModule.kt
│           └── RepositoryModule.kt
├── build.gradle.kts
└── gradle/
```

### Authentication Flow

- Access token stored in memory (ViewModel scope)
- Refresh token stored in **EncryptedSharedPreferences** (Android secure storage)
- `TokenRefreshInterceptor` automatically refreshes token on 401 response
- On app restart, refresh token used to obtain new access token silently

### Offline Strategy

Room caches issues and notifications locally. On app open:

1. Show cached data immediately (fast perceived load)
2. Fetch fresh data from API in background
3. Update UI when fresh data arrives

Write operations (status update, comment) require network — show error if offline.

### Real-Time on Android

OkHttp WebSocket client connects on app foreground, disconnects on background.
Notifications delivered via WebSocket update Room cache and trigger UI recomposition
via StateFlow. Firebase Cloud Messaging (FCM) handles push notifications when app
is backgrounded.

### Key Dependencies (build.gradle.kts)

```kotlin
dependencies {
    // Jetpack Compose
    implementation(platform("androidx.compose:compose-bom:2024.05.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.0")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")

    // Hilt DI
    implementation("com.google.dagger:hilt-android:2.51")
    kapt("com.google.dagger:hilt-compiler:2.51")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Retrofit + OkHttp
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    // Coil (image loading)
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Encrypted storage
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")
}
```

### Screens Overview

| Screen | Purpose |
|---|---|
| Login | Email/password auth, token storage |
| Workspace Switcher | List and switch between workspaces |
| Issue List | My assigned issues, filterable by status |
| Sprint Board | Kanban-style columns, read-only on mobile |
| Issue Detail | Full issue view, status update, comments |
| Notifications | Notification feed, mark as read |

---

---

## 13. Testing Strategy

As a solo dev time is limited. The question is not "test everything" but "what gives the most confidence per hour spent."

### Testing Pyramid — Pragmatic Version

```
        E2E Tests
       (very few)
      Integration Tests
      (selective, high value)
    Unit Tests
    (domain layer focus)
```

### What to Test

**Domain service unit tests — highest priority**

Pure Java, no Spring context, fast to run. All business rules live here — status transitions, sprint activation checks, blocked flag management, permission resolution.

```java
class IssueServiceTest {

    private IssueRepository issueRepository = mock(IssueRepository.class);
    private AuditLogService auditLogService = mock(AuditLogService.class);
    private NotificationService notificationService = mock(NotificationService.class);
    private IssueService issueService;

    @BeforeEach
    void setUp() {
        issueService = new IssueService(issueRepository, auditLogService, notificationService);
    }

    @Test
    void shouldSetClosedByWhenStatusChangedToDone() {
        UUID issueId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        Issue issue = IssueTestFactory.createIssue(issueId, IssueStatus.IN_PROGRESS);
        when(issueRepository.findById(issueId)).thenReturn(Optional.of(issue));
        when(issueRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Issue result = issueService.updateStatus(issueId, IssueStatus.DONE, actorId);

        assertThat(result.getClosedBy()).isEqualTo(actorId);
        assertThat(result.getClosedAt()).isNotNull();
    }

    @Test
    void shouldThrowWhenActivatingSprintAndOneAlreadyActive() {
        when(sprintRepository.existsByProjectIdAndStatus(projectId, SprintStatus.ACTIVE))
            .thenReturn(true);

        assertThatThrownBy(() -> sprintService.startSprint(sprintId, actorId))
            .isInstanceOf(SprintAlreadyActiveException.class);
    }
}
```

**Repository adapter tests**

Verify soft delete filtering and domain/JPA mapping:

```java
@DataJpaTest
class IssueRepositoryAdapterTest {

    @Test
    void shouldNotReturnSoftDeletedIssues() {
        IssueEntity deleted = createIssueEntity();
        deleted.setDeletedAt(LocalDateTime.now());
        jpaRepository.save(deleted);

        List<Issue> result = adapter.findByProjectId(deleted.getProjectId());

        assertThat(result).isEmpty();
    }
}
```

**API integration tests — permission and critical flows**

```java
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class IssueControllerIntegrationTest {

    @Test
    void shouldReturn403WhenDeveloperTriesToStartSprint() throws Exception {
        String token = authenticateAs(Role.DEVELOPER);

        mockMvc.perform(post("/api/v1/sprints/{id}/start", sprintId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }
}
```

### What to Skip or Defer

| Skip | Reason |
|---|---|
| Controller unit tests | Covered by integration tests |
| Spring Data repository unit tests | Framework generates implementation |
| Frontend unit tests early on | Get the product working first |
| WebSocket tests | Complex setup, low return early on |

### Test Tooling

| Tool | Purpose |
|---|---|
| JUnit 5 | Test runner |
| Mockito | Mocking dependencies |
| AssertJ | Fluent assertions |
| @DataJpaTest | Repository tests with in-memory DB |
| TestContainers | Integration tests with real PostgreSQL |
| @SpringBootTest | Full application context tests |
| MockMvc | HTTP layer testing without a running server |

**TestContainers setup:**

```java
@SpringBootTest
@Testcontainers
class IntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("simplira_test")
            .withUsername("simplira")
            .withPassword("simplira");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
}
```

### Testing Priority Order

1. Domain service unit tests — start here, highest value
2. Permission and authorization integration tests — security bugs are worst to find late
3. Repository adapter tests — soft delete, basic CRUD correctness
4. Critical API endpoint tests — auth flow, sprint lifecycle, issue creation
5. Frontend tests — defer until core product is stable

---

## 14. CI/CD and Deployment

### CI Pipeline — GitHub Actions

```yaml
# .github/workflows/ci.yml
name: CI

on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main]

jobs:
  backend:
    runs-on: ubuntu-latest
    services:
      postgres:
        image: postgres:16
        env:
          POSTGRES_DB: simplira_test
          POSTGRES_USER: simplira
          POSTGRES_PASSWORD: simplira
        ports:
          - 5432:5432
    steps:
      - uses: actions/checkout@v4
      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
      - name: Cache Maven
        uses: actions/cache@v4
        with:
          path: ~/.m2
          key: ${{ runner.os }}-maven-${{ hashFiles('**/pom.xml') }}
      - name: Run tests
        run: mvn test
        env:
          DB_URL: jdbc:postgresql://localhost:5432/simplira_test
          DB_USERNAME: simplira
          DB_PASSWORD: simplira
      - name: Build
        run: mvn package -DskipTests

  frontend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Set up Node
        uses: actions/setup-node@v4
        with:
          node-version: '20'
          cache: 'npm'
      - name: Install dependencies
        run: npm ci
        working-directory: simplira-frontend
      - name: Type check
        run: npm run type-check
        working-directory: simplira-frontend
      - name: Build
        run: npm run build
        working-directory: simplira-frontend
```

### Deployment Options

| Option | Cost | Effort | Best For |
|---|---|---|---|
| Railway | ~$5/month | Very low | Getting deployed fast |
| Render | Free tier + paid | Low | Frontend static hosting |
| Hetzner VPS | ~€5/month | High | DevOps experience, CV |

**Recommendation:** Start with Railway. Move to a VPS when you want the DevOps experience on your CV.

### Dockerfile — Backend

```dockerfile
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/simplira-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Dockerfile — Frontend

```dockerfile
FROM node:20-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY ../../../../Downloads .
RUN npm run build

FROM nginx:alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
```

**nginx.conf — required for React Router:**

```nginx
server {
    listen 80;

    location / {
        root /usr/share/nginx/html;
        index index.html;
        try_files $uri $uri/ /index.html;   # client-side routing support
    }

    location /api {
        proxy_pass http://backend:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

### docker-compose.prod.yml

```yaml
services:
  backend:
    image: simplira-backend:latest
    environment:
      DB_URL: jdbc:postgresql://postgres:5432/simplira
      DB_USERNAME: ${DB_USERNAME}
      DB_PASSWORD: ${DB_PASSWORD}
      JWT_SECRET: ${JWT_SECRET}
      RABBITMQ_HOST: rabbitmq
    depends_on: [postgres, rabbitmq]
    ports: ["8080:8080"]

  frontend:
    image: simplira-frontend:latest
    ports: ["80:80"]
    depends_on: [backend]

  postgres:
    image: postgres:16
    environment:
      POSTGRES_DB: simplira
      POSTGRES_USER: ${DB_USERNAME}
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    volumes:
      - postgres_data:/var/lib/postgresql/data

  rabbitmq:
    image: rabbitmq:3-management
    command: >
      bash -c "rabbitmq-plugins enable rabbitmq_stomp && rabbitmq-server"
    environment:
      RABBITMQ_DEFAULT_USER: ${RABBITMQ_USERNAME}
      RABBITMQ_DEFAULT_PASS: ${RABBITMQ_PASSWORD}
    volumes:
      - rabbitmq_data:/var/lib/rabbitmq

volumes:
  postgres_data:
  rabbitmq_data:
```

### CD Pipeline — Deploy on Merge to Main

```yaml
# .github/workflows/deploy.yml
name: Deploy

on:
  push:
    branches: [main]

jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Build Docker images
        run: |
          docker build -t simplira-backend ./simplira-backend
          docker build -t simplira-frontend ./simplira-frontend
      - name: Deploy to server
        uses: appleboy/ssh-action@v1
        with:
          host: ${{ secrets.SERVER_HOST }}
          username: ${{ secrets.SERVER_USER }}
          key: ${{ secrets.SSH_PRIVATE_KEY }}
          script: |
            cd /opt/simplira
            docker compose pull
            docker compose up -d
```

---

## 15. Error Handling Strategy

### Error Categories

| Category | HTTP Status | Example |
|---|---|---|
| Not found | 404 | Issue doesn't exist |
| Forbidden | 403 | Developer trying to start sprint |
| Unauthorized | 401 | No or invalid token |
| Validation | 400 | Missing required field |
| Business rule | 422 | Sprint already active |
| Conflict | 409 | Duplicate workspace slug |
| Server error | 500 | Unexpected exception |

> 400 vs 422: 400 means malformed request. 422 means valid request that violates a business rule. Different problems, different client handling.

### Exception Hierarchy

```java
public abstract class SimpliraException extends RuntimeException {
    private final String errorCode;
    private final HttpStatus httpStatus;

    protected SimpliraException(String errorCode, String message, HttpStatus httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }
}

public abstract class ResourceNotFoundException extends SimpliraException {
    protected ResourceNotFoundException(String errorCode, String message) {
        super(errorCode, message, HttpStatus.NOT_FOUND);
    }
}

public class ForbiddenException extends SimpliraException {
    public ForbiddenException(String action) {
        super("FORBIDDEN", "You don't have permission to: " + action, HttpStatus.FORBIDDEN);
    }
}

public abstract class BusinessRuleException extends SimpliraException {
    protected BusinessRuleException(String errorCode, String message) {
        super(errorCode, message, HttpStatus.UNPROCESSABLE_ENTITY);
    }
}

public abstract class ConflictException extends SimpliraException {
    protected ConflictException(String errorCode, String message) {
        super(errorCode, message, HttpStatus.CONFLICT);
    }
}
```

Concrete exceptions:

```java
public class IssueNotFoundException extends ResourceNotFoundException {
    public IssueNotFoundException(UUID id) {
        super("ISSUE_NOT_FOUND", "Issue not found: " + id);
    }
}

public class SprintAlreadyActiveException extends BusinessRuleException {
    public SprintAlreadyActiveException(UUID projectId) {
        super("SPRINT_ALREADY_ACTIVE", "Project already has an active sprint: " + projectId);
    }
}

public class WorkspaceSlugConflictException extends ConflictException {
    public WorkspaceSlugConflictException(String slug) {
        super("WORKSPACE_SLUG_TAKEN", "Workspace slug already taken: " + slug);
    }
}
```

### GlobalExceptionHandler

```java
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(SimpliraException.class)
    public ResponseEntity<ApiResponse<?>> handleSimpliraException(SimpliraException ex) {
        return ResponseEntity
                .status(ex.getHttpStatus())
                .body(ApiResponse.error(new ErrorResponse(ex.getErrorCode(), ex.getMessage())));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<?>> handleValidation(MethodArgumentNotValidException ex) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .toList();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(new ErrorResponse("VALIDATION_ERROR", "Invalid request", details)));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<?>> handleUnauthorized(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(new ErrorResponse("UNAUTHORIZED", "Authentication required")));
    }

    // Catch-all — never expose internal details to client
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<?>> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);      // full stack trace logged internally
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred")));
    }
}
```

### Error Propagation Rule

```
Controller   → catches nothing, lets exceptions propagate up
Service      → throws domain exceptions, catches nothing else
Repository   → lets JPA exceptions propagate
GlobalHandler → catches everything, converts to ApiResponse
```

Service layer stays clean:

```java
public Issue updateStatus(UUID issueId, IssueStatus newStatus, UUID actorId) {
    Issue issue = issueRepository.findById(issueId)
        .orElseThrow(() -> new IssueNotFoundException(issueId));

    validateStatusTransition(issue.getStatus(), newStatus);

    issue.setStatus(newStatus);
    return issueRepository.save(issue);
    // No try-catch needed — GlobalExceptionHandler handles everything
}
```

### Frontend Error Handling

```typescript
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: (failureCount, error: any) => {
        // Never retry 4xx — they won't fix themselves
        if (error?.response?.status >= 400 && error?.response?.status < 500) return false;
        return failureCount < 2;
      },
    },
    mutations: {
      onError: (error: any) => {
        const code    = error?.response?.data?.error?.code;
        const message = error?.response?.data?.error?.message;
        toast.error(message ?? 'Something went wrong');
        if (code === 'UNAUTHORIZED') {
          useAuthStore.getState().logout();
          window.location.href = '/login';
        }
      },
    },
  },
});
```

---

## 16. Naming Conventions

### Database

```
Tables          snake_case, plural:       issue, issue_assignee, audit_log
Columns         snake_case:               created_at, is_blocked, sprint_id
Foreign keys    {table}_id:               sprint_id, reporter_id
Enum values     UPPER_SNAKE_CASE:         IN_PROGRESS, TEAM_LEADER
Indexes         idx_{table}_{column}:     idx_issue_project_id
Constraints     chk_{table}_{rule}:       chk_issue_link_no_self_link
```

### Java / Spring Boot

```
Packages        lowercase:                com.simplira.domain.service
Classes         PascalCase:               IssueService, SprintRepository
Interfaces      no I prefix:              IssueRepository (not IIssueRepository)
Methods         camelCase, verb first:    findById, updateStatus, createIssue
Constants       UPPER_SNAKE_CASE:         MAX_SPRINT_DURATION_DAYS
Enums           PascalCase + UPPER vals:  IssueStatus.IN_PROGRESS
Requests        {Action}{Entity}Request:  CreateIssueRequest, UpdateSprintRequest
Responses       {Entity}Response:         IssueResponse, WorkspaceResponse
Exceptions      {Reason}Exception:        IssueNotFoundException
```

### REST API

```
Resources       kebab-case, plural:       /issue-types, /notification-preferences
Path params     camelCase:                {issueId}, {workspaceId}
Query params    camelCase:                ?sortBy=createdAt&pageSize=20
Actions         verb sub-resource:        /sprints/{id}/start, /issues/{id}/block
```

### TypeScript / React

```
Files/Components  PascalCase:             IssueCard.tsx, SprintBoard.tsx
Hooks             camelCase, use prefix:  useIssues, useWebSocket
Stores            camelCase, Store:       authStore, workspaceStore
Types             PascalCase:             Issue, CreateIssueRequest
API files         camelCase, Api:         issuesApi, sprintsApi
Query keys        camelCase, Keys:        issueKeys, sprintKeys
Constants         UPPER_SNAKE_CASE:       MAX_FILE_SIZE_BYTES
```

### Git

```
Branches    kebab-case with prefix:
              feature/issue-board
              fix/sprint-close-bug
              chore/update-dependencies

Commits     conventional commits:
              feat: add bulk assign to issues
              fix: clear blocked flag when link removed
              chore: update Spring Boot to 3.3.0
              docs: update API documentation
              refactor: extract position service
```

---

## 17. Security Checklist

### Backend

**CORS configuration:**

```java
@Bean
public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(List.of(
        "http://localhost:5173",
        "https://simplira.com"
    ));
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
    config.setAllowCredentials(true);           // required for HttpOnly cookie

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", config);
    return source;
}
```

**Rate limiting on auth endpoints** — prevents brute force. Use Bucket4j:

```java
// 10 requests per minute per IP on auth endpoints
Bucket bucket = Bucket.builder()
    .addLimit(Bandwidth.classic(10, Refill.intervally(10, Duration.ofMinutes(1))))
    .build();
```

**Input validation** — always at controller boundary:

```java
public record CreateIssueRequest(
    @NotBlank @Size(max = 255) String title,
    @Size(max = 10000) String description,
    @NotNull IssuePriority priority,
    @NotNull UUID typeId,
    @Future LocalDate dueDate
) {}
```

**File upload validation** — use Apache Tika, not Content-Type header (can be spoofed):

```java
public void validateAttachment(MultipartFile file) {
    if (file.getSize() > 10 * 1024 * 1024) {
        throw new ValidationException("FILE_TOO_LARGE", "Maximum file size is 10MB");
    }
    String mimeType = tika.detect(file.getInputStream());
    if (!mimeType.startsWith("image/")) {
        throw new ValidationException("INVALID_FILE_TYPE", "Only images are allowed");
    }
}
```

**Never log sensitive data:**

```java
// Never
log.info("Login attempt with password: {}", password);

// Safe
log.info("Login attempt for user: {}", userId);
```

### Frontend

**Never store access token in localStorage** — XSS vulnerability. Use memory only.

**Sanitize user-generated content** — comments render user input, use DOMPurify:

```typescript
import DOMPurify from 'dompurify';

const SafeComment = ({ content }: { content: string }) => (
  <div dangerouslySetInnerHTML={{ __html: DOMPurify.sanitize(content) }} />
);
```

**Never commit secrets** — add to .gitignore:

```bash
.env.local
.env.production
*.env
```

**Content Security Policy** — set in nginx:

```nginx
add_header Content-Security-Policy
  "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'";
```

### Security Checklist Summary

| Item | Layer | Priority |
|---|---|---|
| CORS configured for production domain | Backend | High |
| Rate limiting on /auth/* endpoints | Backend | High |
| Input validation on all request bodies | Backend | High |
| File type validated with Tika | Backend | High |
| No sensitive data in logs | Backend | High |
| Access token in memory only | Frontend | High |
| Refresh token in HttpOnly cookie | Frontend | High |
| User content sanitized with DOMPurify | Frontend | High |
| Secrets not committed to Git | Both | High |
| CSP headers configured | Frontend/nginx | Medium |
| HTTPS enforced in production | Infrastructure | High |

---

---

## 18. Logging Strategy

### Philosophy

Logs are your eyes in production. The goal is logs that are searchable, consistent, actionable, and safe — never exposing sensitive data.

### Structured Logging Setup

```xml
<!-- pom.xml -->
<dependency>
    <groupId>net.logstash.logback</groupId>
    <artifactId>logstash-logback-encoder</artifactId>
    <version>7.4</version>
</dependency>
```

**logback-spring.xml:**

```xml
<configuration>
    <springProfile name="dev">
        <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
            <encoder>
                <pattern>%d{HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n</pattern>
            </encoder>
        </appender>
        <root level="DEBUG"><appender-ref ref="CONSOLE"/></root>
    </springProfile>

    <springProfile name="prod">
        <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
            <encoder class="net.logstash.logback.encoder.LogstashEncoder"/>
        </appender>
        <root level="INFO"><appender-ref ref="CONSOLE"/></root>
    </springProfile>
</configuration>
```

Production JSON output:

```json
{
  "timestamp": "2026-05-17T10:30:00.000Z",
  "level": "INFO",
  "logger": "com.simplira.domain.service.IssueService",
  "message": "Issue status updated",
  "traceId": "abc-123",
  "issueId": "uuid",
  "oldStatus": "TODO",
  "newStatus": "IN_PROGRESS"
}
```

### Log Levels

| Level | Use For |
|---|---|
| ERROR | Something broke, needs immediate attention |
| WARN | Unexpected but recovered — retry succeeded, token refresh needed |
| INFO | Meaningful business events — sprint started, issue closed |
| DEBUG | Development detail, disabled in production |
| TRACE | Very detailed, almost never used |

### Request Correlation — Trace ID

Ties all logs from a single request together. Without this, concurrent request logs interleave and become unreadable.

```java
@Component
public class TraceIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws IOException, ServletException {
        String traceId = Optional
            .ofNullable(request.getHeader("X-Trace-Id"))
            .orElse(UUID.randomUUID().toString());

        MDC.put("traceId", traceId);
        response.setHeader("X-Trace-Id", traceId);

        try {
            chain.doFilter(request, response);
        } finally {
            MDC.clear();        // critical — prevents MDC leaking between requests
        }
    }
}
```

Every log line automatically includes traceId. If a user reports a bug and gives you the trace ID from their browser network tab, you can find every log line for that exact request instantly.

### Service Layer Logging Pattern

```java
@Slf4j
public class IssueService {

    public Issue updateStatus(UUID issueId, IssueStatus newStatus, UUID actorId) {
        log.debug("Updating issue status: issueId={}, newStatus={}", issueId, newStatus);

        Issue issue = issueRepository.findById(issueId)
            .orElseThrow(() -> {
                log.warn("Issue not found: issueId={}", issueId);
                return new IssueNotFoundException(issueId);
            });

        IssueStatus oldStatus = issue.getStatus();
        Issue saved = issueRepository.save(issue);

        log.info("Issue status updated: issueId={}, oldStatus={}, newStatus={}, actorId={}",
            issueId, oldStatus, newStatus, actorId);

        return saved;
    }
}
```

Never ERROR log expected domain exceptions (404, 403) — those are normal flow. Only ERROR for unexpected exceptions in the catch-all handler.

### What Never to Log

```java
// NEVER — passwords, tokens, personal data
log.info("Login: email={}, password={}", email, password);
log.debug("Token: {}", accessToken);

// SAFE
log.info("Login successful: userId={}", userId);
log.info("Token issued for userId={}", userId);
```

---

## 19. Database Indexes

### Index Strategy

Define an index anywhere you filter, join, sort, or enforce uniqueness on a column.

### All Indexes for Simplira

```sql
-- UserWorkspace
CREATE INDEX idx_user_workspace_user_id ON user_workspace(user_id);
CREATE INDEX idx_user_workspace_workspace_id ON user_workspace(workspace_id);

-- Project
CREATE INDEX idx_project_workspace_id ON project(workspace_id);

-- ProjectMember
CREATE INDEX idx_project_member_project_id ON project_member(project_id);
CREATE INDEX idx_project_member_user_id ON project_member(user_id);

-- Sprint
CREATE INDEX idx_sprint_project_id ON sprint(project_id);
CREATE INDEX idx_sprint_project_status ON sprint(project_id, status);

-- Issue (most queried table)
CREATE INDEX idx_issue_project_id ON issue(project_id);
CREATE INDEX idx_issue_sprint_id ON issue(sprint_id);
CREATE INDEX idx_issue_parent_id ON issue(parent_id);
CREATE INDEX idx_issue_reporter_id ON issue(reporter_id);
CREATE INDEX idx_issue_status ON issue(status);
CREATE INDEX idx_issue_deleted_at ON issue(deleted_at);
CREATE INDEX idx_issue_backlog ON issue(project_id, sprint_id, deleted_at);

-- IssueAssignee
CREATE INDEX idx_issue_assignee_issue_id ON issue_assignee(issue_id);
CREATE INDEX idx_issue_assignee_user_id ON issue_assignee(user_id);

-- IssueLink
CREATE INDEX idx_issue_link_source ON issue_link(source_issue_id);
CREATE INDEX idx_issue_link_target ON issue_link(target_issue_id);

-- Comment
CREATE INDEX idx_comment_issue_id ON comment(issue_id);
CREATE INDEX idx_comment_deleted_at ON comment(deleted_at);

-- IssueLabel
CREATE INDEX idx_issue_label_issue_id ON issue_label(issue_id);
CREATE INDEX idx_issue_label_label_id ON issue_label(label_id);

-- Attachment
CREATE INDEX idx_attachment_issue_id ON attachment(issue_id);

-- Notification
CREATE INDEX idx_notification_recipient_id ON notification(recipient_id);
CREATE INDEX idx_notification_recipient_read ON notification(recipient_id, is_read);
CREATE INDEX idx_notification_expires_at ON notification(expires_at);

-- AuditLog
CREATE INDEX idx_audit_log_workspace_id ON audit_log(workspace_id);
CREATE INDEX idx_audit_log_issue_id ON audit_log(issue_id);
CREATE INDEX idx_audit_log_actor_id ON audit_log(actor_id);

-- RefreshToken
CREATE INDEX idx_refresh_token_user_id ON refresh_token(user_id);

-- Invitation
CREATE INDEX idx_invitation_workspace_id ON invitation(workspace_id);
CREATE INDEX idx_invitation_email ON invitation(email);
```

Add to Flyway:

```sql
-- V17__add_indexes.sql
-- All indexes above
```

---

## 20. Flyway Migration Conventions

### Golden Rules

- **Never edit a migration that has already run** — Flyway checksums every file. Editing causes startup failure.
- **Migrations are forever** — treat like Git commits. Once in a shared environment, permanent.
- **One concern per migration** — easier to reason about, easier to roll back mentally.
- **Test locally before pushing** — run migration, verify schema, test the app.

### Naming

```
V{version}__{description}.sql    ← double underscore required

V1__create_users.sql
V17__add_indexes.sql
V18__add_soft_delete_to_workspace.sql
```

### Safe Migration Patterns

**Adding nullable column — safe:**

```sql
ALTER TABLE issue ADD COLUMN due_date DATE;
```

**Adding NOT NULL column — two steps:**

```sql
-- Wrong — fails if table has existing rows
ALTER TABLE issue ADD COLUMN priority VARCHAR NOT NULL;

-- Right
ALTER TABLE issue ADD COLUMN priority VARCHAR;
UPDATE issue SET priority = 'MEDIUM' WHERE priority IS NULL;
ALTER TABLE issue ALTER COLUMN priority SET NOT NULL;
```

**Renaming a column — two migrations, two deployments:**

```sql
-- V20: add new column, copy data
ALTER TABLE issue ADD COLUMN issue_type_id UUID;
UPDATE issue SET issue_type_id = type_id;

-- V21 (after code deployed without old column): drop old
ALTER TABLE issue DROP COLUMN type_id;
```

**Dropping a column — remove code references first, drop second:**

```sql
-- Only after all references removed from codebase
ALTER TABLE issue DROP COLUMN legacy_field;
```

---

## 21. Soft Delete and Unique Constraints — Bug Fix

### The Problem

The current schema has a real bug. Workspace slug has a UNIQUE constraint. If someone creates workspace "acme-tech", deletes it, then tries to create "acme-tech" again — the deleted row still holds the unique constraint. Creation fails even though the workspace no longer exists.

Affected tables and columns:
- `workspace.slug`
- `label` — unique on `(project_id, name)`
- `issue_type` — unique on `(project_id, name)`

### The Fix — Partial Unique Indexes

PostgreSQL supports partial indexes — uniqueness enforced only on rows matching a condition:

```sql
-- V18__fix_soft_delete_unique_constraints.sql

-- Add soft delete to workspace and issue_type (missing from original schema)
ALTER TABLE workspace ADD COLUMN deleted_at TIMESTAMPTZ;
ALTER TABLE issue_type ADD COLUMN deleted_at TIMESTAMPTZ;
ALTER TABLE issue_type ADD COLUMN deleted_by UUID REFERENCES users(id);

-- Replace unique constraints with partial indexes

-- Workspace slug
ALTER TABLE workspace DROP CONSTRAINT workspace_slug_key;
CREATE UNIQUE INDEX idx_workspace_slug_active
    ON workspace(slug)
    WHERE deleted_at IS NULL;

-- Label name per project
DROP INDEX label_project_id_name_key;
CREATE UNIQUE INDEX idx_label_name_active
    ON label(project_id, name)
    WHERE deleted_at IS NULL;

-- IssueType name per project
DROP INDEX issue_type_project_id_name_key;
CREATE UNIQUE INDEX idx_issue_type_name_active
    ON issue_type(project_id, name)
    WHERE deleted_at IS NULL;
```

Now deleted records are excluded from uniqueness checks. A slug or name previously used by a deleted entity can be reused freely.

### Updated Soft Delete Inventory

| Table | Has Soft Delete |
|---|---|
| Issue | ✅ deleted_at |
| Comment | ✅ deleted_at |
| Attachment | ✅ deleted_at + deleted_by |
| Workspace | ✅ deleted_at (added above) |
| IssueType | ✅ deleted_at + deleted_by (added above) |
| Label | ✅ deleted_at (partial index fix above) |
| Project | ⬜ not needed — deleting cascades |
| Sprint | ⬜ not needed |

---

## 22. API Versioning Strategy

### When Does v2 Happen?

**Breaking changes → require v2:**
- Removing a field from a response
- Renaming a field
- Changing a field type
- Removing an endpoint
- Changing a field from optional to required

**Non-breaking → extend v1:**
- Adding a new optional field to a response
- Adding a new optional request field
- Adding a new endpoint
- Adding a new enum value (be careful — old clients may not handle unknown values)

### Practical Rule

You control both backend and frontend, so breaking changes just mean updating both simultaneously. v2 only matters when:

- The Android app is in use by real users
- You open a public API to third parties

Until then — everything in v1. When v2 is needed, v1 stays alive for a transition period (typically 6-12 months in real products).

---

## 23. Pagination Strategy

### Current Approach — Offset Pagination

```
GET /api/v1/projects/{id}/issues?page=1&pageSize=20&sortBy=sprintPosition&sortDir=asc
```

Translates to SQL:

```sql
SELECT * FROM issue ORDER BY sprint_position ASC LIMIT 20 OFFSET 0;
```

**Pros:** Simple to implement, easy to understand, fine for small datasets.

**Cons at scale:**
- Performance — database reads and discards all rows before the offset. OFFSET 10000 reads 10020 rows to return 20.
- Data drift — new items added between page requests cause duplicates or skipped items.

### Future Upgrade — Cursor Pagination

```
GET /api/v1/projects/{id}/issues?cursor=eyJpZCI6InV1aWQifQ==&pageSize=20
```

Cursor is base64-encoded position marker pointing to the last seen item. No drift, consistent performance regardless of dataset size.

**Decision:** Offset pagination is the right call for Simplira now. Workspace datasets will be small for a long time. Document cursor pagination as the upgrade path if performance becomes an issue. The API contract (`page` + `pageSize` params) can be extended with cursor support without breaking existing clients.

---

---

## 24. Issue Identifier System

### Schema Changes

**Project — add key:**

```sql
Project
+ key    VARCHAR(10), NOT NULL    ← e.g. "SIMP", "AUTH", "BACK"

UNIQUE (workspace_id, key)        ← unique within a workspace
```

Key rules: uppercase letters only, 2-10 characters, unique per workspace.
Generated from project name by default ("Simplira Backend" → "SB"), user can override at creation.

**Issue — add sequential number:**

```sql
Issue
+ number    INTEGER, NOT NULL

UNIQUE (project_id, number)
```

Full identifier assembled at query time: `{project.key}-{issue.number}` → SIMP-42.
Never stored as a string — derived from two existing columns.

**Number generation in service layer:**

```java
@Query("SELECT COALESCE(MAX(i.number), 0) + 1 FROM Issue i WHERE i.projectId = :projectId")
int getNextNumberForProject(@Param("projectId") UUID projectId);
```

**Updated IssueResponse:**

```json
{
  "id": "uuid",
  "number": 42,
  "identifier": "SIMP-42",
  "title": "Fix login button alignment"
}
```

`identifier` computed on backend — frontend never constructs it manually.

**New API endpoint — resolve by identifier:**

```
GET /api/v1/workspaces/{workspaceId}/issues/{identifier}    👤  e.g. /issues/SIMP-42
```

Useful for sharing links. Resolves identifier to UUID internally.

---

## 25. Email Service

### Choice

**Mailgun** — straightforward setup, 1000 emails/month free, EU region available (GDPR), excellent deliverability. Best starting choice.

### Interface

```java
public interface EmailService {
    void sendInvitation(String to, String workspaceName, String invitationLink);
    void sendPasswordReset(String to, String resetLink);
    void sendNotification(String to, String subject, String body);
}
```

### Async Email via RabbitMQ

Never send emails synchronously in the request thread:

```java
// InvitationService — publish to queue
messagingTemplate.convertAndSend("simplira.email",
    new InvitationEmailEvent(invitation.getEmail(), workspaceName, link));

// Separate consumer handles actual sending
@RabbitListener(queues = "simplira.email")
public class EmailConsumer {
    @RabbitHandler
    public void handleInvitationEmail(InvitationEmailEvent event) {
        emailService.sendInvitation(event.getTo(), event.getWorkspaceName(), event.getLink());
    }
}
```

**Dead letter queue for failed emails:**

```java
@Bean
public Queue emailQueue() {
    return QueueBuilder.durable("simplira.email")
        .withArgument("x-dead-letter-exchange", "simplira.dlx")
        .build();
}

@Bean
public Queue deadLetterQueue() {
    return new Queue("simplira.dlx");    // failed emails land here for inspection
}
```

**Test mode — no real emails during tests:**

```java
@Service
@Profile("test")
public class NoOpEmailService implements EmailService {
    @Override
    public void sendInvitation(String to, String workspaceName, String invitationLink) {
        log.info("TEST MODE: Invitation to={}, workspace={}", to, workspaceName);
    }
}
```

**application.yml:**

```yaml
spring:
  mail:
    host: smtp.mailgun.org
    port: 587
    username: ${MAILGUN_USERNAME}
    password: ${MAILGUN_PASSWORD}
    properties:
      mail.smtp.auth: true
      mail.smtp.starttls.enable: true

simplira:
  email:
    from: noreply@simplira.com
```

---

## 26. Member Search Endpoints

Missing from API layer. Needed for assignee picker and @mention autocomplete.

```
GET /api/v1/workspaces/{workspaceId}/members/search?q=john&limit=10    👤
GET /api/v1/projects/{projectId}/members/search?q=john&limit=10        👤
```

Project-scoped search needed for private projects — only show members who have access.
Minimum 2 characters before searching — enforced in both frontend and backend.

**Response:**

```json
{
  "success": true,
  "data": [
    {
      "id": "uuid",
      "fullName": "John Doe",
      "email": "john@example.com",
      "avatarUrl": "https://...",
      "role": "DEVELOPER"
    }
  ]
}
```

**SQL:**

```sql
SELECT u.id, u.full_name, u.email, u.avatar_url, uw.role
FROM users u
JOIN user_workspace uw ON uw.user_id = u.id
WHERE uw.workspace_id = :workspaceId
AND (
    LOWER(u.full_name) LIKE LOWER(CONCAT('%', :query, '%'))
    OR LOWER(u.email)  LIKE LOWER(CONCAT('%', :query, '%'))
)
LIMIT :limit
```

---

## 27. Presigned URLs for Attachments

### The Problem

`file_url` assumed a public URL. S3 and Cloudflare R2 buckets must be private.
The frontend needs temporary signed URLs to access files.

### Schema Change

```sql
Attachment
- file_url      ← REMOVE
+ file_key      VARCHAR, NOT NULL    ← S3 object key e.g. "attachments/issue-uuid/uuid_filename.png"
```

### Flow

```
1. Frontend requests attachment
2. Backend generates presigned URL (1 hour expiry)
3. Frontend uses presigned URL directly against S3
4. URL expires — cannot be reused after expiry
```

### Updated StorageService

```java
public interface StorageService {
    String upload(String key, InputStream data, String mimeType, long size);
    String generatePresignedUrl(String key, Duration expiry);
    void delete(String key);
}
```

### Updated AttachmentResponse

```java
public record AttachmentResponse(
    UUID id,
    String fileName,
    long fileSize,
    String mimeType,
    String url,         // presigned URL, generated fresh on every response
    LocalDateTime createdAt
) {}
```

**Key naming convention:**

```
attachments/{issueId}/{UUID}_{originalFilename}
e.g. attachments/issue-uuid/abc123_screenshot.png
```

---

## 28. Scheduled Cleanup Jobs

### Setup

```java
@SpringBootApplication
@EnableScheduling
public class SimpliraApplication { }
```

### Jobs

```java
@Component
@Slf4j
public class ScheduledJobs {

    // Daily at 2:00 AM — purge expired notifications
    @Scheduled(cron = "0 0 2 * * *")
    public void purgeExpiredNotifications() {
        int deleted = notificationRepository.deleteExpired(LocalDateTime.now());
        log.info("Purged expired notifications: count={}", deleted);
    }

    // Daily at 2:30 AM — purge expired refresh tokens
    @Scheduled(cron = "0 30 2 * * *")
    public void purgeExpiredRefreshTokens() {
        int deleted = refreshTokenRepository.deleteExpired(LocalDateTime.now());
        log.info("Purged expired refresh tokens: count={}", deleted);
    }

    // Hourly — expire pending invitations past their expiry date
    @Scheduled(cron = "0 0 * * * *")
    public void expireOutdatedInvitations() {
        int updated = invitationRepository.expireOutdated(LocalDateTime.now());
        log.info("Expired outdated invitations: count={}", updated);
    }
}
```

### Repository Queries

```java
@Modifying
@Query("DELETE FROM Notification n WHERE n.expiresAt < :now")
int deleteExpired(@Param("now") LocalDateTime now);

@Modifying
@Query("DELETE FROM RefreshToken t WHERE t.expiresAt < :now AND t.revokedAt IS NULL")
int deleteExpired(@Param("now") LocalDateTime now);

@Modifying
@Query("UPDATE Invitation i SET i.status = 'EXPIRED' " +
       "WHERE i.expiresAt < :now AND i.status = 'PENDING'")
int expireOutdated(@Param("now") LocalDateTime now);
```

---

## 29. CloseSprintRequest DTO

```java
public record CloseSprintRequest(
    @NotNull IncompleteIssueAction incompleteIssueAction,
    UUID targetSprintId    // required only when action is MOVE_TO_SPRINT
) {
    public enum IncompleteIssueAction {
        MOVE_TO_BACKLOG,
        MOVE_TO_SPRINT
    }

    @AssertTrue(message = "targetSprintId required when action is MOVE_TO_SPRINT")
    public boolean isTargetSprintIdValid() {
        if (incompleteIssueAction == IncompleteIssueAction.MOVE_TO_SPRINT) {
            return targetSprintId != null;
        }
        return true;
    }
}
```

**Service handling:**

```java
public Sprint closeSprint(UUID sprintId, CloseSprintRequest request, UUID actorId) {
    Sprint sprint = sprintRepository.findById(sprintId)
        .orElseThrow(() -> new SprintNotFoundException(sprintId));

    List<Issue> incompleteIssues = issueRepository
        .findBySprintIdAndStatusNot(sprintId, IssueStatus.DONE);

    switch (request.incompleteIssueAction()) {
        case MOVE_TO_BACKLOG -> incompleteIssues.forEach(issue -> {
            issue.setSprintId(null);
            issue.setSprintPosition(null);
        });
        case MOVE_TO_SPRINT -> {
            Sprint target = sprintRepository.findById(request.targetSprintId())
                .orElseThrow(() -> new SprintNotFoundException(request.targetSprintId()));
            incompleteIssues.forEach(issue -> issue.setSprintId(target.getId()));
        }
    }

    issueRepository.saveAll(incompleteIssues);
    sprint.setStatus(SprintStatus.COMPLETED);
    Sprint saved = sprintRepository.save(sprint);

    auditLogService.logSprintClosed(saved, incompleteIssues.size(), actorId);
    notificationService.notifySprintClosed(saved);

    return saved;
}
```

---

## 30. Spring Actuator Health Check

```xml
<!-- pom.xml -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        include: health, info
      base-path: /actuator
  endpoint:
    health:
      show-details: never
```

```dockerfile
HEALTHCHECK --interval=30s --timeout=10s --retries=3   CMD curl -f http://localhost:8080/actuator/health || exit 1
```

Response: `GET /actuator/health → { "status": "UP" }`

---

## 31. Default IssueType Seeding

```sql
-- V16__create_default_issue_types.sql

CREATE TABLE default_issue_type (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name       VARCHAR NOT NULL,
    icon       VARCHAR,
    color      VARCHAR,
    is_default BOOLEAN NOT NULL DEFAULT false,
    sort_order INTEGER NOT NULL
);

INSERT INTO default_issue_type (name, icon, color, is_default, sort_order) VALUES
    ('Task',  'check-square', '#4A90D9', true,  1),
    ('Bug',   'bug',          '#E74C3C', false, 2),
    ('Story', 'book-open',    '#27AE60', false, 3);
```

Copied into project-scoped IssueType rows on project creation:

```java
public Project createProject(CreateProjectRequest request, UUID workspaceId, UUID actorId) {
    Project project = // ... create project

    List<DefaultIssueType> defaults = defaultIssueTypeRepository.findAllByOrderBySortOrder();
    defaults.forEach(def -> {
        IssueType type = new IssueType();
        type.setProjectId(project.getId());
        type.setName(def.getName());
        type.setIcon(def.getIcon());
        type.setColor(def.getColor());
        type.setDefault(def.isDefault());
        issueTypeRepository.save(type);
    });

    return project;
}
```

---

## 32. Schema Corrections

### Workspace — Add deleted_by

```sql
-- Add to V18 migration
ALTER TABLE workspace ADD COLUMN deleted_by UUID REFERENCES users(id);
```

### Project — Add Soft Delete

Hard-deleting a project destroys all issues, sprints, and history permanently. Soft delete instead:

```sql
-- V19__add_project_soft_delete.sql
ALTER TABLE project ADD COLUMN deleted_at TIMESTAMPTZ;
ALTER TABLE project ADD COLUMN deleted_by UUID REFERENCES users(id);
```

All project queries exclude soft-deleted projects:

```java
projectRepository.findByIdAndDeletedAtIsNull(projectId)
    .orElseThrow(() -> new ProjectNotFoundException(projectId));
```

### Updated Flyway Migration List

> **Superseded.** This list (and the one in §33) has been replaced by the single canonical
> migration list in §9. The canonical list is FK-ordered and adds the join-table migrations
> (user_workspaces, project_members, issue_labels, comment_mentions, notification_preferences)
> and email_verification_tokens that were missing here. The soft-delete columns added by the
> ALTER statements above are folded into the relevant CREATE TABLE statements there.

### Final Soft Delete Inventory

| Table | deleted_at | deleted_by |
|---|---|---|
| Issue | ✅ | ❌ use closed_by |
| Comment | ✅ | ❌ use author_id |
| Attachment | ✅ | ✅ |
| Workspace | ✅ | ✅ |
| Project | ✅ | ✅ |
| IssueType | ✅ | ✅ |

### Final Updated API Endpoint Count

> **Superseded by the canonical count in §7 (99 total).** The endpoints below are already
> included there; the "92" running total predates the issue-status and verification endpoints.

| Added | Endpoints |
|---|---|
| Member search (workspace) | 1 |
| Member search (project) | 1 |
| Resolve issue by identifier | 1 |

---

---

## 33. Configurable Issue Statuses

### Decision

Status is not a hardcoded enum. Each project defines its own statuses with custom names, colors, and ordering. Each status maps to a fixed **category** that the system uses for logic. Teams see custom names — the system uses categories.

### Category System

| Category | Meaning |
|---|---|
| TODO | Not started — counts as incomplete in sprints |
| IN_PROGRESS | Active work — counts as incomplete in sprints |
| DONE | Finished — counts as complete everywhere |

Charts, sprint close logic, and blocked detection all use `category`, never the display name.

### New Table — IssueStatus

```sql
IssueStatus
- id            UUID, PK
- project_id    UUID, FK → Project, NOT NULL
- name          VARCHAR, NOT NULL              ← e.g. "In Review", "In QA", "Blocked"
- category      ENUM(TODO, IN_PROGRESS, DONE), NOT NULL
- color         VARCHAR, NOT NULL              ← hex code
- position      INTEGER, NOT NULL              ← display order on board
- is_default    BOOLEAN, NOT NULL, DEFAULT false
- created_at    TIMESTAMPTZ, NOT NULL
- deleted_at    TIMESTAMPTZ, nullable            ← soft delete

UNIQUE (project_id, position) WHERE deleted_at IS NULL
UNIQUE (project_id, name) WHERE deleted_at IS NULL
```

### Issue Table Change

```sql
Issue
- status        ENUM(TODO, IN_PROGRESS, DONE)    ← REMOVED
+ status_id     UUID, FK → IssueStatus, NOT NULL  ← REPLACES status
```

### Default Statuses per Project

Every new project is seeded with three defaults matching the old enum behavior:

| Name | Category | Color | Position | Default for new issues |
|---|---|---|---|---|
| To Do | TODO | #E2E8F0 | 1 | ✅ |
| In Progress | IN_PROGRESS | #3B82F6 | 2 | ❌ |
| Done | DONE | #22C55E | 3 | ❌ |

Teams can rename, recolor, reorder, or add new statuses freely.

### Default Status Seeding Migration

```sql
-- V21__create_default_statuses.sql

CREATE TABLE default_issue_status (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name       VARCHAR NOT NULL,
    category   VARCHAR NOT NULL,
    color      VARCHAR NOT NULL,
    position   INTEGER NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT false
);

INSERT INTO default_issue_status (name, category, color, position, is_default) VALUES
    ('To Do',       'TODO',        '#E2E8F0', 1, true),
    ('In Progress', 'IN_PROGRESS', '#3B82F6', 2, false),
    ('Done',        'DONE',        '#22C55E', 3, false);
```

Copied into project-scoped IssueStatus rows on project creation alongside IssueTypes:

```java
public Project createProject(CreateProjectRequest request, UUID workspaceId, UUID actorId) {
    Project project = // ... create project

    // Seed default issue types
    seedDefaultIssueTypes(project.getId());

    // Seed default statuses
    List<DefaultIssueStatus> defaults = defaultStatusRepository.findAllByOrderByPosition();
    defaults.forEach(def -> {
        IssueStatus status = new IssueStatus();
        status.setProjectId(project.getId());
        status.setName(def.getName());
        status.setCategory(def.getCategory());
        status.setColor(def.getColor());
        status.setPosition(def.getPosition());
        status.setDefault(def.isDefault());
        issueStatusRepository.save(status);
    });

    return project;
}
```

### Business Rules (Service Layer)

```java
public void deleteStatus(UUID statusId, DeleteStatusRequest request, UUID actorId) {
    IssueStatus status = issueStatusRepository.findById(statusId)
        .orElseThrow(() -> new StatusNotFoundException(statusId));

    // Rule 1: must always have at least one status per category
    long remainingInCategory = issueStatusRepository
        .countByProjectIdAndCategoryAndDeletedAtIsNull(
            status.getProjectId(), status.getCategory());
    if (remainingInCategory <= 1) {
        throw new BusinessRuleException("LAST_STATUS_IN_CATEGORY",
            "Cannot delete the last status in category: " + status.getCategory());
    }

    // Rule 2: migrate existing issues before deleting
    long issueCount = issueRepository.countByStatusId(statusId);
    if (issueCount > 0) {
        if (request.migrateToStatusId() == null) {
            throw new BusinessRuleException("STATUS_HAS_ISSUES",
                "Status has " + issueCount + " issues. Provide migrateToStatusId.");
        }
        issueRepository.migrateStatus(statusId, request.migrateToStatusId());
    }

    status.setDeletedAt(LocalDateTime.now());
    issueStatusRepository.save(status);
}

public void reorderStatuses(UUID projectId, List<UUID> orderedStatusIds, UUID actorId) {
    // Assign position based on list index
    for (int i = 0; i < orderedStatusIds.size(); i++) {
        IssueStatus status = issueStatusRepository.findById(orderedStatusIds.get(i))
            .orElseThrow(() -> new StatusNotFoundException(orderedStatusIds.get(i)));
        status.setPosition(i + 1);
        issueStatusRepository.save(status);
    }
}
```

### API Endpoints

```
GET    /api/v1/projects/{projectId}/statuses                        👤 List statuses
POST   /api/v1/projects/{projectId}/statuses                        👥 Create status
PATCH  /api/v1/projects/{projectId}/statuses/{statusId}             👥 Update status
DELETE /api/v1/projects/{projectId}/statuses/{statusId}             👥 Delete status
PATCH  /api/v1/projects/{projectId}/statuses/reorder                👥 Reorder statuses
```

**DeleteStatusRequest:**

```java
public record DeleteStatusRequest(
    UUID migrateToStatusId    // required if issues exist with this status
) {}
```

**ReorderStatusesRequest:**

```java
public record ReorderStatusesRequest(
    @NotNull @NotEmpty List<UUID> orderedStatusIds    // all status IDs in desired order
) {}
```

### Impact on Existing Features

**Charts** — use category instead of status name:

```java
// Burndown — completed issues have status.category == DONE
long completedCount = issueRepository.countBySprintIdAndStatusCategory(sprintId, Category.DONE);

// Incomplete at sprint close
List<Issue> incomplete = issueRepository.findBySprintIdAndStatusCategoryNot(sprintId, Category.DONE);
```

**Sprint close** — incomplete means `status.category != DONE`, not a specific status name.

**Board columns** — columns are built dynamically from project statuses, ordered by position. No hardcoded column names in the frontend.

**AuditLog** — status changes now store status name + category in old_value/new_value JSON:

```json
old_value: { "statusId": "uuid", "statusName": "To Do", "category": "TODO" }
new_value: { "statusId": "uuid", "statusName": "In Review", "category": "IN_PROGRESS" }
```

### Updated Flyway Migration List

```
Superseded — see the canonical Flyway migration list in §9, which replaces this list and the
one in §32. This draft correctly splits issue statuses (default table + project table) but still
omits the join-table migrations; the canonical list includes them and is FK-ordered.
```

### Updated API Endpoint Count

> **Superseded by §7 (canonical total 99).** The five status endpoints below are included there.

| Added | Endpoints |
|---|---|
| List statuses | 1 |
| Create status | 1 |
| Update status | 1 |
| Delete status | 1 |
| Reorder statuses | 1 |

---

## 34. Issue Filtering & Search

Filtering is **folded into the existing list endpoints** as query parameters — it adds no new
endpoints. `GET /projects/{projectId}/issues` and `GET /sprints/{sprintId}/issues` accept the
same filter set.

### Query Parameters

```
?statusCategory=IN_PROGRESS        filter by category (TODO | IN_PROGRESS | DONE)
&statusId={uuid}                   filter by a specific configurable status
&assigneeId={uuid}                 issues assigned to this user
&reporterId={uuid}                 issues created by this user
&typeId={uuid}                     by issue type
&priority=HIGH                     LOW | MEDIUM | HIGH | CRITICAL
&labelId={uuid}                    has this label (repeatable for AND/OR)
&isBlocked=true                    only blocked / only unblocked
&unassigned=true                   issues with no assignee
&dueBefore=2026-07-01              due date upper bound
&dueAfter=2026-06-01               due date lower bound
&q=login button                    text match on title + description
```

All filters are optional and combine with AND. Pagination/sorting params from §23 still apply.

### Implementation — JPA Specifications

Keep the domain repository port clean (it takes a filter value object, not Spring types). The
infrastructure adapter translates that filter into a JPA `Specification`, composing only the
predicates that are present:

```java
// domain port
List<Issue> search(IssueFilter filter, Pageable pageable);

// infrastructure adapter
Specification<IssueEntity> spec = Specification.where(notDeleted());
if (filter.statusCategory() != null) spec = spec.and(hasCategory(filter.statusCategory()));
if (filter.assigneeId() != null)     spec = spec.and(hasAssignee(filter.assigneeId()));
if (filter.priority() != null)       spec = spec.and(hasPriority(filter.priority()));
if (filter.isBlocked() != null)      spec = spec.and(isBlocked(filter.isBlocked()));
if (filter.q() != null)              spec = spec.and(textMatches(filter.q()));
return jpaRepo.findAll(spec, pageable).map(mapper::toDomain).getContent();
```

### Text Search

For v1, `q` uses case-insensitive `ILIKE '%term%'` on `title` and `description`. This is fine up
to tens of thousands of issues. When it stops scaling, upgrade to PostgreSQL full-text search
without an API change:

```sql
ALTER TABLE issue ADD COLUMN search_vector tsvector
    GENERATED ALWAYS AS (
        to_tsvector('english', coalesce(title,'') || ' ' || coalesce(description,''))
    ) STORED;

CREATE INDEX idx_issue_search ON issue USING GIN (search_vector);
```

The endpoint contract stays identical — only the adapter's predicate changes from `ILIKE` to a
`@@ plainto_tsquery(...)` match.

---

## 35. API Documentation (OpenAPI / Swagger)

With ~99 endpoints, hand-maintained docs rot immediately. Generate them from the controllers
with **springdoc-openapi**.

### Dependency

```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.6.0</version>
</dependency>
```

### Served At

```
/v3/api-docs        ← raw OpenAPI 3 JSON (machine-readable, import into Postman/clients)
/swagger-ui.html    ← interactive browser UI
```

> These tooling endpoints are **not** part of the 99 application endpoints counted in §7.

### Configuration

```java
@Bean
public OpenAPI simpliraOpenAPI() {
    return new OpenAPI()
        .info(new Info().title("Simplira API").version("v1"))
        .components(new Components().addSecuritySchemes("bearer-jwt",
            new SecurityScheme().type(HTTP).scheme("bearer").bearerFormat("JWT")))
        .addSecurityItem(new SecurityRequirement().addList("bearer-jwt"));
}
```

Group endpoints with `@Tag` on each controller (Auth, Issues, Sprints, …) and document request
bodies/responses with `@Operation` where the intent isn't obvious from the method signature.

### Production

Expose Swagger UI only in non-prod, or gate it behind admin auth. The raw `/v3/api-docs` can stay
on if it's behind the same JWT filter as the rest of `/api`.

```properties
# application-prod.properties
springdoc.swagger-ui.enabled=false
```

---

## 36. Optimistic Locking & Concurrency

Multiple users edit the same board at once. Two risks: two people editing the same issue and
overwriting each other ("lost update"), and concurrent drag-and-drop racing on the same
position. The `version` column added to `Issue` (§4) handles the first cleanly.

### How It Works

The JPA entity carries a `@Version` field mapped to the `version` column. Hibernate adds
`WHERE version = ?` to every UPDATE and bumps the value on success. If two transactions read
version 5 and both try to write, the first wins (version → 6) and the second's UPDATE matches
zero rows, so Hibernate throws `OptimisticLockException`.

```java
@Entity
public class IssueEntity {
    @Version
    private long version;   // maps to issue.version
}
```

The domain `Issue` also carries `version` so it survives the round-trip to the client. The client
sends back the version it last read on a `PATCH /issues/{id}`; the server compares.

### Surfacing the Conflict

Translate the exception in `GlobalExceptionHandler` to a real HTTP status instead of a 500:

```java
@ExceptionHandler({OptimisticLockException.class, ObjectOptimisticLockingFailureException.class})
public ResponseEntity<ErrorResponse> handleConflict(Exception e) {
    return ResponseEntity.status(409).body(ErrorResponse.of(
            "ISSUE_VERSION_CONFLICT",
            "This issue was changed by someone else. Reload and reapply your edit."));
}
```

The frontend catches 409, refetches the issue, and asks the user to redo their change — no silent
data loss.

### Drag-and-Drop Positions

Position writes (§10 float midpoint) should run inside a short transaction and rely on the same
`version` check. If two reorders collide, the loser gets a 409 and the client re-reads the
column's current order before retrying. The periodic position-normalization job must also run in
a transaction so it never fights a live reorder.

### Scope for v1

Apply `@Version` to `Issue` only — it's where concurrent edits actually happen. `Comment` and
`Sprint` can adopt the same pattern later if needed; they're lower-contention and not worth the
added column churn now.

---

---

## 37. Implementation Patterns — Persistence & Auth

Everything above describes *what* to build. This section records *how* it was actually built
during the first vertical slice (auth), including the traps hit along the way. These patterns
repeat for every aggregate — Workspace, Project, Sprint, Issue — so they're worth internalizing
once here rather than rediscovering per feature.

---

### 37.1 Local Startup Order

Containers first, application second. The Spring Boot app does not start its own dependencies.

```bash
docker compose up -d     # Postgres + RabbitMQ + pgAdmin
docker ps                # confirm 0.0.0.0:5432->5432/tcp before continuing
# then start the app
```

Starting the app alone produces a Flyway failure at boot:

```
FlywaySqlUnableToConnectToDbException: Unable to obtain connection from database:
FATAL: password authentication failed for user "simplira"    (SQL State 28P01)
```

That same error also appears when a **local** PostgreSQL install is occupying port 5432 — it
answers the connection instead of Docker and rejects the credentials. Diagnose with:

```bash
netstat -ano | findstr 5432    # two LISTENING lines = conflict
net stop postgresql-x64-18
sc config postgresql-x64-18 start= disabled    # note the space after start=
```

> A `psql` session that works via `docker exec` proves nothing about credentials — it connects
> over a Unix socket inside the container and skips password auth entirely. Always test the
> TCP path the application actually uses.

---

### 37.2 The Vertical Slice Recipe

Build one feature top to bottom before starting the next. Ten steps, in dependency order — each
file is compilable once the ones before it exist:

| # | File | Layer |
|---|---|---|
| 1 | `V{n}__create_{table}.sql` | resources/db/migration |
| 2 | `{Aggregate}.java` — pure domain model | domain/model |
| 3 | `{Aggregate}Repository.java` — the port (interface) | domain/repository |
| 4 | `{Aggregate}Entity.java` — JPA entity | infrastructure/persistence/postgres/entity |
| 5 | `Jpa{Aggregate}Repository.java` — Spring Data interface | infrastructure/persistence/postgres/jpa |
| 6 | `{Aggregate}EntityMapper.java` — entity ↔ domain | infrastructure/persistence/postgres/mapper |
| 7 | `{Aggregate}RepositoryAdapter.java` — implements the port | infrastructure/persistence/postgres/adapter |
| 8 | `{Aggregate}Service.java` — orchestration + business rules | domain/service |
| 9 | `{Aggregate}Controller.java` + request/response DTOs | application/api |
| 10 | exception + `GlobalExceptionHandler` entry | application/shared/exception |

Verify each slice against the running database (pgAdmin or psql) before moving on. The only real
per-aggregate thinking is step 3 — *which queries does this aggregate need?* Everything else is
mechanical.

> Frontend work stays deferred until the core API slices are working end to end. Building UI
> against endpoints that don't exist yet means either throwaway mocks or constant blocking.

---

### 37.3 Assigned UUIDs and the `Persistable` Trap

**This is the single most reusable lesson from the auth slice.** It will recur on every aggregate.

The domain owns identity — `User.register()` calls `UUID.randomUUID()` so an object is
identifiable before it is ever persisted. The consequence: entities arrive at
`jpaRepository.save()` with a non-null ID. Spring Data uses ID-null-ness to choose between
`persist()` (INSERT) and `merge()` (UPDATE), so a pre-populated ID sends a brand-new record down
the UPDATE path against a row that doesn't exist:

```
org.hibernate.StaleObjectStateException:
Row was already updated or deleted by another transaction for entity [...UserEntity with id '...']
```

The message says "already updated by another transaction," but nothing concurrent is happening —
the real meaning is *"I tried to UPDATE a row that isn't there."*

**Fix — implement `Persistable<UUID>` and flag newness explicitly:**

```java
@Entity
@Table(name = "users")
@Getter @Setter @NoArgsConstructor
public class UserEntity implements Persistable<UUID> {

    @Id
    private UUID id;                 // NO @GeneratedValue — the domain assigns it

    // ... other fields ...

    @Transient
    private boolean isNew = false;   // @Transient = in-memory only, not a column

    @Override public UUID getId()      { return id; }
    @Override public boolean isNew()   { return isNew; }
    public void markNew()              { this.isNew = true; }
}
```

The mapper sets the flag when converting a domain object destined for insert:

```java
public static UserEntity toEntity(User user) {
    UserEntity entity = new UserEntity();
    // ... field copies ...
    entity.markNew();
    return entity;
}
```

`isNew() == true` → Spring Data calls `persist()` → INSERT. Correct behavior restored.

**Rejected alternative:** keep `@GeneratedValue(strategy = GenerationType.UUID)` and have the
domain pass `null` for the id. Simpler, but domain objects then have no identity until saved,
which breaks any logic that references an unsaved aggregate. Domain-owned identity is the better
fit for hexagonal and is the project standard.

> Apply this pattern to **every** aggregate with a domain-assigned UUID PK. The database
> `DEFAULT gen_random_uuid()` remains in the DDL as a harmless fallback that will not normally
> fire.

---

### 37.4 Lombok Policy

| Class kind | Lombok usage |
|---|---|
| JPA entity | `@Getter @Setter @NoArgsConstructor` — **never bare `@Data`** |
| Domain model | none, or `@Getter` only — never `@Setter` |
| DTO | prefer a `record` over Lombok entirely |

**Why `@Data` is banned on entities.** It generates `equals()`, `hashCode()`, and `toString()`
across all fields, and all three misbehave under JPA:

- `toString()` walks lazy associations, triggering extra queries or `LazyInitializationException`,
  and can recurse infinitely across bidirectional relationships.
- `hashCode()` built on a mutable ID changes when the entity is persisted, corrupting any
  `HashSet`/`HashMap` the entity was placed in beforehand.
- `@Data` also implies `@RequiredArgsConstructor`, which can conflict with the no-arg constructor
  JPA requires.

**Domain models get no setters at all.** Fields that never change after construction (`id`,
`createdAt`) are `final`; everything else mutates only through intention-revealing methods that
also maintain invariants:

```java
public void markEmailVerified() {
    this.emailVerified = true;
    this.touch();               // updatedAt bump can't be forgotten
}
```

A generic `setEmailVerified(true)` would let a caller skip the timestamp update. This is why the
domain model is the one place worth writing by hand.

---

### 37.5 Mapping Strategy

**Entity ↔ Domain: hand-written, always.** Two static methods per aggregate, roughly 15 lines:

```java
public final class UserEntityMapper {
    private UserEntityMapper() {}
    public static User toDomain(UserEntity e) { return new User(e.getId(), ...); }
    public static UserEntity toEntity(User u) { /* setters + markNew() */ }
}
```

**ModelMapper was evaluated and rejected** for this seam:

- It populates objects by reflection via setters or field access — defeating the deliberate
  no-setter encapsulation of the domain model, and pushing toward adding a no-arg constructor and
  open fields purely to satisfy the library.
- Mismatches surface as runtime nulls or mapping exceptions instead of compile errors. A
  hand-written mapper fails the build the moment a field is renamed on one side.
- Reflection cost on hot paths (board loads, issue lists).
- The seam is exactly where the two shapes legitimately diverge (factory methods, fields present
  on one side only), so custom configuration would be needed anyway.

**If a mapping library is introduced later — use MapStruct, not ModelMapper.** MapStruct generates
plain Java at compile time: no reflection overhead, and mismatches are build errors. Reasonable
for domain ↔ response DTO, where both sides are flat data holders.

---

### 37.6 Password Handling

The `PasswordEncoder` bean lives in infrastructure, and hashing happens in the **service**, never
in the domain model. `User.register()` accepts an already-hashed string it treats as opaque —
that keeps BCrypt (an infrastructure concern) out of the pure domain.

```java
@Configuration
public class PasswordConfig {
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

```java
String hashed = passwordEncoder.encode(rawPassword);
User user = User.register(email, hashed, fullName);
```

**Verification uses `matches(raw, hash)` — never re-hash and string-compare.** BCrypt embeds a
random salt per hash, so `encode()` on the same password twice produces different output.
`matches()` extracts the salt and performs a constant-time comparison.

---

### 37.7 Account Enumeration Prevention on Login

Unknown email and wrong password must be **indistinguishable** to the caller — same exception,
same message, same status code:

```java
User user = userRepository.findByEmail(email)
        .orElseThrow(InvalidCredentialsException::new);

if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
        throw new InvalidCredentialsException();   // identical to the above
}
```

Response is always `401 INVALID_CREDENTIALS` / "Invalid email or password". Distinguishing them
would let an attacker enumerate registered addresses. This mirrors the always-200 rule already
specified for password reset (§6).

**Related — the duplicate-email check is not redundant with the DB constraint.** `existsByEmail()`
before insert gives a clean `409 EMAIL_ALREADY_EXISTS`; the `UNIQUE` constraint on `users.email`
is the actual correctness backstop against the race where two simultaneous registrations both
pass the check. UX from the check, correctness from the constraint.

---

### 37.8 Security Configuration

Adding `spring-boot-starter-security` to the classpath with no configuration locks down **every**
endpoint — including `/auth/login` itself, making authentication impossible. The giveaway is
`Using generated security password: <uuid>` in the startup log and a 401 on every route.

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())                    // bearer tokens, no session cookies
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/**").permitAll()
                .anyRequest().authenticated())
            .addFilterBefore(jwtAuthenticationFilter,
                UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
```

Omitting `.httpBasic()` and `.formLogin()` removes the default login page and basic-auth prompt.
As the public endpoint list in §6 grows (forgot-password, reset-password, verify-email,
accept-invitation, WebSocket handshake), extend the `permitAll` matchers accordingly.

---

### 37.9 JWT Authentication Filter

Extends `OncePerRequestFilter` (guarantees single execution per request even across
forwards/includes). The filter **only populates** the security context — it never rejects.
Rejection is left to the authorization rules, which produce the 401 when the context is empty.

```java
String header = request.getHeader("Authorization");
if (header == null || !header.startsWith("Bearer ")) {
        filterChain.doFilter(request, response);    // no token → continue, don't reject
    return;
            }
String token = header.substring(7);

if (jwtService.isValid(token)
        && SecurityContextHolder.getContext().getAuthentication() == null) {
UUID userId = jwtService.extractUserId(token);
    userRepository.findById(userId).ifPresent(user -> {
var authentication = new UsernamePasswordAuthenticationToken(
        user,        // principal = the domain User
        null,        // credentials not needed post-auth
        List.of()    // authorities empty by design — see below
);
        authentication.setDetails(
            new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    });
            }
            filterChain.doFilter(request, response);
```

**Authorities are deliberately empty.** Per §6, roles are never carried in the JWT — they are
workspace-scoped and fetched from the database per request via `AuthorizationService`. No
`ROLE_` authorities are attached here.

**Known tradeoff:** the filter performs one `findById` per authenticated request. This is the
correct default — deleted or modified users take effect immediately — at the cost of a query per
request. If it ever becomes a bottleneck, the options are a short-TTL cache or trusting the JWT
claims for identity. Deliberate choice, not an oversight.

**Convenience annotation** so controllers receive the domain `User` directly:

```java
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@AuthenticationPrincipal
public @interface CurrentUser {}
```

```java
@GetMapping("/me")
public ApiResponse<UserResponse> me(@CurrentUser User user) {
    return ApiResponse.success(UserResponse.from(user));
}
```

---

### 37.10 Response Envelope & DTO Rules

The envelope from §7 is implemented as a generic record; `@JsonInclude(NON_NULL)` keeps the unused
half out of the JSON so success responses are `{success, data}` and errors are `{success, error}`.

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, T data, ErrorResponse error) {
    public static <T> ApiResponse<T> success(T data)          { return new ApiResponse<>(true, data, null); }
    public static <T> ApiResponse<T> failure(ErrorResponse e)  { return new ApiResponse<>(false, null, e); }
}
```

**DTOs are Java records with Jakarta validation annotations**, mapped explicitly from the domain
via a static `from()` factory. Explicit mapping is what guarantees the password hash can never
leak into a response — `UserResponse` simply has no such field:

```java
public record UserResponse(UUID id, String email, String fullName,
                           String avatarUrl, boolean emailVerified, Instant createdAt) {
    public static UserResponse from(User user) { /* field copies — no password */ }
}
```

> `spring-boot-starter-validation` must be present or `@Valid`, `@NotBlank`, and `@Email` compile
> silently and enforce nothing.

**Placement:** `ApiResponse`, `ErrorResponse`, and `GlobalExceptionHandler` live in the
**application** layer, not the framework-free top-level `shared` package — they depend on Spring
and produce HTTP concerns.

**Status code mapping established so far:**

| Exception | Status | Code |
|---|---|---|
| `EmailAlreadyExistsException` | 409 | `EMAIL_ALREADY_EXISTS` |
| `InvalidCredentialsException` | 401 | `INVALID_CREDENTIALS` |
| `MethodArgumentNotValidException` | 400 | `VALIDATION_FAILED` (+ field details) |
| `OptimisticLockException` (§36) | 409 | `ISSUE_VERSION_CONFLICT` |

---

### 37.11 Flyway in Practice

Migrations are applied automatically at application startup — drop a new
`V{n}__{description}.sql` into `src/main/resources/db/migration/` and boot the app. No manual
execution step.

**Applied migrations cannot be edited.** Flyway checksums every file; changing one that has
already run fails startup with a checksum mismatch. Two correction paths:

| Situation | Action |
|---|---|
| Local dev, no data worth keeping | Edit the migration, then `docker compose down -v && docker compose up -d` to replay from scratch |
| Shared or production environment | Never edit — add a new forward migration (`ALTER TABLE ...`) |

Verify applied state at any time:

```sql
SELECT * FROM flyway_schema_history;
```

---

### 37.12 Entity Mapping Gotcha — Duplicate Column

```
org.hibernate.MappingException: Column 'avatar_url' is duplicated in mapping for entity 'UserEntity'
```

Two properties resolved to the same column. Usually a pasted duplicate field, or a camelCase and a
snake_case field coexisting — Spring Boot's default naming strategy converts `avatarUrl` to
`avatar_url`, so a second field literally named `avatar_url` collides with it. One field per
column:

```java
@Column(name = "avatar_url")
private String avatarUrl;
```

Ignore the exception's suggestion to add `@Column(insertable=false, updatable=false)` — that is
for intentionally mapping two properties to one column (e.g. an FK value alongside its
relationship object), which is not this case. Hibernate reports only the first collision, so
re-check the whole class after fixing one.

---

---

*Document generated from design sessions — Simplira v1.0 Architecture*
