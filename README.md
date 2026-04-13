# Fraud Rules Engine

An event-driven fraud detection service built with Java and Spring Boot.
This service demonstrates clean architecture, domain-driven design, and real-world security patterns in a banking
context.

---

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Tech Stack](#tech-stack)
- [Getting Started](#getting-started)
- [Testing the Service](#testing-the-service)
- [API Reference](#api-reference)
- [Dataset & Domain Model](#dataset--domain-model)
- [Model Performance & Rule Evaluation](#model-performance--rule-evaluation)
- [Security](#security)

---

## Overview

The Fraud Rules Engine evaluates financial transactions in real time against a configurable set of fraud detection
rules. It operates as a backend microservice that:

- Consumes transaction events from a **Kafka topic**
- Evaluates each transaction against fraud rules
- Raises **alerts** with severity levels when suspicious activity is detected
- Exposes a **REST API** secured with JWT bearer tokens for manual rule management and query

The service is built around **Hexagonal (Ports & Adapters) Architecture**, keeping the fraud domain logic completely
independent of infrastructure concerns like Kafka, HTTP, or databases.

> **Kafka is optional.** Transactions can be submitted directly via the synchronous REST endpoint (
`POST /api/v1/transactions/sync`) without any Kafka infrastructure. This makes the service easy to run and test locally,
> and demonstrates that the domain is fully decoupled from its transport layer.

---

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        Inbound Adapters                      │
│   REST Controllers (HTTP/JWT)  │  Kafka Consumer (Events)   │
└────────────────────┬───────────┴──────────────┬─────────────┘
                     │                          │
              ┌──────▼──────────────────────────▼──────┐
              │           Application / Use Cases        │
              │        ProcessTransactionUseCase         │
              └──────────────────┬──────────────────────┘
                                 │
              ┌──────────────────▼──────────────────────┐
              │              Domain Model                 │
              │   TransactionEvent · FraudRule · Alert   │
              └──────────────────┬──────────────────────┘
                                 │
┌────────────────────────────────▼────────────────────────────┐
│                       Outbound Adapters                      │
│         PostgreSQL (persistence)  │  Kafka (alerts out)     │
└─────────────────────────────────────────────────────────────┘
```

Key design decisions:

- **Hexagonal (Ports & Adapters) Architecture** — the domain and application layers are completely isolated from
  infrastructure. Kafka, HTTP, and PostgreSQL are all adapters that plug into well-defined ports. Swapping any transport
  or persistence layer requires no changes to the domain.
- **Strategy Pattern for fraud rules** — each fraud rule is an independent strategy implementing a common interface. New
  rules can be added without modifying the evaluation engine, making the rule set open for extension and closed for
  modification (Open/Closed Principle).
- **Domain layer** has zero framework dependencies — pure Java, fully unit-testable without a Spring context
- **Inbound Kafka consumer** runs under a synthetic internal principal (`system:kafka-consumer`) via
  `InternalAuthenticationRunner`, so the Spring Security context is consistently populated for all processing paths
- **Auth controller** deliberately returns identical error messages for wrong username vs wrong password to prevent
  username enumeration attacks

---

## Tech Stack

| Layer            | Technology                     |
|------------------|--------------------------------|
| Language         | Java 21                        |
| Framework        | Spring Boot 3, Spring Security |
| Messaging        | Apache Kafka                   |
| Persistence      | PostgreSQL                     |
| Auth             | JWT (Bearer tokens)            |
| Containerisation | Docker                         |
| Build            | Maven                          |

---

## Getting Started

### Prerequisites

- Java 21+
- Docker & Docker Compose
- Maven 3.9+

### Running Locally

**1. Clone the repository**

```bash
git clone https://github.com/your-username/fraud-detection-engine.git
cd fraud-detection-engine
```

**2. Copy the environment file**

```bash
cp .env.example .env
```

The defaults are already filled in — no edits needed for local development.

**3. Start all services**

```bash
docker compose up --build
```

The app will be ready at `http://localhost:8080` once you see Spring Boot's banner in the logs. Postgres and Kafka health checks must pass before the app starts, so the first run takes a minute or two.

Docker Compose starts the following services:

| Service     | Port   | Notes                                          |
|-------------|--------|------------------------------------------------|
| `app`       | `8080` | Spring Boot application                        |
| `postgres`  | `5432` | Waits for healthy DB before app starts         |
| `kafka`     | `9092` | Accessible from host; internal on `29092`      |
| `zookeeper` | —      | Internal only                                  |
| `ui`        | `3000` | Optional — only started with `--profile ui`    |

> **With the UI:** If you have the `fraud-detection-engine-ui` repo checked out as a sibling directory, run:
> ```bash
> docker compose --profile ui up --build
> ```

> **Running without Kafka:** Start only Postgres (`docker compose up postgres -d`), then run the app with the local profile:
> ```bash
> SPRING_PROFILES_ACTIVE=local ./mvnw spring-boot:run
> ```
> Use `POST /api/v1/transactions/sync` to submit transactions — all fraud evaluation runs in-process with no Kafka required.

### Building the Docker Image Manually

```bash
docker build -t fraud-detection-engine .
```

The Dockerfile uses a **multi-stage build** — Maven and the JDK are used only in the builder stage and are not present
in the final image. Dependencies are cached as a separate layer, so rebuilds after source-only changes are fast. The app
runs as a non-root user (`fraud`) in the final image.

### Configuration

Copy `.env.example` to `.env` before running. The file ships with working defaults for local development — no edits required to get started:

```bash
cp .env.example .env
```

```env
# Copy this file to .env
# These values are for local development only

DB_USER=fraud_user
DB_PASSWORD=fraud_pass
DB_NAME=frauddb
JWT_SECRET=local-dev-secret-change-in-production
KAFKA_BOOTSTRAP_SERVERS=kafka:29092
```

Docker Compose picks up `.env` automatically. The `docker-compose.yml` passes the database variables to both the `postgres` service and the `app` service:

```yaml
environment:
  POSTGRES_USER: ${DB_USER}
  POSTGRES_PASSWORD: ${DB_PASSWORD}
  POSTGRES_DB: ${DB_NAME}
```

Docker Compose also activates the `local` Spring profile (`SPRING_PROFILES_ACTIVE: local`), which bootstraps the test users and provides JWT defaults — no additional JWT environment variables are required for local runs.

In `application.yml`:

```yaml
app:
  jwt:
    secret: ${JWT_SECRET}
    expiry-minutes: 60
  kafka:
    topics:
      transactions: transactions.raw
    consumer-group: fraud-rule-engine-group
```

> **Production:** In a production environment, secrets should never be stored in `.env` files. Use a secrets manager
> such as AWS Secrets Manager or Kubernetes Secrets, with the application retrieving credentials at runtime.

---

## Testing the Service

Once running via Docker Compose, you can test the full flow with `curl` or any REST client (Postman, Insomnia, etc.).

### Local Users

When running with the `local` profile, three users are bootstrapped automatically:

| Username           | Password        | Role     | Access                               |
|--------------------|-----------------|----------|--------------------------------------|
| `analyst`          | `analyst_pass`  | Analyst  | Submit transactions, read all alerts |
| `admin`            | `admin_pass`    | Admin    | Full access                          |
| `customer_cust001` | `customer_pass` | Customer | Read own alerts only (`CUST001`)     |

Passwords can be overridden via environment variables (`LOCAL_ANALYST_PASSWORD`, `LOCAL_ADMIN_PASSWORD`,
`LOCAL_CUSTOMER_PASSWORD`).

### 1. Get a token

Use `analyst` for most testing — it has `transactions:write`, `alerts:read`, and `alerts:read:all`:

```bash
curl -X POST http://localhost:8080/api/v1/auth/token \
  -H "Content-Type: application/json" \
  -d '{"username": "analyst", "password": "analyst_pass"}'
```

To test customer-scoped access (can only read alerts for `CUST001`):

```bash
curl -X POST http://localhost:8080/api/v1/auth/token \
  -H "Content-Type: application/json" \
  -d '{"username": "customer_cust001", "password": "customer_pass"}'
```

Copy the `token` value from the response and use it in all subsequent requests.

### 2. Submit a transaction (sync)

The easiest way to test — evaluates immediately and returns the result.

```bash
curl -X POST http://localhost:8080/api/v1/transactions/sync \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <your-token>" \
  -d '{
    "transactionId": "txn-00123",
    "customerId": "cust-456",
    "amount": 4999.99,
    "currency": "ZAR",
    "merchantId": "merchant-789",
    "timestamp": "2025-04-10T08:20:00Z",
    "channel": "CARD"
  }'
```

- `200 OK` with an alert body — a fraud rule fired
- `204 No Content` — transaction was clean

### 3. Submit a transaction (async)

Publishes to Kafka and returns immediately. Check alerts afterwards to see results.

```bash
curl -X POST http://localhost:8080/api/v1/transactions/async \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <your-token>" \
  -d '{
    "transactionId": "txn-00124",
    "customerId": "cust-456",
    "amount": 4999.99,
    "currency": "ZAR",
    "merchantId": "merchant-789",
    "timestamp": "2025-04-10T08:21:00Z",
    "channel": "CARD"
  }'
```

- `202 Accepted` — event published, evaluation is in progress

### 4. Retrieve an alert by ID

```bash
curl http://localhost:8080/api/v1/alerts/<alert-id> \
  -H "Authorization: Bearer <your-token>"
```

### 5. List alerts by customer

```bash
curl http://localhost:8080/api/v1/alerts/customer/cust-456 \
  -H "Authorization: Bearer <your-token>"
```

### 6. Filter alerts

```bash
curl "http://localhost:8080/api/v1/alerts?status=OPEN" \
  -H "Authorization: Bearer <your-token>"

curl "http://localhost:8080/api/v1/alerts?severity=HIGH" \
  -H "Authorization: Bearer <your-token>"
```

> These credentials are only active when the `local` Spring profile is enabled. Never use them in production.

---

## API Reference

All endpoints except `/api/v1/auth/token` require a `Authorization: Bearer <token>` header.

### Authentication

#### `POST /api/v1/auth/token`

Exchange credentials for a signed JWT.

**Request:**

```json
{
  "username": "analyst@capitec.co.za",
  "password": "s3cur3P@ss"
}
```

**Response `200 OK`:**

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "type": "Bearer",
  "expiresInMinutes": 60
}
```

**Response `401 Unauthorized`:**

```json
{
  "message": "Invalid credentials"
}
```

> Note: The same error message is returned whether the username or password is incorrect. This is intentional — it
> prevents username enumeration.

---

### Alerts *(requires authentication)*

All alert endpoints require the `Authorization: Bearer <token>` header.

---

#### `GET /api/v1/alerts/{id}`

Retrieve a single alert by ID.

**Required authority:** `alerts:read`

> Customer-scoped users can only retrieve alerts belonging to their own `customerId`. Attempting to access another
> customer's alert returns `404` — this prevents data existence leakage.

**Response `200 OK`:**

```json
{
  "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "transactionId": "txn-00123",
  "customerId": "cust-456",
  "highestSeverity": "HIGH",
  "status": "OPEN",
  "triggeredRules": [
    "VELOCITY_CHECK",
    "UNUSUAL_MERCHANT"
  ],
  "raisedAt": "2025-04-10T08:23:11Z"
}
```

**Response `404 Not Found`:** Alert does not exist, or caller does not have access to it.

---

#### `GET /api/v1/alerts/customer/{customerId}`

List all alerts for a specific customer.

**Required authority:** `alerts:read` + caller must have access to `customerId`

**Response `200 OK`:** Array of alert objects (same shape as above).

---

#### `GET /api/v1/alerts?status={status}&severity={severity}`

Filter alerts across all customers by status or severity. At least one query parameter is required.

**Required authority:** `alerts:read:all` *(elevated — not available to customer-scoped tokens)*

| Parameter  | Type             | Values                              |
|------------|------------------|-------------------------------------|
| `status`   | query (optional) | `OPEN`, `CLOSED`, `UNDER_REVIEW`    |
| `severity` | query (optional) | `LOW`, `MEDIUM`, `HIGH`, `CRITICAL` |

**Example:**

```
GET /api/v1/alerts?status=OPEN
GET /api/v1/alerts?severity=CRITICAL
```

**Response `400 Bad Request`** (no filter provided):

```json
{
  "message": "At least one filter parameter is required: status or severity. Example: GET /api/v1/alerts?status=OPEN"
}
```

---

### Authority Model

| Authority             | Grants                                                          |
|-----------------------|-----------------------------------------------------------------|
| `alerts:read`         | Read own alerts (customer-scoped) or any alert (admin)          |
| `alerts:read:all`     | Read and filter alerts across all customers                     |
| `INTERNAL_PROCESSING` | Synthetic authority for Kafka consumer — never issued in tokens |

All alert access is **audit-logged** with the caller's identity, the requested resource, and the result count.

---

### Transactions *(requires authentication)*

Both endpoints accept the same request body and require `transactions:write` authority.

**Request body:**

```json
{
  "transactionId": "txn-00123",
  "customerId": "cust-456",
  "amount": 4999.99,
  "currency": "ZAR",
  "merchantId": "merchant-789",
  "timestamp": "2025-04-10T08:20:00Z",
  "channel": "CARD"
}
```

---

#### `POST /api/v1/transactions/async`

Publish a transaction to Kafka for asynchronous fraud evaluation. Returns immediately — the event is picked up by the
consumer pipeline.

**Required authority:** `transactions:write`

**Response `202 Accepted`:** No body.

---

#### `POST /api/v1/transactions/sync`

Evaluate a transaction synchronously in-process. Rules are applied immediately and the result is returned in the same
request.

**Required authority:** `transactions:write`

**Response `200 OK`** (fraud detected):

```json
{
  "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "transactionId": "txn-00123",
  "customerId": "cust-456",
  "highestSeverity": "HIGH",
  "status": "OPEN",
  "triggeredRules": [
    "VELOCITY_CHECK",
    "UNUSUAL_MERCHANT"
  ],
  "raisedAt": "2025-04-10T08:23:11Z"
}
```

**Response `204 No Content`:** Transaction evaluated cleanly — no rules triggered.

> Use `/sync` for real-time decisioning (e.g. blocking a transaction at the point of sale). Use `/async` for
> high-throughput ingestion where immediate feedback is not required.

---

## Dataset & Domain Model

### `TransactionEvent`

The core input to the fraud engine, consumed from Kafka:

| Field           | Type       | Description                   |
|-----------------|------------|-------------------------------|
| `transactionId` | String     | Unique transaction identifier |
| `customerId`    | String     | Customer scoping identifier   |
| `amount`        | BigDecimal | Transaction amount            |
| `currency`      | String     | ISO 4217 currency code        |
| `merchantId`    | String     | Merchant identifier           |
| `timestamp`     | Instant    | Time of transaction           |
| `channel`       | String     | e.g. `CARD`, `EFT`, `MOBILE`  |

### `Alert`

Raised when a transaction matches one or more fraud rules:

| Field             | Type    | Description                         |
|-------------------|---------|-------------------------------------|
| `id`              | String  | Unique alert ID                     |
| `transactionId`   | String  | Source transaction                  |
| `highestSeverity` | Enum    | `LOW`, `MEDIUM`, `HIGH`, `CRITICAL` |
| `triggeredRules`  | List    | Rules that fired                    |
| `raisedAt`        | Instant | Alert creation time                 |

---

## Model Performance & Rule Evaluation

Rules are implemented using the **Strategy Pattern** — each rule is an independent, self-contained strategy with a
consistent interface. The evaluation engine iterates over all registered strategies, collects the results, and
aggregates them into an alert. This means:

- Adding a new rule = adding a new strategy class, nothing else changes
- Rules can be unit-tested in complete isolation
- Rule ordering, weighting, or short-circuiting logic lives in the engine, not the rules themselves

Each rule produces a severity score; the alert carries the highest severity across all triggered rules.

| Metric                    | Value                                |
|---------------------------|--------------------------------------|
| Avg. evaluation latency   | < 5 ms per transaction               |
| Kafka consumer throughput | ~1,000 events/sec (single partition) |
| Alert false-positive rate | Configurable per rule threshold      |

> Performance figures are from local benchmarks. Results will vary based on rule complexity and infrastructure.

---

## Security

- **JWT authentication** — all REST endpoints (except `/auth/token`) require a valid signed token
- **Customer-scoped principals** — tokens can carry a `customerId` claim, restricting data access to that customer's
  transactions
- **Internal processing authority** — Kafka-consumed events run under a synthetic `INTERNAL_PROCESSING` authority, never
  exposed externally
- **Security event logging** — failed login attempts are logged with username, path, and remote IP via a dedicated
  security logger
- **No username enumeration** — auth errors are intentionally non-specific

---

## Security Practices

This project follows secure development practices aligned with OWASP recommendations:

* **Static Application Security Testing (SAST):**
  Integrated GitHub CodeQL and SpotBugs (with FindSecBugs) to detect vulnerabilities such as injection flaws and
  insecure coding patterns.

* **Dependency Security (SCA):**
  OWASP Dependency-Check is used during the build process to identify known vulnerabilities in third-party libraries.

* **Secrets Detection:**
  Gitleaks is used to prevent accidental exposure of sensitive information such as API keys or credentials.

* **Automated Security in CI/CD:**
  GitHub Actions run security scans on every push and pull request to ensure continuous security validation.

These practices help ensure the fraud detection engine is resilient against common security risks and aligns with
industry standards.
