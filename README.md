# be-interview-prep

Backend interview prep assignment: five features in one Spring Boot application, each shipped as
its own pull request.

**Stack:** Java 17+, Spring Boot 3.5 (3.x), Maven (wrapper included), Spring Data JPA with an
in-memory H2 database, JUnit 5, MockMvc and AssertJ.

| # | Question | PR link |
|---|---|---|
| 1 | Task Manager API | |
| 2 | URL Shortener | |
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
`dueDate` (`yyyy-MM-dd`, not in the past) and `createdAt` (set by the server).

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
