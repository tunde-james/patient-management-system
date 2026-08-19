# Patient Management System

A production-ready microservices system for managing patient registration, billing provisioning, and operational analytics — built with Java 21, Spring Boot 3, and deployed via Docker.

Built by following the tutorial: [Build & Deploy a Production-Ready Patient Management System with Microservices: Java Spring Boot + AWS](https://www.youtube.com/watch?v=tseqdcFfTUY), then extended with additional features beyond the tutorial scope.

## Architecture

```
┌─────────────────┐       gRPC (sync)       ┌──────────────────┐
│  patient-service │ ─────────────────────► │  billing-service  │
│    :4000 (HTTP)  │                         │  :4001 (HTTP)     │
│                  │                         │  :9001 (gRPC)     │
└────────┬─────────┘                         └──────────────────┘
         │                                           │
         │ Kafka (async)                             │
         │ topic: patient.events                     │
         ▼                                           │
┌──────────────────┐                                 │
│ analytics-service│                                 │
│   :4002 (HTTP)   │                                 │
└──────────────────┘                                 │
                                                     │
   ┌────────────┐    ┌────────────┐    ┌────────────┐
   │patient-    │    │billing-    │    │analytics-  │
   │service-db  │    │service-db  │    │service-db  │
   │  :5000     │    │  :5001     │    │  :5002     │
   └────────────┘    └────────────┘    └────────────┘
         Postgres 17 (database-per-service)
```

## Services

| Service | Port | Description | Database |
|---------|------|-------------|----------|
| **patient-service** | `4000` | Patient CRUD, registration, billing provisioning (best-effort), Kafka event producer, reconciler | `patient-service-db` (:5000) |
| **billing-service** | `4001` | Billing account provisioning and lifecycle via gRPC | `billing-service-db` (:5001) |
| **analytics-service** | `4002` | Kafka consumer of patient events; counts-only REST read API for totals and per-day trends | `analytics-service-db` (:5002) |

### Inter-Service Communication

| Path | Protocol | Description |
|------|----------|-------------|
| patient → billing | **gRPC** (sync) | Provisions a billing account when a patient is created |
| patient → analytics | **Kafka** (async) | Emits `PATIENT_CREATED` / `PATIENT_CREATED_BILLING_FAILED` events on `patient.events` topic |

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 21 |
| Framework | Spring Boot 3.5 |
| Databases | PostgreSQL 17 (one per service) |
| Migrations | Flyway |
| Sync IPC | gRPC (protobuf) |
| Async IPC | Apache Kafka |
| Containerization | Docker / Docker Compose |
| API Docs | SpringDoc OpenAPI (Swagger UI) |
| Testing | JUnit 5, Mockito, Testcontainers (real Postgres), `@WebMvcTest` |
| Production Target | AWS (ECS / EKS) |

## Quick Start

### Prerequisites

- Java 21+
- Maven 3.9+
- Docker & Docker Compose

### 1. Set up environment variables

```bash
cp .env.example .env
```

Edit `.env` with your preferred credentials:

```env
# Patient Service DB
PATIENT_SERVICE_DB_USER=admin_user
PATIENT_SERVICE_DB_PASSWORD=password
PATIENT_SERVICE_DB_NAME=patient_db

# Billing Service DB
BILLING_SERVICE_DB_USER=admin_user
BILLING_SERVICE_DB_PASSWORD=password
BILLING_SERVICE_DB_NAME=billing_db

# Kafka
SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:9092

# gRPC
BILLING_SERVICE_ADDRESS=billing-service
```

### 2. Start infrastructure + services

```bash
docker compose up --build -d
```

This starts:
- Three Postgres 17 databases (one per service)
- Apache Kafka (KRaft mode, no ZooKeeper)
- patient-service and billing-service (built from local Dockerfiles)

> **Note:** analytics-service does not yet have a Dockerfile or docker-compose entry. Run it locally for now (see below).

### 3. Run analytics-service locally

```bash
cd analytics-service
mvn spring-boot:run
```

Make sure your local `application.yml` or environment variables point at the correct database and Kafka broker (`localhost:9094` for Kafka when running outside Docker).

### 4. Verify services are up

```bash
# Patient service health
curl http://localhost:4000/actuator/health

# Billing service health
curl http://localhost:4001/actuator/health

# Analytics service health
curl http://localhost:4002/actuator/health
```

## API Endpoints

### Patient Service (`:4000`)

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/patients` | Register a new patient (auto-provisions billing account) |
| `GET` | `/api/v1/patients` | List all patients |
| `GET` | `/api/v1/patients/{id}` | Get patient by ID |
| `PUT` | `/api/v1/patients/{id}` | Update patient |
| `DELETE` | `/api/v1/patients/{id}` | Delete patient |

Swagger UI: [http://localhost:4000/swagger-ui.html](http://localhost:4000/swagger-ui.html)

### Analytics Service (`:4002`)

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/v1/analytics/patients/total` | Lifetime patient totals by event type |
| `GET` | `/api/v1/analytics/patients/total?since=2026-08-01` | Totals filtered from a date |
| `GET` | `/api/v1/analytics/patients/by-day` | Per-day counts (default: last 30 days, sparse) |
| `GET` | `/api/v1/analytics/patients/by-day?from=2026-08-01&to=2026-08-10` | Per-day counts for a custom window |

Swagger UI: [http://localhost:4002/swagger-ui.html](http://localhost:4002/swagger-ui.html)

### Example Responses

**`GET /api/v1/analytics/patients/total`**

```json
{
  "total_enrolled": 42,
  "total_billing_failed": 3
}
```

**`GET /api/v1/analytics/patients/by-day`**

```json
{
  "from": "2026-07-12",
  "to": "2026-08-10",
  "buckets": [
    { "date": "2026-08-05", "enrolled": 4, "billing_failed": 1 },
    { "date": "2026-08-06", "enrolled": 3, "billing_failed": 0 }
  ]
}
```

## Running Tests

Each service has its own test suite. Tests use **Testcontainers** (requires Docker running) for real Postgres integration tests.

```bash
# All services from the repo root
cd patient-service  && mvn test && cd ..
cd billing-service  && mvn test && cd ..
cd analytics-service && mvn test && cd ..
```

## Project Structure

```
patient-management-system/
├── patient-service/          # Patient CRUD + billing provisioning + Kafka producer
│   ├── src/main/java/        # Controllers, services, gRPC client, Kafka producer
│   ├── src/main/proto/       # billing_service.proto, patient_events.proto
│   ├── src/main/resources/   # application.yml, Flyway migrations
│   └── Dockerfile
├── billing-service/          # Billing account provisioning (gRPC server)
│   ├── src/main/java/        # gRPC service impl, JPA entities
│   ├── src/main/proto/       # billing_service.proto
│   ├── src/main/resources/   # application.yml, Flyway migrations
│   └── Dockerfile
├── analytics-service/        # Kafka consumer + counts-only REST read API
│   ├── src/main/java/        # Kafka consumer, query service, REST controller
│   ├── src/main/proto/       # patient_events.proto (consumer copy)
│   ├── src/main/resources/   # application.yml, Flyway migrations
│   └── pom.xml
├── docker-compose.yml        # Local infrastructure (3 DBs + Kafka + services)
├── .env.example              # Template for environment variables
└── api-requests/             # Sample HTTP requests for manual testing
```

## Tutorial

This project follows the video tutorial linked above. Progress and learning records are maintained locally (gitignored):

- `learning-records/` — Permanent learning records from each module
- `docs/walkthroughs/` — Detailed code walkthroughs
- `.scratch/issues/` — Local issue tracker
