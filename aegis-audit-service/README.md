# Aegis Audit Service

Audit trail service for the AegisNotify platform: it consumes notification audit events from Kafka, stores them in MongoDB, and serves read-only audit queries.

## Responsibility

- Consumes events from the `notification-audit-events` Kafka topic (consumer group `audit-service`) with manual offset acknowledgment; an offset is acknowledged only after the event is processed.
- Encrypts the recipient with AES-256-GCM before persisting it to MongoDB.
- Exposes audit queries under `/api/v1/audit` on port 8083:
  - `GET /api/v1/audit/{notificationId}` returns the audit trail of one notification.
  - `GET /api/v1/audit` searches events by `channel`, `status`, `from`, `to`, `page`, and `size`.

## Security

- Stateless JWT resource server; tokens are validated against `JWKS_URI` (default: the local Keycloak realm).
- `/api/v1/audit/**` requires the `audit:read` scope; any other request must be authenticated.
- Public endpoints: `/actuator/health/**`, `/actuator/info`, and `/actuator/prometheus`. Review this exposure before a non-local deployment.
- `AUDIT_ENCRYPTION_KEY` (Base64, decoding to 32 bytes) has no default in `application.yml`. Only the `local` profile (`application-local.yml`) provides a development fallback; other environments must supply the key.
- Eureka credentials come from `EUREKA_SECURITY_USER` / `EUREKA_SECURITY_PASSWORD`.
- The service is also reachable through the API Gateway, which enforces the same scope on `/api/v1/audit/**`.

## Startup order and dependencies

| Dependency | Default | Notes |
| --- | --- | --- |
| Eureka Server | `http://localhost:8761/eureka/` | Credentials are embedded in the registration URL |
| Kafka | `KAFKA_BOOTSTRAP_SERVERS` = `localhost:9092` | Source of audit events |
| MongoDB | `MONGODB_URI` = `mongodb://localhost:27017/aegisnotify-audit` | Audit persistence |
| Keycloak (JWKS) | `JWKS_URI` | Token signature validation |
| Config Server | `optional:configserver:` | Optional; the service starts without it |

Start it after Eureka, Kafka, and MongoDB, and before (or independently of) the API Gateway. The events it consumes are published by `aegis-notification-service`.

```bash
export EUREKA_SECURITY_USER=... EUREKA_SECURITY_PASSWORD=...
./mvnw -pl aegis-audit-service spring-boot:run -Dspring-boot.run.profiles=local
```

See the [root README](../README.md) for the full platform setup.
