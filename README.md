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
