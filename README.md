# be-interview-prep

Backend interview prep assignment: five features in one Spring Boot application, each shipped as
its own pull request.

**Stack:** Java 17+, Spring Boot 3.5 (3.x), Maven (wrapper included), Spring Data JPA with an
in-memory H2 database, JUnit 5, MockMvc and AssertJ.

| # | Question | PR link |
|---|---|---|
| 1 | Task Manager API | [#1](https://github.com/sherin-vargheese-3/be-interview-prep/pull/1) |
| 2 | URL Shortener | [#2](https://github.com/sherin-vargheese-3/be-interview-prep/pull/2) |
| 3 | Authentication & Roles | |
| 4 | Product Catalog | |
| 5 | Order Service | |

**Video:**

## Run

Requires only a JDK 17+; the Maven wrapper downloads Maven.

```bash
./mvnw test               # run all tests
./mvnw spring-boot:run    # start on http://localhost:8080
```

The database is an in-memory H2 created at startup, so no setup is needed and data resets on
restart.

## Project structure

```
src/main/java/com/edstem/interviewprep
├── config       configuration and startup data
├── controller   REST endpoints
├── service      business logic and transactions
├── dto          request/response records
├── model        JPA entities
├── repository   Spring Data repositories
├── enums        enumerations
└── exception    custom exceptions and the global error handler
```

## Q1 — Task Manager API

| Method | Path | Result |
|---|---|---|
| `POST` | `/api/v1/tasks` | `201` + `Location` |
| `GET` | `/api/v1/tasks?status=DONE` | `200` list (`status` optional: `TODO`, `IN_PROGRESS`, `DONE`) |
| `GET` | `/api/v1/tasks/{id}` | `200` / `404` |
| `PUT` | `/api/v1/tasks/{id}` | `200` / `404` (full replace; `createdAt` kept) |
| `DELETE` | `/api/v1/tasks/{id}` | `204` / `404` |

A task has `title` (required, max 100), `description` (max 1000), `status` (default `TODO`),
`dueDate` (`yyyy-MM-dd`, not in the past) and `createdAt` (set by the server). "Today" is the UTC
date. An overdue task can still be updated (e.g. marked `DONE`) without moving its due date; only
setting a *new* past due date is rejected.

```bash
curl -i -X POST localhost:8080/api/v1/tasks -H 'Content-Type: application/json' \
  -d '{"title":"Write report","dueDate":"2030-01-01"}'
```

**Error format.** Every error, from every feature, is an RFC 9457 `ProblemDetail` with a stable
`code`; invalid input adds one entry per field in `errors[]`:

```json
{"type":"about:blank","title":"Bad Request","status":400,"detail":"One or more fields are invalid",
 "instance":"/api/v1/tasks","code":"VALIDATION_FAILED",
 "errors":[{"field":"dueDate","message":"dueDate cannot be in the past"},
           {"field":"title","message":"title must be at most 100 characters"}]}
```

`404 TASK_NOT_FOUND` for an unknown task, `500 INTERNAL_ERROR` (generic message, cause logged)
for anything unexpected. Tests: `TaskControllerTest`.

## Q2 — URL Shortener

| Method | Path | Result |
|---|---|---|
| `POST` | `/api/v1/links` `{"url", "expiresAt"?}` | `201` new link / `200` existing live link; body has `code` and `shortUrl` |
| `GET` | `/{code}` | `302` to the original URL (visit counted); `404` unknown; `410` expired |
| `GET` | `/api/v1/links/{code}/stats` | `{code, url, visitCount, createdAt, expiresAt}` |

```bash
curl -s -X POST localhost:8080/api/v1/links -H 'Content-Type: application/json' \
  -d '{"url":"https://example.com/a/very/long/path"}'
curl -i localhost:8080/<code>
curl -s localhost:8080/api/v1/links/<code>/stats
```

- **Same URL twice:** same URL + same expiry while the link is live returns the existing code
  (`200`), so retries don't create duplicates; a different expiry or an expired link gets a new
  code (`201`).
- **Codes:** 7 random base62 characters from `SecureRandom` (URL-safe, unguessable), unique by
  primary key, retried on collision.
- **Accurate counts:** each visit is one SQL `UPDATE ... SET visit_count = visit_count + 1`, atomic
  under the row lock. With a Java read-modify-write the concurrency test counted 584 of 5,000
  visits.
- **302, not 301,** plus `Cache-Control: no-store`: browsers cache 301s, so repeat visits wouldn't
  be counted and expired links would keep working.
- `HEAD` requests (link previews, uptime checks) redirect too but aren't counted as visits.

Tests: `ShortLinkServiceTest`, `ShortLinkControllerTest`.
