# Developer Guide

End-to-end path from a fresh clone to a merged pull request (issue #201). Rules and checklist
live in [`CONTRIBUTING.md`](../CONTRIBUTING.md); this page holds the commands and the "how".

## 1. Prerequisites

| Tool | Version | Needed for |
|---|---|---|
| JDK | 21 | All backend modules |
| Docker + Docker Compose | recent | Keycloak, PostgreSQL, MongoDB, Kafka, Redis; Testcontainers tests |
| Node.js | 22 | `aegis-admin-frontend` (same as CI) |
| pnpm | 11 (`pnpm@11.22.0`, pinned in `package.json`) | `aegis-admin-frontend` |
| `jq` | any | Extracting the token in the README walkthrough |

Maven is not required: use the wrapper (`./mvnw`, `mvnw.cmd` on Windows). `mvnw` is committed
without the executable bit, so on Linux and macOS run `chmod +x mvnw` once after cloning (CI does
the same) or call it as `sh mvnw`. The first run downloads Maven, so it needs access to Maven
Central.

## 2. Modules

Defined in the root [`pom.xml`](../pom.xml) (one reactor), plus the frontend (not part of Maven):

| Module | Port | Notes |
|---|---|---|
| `aegis-api-docs-support` | - | Shared OpenAPI support library |
| `aegis-eureka-server` | 8761 | Service discovery. Thin module |
| `aegis-config-server` | 8888 | Config server. Thin module |
| `aegis-notification-service` | 8082 | Hexagonal; Testcontainers ITs (PostgreSQL, Kafka) |
| `aegis-audit-service` | 8083 | Hexagonal; Testcontainers ITs (MongoDB) |
| `aegis-api-gateway` | 8080 | Edge, JWT validation, route scopes |
| `aegis-user-service` | 8084 | Hexagonal; Keycloak Admin API |
| `aegis-admin-frontend` | 4200 | Angular 22, pnpm, Vitest |

Ports are the defaults from each module's `application.yml`.

## 3. Local setup

```bash
git clone https://github.com/Exar-lab/AegisNotify.git
cd AegisNotify
cp .env.example .env        # optional for local compose; .env is gitignored
./mvnw clean package -DskipTests
```

To run the system, follow the README ([Installation](../README.md#installation)): Keycloak first
(`docker compose up -d keycloak`, see [`security/keycloak.md`](security/keycloak.md)), then the
infrastructure containers, then the services with Maven. To run everything in containers, use
`docker-compose.full.yml` ([`deployment.md`](deployment.md)).

Run one service with a profile (`local` profiles exist for the notification, audit and user services):

```bash
./mvnw -pl aegis-notification-service spring-boot:run -Dspring-boot.run.profiles=local
```

## 4. Build, test and run commands

### Backend (per module)

| Goal | Command |
|---|---|
| Build without tests | `./mvnw clean package -DskipTests` |
| Checkstyle only (what CI runs first) | `./mvnw validate -Dmaven.test.skip=true` |
| Everything CI runs | `./mvnw clean verify -Dcheckstyle.skip=true` (after the Checkstyle step above) |
| One module (+ upstream modules) | `./mvnw -pl aegis-user-service -am test` |
| One test class | `./mvnw test -Dtest=ClassName` |
| One test method | `./mvnw test -Dtest=ClassName#methodName` |
| Architecture tests only | `./mvnw test -Dtest=ArchitectureTest` (every backend service module has one; `CLAUDE.md` mentions `*ArchTest*`, which does not match this class name) |

### Frontend (`aegis-admin-frontend`)

Run from the `aegis-admin-frontend` directory.

| Goal | Command |
|---|---|
| Install | `pnpm install --frozen-lockfile` |
| Dev server (http://localhost:4200) | `pnpm start` |
| Production build | `pnpm build` |
| Unit tests, watch mode | `pnpm test` |
| Unit tests, as CI | `pnpm test:ci` |
| Static hosting test | `pnpm test:hosting` (not a unit test: it needs the built app served on port 4300, e.g. `pnpm build` then `npx serve dist/aegis-admin-frontend/browser -l 4300`; override the URL with `AEGIS_STATIC_URL`) |

The frontend logs in through Keycloak (`aegis-admin-frontend` client), so start Keycloak before
`pnpm start`.

## 5. Testing approach

- **Unit and web-layer tests**: JUnit 5 + Spring Boot Test. Fast, no Docker.
- **Integration tests** (`*IT.java`): Testcontainers with real PostgreSQL, Kafka or MongoDB, in
  `aegis-notification-service` and `aegis-audit-service`. Docker must be running, otherwise they
  fail (not skip) with `Could not find a valid Docker environment`.
- **Architecture tests**: ArchUnit `ArchitectureTest` in each service module. They fail the build
  on layering violations (see below).
- **Scope canaries**: `SecurityScopesTest` per service pins the scope literals documented in
  [`security/scopes.md`](security/scopes.md).
- **Frontend**: Vitest + JSDOM through `ng test`.
- A new behaviour needs a test that fails without the change; a bug fix needs one that reproduces
  the bug. Do not mock the thing under test (for example, the database in a query test).

## 6. Architecture rules

For `aegis-notification-service`, `aegis-audit-service` and `aegis-user-service`
(`domain/` → `application/` → `infrastructure/`), enforced by ArchUnit:

- `domain` depends on neither `application` nor `infrastructure`, and has no Spring or
  persistence imports.
- `application` does not depend on `infrastructure`.
- Infrastructure adapters implement ports defined in `application` (outbound ports in
  `application/port/out`).

Also: Google Java Style (Checkstyle), no wildcard imports, constructor injection, SLF4J instead
of `System.out`, no commented-out code. Details in
[`ARCHITECTURE.md`](ARCHITECTURE.md#hexagonal-layering).

## 7. Workflow: issue to merged PR

1. **Open an issue** with a template under `.github/ISSUE_TEMPLATE/` (bug, feature or maintenance
   task; blank issues are disabled). New issues get `status:needs-review`.
2. **Wait for `status:approved`.** The templates state that an issue needs `status:approved`
   before a PR can be opened. A maintainer applies it. Do not start a PR for an unapproved issue.
3. **Branch off `main`**, named `type/<issue-number>-short-description`
   (for example `docs/208-gateway-readme`, `feat/212-notification-composer`).
4. **Commit with Conventional Commits** (section 8).
5. **Run the checks locally** (section 4): Checkstyle, then `./mvnw clean verify` with Docker
   running; for frontend changes also `pnpm build` and `pnpm test:ci`.
6. **Open a PR to `main`** linking the issue (`Closes #N`), explaining why, and ticking the
   checklist in `CONTRIBUTING.md`.
7. **CI** must be green: backend `CI` (Checkstyle, `verify`, Docker image builds for the six
   services, SonarCloud when a token is configured) and, if `aegis-admin-frontend/**` changed,
   `CI Frontend` (`pnpm build`, `pnpm test:ci`).
8. Address review comments, then a maintainer merges.

## 8. Commit and PR conventions

```text
<type>(<scope>): <description>
```

Types: `feat`, `fix`, `perf`, `refactor`, `docs`, `test`, `ci`, `chore`. Scope is the module or
concern (`notification`, `gateway`, `security`, `frontend`, `readme`). Mark breaking changes with
`!` or a `BREAKING CHANGE:` footer. The issue title follows the same shape
(`feat(scope): ...`).

## 9. Releases (release-please)

Releases are automated by `.github/workflows/release-please.yml` on every push to `main`, using
`release-please-config.json` and `.release-please-manifest.json`:

- It reads the commit messages on `main` and opens (or updates) a **release PR** that bumps the
  version and writes `CHANGELOG.md`. The version lives in the root `pom.xml` and
  `aegis-eureka-server/pom.xml`; the manifest holds the current version.
- Merging the release PR creates the tag and GitHub release. Do not edit `CHANGELOG.md` or the
  version by hand.
- Changelog sections: `feat` → Features, `fix` → Bug Fixes, `perf`, `refactor`, `docs`, `test`,
  `ci`. `chore` commits are hidden from the changelog.
- A commit that does not follow Conventional Commits is ignored or mangled in the changelog, which
  is why commit format is a hard rule.
- Pushes to `main` also build and publish the six service images to GHCR from the CI workflow;
  pull requests only build them.

## 10. Where things are documented

| Topic | File |
|---|---|
| Overview, quick start, configuration | [`README.md`](../README.md) |
| Architecture deep dive | [`ARCHITECTURE.md`](ARCHITECTURE.md), [`adr/`](adr/) |
| Keycloak, scopes, login theme | [`security/`](security/) |
| Docker and deployment | [`deployment.md`](deployment.md) |
| Operations runbooks | [`runbooks/`](runbooks/) |
| Frontend | [`../aegis-admin-frontend/README.md`](../aegis-admin-frontend/README.md) |
