# AegisNotify

Resilient notification orchestration platform built with Java 21 and Spring Boot 3.x. Centralizes delivery of Email, SMS, WhatsApp, and Push notifications with fault tolerance via circuit breakers, provider failover, and the Transactional Outbox pattern.

## Architecture

```
                    ┌──────────────────┐
                    │   API Gateway    │
                    └────────┬─────────┘
                             │
              ┌──────────────┼──────────────┐
              │              │              │
   ┌──────────▼───┐  ┌──────▼──────┐  ┌────▼────────────┐
   │ Eureka Server │  │Config Server│  │Notification Svc  │
   │    :8761      │  │   :8888     │  │     :8082        │
   └───────────────┘  └─────────────┘  └──────┬───────────┘
                                              │
                                    ┌─────────┼─────────┐
                                    │         │         │
                                ┌───▼──┐ ┌────▼──┐ ┌───▼───┐
                                │Outbox│ │Broker │ │  DB   │
                                │Worker│ │Kafka/ │ │Postgre│
                                │      │ │Rabbit │ │  SQL  │
                                └──────┘ └───────┘ └───────┘
```

## Modules

| Module | Port | Description |
|--------|------|-------------|
| [`aegis-eureka-server`](aegis-eureka-server/) | 8761 | Service discovery (Netflix Eureka) |
| [`aegis-config-server`](aegis-config-server/) | 8888 | Centralized configuration (Spring Cloud Config, Git-backed) |
| `aegis-notification-service` | 8082 | Core notification microservice (Ingress API, Outbox, Providers) |

## Tech Stack

- **Runtime**: Java 21, Spring Boot 3.4.1, Spring Cloud 2024.0.0
- **Persistence**: PostgreSQL, Flyway, Spring Data JPA
- **Messaging**: Apache Kafka / RabbitMQ (planned)
- **Resilience**: Resilience4j (Circuit Breaker, Retry, TimeLimiter)
- **Security**: Spring Security, OAuth2 Resource Server, Keycloak
- **Observability**: Micrometer, Prometheus
- **Testing**: JUnit 5, Testcontainers, ArchUnit
- **Code Quality**: Checkstyle (Google style), ArchUnit (hexagonal layer enforcement)

## Prerequisites

- Java 21+
- Maven 3.9+ (wrapper included)
- Docker (for Testcontainers and local infrastructure)
- PostgreSQL 15+ (or use Docker)

## Build

```bash
# Build all modules (skip tests)
./mvnw clean package -DskipTests

# Build and run tests
./mvnw clean verify

# Checkstyle validation
./mvnw checkstyle:check

# Run a specific module
./mvnw spring-boot:run -pl aegis-eureka-server
```

## Startup Order

1. **Eureka Server** — service registry must be available first
2. **Config Server** — registers with Eureka, serves configuration
3. **Notification Service** — fetches config, registers with Eureka

## Notification Lifecycle

```
PENDING → QUEUED → PROCESSING → SENT
                              → SENT_VIA_FALLBACK
                              → FAILED
                              → FAILED_CRITICAL
```

Provider failover: Primary fails → circuit opens → fallback provider → if both fail → DLQ + `FAILED_CRITICAL`

## Project Structure

The project follows **Hexagonal (Ports & Adapters) Architecture** in the notification service:

```
domain/          → Entities, value objects, enums, business rules (zero framework deps)
application/     → Use cases, ports, DTOs, orchestration (@Service + @Transactional)
infrastructure/  → Controllers, JPA repos, adapters, security, resilience config
```

Architectural rules are enforced at build time by ArchUnit tests.

## License

This project is part of a personal portfolio. All rights reserved.
