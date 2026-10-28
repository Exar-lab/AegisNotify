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

- No Spring Security dependency or configuration: the dashboard (`/`) and registry (`/eureka/**`) accept unauthenticated requests.
- Intended for local development only.

## Startup order and dependencies

Start this module first. It has no external dependency and does not register with or fetch from another registry. Config Server, gateway, and the domain services register with it on startup.

## Known limitations

- Unauthenticated registry and dashboard (see Security).
- Self-preservation is disabled and eviction runs every 5 seconds; instances may be evicted during network partitions.

## Run

```bash
# From project root
./mvnw spring-boot:run -pl aegis-eureka-server

# Or standalone
cd aegis-eureka-server
../mvnw spring-boot:run
```

The Eureka dashboard is available at [http://localhost:8761](http://localhost:8761).

## Dependencies

- Spring Cloud Netflix Eureka Server
- Spring Boot Starter Test
