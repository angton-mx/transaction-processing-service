# AGENTS.md

## Project

This repository contains a senior-level Java REST API challenge for
high-volume financial transaction processing.

Technology stack:

- Java 21
- Spring Boot 3.x
- Maven
- PostgreSQL
- Flyway
- JUnit 5
- Mockito
- AssertJ
- MockMvc
- WireMock
- Testcontainers
- Docker Compose

Architecture:

Use a lightweight Ports and Adapters / Hexagonal Architecture.

Main logical areas:

- api
- application
- domain
- port
- infrastructure.persistence
- infrastructure.provider

Prefer simple, explicit, production-quality code.
Do not overengineer.

---

## Clean Code conventions

- Methods should have one clear responsibility.
- Prefer small methods.
- Production method names should be concise and intention-revealing.
- Prefer production method names of 30 characters or fewer when clarity is
  preserved.
- Never use unclear abbreviations only to satisfy the method-name limit.
- If a production method needs a very long name to explain what it does,
  reconsider whether it has multiple responsibilities.
- Tests should describe one behavior.
- Test names may exceed 30 characters when necessary for clarity.
- Prefer readable code over clever code.
- Avoid unnecessary comments and premature abstractions.

---

## Git workflow

Never work directly on `main`.

Never work directly on `dev`.

All work branches must originate from `dev`.

Branch naming conventions:

- `feature-ivan/<name>`
- `fix-ivan/<name>`
- `refactor-ivan/<name>`
- `chore-ivan/<name>`
- `docs-ivan/<name>`

Expected flow:

work branch -> Pull Request -> dev -> final Pull Request -> main

Rules:

- Never create or switch branches unless explicitly requested.
- Never commit unless explicitly requested.
- Never push unless explicitly requested.
- Never merge a Pull Request.
- Never push directly to `main`.
- Never push directly to `dev`.
- Never modify branch protection or repository rulesets.
- Do not amend or rewrite existing commits unless explicitly requested.
- Before finishing a task, always run `git status`.
- Report modified/untracked files.
- Human review is required before commit and merge.

---

## TDD workflow

This project follows Test Driven Development.

For business functionality, use:

RED -> GREEN -> REFACTOR

### RED phase

When explicitly told that a task is a RED phase:

- Create or modify tests first.
- Do not implement the production behavior needed to make them pass.
- Minimal production types/interfaces may be introduced only when required
  for the tests to compile.
- Run the relevant tests.
- Confirm that the new tests fail for the expected reason.
- Stop after reporting the RED result.

### GREEN phase

When explicitly told that a task is a GREEN phase:

- Implement only enough production code to satisfy the tests.
- Do not weaken, remove, or rewrite valid tests just to make them pass.
- Run relevant tests.
- Run the complete verification suite.
- Stop and report results.

### REFACTOR phase

Only refactor after tests are GREEN.

- Preserve behavior.
- Avoid unrelated changes.
- Run the complete verification suite afterward.

---

## Testing conventions

Use:

- `*Test.java` for unit tests.
- `*IT.java` for integration tests.

Unit tests must not require network or database access.

Use mocks for application ports in unit tests.

Integration tests may use:

- Testcontainers PostgreSQL
- WireMock

Do not use H2 as a substitute for PostgreSQL.

For complete verification run:

Windows:

`mvnw.cmd clean verify`

Unix/macOS:

`./mvnw clean verify`

Always report the exact test result.

---

## Financial correctness

Never use `double` or `float` for monetary values.

Use `BigDecimal`.

PostgreSQL monetary columns should use an appropriate `NUMERIC` type.

Do not implement account balance or locking logic locally.
The external provider owns account balances and locking.

Do not silently retry financial transaction execution unless retry behavior
has been explicitly designed to be safe and idempotent.

---

## Persistence

PostgreSQL is the source of persisted transaction data.

Flyway owns schema evolution.

Do not rely on Hibernate automatic schema creation for production.

Database credentials or secrets must never be committed.

---

## External provider

The external transaction provider must be accessed through a port/interface.

Domain/application logic must not depend directly on WireMock or HTTP-specific
implementation details.

WireMock is used for provider adapter integration tests.

---

## Scope discipline

Implement only the currently requested phase.

Do not add speculative infrastructure or features.

Do not add technologies such as Kafka, Redis, Kubernetes, authentication,
observability stacks, Lombok, MapStruct, or resilience libraries unless the
current task explicitly requires and justifies them.

Do not implement future phases early.

---

## Completion checklist

Before reporting a task complete:

1. Run the requested tests.
2. Run broader verification when required.
3. Inspect `git diff`.
4. Run `git status --short`.
5. Check for accidental credentials or generated artifacts.
6. Summarize files changed.
7. Report test results and unresolved issues.
8. Do not commit, push, create PRs, or merge unless explicitly requested.
