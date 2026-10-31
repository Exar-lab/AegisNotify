# Aegis Audit Service

Audit trail service for the AegisNotify platform. Consumes notification audit events from Kafka, encrypts the recipient, stores events in MongoDB, and exposes them through a read-only HTTP API.

## Overview

Responsibility:

- Ingest audit events from the `notification-audit-events` Kafka topic.
- Encrypt the recipient with AES-GCM before persistence.
- Persist events in MongoDB.
- Serve audit queries by notification id or by filtered search.

Package `com.aegisnotify.audit` follows the hexagonal layout:

```
KafkaAuditEventConsumer -> ConsumeAuditEventService -> AesGcmEncryptionAdapter (recipient)
                                                    -> MongoAuditEventRepositoryAdapter
AuditQueryController (REST) -> query use cases -> MongoAuditEventRepositoryAdapter
```

## Endpoints

| Endpoint | Required authority | Description |
|----------|--------------------|-------------|
| `GET /api/v1/audit/{notificationId}` | `audit:read` | Audit trail of one notification |
| `GET /api/v1/audit` | `audit:read` | Search by `channel`, `status`, `from`, `to`, `page`, `size` |
| `GET /actuator/health/**`, `/actuator/info`, `/actuator/prometheus` | none | Public |

See the root [README](../README.md#query-an-audit-trail) for request examples.

## Configuration

| Property | Env Variable | Default | Description |
|----------|--------------|---------|-------------|
| `server.port` | — | 8083 | HTTP port |
| `spring.kafka.bootstrap-servers` | `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka brokers |
| `spring.data.mongodb.uri` | `MONGODB_URI` | `mongodb://localhost:27017/aegisnotify-audit` | MongoDB connection |
| `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` | `JWKS_URI` | Keycloak `localhost:8088`, realm `aegis` | JWT signing keys |
| `audit.encryption.key` | `AUDIT_ENCRYPTION_KEY` | none | AES key used to encrypt recipients |
| `audit.topic` | — | `notification-audit-events` | Consumed topic |
| `audit.consumer.group-id` | — | `audit-service` | Kafka consumer group |
| `eureka.client.service-url.defaultZone` | — | `http://localhost:8761/eureka/` | Eureka registry |

`spring.config.import` is `optional:configserver:`, so the service starts without a Config Server.

## Security

- Stateless OAuth2 JWT resource server; CSRF is disabled.
- Public: `/actuator/health/**`, `/actuator/info`, `/actuator/prometheus`.
- `/api/v1/audit/**` requires authority `audit:read`; any other request requires authentication.
- Requests sent directly to this service are validated again; they do not depend on gateway enforcement.
- The recipient is encrypted at rest with AES-GCM. `AUDIT_ENCRYPTION_KEY` has no default in `application.yml`: startup fails if it is unset.
- Profile `local` (`application-local.yml`) defines a development-only fallback key. Do not use it outside local development.
- Kafka consumer: manual acknowledgement, deserialization restricted to `com.aegisnotify.audit.application.dto`, retries with a fixed 1 second backoff, then the record is published to a dead-letter topic by `DeadLetterPublishingRecoverer`.

## API documentation

Disabled by default. Enabled only with the Spring profile `local` (`-Dspring-boot.run.profiles=local`), where it is public (no JWT):

- OpenAPI JSON: `http://localhost:<port>/v3/api-docs`
- Swagger UI: `http://localhost:<port>/swagger-ui/index.html`

Without the `local` profile these paths are not served and require authentication (401).

## Startup order and dependencies

Start after:

1. Eureka Server (`aegis-eureka-server`, 8761) for registration.
2. Optionally Config Server (`aegis-config-server`, 8888).
3. Kafka (9092), MongoDB (27017), and Keycloak (8088, JWKS endpoint).

The service is independent of the other domain services at startup. The API Gateway does not currently define an audit route in `application.yml`; clients call this service directly on port 8083.

## Run

```bash
# From project root
export AUDIT_ENCRYPTION_KEY=<base64 AES key>
./mvnw spring-boot:run -pl aegis-audit-service

# Local development (uses the fallback key)
./mvnw spring-boot:run -pl aegis-audit-service -Dspring-boot.run.profiles=local
```

## Known limitations

- No gateway route for audit endpoints in this checkout.

## Dependencies

- Spring Web, Validation, Actuator
- Spring Security OAuth2 Resource Server
- Spring Data MongoDB
- Spring Kafka
- Spring Cloud Netflix Eureka Client and Config client
