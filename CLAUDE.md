# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

AegisNotify is a mission-critical notification orchestration microservice built with Java 21 and Spring Boot 3.x. It centralizes delivery of Email, SMS, WhatsApp, and Push notifications with fault tolerance via circuit breakers, provider failover, and the Transactional Outbox pattern. Part of a microservices ecosystem using Eureka, API Gateway, Config Server, and Keycloak for auth.

## Build & Run Commands

```bash
# Build (skip tests)
./mvnw clean package -DskipTests

# Run tests
./mvnw test

# Run a single test class
./mvnw test -Dtest=ClassName

# Run a single test method
./mvnw test -Dtest=ClassName#methodName

# Run with Spring profile
./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# ArchUnit tests (architecture validation)
./mvnw test -Dtest="*ArchTest*"

# Testcontainers require Docker running
```

## Architecture

**Hexagonal / Clean Architecture** with three layers:

- **Domain** (`domain/`): Entities, value objects, enums, domain events, business rules. Zero framework dependencies.
- **Application** (`application/`): Use cases (ports), orchestration logic, template rendering, DTOs. Depends only on domain.
- **Infrastructure** (`infrastructure/`): REST controllers, Kafka/RabbitMQ consumers/producers, JPA repositories, WebClient provider adapters, security config, Resilience4j config. Implements application ports.

### Key Architectural Rules (enforced by ArchUnit)

- Domain layer MUST NOT depend on application or infrastructure
- Application layer MUST NOT depend on infrastructure
- Infrastructure adapters implement ports defined in application layer
- No Spring annotations in domain or application layers (except application service stereotypes)

### Core Components

| Component | Responsibility |
|---|---|
| Ingress API | `POST /api/v1/notifications` — validates, authenticates (JWT/Keycloak), returns 202 + UUID |
| Outbox Worker | Polls `outbox_events` table, publishes to broker, marks PROCESSED on ack |
| Consumer Engine | Dequeues messages, invokes template renderer, calls provider adapters |
| Template Engine | Thymeleaf/Mustache-based, merges templates with param maps, fails on missing vars |
| Resilience Core | Resilience4j circuit breakers per provider — 50% failure rate or 40% slow calls opens circuit |
| Telemetry Hub | Micrometer metrics exposed at `/actuator/prometheus` |

### Notification Lifecycle States

`PENDING → QUEUED → PROCESSING → SENT / SENT_VIA_FALLBACK / FAILED / FAILED_CRITICAL`

Provider failover: Primary (A) fails → circuit opens → fallback to Provider (B) → if B fails → DLQ + FAILED_CRITICAL

### Message Priority Routing

Messages route to priority-specific topics/queues: `high-priority-topic`, `medium-priority-topic`, `low-priority-topic`.

## Key Design Patterns

- **Transactional Outbox**: Notification + outbox event saved in same DB transaction. Background worker publishes to broker. Guarantees at-least-once delivery.
- **Circuit Breaker**: Resilience4j per provider channel. Failure threshold 50%/10 calls, slow call threshold 40%/2000ms, wait duration 60s in OPEN state.
- **Provider Fallback**: Each channel has primary + secondary provider. Fallback is automatic when circuit opens.
- **Dead Letter Queue**: Messages that fail both providers go to DLQ for manual intervention.

## Tech Stack

- Java 21, Spring Boot 3.x, Spring Web, Spring Data JPA
- Spring Security + OAuth2 Resource Server (Keycloak JWT, scope: `notification:write`)
- Apache Kafka or RabbitMQ (manual consumer acks)
- Resilience4j (CircuitBreaker, Retry, TimeLimiter)
- PostgreSQL / MySQL
- Micrometer + Prometheus registry
- Caffeine / Redis for template caching
- Testcontainers + JUnit 5 for integration tests
- ArchUnit for architecture validation
- Eureka (service discovery), API Gateway, Config Server

## Validation Rules

- EMAIL recipients: regex `^[\w-\.]+@([\w-]+\.)+[\w-]{2,4}$`
- SMS/WHATSAPP recipients: E.164 format (e.g., `+34600000000`)
- Channel enum: `EMAIL, SMS, WHATSAPP, PUSH`
- Priority enum: `HIGH, MEDIUM, LOW`
- Template must exist and be active (checked against cache/DB)

## Custom Metrics (Micrometer)

- `aegisnotify.requests.total` — counter by channel and priority
- `aegisnotify.provider.error.count` — counter with provider and error_code tags
- `aegisnotify.fallback.transmissions` — fallback activation counter
- `aegisnotify.delivery.latency.seconds` — timer/histogram for external API response times

## API Endpoints

- `POST /api/v1/notifications` — submit notification (requires Bearer JWT with `notification:write` scope)
- `GET /api/v1/notifications/{id}/status` — full audit trail with timestamped status timeline
- `GET /actuator/prometheus` — metrics endpoint
