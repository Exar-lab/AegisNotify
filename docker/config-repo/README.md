# Local Config Server repository

`docker-compose.full.yml` runs `aegis-config-server` with the `native` profile and mounts this
directory at `/config-repo`, so the local stack needs no external Git repository.

It is intentionally empty: every client imports the Config Server as `optional:`, so each service
falls back to its own `application.yml`. To override a property locally, drop a file here named
`<spring.application.name>.yml` (e.g. `aegis-notification-service.yml`) or `application.yml` for
all services, then restart `aegis-config-server`.
