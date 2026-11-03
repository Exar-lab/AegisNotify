# ADR-0004: Hexagonal layering enforced by ArchUnit

- **Status:** Accepted
- **Date:** 2026-06-20 (notification service, `26fe95c`); 2026-10-02 (coverage extended to all
  modules, `dfd9f57`, PR #132)
- **Evidence:** `26fe95c`, `dfd9f57`, `ArchitectureTest` in each module, `docs/ARCHITECTURE.md`

## Context

The notification service integrates Kafka, JPA, HTTP providers, and Spring Security. Without a
mechanical check, framework concerns tend to leak into business logic (inferred from
implementation). The first service commit (`26fe95c`) states the intent: "hexagonal architecture
with strict layer separation enforced by ArchUnit."

## Decision

Structure the notification, audit, and user services as `domain`, `application`, and
`infrastructure`, and enforce the dependency direction with ArchUnit tests that run with the normal
test suite.

Rules in `aegis-notification-service` (`ArchitectureTest`):

- `domain` must not depend on `application` or `infrastructure`.
- `application` must not depend on `infrastructure`.
- `domain` and `application.port` must not depend on `org.springframework..`.
- Everything in `application.port` must be an interface.
- `@RestController` classes must reside in `infrastructure.web`.
- Classes implementing `application.port.out` interfaces must reside in `infrastructure`.

Per `dfd9f57`, the gateway, config server, and Eureka server were missing the check. They are thin
and not hexagonal, so they received a minimal rule set: no field injection, plus (gateway only)
configuration classes stay under `..config..` and the scope class stays under `..security..`. The
commit records that the new rules were shown to fail on a deliberate violation.

## Alternatives considered

None are recorded in the repository. `dfd9f57` does record one scoping choice: applying a full layer
check to the three thin modules was not done because they "don't follow hexagonal layering".

## Consequences

### Positive

- Layer violations fail the build instead of surviving until review.
- Ports and adapters stay swappable. For example, adding the Mailtrap, Vonage, and Groq adapters
  required only infrastructure and configuration changes (inferred from the commit history).

### Negative / trade-offs

- `CLAUDE.md` says no Spring annotations in the domain or application layers except service
  stereotypes. The ArchUnit rule only bans Spring from `domain` and `application.port`, so
  `@Service` in `application.service` is allowed by design, and other Spring use there is not
  caught.
- Gateway, config server, and Eureka server have weaker checks than the three domain services.
- Rules reference package names, so a package rename must update the tests.

## Related

- Run with `./mvnw test -Dtest=ArchitectureTest`.
- Earlier docs work: #63, #65, #169, #172.
