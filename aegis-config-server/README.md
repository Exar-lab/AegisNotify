# Aegis Config Server

Centralized configuration server for the AegisNotify platform using Spring Cloud Config with a Git-backed repository.

## Overview

Serves externalized configuration to all microservices at startup and on refresh. Configurations are stored in a Git repository, enabling version control, audit trails, and environment-specific overrides.

## Configuration

| Property | Env Variable | Description |
|----------|-------------|-------------|
| `server.port` | — | 8888 (default Config Server port) |
| `spring.security.user.name` | `CONFIG_SERVER_USER` | HTTP Basic auth username |
| `spring.security.user.password` | `CONFIG_SERVER_PASSWORD` | HTTP Basic auth password |
| `spring.cloud.config.server.git.uri` | `CONFIG_REPO_URI` | Git repository URL for configs |
| `spring.cloud.config.server.git.username` | `CONFIG_REPO_USERNAME` | Git repo username |
| `spring.cloud.config.server.git.password` | `CONFIG_REPO_TOKEN` | Git repo token/password |

## Security

- HTTP Basic authentication protects all config endpoints
- Actuator health and info endpoints are public
- CSRF is disabled (machine-to-machine traffic only)

## Run

```bash
# Set required environment variables
export CONFIG_SERVER_USER=admin
export CONFIG_SERVER_PASSWORD=secret
export CONFIG_REPO_URI=https://github.com/your-org/config-repo.git
export CONFIG_REPO_USERNAME=your-user
export CONFIG_REPO_TOKEN=your-token

# From project root
./mvnw spring-boot:run -pl aegis-config-server
```

## Endpoints

| Endpoint | Description |
|----------|-------------|
| `GET /{app}/{profile}` | Fetch config for app and profile |
| `GET /{app}/{profile}/{label}` | Fetch config for specific Git label (branch/tag) |
| `GET /actuator/health` | Health check (public) |
| `GET /actuator/info` | Info endpoint (public) |

## Service Discovery

Registers with Eureka Server at `http://localhost:8761/eureka/` (`register-with-eureka: true`, `fetch-registry: false`). Ensure the Eureka Server is running before starting this module.

## Startup order and dependencies

1. Eureka Server (8761) first.
2. Config Server (this module). The Git repository must be reachable at startup (`clone-on-start: true`, default label `main`); the server fails to start otherwise.
3. Client services afterwards. Gateway and audit service import configuration with `optional:configserver:`, so they also start when this server is absent.

All five environment variables above have no default and must be set.

## Known limitations

- Actuator exposes only `health` and `info`; `health` details are shown only to authorized users.
- Client imports in `aegis-api-gateway` and `aegis-audit-service` carry no Basic credentials, so they cannot fetch configuration from this server as configured.

## Dependencies

- Spring Cloud Config Server
- Spring Cloud Netflix Eureka Client
- Spring Boot Starter Security
- Spring Boot Starter Actuator
