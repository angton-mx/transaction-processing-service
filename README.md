# transaction-processing-service

Senior Java REST API challenge for high-volume financial transaction processing with PostgreSQL and TDD.

## Prerequisites

- Java 21
- Docker with Docker Compose

## Bootstrap commands

Start the local PostgreSQL database on host port `5433`:

```shell
docker compose up -d postgres
```

Set `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` from `.env.example`, then start the application:

```shell
./mvnw spring-boot:run
```

Run the complete unit and integration test suite:

```shell
./mvnw clean verify
```

On Windows, use `mvnw.cmd` instead of `./mvnw`.
