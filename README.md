# transaction-processing-service

A Spring Boot REST API for validating, executing, and recording financial transactions. The service delegates balance ownership and transaction execution to an external provider, then persists the provider outcome in PostgreSQL.

## Stack

- Java 21
- Spring Boot 3.5
- Maven Wrapper
- PostgreSQL 17
- Flyway
- Spring Data JPA
- JUnit 5, Mockito, AssertJ, and MockMvc
- Testcontainers and WireMock

## Prerequisites

- Git
- JDK 21 (`java -version`)
- Docker with Docker Compose for the provided PostgreSQL setup and integration tests

No system Maven installation is required; use the included wrapper.

## Clone and configure

```shell
git clone https://github.com/angton-mx/transaction-processing-service.git
cd transaction-processing-service
```

The application requires all four variables below. `PROVIDER_BASE_URL` is mapped by `application.yaml` to the Spring property `provider.base-url`.

| Variable | Local example | Purpose |
| --- | --- | --- |
| `DB_URL` | `jdbc:postgresql://localhost:5433/transaction_processing` | JDBC connection URL |
| `DB_USERNAME` | `transaction_processing` | PostgreSQL user |
| `DB_PASSWORD` | `transaction_processing_dev` | PostgreSQL password |
| `PROVIDER_BASE_URL` | `http://localhost:8081` | Base URL of the external execution provider |

The values are also shown in `.env.example`. Docker Compose reads a root `.env` file for Compose interpolation, but Spring Boot does not import that file automatically. Export the application variables in the shell that starts Java, or supply them through the deployment environment.

PowerShell:

```powershell
Copy-Item .env.example .env
docker compose up -d postgres

$env:DB_URL = "jdbc:postgresql://localhost:5433/transaction_processing"
$env:DB_USERNAME = "transaction_processing"
$env:DB_PASSWORD = "transaction_processing_dev"
$env:PROVIDER_BASE_URL = "http://localhost:8081"
```

Bash:

```bash
cp .env.example .env
docker compose up -d postgres

export DB_URL='jdbc:postgresql://localhost:5433/transaction_processing'
export DB_USERNAME='transaction_processing'
export DB_PASSWORD='transaction_processing_dev'
export PROVIDER_BASE_URL='http://localhost:8081'
```

The example credentials are for local development only. Use secret-managed credentials outside a local environment.

## Run the application

Windows:

```powershell
.\mvnw.cmd spring-boot:run
```

macOS/Linux:

```shell
./mvnw spring-boot:run
```

The API uses port `8080` by default. The provided Compose service exposes PostgreSQL on host port `5433`; the example provider mock uses `8081`. Set `POSTGRES_PORT` and update `DB_URL` together if `5433` is unavailable. Standard Spring Boot configuration such as `SERVER_PORT` can change the API port.

Flyway runs automatically at startup and applies migrations from `src/main/resources/db/migration`. Hibernate is configured with `ddl-auto: validate`, so Flyway, not Hibernate, owns schema creation and evolution.

## API

### Create a transaction

```shell
curl --request POST 'http://localhost:8080/transactions' \
  --header 'Content-Type: application/json' \
  --data '{
    "accountId": "acc-123456",
    "type": "CREDIT",
    "amount": 1500.00,
    "currency": "MXN",
    "description": "Transferencia recibida"
  }'
```

The endpoint returns `201 Created` for both provider-approved (`EXECUTED`) and provider-declined (`REJECTED`) transactions. Its public response contains:

```json
{
  "id": "4ea989f5-6c80-420b-a9c2-3dac1e96f5e1",
  "accountId": "acc-123456",
  "type": "CREDIT",
  "amount": 1500.00,
  "currency": "MXN",
  "description": "Transferencia recibida",
  "status": "EXECUTED",
  "providerTransactionId": "provider-transaction-123",
  "balanceAfter": 8500.00,
  "createdAt": "2026-08-10T15:00:00Z"
}
```

Provider diagnostics are deliberately not exposed. `providerTransactionId` and `balanceAfter` are `null` for a rejected transaction.

Request constraints:

- `accountId` must be non-null and non-blank.
- `type` is `CREDIT` or `DEBIT`.
- `amount` must be greater than `1.00`; a debit cannot exceed `10000.00`.
- `currency` must be `MXN`.
- `description` is optional and is not sent to the external provider.

### List transactions

