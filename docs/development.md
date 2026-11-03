# Developer guide

From a fresh clone to a merged pull request (issue #201). [`CONTRIBUTING.md`](../CONTRIBUTING.md) holds the
rules; this page holds the commands and the reasoning behind them.

- [Prerequisites](#prerequisites)
- [Repository layout](#repository-layout)
- [Build](#build)
- [Run the stack locally](#run-the-stack-locally)
- [Run the admin frontend](#run-the-admin-frontend)
- [Test](#test)
- [Architecture rules](#architecture-rules)
- [From issue to merged PR](#from-issue-to-merged-pr)
- [Commits and releases](#commits-and-releases)
- [What CI runs](#what-ci-runs)

## Prerequisites

| Tool | Version | Needed for |
|---|---|---|
| JDK | 21 | All backend modules |
| Docker + Docker Compose | recent | Keycloak, PostgreSQL, MongoDB, Kafka, Redis, and Testcontainers tests |
| Node.js | 22 (CI version; the frontend README says 20+) | Admin frontend |
| pnpm | 11 (`pnpm@11.22.0` in `package.json`) | Admin frontend |
| `jq` | any | Extracting tokens in the examples |

Maven is not required: the repository ships the wrapper.

> **`./mvnw: Permission denied`.** The wrapper is committed without the executable bit, and CI works around
> it with `chmod +x ./mvnw`. Locally run `chmod +x mvnw` once, or invoke it as `sh ./mvnw ...`. On Windows
> use `.\mvnw.cmd`.

## Repository layout

| Path | What it is | Port | Architecture |
|---|---|---|---|
| `aegis-api-gateway` | Spring Cloud Gateway, JWT validation, per-route scope rules, rate limiting (needs Redis) | 8080 | thin |
| `aegis-notification-service` | Ingress API, outbox, consumers, template rendering, providers | 8082 | hexagonal |
| `aegis-audit-service` | Audit trail (MongoDB) | 8083 | hexagonal |
| `aegis-user-service` | User management through the Keycloak Admin API | 8084 | hexagonal |
| `aegis-eureka-server` | Service discovery | 8761 | thin |
| `aegis-config-server` | Centralized configuration (optional locally) | 8888 | thin |
| `aegis-api-docs-support` | Shared OpenAPI support library | n/a | library |
| `aegis-admin-frontend` | Angular admin console (pnpm, not part of the Maven reactor) | 4200 | n/a |
| `docker/` | Keycloak realm and login theme, compose support files | n/a | n/a |
| `docs/` | Architecture, ADRs, runbooks, security, user manual, Postman | n/a | n/a |

Keycloak runs on port 8088. See [`ARCHITECTURE.md`](ARCHITECTURE.md) for how the pieces interact and
`docs/security/keycloak.md` (once PR #219 merges) for the identity setup.

## Build

From the repository root:

```bash
./mvnw clean package -DskipTests      # build everything, skip tests
./mvnw -pl aegis-notification-service -am package -DskipTests   # one module plus what it needs
```

## Run the stack locally

The [README quick start](../README.md#installation) is the authoritative step-by-step. In short:

```bash
docker compose up -d keycloak
docker compose -f docker-compose.full.yml up -d postgres mongo kafka redis
```

Name the infrastructure services explicitly: a bare `docker compose -f docker-compose.full.yml up -d` also
starts the application containers, which clash with the ports of services you run from Maven.

Then, in separate terminals and in this order:

```bash
./mvnw -pl aegis-eureka-server spring-boot:run
./mvnw -pl aegis-config-server spring-boot:run
./mvnw -pl aegis-notification-service spring-boot:run -Dspring-boot.run.profiles=local
./mvnw -pl aegis-audit-service spring-boot:run -Dspring-boot.run.profiles=local
./mvnw -pl aegis-user-service spring-boot:run
./mvnw -pl aegis-api-gateway spring-boot:run
```

Things that trip people up (details and variable names are in the README):

- The notification service needs provider credentials in the environment. Placeholders let it start, but
  real sends fail. Notifications stay `PENDING` unless `NOTIFICATION_KAFKA_RELAY_ENABLED=true` and
  `NOTIFICATION_KAFKA_CONSUMER_ENABLED=true`.
- `aegis-user-service` requires `KEYCLOAK_ADMIN_CLIENT_SECRET`; the imported realm ships a local value.
- There is no seed template. Create one before submitting a notification.
- To run everything in containers instead, see "Running the full stack in Docker" in the README.

## Run the admin frontend

```bash
cd aegis-admin-frontend
pnpm install
pnpm start          # http://localhost:4200
```

It expects Keycloak on `http://localhost:8088` (realm `aegis`, client `aegis-admin-frontend`) and the
gateway on `8080`. Sign in with the seeded local user documented in the README.

## Test

### Backend

| Goal | Command |
|---|---|
| Everything, as CI does | `./mvnw clean verify -Dcheckstyle.skip=true` (after the Checkstyle step below) |
| Checkstyle only | `./mvnw validate -Dmaven.test.skip=true` |
| One module and its dependencies | `./mvnw -pl aegis-notification-service -am test` |
| One class | `./mvnw test -Dtest=ClassName` |
| One method | `./mvnw test -Dtest=ClassName#methodName` |
| Architecture tests, all modules | `./mvnw test -Dtest=ArchitectureTest -Dsurefire.failIfNoSpecifiedTests=false` |
| Architecture tests, one module | `./mvnw -pl aegis-notification-service test -Dtest=ArchitectureTest` |

The kinds of test in use:

- **Unit and web-layer tests**: JUnit 5 with Spring Boot Test.
- **Integration tests (`*IT`)**: Testcontainers with real PostgreSQL, Kafka, or MongoDB, mainly in
  `aegis-notification-service` (for example `KafkaNotificationConsumerIT`,
  `NotificationRepositoryAdapterIT`). **Docker must be running**; without it they fail with `Could not
  find a valid Docker environment` rather than being skipped. The gateway's `RateLimiterIntegrationTest`
  also needs Docker.
- **Architecture tests**: an `ArchitectureTest` (ArchUnit) in each of the six Java service modules. Select
  them with `-Dtest=ArchitectureTest`; a pattern such as `*ArchTest*` matches nothing and silently runs no
  tests.
- **Scope canaries**: a `SecurityScopesTest` per service that pins scope literals to
  [`security/scopes.md`](security/scopes.md).

A new behavior needs a test that fails without the change; a bug fix needs a test that reproduces the bug.
Do not mock the thing you are verifying (for example, do not mock the database in a test about a query).

### Frontend

From `aegis-admin-frontend`:

| Goal | Command |
|---|---|
| Unit tests, single run (what CI runs) | `pnpm test:ci` |
| Unit tests, watch mode | `pnpm test` |
| Production build (also type-checks) | `pnpm build` |
| Static hosting tests | `pnpm test:hosting` |

CI runs `pnpm install --frozen-lockfile`, `pnpm build`, then `pnpm test:ci`. Commit `pnpm-lock.yaml`
changes together with `package.json`.

## Architecture rules

`aegis-notification-service`, `aegis-audit-service`, and `aegis-user-service` follow hexagonal layering,
enforced by ArchUnit:

- `domain/` depends on neither `application/` nor `infrastructure/`, and has no Spring or persistence
  types.
- `application/` does not depend on `infrastructure/`.
- `infrastructure/` adapters implement ports declared in `application/`.

A new outbound integration means: declare a port in `application/port/out`, implement it under
`infrastructure/`, and wire it in Spring configuration. Read
[`ARCHITECTURE.md`](ARCHITECTURE.md#hexagonal-layering) and the ADRs in [`adr/`](adr/README.md) before
touching these layers. A change that violates a rule fails CI; do not suppress the test.

## From issue to merged PR

1. **Open an issue** using one of the templates (bug report, feature request, maintenance task). Blank
   issues are disabled. Each template carries the checkbox "I understand this issue needs
   `status:approved` before a PR can be opened".
2. **Wait for `status:approved`.** A maintainer applies the label once the direction is agreed. This is a
   convention: no workflow in `.github/workflows` enforces it, but PRs for unapproved issues are likely to
   be asked to wait.
3. **Branch from `main`**, named `type/<issue-number>-short-description`, for example
   `docs/201-contributor-guide` or `fix/214-gateway-cancel-retry-routes`. One change per branch.
4. **Write the test first** where the change has behavior, then the code.
5. **Run the checks locally** (see [Test](#test)). Backend changes: Checkstyle, then `clean verify` with
   Docker running. Frontend changes: `pnpm build` and `pnpm test:ci`.
6. **Open the PR against `main`**, with `Closes #<issue>` in the description and an explanation of why, not
   just what. State what you did not verify (for example, "not exercised end to end").
7. **Stacked PRs.** The frontend CI deliberately has no `branches:` filter so a PR that targets another
   feature branch still gets CI. If you stack, retarget to `main` once the parent merges, and expect to
   resolve conflicts in shared files such as READMEs.
8. **Merge** once CI is green and the review is resolved.

## Commits and releases

Commits follow [Conventional Commits](https://www.conventionalcommits.org/):
`<type>(<scope>): <description>`, with `!` or a `BREAKING CHANGE:` footer for breaking changes.

[release-please](https://github.com/googleapis/release-please) runs on every push to `main`
(`.github/workflows/release-please.yml`, config in `release-please-config.json`). It reads the commits that
land on `main` and maintains a release PR that bumps the version in `pom.xml` and
`aegis-eureka-server/pom.xml` and updates [`CHANGELOG.md`](../CHANGELOG.md). Merging that PR cuts the
release. The current version is tracked in `.release-please-manifest.json`.

How types map to the changelog:

| Type | Changelog section |
|---|---|
| `feat` | Features |
| `fix` | Bug Fixes |
| `perf` | Performance |
| `refactor` | Refactoring |
| `docs` | Documentation |
| `test` | Tests |
| `ci` | CI/CD |
| `chore` | hidden |

A commit that does not follow the format still merges, but lands in the changelog wrongly or not at all.

## What CI runs

| Workflow | Triggers | Does |
|---|---|---|
| `ci.yml` | push and PR to `main` | Checkstyle, `./mvnw clean verify`, SonarCloud scan, Docker image build (pushed only from `main`) |
| `ci-frontend.yml` | push to `main`, any PR, only when `aegis-admin-frontend/**` changes | `pnpm install --frozen-lockfile`, `pnpm build`, `pnpm test:ci` |
| `release-please.yml` | push to `main` | Maintains the release PR |
| `claude-code-review.yml`, `claude.yml` | PR events and mentions | Automated review assistance |

A PR with a red required check cannot merge. Review rules are summarized in [`AGENTS.md`](../AGENTS.md).
