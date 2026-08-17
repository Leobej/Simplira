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
| Users 1 | Get me | 200 — inherits the collection's bearer auth |
| Users 2 | Get me - no token | 401 `UNAUTHENTICATED` |

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