```shell
curl 'http://localhost:8080/transactions?accountId=acc-123456&status=EXECUTED&type=CREDIT&page=0&limit=20'
```

All query parameters are optional:

- `accountId`, `status`, and `type` filter the result.
- `page` is zero-based and defaults to `0`.
- `limit` defaults to `20` and must be between `1` and `100`.
- Results are ordered deterministically by `createdAt` descending and then `id` descending (newest first).

Only the public transaction fields shown above are returned. Provider status, provider timestamps, provider codes/messages, and internal errors remain private.

## Local provider mock

The application expects the provider to implement `POST /provider/v1/execute`. No standalone mock process is bundled. For manual testing, run an HTTP stub (for example WireMock) on port `8081` and set `PROVIDER_BASE_URL=http://localhost:8081`.

The service sends exactly these fields and does not retry automatically:

```json
{
  "accountId": "acc-123456",
  "type": "CREDIT",
  "amount": 1500.00,
  "currency": "MXN"
}
```

An approved response is HTTP `200` with this shape:

```json
{
  "transactionId": "provider-transaction-123",
  "status": "APPROVED",
  "balance": 8500.00,
  "executedAt": "2026-08-10T15:00:00Z"
}
```

A provider rejection is an HTTP `4xx` or `5xx` response with a body such as:

```json
{
  "code": "INSUFFICIENT_FUNDS",
  "message": "Insufficient funds"
}
```

The automated provider and end-to-end tests start WireMock on a dynamic port, so they do not require this manual stub.

## Tests

Unit tests (`*Test.java`):

```shell
./mvnw test
```

A focused integration test (`verify` also runs the unit-test phase first):

```shell
./mvnw -Dit.test=TransactionPostIT verify
```

Complete unit and integration verification:

```shell
./mvnw clean verify
```

On Windows, replace `./mvnw` with `.\mvnw.cmd`; PowerShell users should quote a property argument when needed, for example `"-Dit.test=TransactionPostIT"`. Integration tests use Testcontainers PostgreSQL and therefore require a running Docker engine. WireMock is started in-process by the provider integration tests.

## Architecture and tradeoffs

The project uses a lightweight ports-and-adapters flow:

```text
HTTP controller / request-response DTOs
  -> TransactionService
       -> TransactionProvider port
            -> HttpTransactionProvider / configured RestClient
       -> TransactionRepository
            -> PostgreSQL
```

- Money uses `BigDecimal` in Java and `NUMERIC` in PostgreSQL; binary floating-point is avoided.
- The provider is called before persistence because the provider owns balances and execution. `TransactionService` intentionally does not hold a database transaction open during the network call.
- There are no automatic retries. Retrying an ambiguous financial operation without an idempotency contract can duplicate execution.
- An HTTP response from the provider can confirm a business rejection and is persisted as `REJECTED`. A timeout, DNS error, connection refusal, or reset is a transport failure, not proof of rejection; it is treated separately as `FAILED`.
- A transport timeout remains ambiguous: the provider may have executed the request before connectivity was lost. A production system should add a provider-supported idempotency key and reconciliation workflow before considering retries.
- PostgreSQL `CHECK` constraints enforce valid types, amount/currency rules, and coherent `EXECUTED`, `REJECTED`, and `FAILED` column combinations. Indexes support deterministic newest-first pagination and account history queries.
- Offset/page pagination is intentionally simple for this challenge. At very high write rates, cursor pagination would avoid shifting pages, but is outside the current scope.

## Troubleshooting

- **`Could not resolve placeholder 'DB_URL'` or `PROVIDER_BASE_URL`:** export all four required variables in the same shell/process that starts Spring Boot. Creating `.env` alone does not configure the Java process.
- **Database connection refused:** run `docker compose ps` and `docker compose logs postgres`; confirm that the host port in `DB_URL` matches `POSTGRES_PORT` (default `5433`).
- **Provider connection refused:** start the local stub or provider and confirm that `PROVIDER_BASE_URL` is its base URL, without the `/provider/v1/execute` path.
- **Testcontainers cannot start:** ensure Docker is running and accessible to the current user.
- **Flyway validation fails:** do not edit an already-applied migration. Add a new versioned migration for schema changes, or recreate only disposable local data when appropriate.
- **Port already in use:** change `POSTGRES_PORT` plus `DB_URL`, the mock port plus `PROVIDER_BASE_URL`, or the API's `SERVER_PORT`.

Stop local PostgreSQL with:

```shell
docker compose down
```
