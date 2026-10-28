# Aegis Eureka Server

Service discovery server for the AegisNotify platform using Netflix Eureka.

## Overview

All microservices register with this server to enable dynamic service-to-service communication without hardcoded URLs. The Eureka Server maintains a registry of available service instances and their locations.

## Configuration

| Property | Value | Description |
|----------|-------|-------------|
| `server.port` | 8761 | Default Eureka port |
| `eureka.client.register-with-eureka` | false | Does not register itself |
| `eureka.client.fetch-registry` | false | Does not fetch registry (pure server) |
| `eureka.server.enable-self-preservation` | false | Disabled for faster eviction in dev |
| `eureka.server.eviction-interval-timer-in-ms` | 5000 | Aggressive eviction for local dev |

## Security

- HTTP Basic authentication protects the dashboard and the registry API (`/eureka/**`); credentials come from `EUREKA_SECURITY_USER` and `EUREKA_SECURITY_PASSWORD`, which have no defaults in `application.yml`, so both must be set.
- Public endpoints: `/actuator/health` and `/actuator/info` only. CSRF is disabled.
- Registering clients authenticate with the same credentials embedded in their Eureka URL (`http://user:password@host:8761/eureka/`).

## Startup order and dependencies

- Start this module first. It has no external dependencies and does not import from the Config Server (`config-repo/aegis-eureka-server.yml` is currently a placeholder).
- Every other module registers here, including `aegis-config-server`, so they must start after it.


```bash
# From project root
./mvnw spring-boot:run -pl aegis-eureka-server

# Or standalone
cd aegis-eureka-server
../mvnw spring-boot:run
```

The Eureka dashboard is available at [http://localhost:8761](http://localhost:8761) after signing in with the Basic credentials above.

## Dependencies

- Spring Cloud Netflix Eureka Server
- Spring Boot Starter Security
- Spring Boot Starter Actuator

See the [root README](../README.md) for the full platform setup.
