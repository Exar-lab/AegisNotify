# Providers health screen (#85)

## Contract and architecture

`/providers` polls the authenticated `GET /api/v1/dashboard/circuit-breakers` endpoint
every 15 seconds. The existing gateway route and `notification:read` authorization
apply. No public actuator endpoint or additional gateway permission is required.

The application use case combines two output ports: live circuit inspection and
persisted delivery-attempt aggregation. The JDBC adapter queries all channel/role
groups in one round trip. V6 adds an index for the time-window query. The frontend
keeps HTTP access in its existing API service, polling in the page, and rendering
in a separate provider card component.

Each entry retains `key`, `name`, `channel`, `role`, `fallbackProvider`, and `state`,
and adds nullable `failureRate`, `slowCallRate`, `lastStateChange`, `calls24h`,
`callsToday`, and `successRate`.

- Circuit states and failure/slow-call rates come directly from Resilience4j on
  the responding service instance. Rates use its configured sliding window, not
  24 hours. A rate of -1 becomes null (insufficient samples), never 0%.
- Four primary circuits exist. Fallback accounts are invoked directly in the
  existing delivery implementation. Their cards explicitly report
  `NOT_PROTECTED` / “NO CIRCUIT”, and their circuit rates are unavailable. This
  feature does not change delivery or introduce new fallback circuit breakers.
- Call counts and success rates use persisted delivery attempts grouped by
  channel and role. Retries count individually. `CIRCUIT_OPEN` and
  `NOT_CONFIGURED` are excluded because they do not contact a provider.
- The history window is `[now - 24h, now)`; today starts at 00:00 UTC. No calls
  means a null success rate and an empty neutral bar, rather than 100% success.
  Attempts become visible when the notification processing transaction commits.
- State-change timestamps are observed from live circuit events since startup;
  no historical transition is fabricated. History is not persisted or shared
  between service replicas. Call history is shared through PostgreSQL.
- Successful responses replace the snapshot. Refresh failures retain cards with
  a visible stale-data warning. Requests do not overlap; navigation destroys the
  polling subscription.
- View Logs opens `/notifications?channel=…` and initializes that channel filter.
  These are channel delivery logs; the list has no provider-specific log filter.
- Existing theme variables are reused. Cards are side by side on desktop and
  stack on mobile. OPEN/FORCED_OPEN have a red border/shadow; HALF_OPEN amber.
  Success bars are green at >=95%, amber at >=70% and <95%, red below 70%.

## Validation

Frontend: `npm run build` and `npm run test:ci` in `aegis-admin-frontend`.
Tests cover threshold boundaries, rendered widths/accessibility, circuit warning
styles, grouping, polling, stale snapshots, retry, empty results, teardown, and
the channel logs link.

Backend (Java 21):

```powershell
./mvnw.cmd -pl aegis-notification-service '-Dtest=GetProviderCircuitBreakersServiceTest,Resilience4jCircuitBreakerAdapterTest,DashboardControllerTest,ArchitectureTest' test
./mvnw.cmd -pl aegis-notification-service '-Dit.test=ProviderCallMetricsAdapterIT' test-compile failsafe:integration-test failsafe:verify
```

The integration test requires Docker and runs PostgreSQL with Testcontainers,
applies real migrations, checks UTC/24-hour boundaries, role/channel separation,
and exclusions for blocked or unconfigured calls.

Validation on this workstation: production frontend build and all 25 frontend
tests passed; the targeted
Java suite passed 17 checks including six ArchUnit rules; PostgreSQL integration
passed with all six Flyway migrations applied. Docker 29 requires the existing
Testcontainers 1.20.4 client to use `-Dapi.version=1.44` in the integration command.

## Remaining external validation

`design/providers-claude-preview.png` is absent from this checkout and its location
is unknown. Exact comparison with the approved mockup remains pending. The screen
follows existing application styles. A PR must wait for `status:approved`.
Browser visual QA against a running authenticated stack has not been performed.
