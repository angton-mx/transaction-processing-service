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

## Manual end-to-end run

The following PowerShell flow starts every dependency explicitly and exercises the complete client -> application -> provider mock -> PostgreSQL -> GET retrieval path.

### 1. Clone and configure

```powershell
git clone https://github.com/angton-mx/transaction-processing-service.git
Set-Location transaction-processing-service
Copy-Item .env.example .env
```

The application uses these local values:

| Variable | Local value | Purpose |
| --- | --- | --- |
| `DB_URL` | `jdbc:postgresql://localhost:5433/transaction_processing` | JDBC connection URL |
| `DB_USERNAME` | `transaction_processing` | PostgreSQL user |
| `DB_PASSWORD` | `transaction_processing_dev` | PostgreSQL password |
| `PROVIDER_BASE_URL` | `http://localhost:8081` | Base URL of the external execution provider |

Docker Compose reads the root `.env` file, but Spring Boot does **not** import it automatically. The variables must still be exported in the terminal that starts Java. The example credentials are for local development only.

### 2. Start PostgreSQL

```powershell
docker compose up -d postgres
docker compose ps
```

PostgreSQL runs detached on host port `5433`. Before continuing, confirm that `transaction-processing-service-postgres-1` reports `healthy`.

### 3. Start the WireMock provider

In a second terminal, run WireMock 3.13.2 in the foreground and leave it open:

```powershell
docker run --rm --name transaction-provider-mock `
  -p 8081:8080 `
  wiremock/wiremock:3.13.2 --verbose
```

On macOS/Linux, the same command can be written on one line:

```bash
docker run --rm --name transaction-provider-mock -p 8081:8080 wiremock/wiremock:3.13.2 --verbose
```

No standalone mock is bundled with the application; this container is the manual provider.

### 4. Configure and verify an approved response

In a third PowerShell terminal, create the provider mapping through WireMock's Admin API:

```powershell
$mapping = @'
{
  "request": {
    "method": "POST",
    "urlPath": "/provider/v1/execute"
  },
  "response": {
    "status": 200,
    "headers": {
      "Content-Type": "application/json"
    },
    "jsonBody": {
      "transactionId": "txn-manual-001",
      "status": "APPROVED",
      "balance": 5500.00,
      "executedAt": "2026-08-10T19:00:00Z"
    }
  }
}
'@
Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8081/__admin/mappings" `
  -ContentType "application/json" `
  -Body $mapping
```

Verify that WireMock metadata shows one configured mapping:

```powershell
Invoke-RestMethod http://localhost:8081/__admin/mappings
```

### 5. Start the application

In another terminal, export the variables and start Spring Boot:

```powershell
$env:DB_URL = "jdbc:postgresql://localhost:5433/transaction_processing"
$env:DB_USERNAME = "transaction_processing"
$env:DB_PASSWORD = "transaction_processing_dev"
$env:PROVIDER_BASE_URL = "http://localhost:8081"

.\mvnw.cmd spring-boot:run
```

After Spring Boot reports that the application has started, the API is available at `http://localhost:8080`. Flyway applies the schema automatically; Hibernate validates it rather than creating it.

For macOS/Linux, export the same values and run `./mvnw spring-boot:run`.

### 6. Execute the POST smoke test

From a separate terminal:

```bash
curl --location 'http://localhost:8080/transactions' \
--header 'Content-Type: application/json' \
--data '{
  "accountId": "acc-123456",
  "type": "CREDIT",
  "amount": 1500.00,
  "currency": "MXN",
  "description": "Manual smoke test"
}'
```

The response is `HTTP 201 Created` and contains generated `id` and `createdAt` values plus these fields:

```json
{
  "accountId": "acc-123456",
  "type": "CREDIT",
  "amount": 1500.00,
  "currency": "MXN",
  "description": "Manual smoke test",
  "status": "EXECUTED",
  "providerTransactionId": "txn-manual-001",
  "balanceAfter": 5500.00
}
```

Postman users can import the request directly with **Import -> Raw text** and paste the cURL command.

### 7. Verify persistence

```bash
curl --location 'http://localhost:8080/transactions?accountId=acc-123456&page=0&limit=20'
```

The returned JSON array should contain the previously created `EXECUTED` transaction. This verifies the complete manual path through the provider mock, PostgreSQL persistence, and GET retrieval.

