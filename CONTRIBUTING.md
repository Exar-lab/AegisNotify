# Contributing to AegisNotify

Thanks for taking the time to contribute. This document covers everything needed to propose,
build, test, and submit a change: environment setup, coding standards, commit conventions, and
the pull request checklist that CI actually enforces. Commands, module ports and the release
process are in the [Developer Guide](docs/development.md).

## Table of contents

- [Before you start](#before-you-start)
- [Local environment setup](#local-environment-setup)
- [Project structure](#project-structure)
- [Branching](#branching)
- [Commit message convention](#commit-message-convention)
- [Coding standards](#coding-standards)
- [Architecture rules (Hexagonal)](#architecture-rules-hexagonal)
- [Testing requirements](#testing-requirements)
- [Running checks locally](#running-checks-locally)
- [Pull request checklist](#pull-request-checklist)
- [Reporting bugs and requesting features](#reporting-bugs-and-requesting-features)
- [Documentation changes](#documentation-changes)

## Before you start

- **Issue first.** Open an issue with one of the templates under `.github/ISSUE_TEMPLATE/` and
  wait for a maintainer to label it `status:approved` before opening a pull request. The
  templates ask you to confirm this. New issues start as `status:needs-review`.
- Link the issue from the PR (`Closes #N`).
- Read [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) before touching `domain/`, `application/`,
  or `infrastructure/` in `aegis-notification-service`, `aegis-audit-service`, or
  `aegis-user-service` — the layering rules are enforced by ArchUnit and a violating PR will fail
  CI.

## Local environment setup

Requirements:

- JDK 21
- Docker, to run Testcontainers-based integration tests (PostgreSQL, Kafka, MongoDB)
- Node.js 22 and pnpm 11, only for `aegis-admin-frontend`
- Git

Maven does not need to be installed separately; the repository ships `mvnw` / `mvnw.cmd`.

Clone and build:

```bash
git clone https://github.com/Exar-lab/AegisNotify.git
cd AegisNotify
./mvnw clean package -DskipTests
```

See the [README](README.md#installation) for prerequisites needed to actually run a service
(PostgreSQL, Kafka, MongoDB, an OIDC provider, provider API keys), and
[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for how the services fit together.

## Project structure

Seven Maven modules under one reactor, plus the Angular frontend (not built by Maven):

```text
AegisNotify/
├── aegis-api-docs-support/     # shared OpenAPI auto-configuration library
├── aegis-api-gateway/
├── aegis-audit-service/
├── aegis-config-server/
├── aegis-eureka-server/
├── aegis-notification-service/
├── aegis-user-service/
├── aegis-admin-frontend/       # Angular, pnpm
└── pom.xml
```

`aegis-notification-service`, `aegis-audit-service`, and `aegis-user-service` follow Hexagonal
Architecture (`domain/` → `application/` → `infrastructure/`); the gateway, config server and
Eureka server are thin Spring Cloud infrastructure modules.

## Branching

- Branch off `main`.
- Name branches `type/short-description` or `type/<issue-number>-short-description`, matching
  the existing history, e.g. `feat/212-notification-composer`,
  `fix/214-gateway-cancel-retry-routes`, `docs/208-gateway-readme`.
- Keep branches focused on one change. Prefer several small PRs over one large one.

## Commit message convention

This repository uses [Conventional Commits](https://www.conventionalcommits.org/) and automates
releases and the changelog with `release-please` from commit history — non-conforming commits on
`main` produce a broken changelog entry. See
[Releases](docs/development.md#9-releases-release-please).

Format:

```
<type>(<scope>): <description>
```

Types in use: `feat`, `fix`, `refactor`, `test`, `ci`, `docs`, `perf`, `chore`.

Scope is typically the module or concern being touched: `notification`, `audit`, `gateway`,
`config`, `security`, `readme`, etc.

Examples from this repository's history:

```
feat(security): enforce notification:read and audit:read scopes
feat(notification): add Resilience4j circuit breaker with provider failover
fix(audit): use dummy jwk-set-uri in test config
docs(readme): document project architecture and capabilities
```

A breaking change adds `!` after the type/scope (`feat(notification)!: ...`) or a
`BREAKING CHANGE:` footer.

## Coding standards

- Java 21; use modern language features where they improve clarity (records, sealed classes,
  pattern matching).
- Google Java Style Guide, enforced by Checkstyle (`./mvnw checkstyle:check`). No wildcard
  imports.
- Prefer immutable objects and `final` fields.
- Constructor injection only — no field injection (`@Autowired` on fields).
- Keep controllers thin; delegate to application services. Use the correct HTTP status code for
  the operation (e.g. `202 Accepted` for the async submit endpoint).
- No commented-out code and no `System.out.println` — use SLF4J logging.
- Don't add abstractions, configuration flags, or defensive handling for scenarios the codebase
  doesn't actually have yet. Match the existing pattern in the module you're editing before
  introducing a new one.

## Architecture rules (Hexagonal)

Enforced by the ArchUnit `ArchitectureTest` class in every backend service module
(notification, audit, user, gateway, config, eureka):

- `domain/` must not depend on `application/` or `infrastructure/`, and must not import Spring or
  persistence framework types.
- `application/` must not depend on `infrastructure/`.
- `infrastructure/` adapters implement ports defined in `application/`; they never get called
  from `domain/`.

If you're adding a new outbound integration (a new provider, a new persistence store), add an
outbound port in `application/port/out`, implement it under `infrastructure/`, and wire it via
Spring configuration — don't reach into `infrastructure/` types from `application/` or `domain/`.
See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md#hexagonal-layering) for the full picture.

## Testing requirements

- JUnit 5 + Spring Boot Test for unit and web-layer tests.
- Testcontainers for integration tests that need a real PostgreSQL, Kafka, or MongoDB instance —
  these require Docker locally and in CI.
- ArchUnit tests must keep passing; add a case there if you introduce a new package boundary.
- Frontend changes (`aegis-admin-frontend`) are tested with Vitest: `pnpm test:ci` and
  `pnpm build` from that directory, as `CI Frontend` does.
- New behavior needs a test that would fail without the change. A bug fix should include a test
  that reproduces the bug.
- Don't mock away the thing you're actually testing (e.g. don't mock the database in a test whose
  purpose is to verify a query or a migration).

## Running checks locally

Run the same checks CI runs, in the same order:

```bash
# 1. Checkstyle (validate phase, tests skipped)
./mvnw validate -Dmaven.test.skip=true

# 2. Full build and test suite (Checkstyle skipped, already ran above)
./mvnw clean verify -Dcheckstyle.skip=true
```

Useful narrower commands while iterating:

```bash
# One module and its required reactor dependencies
./mvnw -pl aegis-notification-service -am test

# One test class
./mvnw test -Dtest=ClassName

# One test method
./mvnw test -Dtest=ClassName#methodName

# Architecture tests only
./mvnw test -Dtest=ArchitectureTest
```

Testcontainers-based integration tests require Docker to be running; without it, tests like
`KafkaNotificationConsumerIT` fail with `Could not find a valid Docker environment`
rather than being skipped — start Docker before running the full suite.

## Pull request checklist

Before opening a PR:

- [ ] The linked issue is labelled `status:approved`.
- [ ] `./mvnw clean verify` passes locally (Checkstyle, tests, ArchUnit) with Docker running.
- [ ] For frontend changes: `pnpm build` and `pnpm test:ci` pass in `aegis-admin-frontend`.
- [ ] Commit messages follow the [Conventional Commits](#commit-message-convention) format.
- [ ] New or changed behavior has test coverage.
- [ ] No architecture boundary violations (`domain` → `application` → `infrastructure` stays
      one-directional).
- [ ] Public API changes (request/response shapes, new endpoints, new required config) are
      reflected in the [README](README.md) or [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).
- [ ] The PR description explains *why*, not just *what* — link the issue it addresses, if any.

CI (`.github/workflows/ci.yml`) runs Checkstyle, the full `mvnw clean verify` and the Docker image
builds on every push and pull request targeting `main`; `ci-frontend.yml` runs the frontend build
and tests when `aegis-admin-frontend/**` changes. A PR can't merge with a red CI run.

## Reporting bugs and requesting features

Use the issue templates under `.github/ISSUE_TEMPLATE/`:

- **Bug report** — include steps to reproduce, expected vs. actual behavior, and which module is
  affected.
- **Feature request** — describe the problem being solved, not just the desired implementation.
- **Maintenance task** — dependency bumps, refactors, tooling changes with no behavior change.

## Documentation changes

- Keep the [README](README.md) focused on "what is this and how do I run/call it."
- Deep-dive material (diagrams, Kafka topic details, persistence schema notes, resilience
  internals, observability) belongs in [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).
- Keycloak realm, client and login-theme documentation lives in
  [`docs/security/`](docs/security/).
- Security scope literals must match `docs/security/scopes.md` byte-for-byte — there is a
  dedicated `SecurityScopesTest` canary test per service to catch drift; update both the code and
  that doc together.