### 8. Clean up

Stop Spring Boot with `Ctrl+C`. Stop and remove the provider plus PostgreSQL resources with:

```powershell
docker rm -f transaction-provider-mock
docker compose down
```

Stopping the foreground WireMock terminal with `Ctrl+C` is also sufficient because the container was started with `--rm`.

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

Invalid business input is rejected before service/provider execution with `400 Bad Request` and a concise domain message:

```json
{
  "error": "Transaction amount must be greater than 1.00"
}
```

### List transactions

```shell
curl 'http://localhost:8080/transactions?accountId=acc-123456&status=EXECUTED&type=CREDIT&page=0&limit=20'
```

All query parameters are optional:

- `accountId`, `status`, and `type` filter the result.
- `page` is zero-based and defaults to `0`.
- `limit` defaults to `20` and must be between `1` and `100`.
- Results are ordered deterministically by `createdAt` descending and then `id` descending (newest first).

The response is a JSON array of the public transaction objects shown above; pagination metadata is not included. Provider status, provider timestamps, provider codes/messages, and internal errors remain private.

## Provider contract

The application expects the provider to implement `POST /provider/v1/execute`. The manual flow above starts WireMock explicitly with Docker; no standalone mock process is bundled with the application.

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

If the provider cannot be reached or the connection fails, the service stores a `FAILED` transaction with an unknown provider outcome and returns an empty `503 Service Unavailable` response. This path is not retried automatically.

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

## Verification summary

The final delivery was verified with 40 unit tests and 57 integration tests: 97 total, with 0 failures, 0 errors, and 0 skipped. GitHub Actions CI is required to pass before merge.

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

## Persistence decision

PostgreSQL was selected because financial transaction records need reliable, transactional persistence and deterministic querying. Database constraints provide a second integrity boundary behind domain validation, while indexes support account-history filters and newest-first reads. It is a more realistic production-oriented choice than an in-memory database, and Flyway keeps schema evolution explicit and repeatable. PostgreSQL does not by itself solve every scaling concern; capacity, query behavior, and data lifecycle still require operational planning.

## Scalability considerations

Implemented behavior for the challenge:

- The REST service is stateless and can be replicated horizontally.
- The external provider owns account balances and locking.
- Provider HTTP execution occurs without holding a database transaction open.
- PostgreSQL indexes support the current account-history and newest-first query patterns.
- Ambiguous financial operations are never retried blindly.
- Page/offset pagination is a pragmatic bounded implementation for the challenge.

Potential production evolution at substantially higher write and persisted-data volumes:

- Prefer cursor/keyset pagination to avoid shifting pages and large offsets.
- Evaluate time- or account-access-based partitioning against measured query and retention needs.
- Add provider-supported idempotency and reconciliation before introducing safe retries.

## AI Usage

OpenAI ChatGPT and Codex were used extensively throughout the development process. I defined the system design, architecture, business rules, implementation direction, and engineering decisions, while using AI as a development assistant to help translate those decisions into code, create and refine tests, debug issues, review implementation details, and validate the final solution.

AI was also used during the final review of the challenge to verify requirement coverage and to improve the README so that the project setup, execution flow, external provider mock, and API testing steps are clearly documented and reproducible.

All delivered code and documentation were reviewed, tested, and validated by me, and I take responsibility for the final implementation and the engineering tradeoffs made in the project.

## Troubleshooting

- **`Could not resolve placeholder 'DB_URL'` or `PROVIDER_BASE_URL`:** export all four required variables in the same shell/process that starts Spring Boot. Creating `.env` alone does not configure the Java process.
- **Database connection refused:** run `docker compose ps` and `docker compose logs postgres`; confirm that the host port in `DB_URL` matches `POSTGRES_PORT` (default `5433`).
- **Provider connection refused:** confirm that the `transaction-provider-mock` container is running and that `PROVIDER_BASE_URL` is its base URL, without the `/provider/v1/execute` path.
- **Testcontainers cannot start:** ensure Docker is running and accessible to the current user.
- **Flyway validation fails:** do not edit an already-applied migration. Add a new versioned migration for schema changes, or recreate only disposable local data when appropriate.
- **Port already in use:** change `POSTGRES_PORT` plus `DB_URL`, the mock port plus `PROVIDER_BASE_URL`, or the API's `SERVER_PORT`.
