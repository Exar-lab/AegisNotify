# Deploy the full stack on a single VM (portfolio demo)

This guide deploys AegisNotify, Keycloak and the admin frontend on **one virtual machine** with
Docker Compose and HTTPS, so the demo can be opened from any computer. It is written for a
cost-bounded, stoppable demo, not for production traffic (issue #236).

Everything is driven by [`docker-compose.prod.yml`](../docker-compose.prod.yml), the Caddy
configuration in [`docker/caddy`](../docker/caddy), and one server-side file, `.env.prod`. For the
meaning of each service variable see [`deployment.md`](deployment.md); for the local-only setup
see the [README](../README.md#running-the-full-stack-in-docker).

## Navigate

- [Status of this guide: what was verified](#status-of-this-guide-what-was-verified)
- [Architecture](#architecture)
- [Sizing](#sizing)
- [Provision an AWS EC2 instance](#provision-an-aws-ec2-instance)
- [Public IP, sslip.io and the stop/start problem](#public-ip-sslipio-and-the-stopstart-problem)
- [First deploy](#first-deploy)
- [Choose where the frontend lives](#choose-where-the-frontend-lives)
- [HTTPS certificates and rate limits](#https-certificates-and-rate-limits)
- [Update](#update)
- [Stop and start to save credits](#stop-and-start-to-save-credits)
- [Backups](#backups)
- [Teardown](#teardown)
- [Troubleshooting](#troubleshooting)
- [Demo script](#demo-script)
- [Recorded walkthrough as a fallback](#recorded-walkthrough-as-a-fallback)
- [Alternatives to AWS](#alternatives-to-aws)
- [Security notes](#security-notes)
- [Unverified assumptions](#unverified-assumptions)

## Status of this guide: what was verified

The repository changes were checked locally (Windows, Docker Desktop). **No cloud resource was
created and no end-to-end deployment was run**; the guide has not been executed on an EC2
instance.

| Check | Result |
| --- | --- |
| `docker compose --env-file .env.prod.example -f docker-compose.prod.yml config` | Passes; the only published ports are 80 and 443 (Caddy). |
| `caddy validate` for the proxy Caddyfile (default and staging issuer snippets) and the frontend Caddyfile, using `caddy:2-alpine` | "Valid configuration". |
| Rendering `aegis-realm.json.tpl` with the same `sed` as `keycloak-realm-init`, then importing it into `quay.io/keycloak/keycloak:26.0` (`start-dev`) | Realm `aegis` imported; issuer and JWKS URI use the `KC_HOSTNAME` value; password grant for `aegis-dev` returns a token with all five scopes; a second start logs "Realm 'aegis' already exists. Import skipped". |
| Frontend Caddyfile in `caddy:2-alpine` | `/config.json` returns the JSON built from `API_BASE_URL` / `KEYCLOAK_URL` with `Cache-Control: no-store`; unknown routes fall back to `index.html`; `/silent-check-sso.html` returns the real file. |
| `shellcheck scripts/deploy`, `scripts/deploy init` / `check` | Clean; init and check work against the example file. |

Not run: building the six Spring Boot images and the frontend image, `docker compose up` of the
whole stack, Keycloak in production mode (`start`) with PostgreSQL behind Caddy, its health check,
real ACME issuance, memory measurements, and anything on AWS. These are listed again in
[Unverified assumptions](#unverified-assumptions). Treat the first real deploy as the test.

## Architecture

```text
                       Internet (80, 443)
                              |
                         +----v----+
                         |  Caddy  |  automatic HTTPS, the only published ports
                         +----+----+
        app.HOST   api.HOST   |   auth.HOST
        +---------+-----------+-----------+
        |                     |           |
 aegis-admin-frontend   aegis-api-gateway  keycloak ---- postgres (db: keycloak)
 (static SPA, optional)       |
                  Eureka / Config Server (internal)
                  notification-service ---- postgres (db: aegisnotify), kafka
                  audit-service --------- mongo, kafka
                  user-service ---------- keycloak (Admin API, internal URL)
                  redis (gateway rate limiter)
```

`HOST` is `PUBLIC_HOST` from `.env.prod`, for example `203-0-113-10.sslip.io`, giving
`https://app.203-0-113-10.sslip.io`, `https://api.203-0-113-10.sslip.io` and
`https://auth.203-0-113-10.sslip.io`.

Design decisions:

- **Standalone compose file, not an override of `docker-compose.full.yml`.** The full file includes
  Keycloak as `start-dev` with a published port and publishes every service on `127.0.0.1`. An
  override cannot cleanly remove published ports on older Compose versions and would restate most
  services anyway. The cost: service definitions are duplicated; when one changes in
  `docker-compose.full.yml`, mirror it in `docker-compose.prod.yml`.
- **Subdomains, not paths.** Keycloak and the SPA both assume they own `/`; separate hostnames avoid
  path rewriting. sslip.io resolves any prefix in front of the dashed IP.
- **Keycloak uses the shared PostgreSQL container** (database `keycloak`, created by
  `docker/postgres/init-keycloak.sql` on first volume initialisation) so users and admin-console
  edits survive restarts and are covered by the same backup.
- **Service-to-service traffic uses internal URLs** (`http://keycloak:8080/...` for JWKS and the
  Admin API). The gateway validates the JWT signature through JWKS only; it does not check the
  issuer (see `SecurityConfig` and the `jwk-set-uri` settings), so the public issuer in the tokens
  does not need to match the internal URL.
- **No `local` Spring profile.** It enables Swagger UI and a committed audit encryption key. The
  notification service gets replication factor 1 through environment variables instead, and
  `AUDIT_ENCRYPTION_KEY` is required.
- **Realm rendering at deploy time.** `docker/keycloak/prod/aegis-realm.json.tpl` is rendered by the
  one-shot `keycloak-realm-init` container (secrets and the frontend origin are substituted), then
  Keycloak imports it. The development realm file stays untouched.

## Sizing

The 8 GiB target comes from the issue estimate of 6 to 8 GB for ten containers plus Keycloak.
**The numbers below are estimates, not measurements.** Measure on the real VM with
`docker stats --no-stream` after a few minutes of idle and after a burst of requests, then adjust
`deploy.resources.limits.memory` in the compose file.

| Container | Memory limit | JVM / engine setting |
| --- | ---: | --- |
| keycloak | 1024 MiB | `JAVA_OPTS_KC_HEAP=-XX:MaxRAMPercentage=65 -XX:InitialRAMPercentage=30` |
| kafka | 1024 MiB | `KAFKA_HEAP_OPTS=-Xms256m -Xmx512m` |
| aegis-notification-service | 768 MiB | `JAVA_TOOL_OPTIONS`: `MaxRAMPercentage=60`, SerialGC, metaspace 160m |
| postgres | 512 MiB | defaults |
| mongo | 512 MiB | `--wiredTigerCacheSizeGB 0.25` |
| aegis-api-gateway | 512 MiB | as above |
| aegis-audit-service | 512 MiB | as above |
| aegis-user-service | 448 MiB | as above |
| aegis-eureka-server | 384 MiB | as above |
| aegis-config-server | 384 MiB | as above |
| redis | 96 MiB | `--maxmemory 64mb`, no persistence |
| caddy | 64 MiB | |
| aegis-admin-frontend | 32 MiB | |
| **Sum of limits** | **about 6.2 GiB** | caps, not expected steady usage |

That leaves roughly 1.8 GiB of an 8 GiB host for the operating system, Docker and page cache.
A container that hits its limit is OOM-killed and restarted by `restart: unless-stopped`, so
watch `docker stats` and `docker compose ps` for restart loops (symptom: a service restarting
every few minutes; see [Troubleshooting](#troubleshooting)).

Other sizing notes:

- **Image builds are the memory peak.** Building six Maven images and the Angular app on the VM can
  exhaust 8 GiB. `scripts/deploy up` limits Compose to two parallel builds; also add swap
  ([step 6](#provision-an-aws-ec2-instance)).
- **CPU:** 2 vCPU is workable; the first start is slow (several minutes) because every JVM starts
  at once and Keycloak runs its build step.
- **Disk:** 30 GiB is enough for images, build cache and demo data; clean the build cache with
  `docker builder prune` if it fills up.
- **Architecture:** prefer x86_64. ARM (Graviton, Oracle Ampere) should work with the multi-arch
  images used here, but it was not tested.

## Provision an AWS EC2 instance

> Confirm the current free-plan terms, eligible instance types and prices on
> <https://aws.amazon.com/free> and the EC2 pricing page before you start. The issue assumes up to
> 200 USD of credits for up to 6 months on accounts created since 15 July 2025; this guide did not
> verify that, nor which 8 GiB types are eligible.

1. **Region.** Pick the one closest to you and keep everything (instance, Elastic IP) in it.
2. **Launch an instance** (EC2 console, Launch instance):
   - Name: `aegisnotify-demo`.
   - AMI: Ubuntu Server 24.04 LTS, x86_64.
   - Instance type: 8 GiB RAM, 2 vCPU class (for example `m7i-flex.large` if it is free-plan
     eligible for your account, otherwise `t3.large`). Check eligibility and cost first.
   - Key pair: create one and keep the `.pem` file; it is the only way in over SSH.
   - Storage: 30 GiB gp3.
3. **Security group** (create a new one):

   | Type | Port | Source | Why |
   | --- | ---: | --- | --- |
   | SSH | 22 | My IP only | Administration |
   | HTTP | 80 | 0.0.0.0/0 | Certificate validation and redirect to HTTPS |
   | HTTPS | 443 | 0.0.0.0/0 | The demo |

   Do not open any other port. Kafka, PostgreSQL, MongoDB, Redis, Eureka and the services have no
   published ports and must stay that way.
4. **Elastic IP (recommended).** EC2, Elastic IPs, Allocate, then Associate it with the instance.
   Read [the next section](#public-ip-sslipio-and-the-stopstart-problem) first: it decides whether
   the demo URLs survive a stop/start.
5. **Connect and install Docker.**

   ```bash
   ssh -i aegisnotify.pem ubuntu@<public-ip>
   curl -fsSL https://get.docker.com | sudo sh
   sudo usermod -aG docker ubuntu && newgrp docker
   sudo systemctl enable --now docker      # containers return after a reboot
   docker compose version                  # needs Compose v2.20+ for this file
   ```

6. **Add swap** (protects the image builds and absorbs short memory spikes):

   ```bash
   sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile
   sudo mkswap /swapfile && sudo swapon /swapfile
   echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
   ```

7. **Get the code.**

   ```bash
   git clone https://github.com/Exar-lab/AegisNotify.git && cd AegisNotify
   ```

## Public IP, sslip.io and the stop/start problem

sslip.io maps `app.203-0-113-10.sslip.io` to `203.0.113.10`. The hostname **is** the IP, so
everything derived from `PUBLIC_HOST` is tied to it: the TLS certificates, the Keycloak issuer,
the realm redirect URIs, the gateway CORS origin and the frontend `config.json`.

**What happens when the IP changes.** A normal EC2 public IPv4 address is released when the
instance is stopped and a different one is assigned on start (a plain reboot keeps it). After a
stop/start without an Elastic IP:

- the old hostnames no longer point at your server and the new ones have no certificates yet;
- `PUBLIC_HOST` in `.env.prod` is wrong, so Keycloak's `KC_HOSTNAME`, the CORS origin and the
  frontend URLs are wrong;
- **the realm is not re-imported**, because Keycloak skips the import when the realm already
  exists. The `aegis-admin-frontend` client keeps the old redirect URIs and web origin, and login
  fails with "Invalid parameter: redirect_uri".

Recovery, in order of effort:

1. Edit `PUBLIC_HOST` in `.env.prod` (new IP with dots replaced by dashes, plus `.sslip.io`), then
   run `scripts/deploy up`.
2. Fix the Keycloak client. Either edit it in the admin console (`https://auth.<new host>/admin`,
   realm `aegis`, Clients, `aegis-admin-frontend`: Valid redirect URIs `https://app.<new host>/*`,
   `https://app.<new host>/silent-check-sso.html`, `https://app.<new host>`, and Web origins
   `https://app.<new host>`), or reset the Keycloak database so the realm is imported again with the
   new values (this deletes users and edits made in the console):

   ```bash
   docker compose --env-file .env.prod -f docker-compose.prod.yml stop keycloak
   docker compose --env-file .env.prod -f docker-compose.prod.yml exec postgres \
     psql -U aegis -d aegisnotify -c 'DROP DATABASE keycloak WITH (FORCE)' -c 'CREATE DATABASE keycloak'
   docker compose --env-file .env.prod -f docker-compose.prod.yml up -d keycloak
   ```

3. Wait for new certificates (one per hostname, three in total). Each new IP is a new set of
   names, so the Let's Encrypt limits in
   [HTTPS certificates and rate limits](#https-certificates-and-rate-limits) apply again.
4. If the frontend is on Vercel or S3, update its `KEYCLOAK_URL` / `API_BASE_URL`, redeploy it, and
   set `FRONTEND_ORIGIN` only if the SPA origin changed.

**Elastic IP: avoids all of that, costs money.** An Elastic IP stays with your account, so the
hostnames, certificates and realm survive any number of stops and starts. AWS charges for every
public IPv4 address, in use or idle; at the time of writing that is about 0.005 USD per hour
(roughly 3.65 USD per month if held continuously). Confirm the current rate and whether your
free plan covers it on the VPC pricing page (Public IPv4 Address tab). The address keeps costing
while the instance is stopped. Release it at [teardown](#teardown).

Recommendation: allocate an Elastic IP for the demo period. If you choose not to, follow the
recovery steps above on every start, or use a real domain name instead of sslip.io and update
its DNS record.

## First deploy

On the server, in the repository directory:

```bash
scripts/deploy init <public-ip> <your-email>   # creates .env.prod (mode 600) with generated secrets
scripts/deploy up                              # validates, builds the images, starts the stack
```

`init` derives `PUBLIC_HOST` from the IP (`203.0.113.10` becomes `203-0-113-10.sslip.io`), fills
every secret with random values, and prints the demo login password. It refuses to overwrite an
existing `.env.prod`. **Back `.env.prod` up outside the server**; it holds the only copy of the
secrets, including `AUDIT_ENCRYPTION_KEY`. Without the helper, copy
[`.env.prod.example`](../.env.prod.example) and replace every `CHANGE_ME`.

Required variables (every one except the optional blocks is checked by Compose and by
`scripts/deploy check`):

| Variable | Purpose |
| --- | --- |
| `PUBLIC_HOST` | `<ip-with-dashes>.sslip.io`; the single source of every public URL |
| `ACME_EMAIL` | Contact for Let's Encrypt / ZeroSSL |
| `KC_BOOTSTRAP_ADMIN_USERNAME`, `KC_BOOTSTRAP_ADMIN_PASSWORD` | Keycloak admin console login |
| `KEYCLOAK_ADMIN_CLIENT_SECRET` | Secret of the `aegis-user-service` client (rendered into the realm) |
| `DEMO_USER_PASSWORD` | Password of the demo user `aegis-dev` |
| `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | Database credentials (shared by the notification service and Keycloak) |
| `EUREKA_SECURITY_USER`, `EUREKA_SECURITY_PASSWORD` | Eureka Basic auth |
| `CONFIG_SERVER_USER`, `CONFIG_SERVER_PASSWORD` | Config Server Basic auth |
| `AUDIT_ENCRYPTION_KEY` | `openssl rand -base64 32`; encrypts recipients at rest |
| `ACME_DIR`, `COMPOSE_PROFILES`, `FRONTEND_ORIGIN` | Optional switches, see below |
| Provider and aggregation variables | Optional; defaults are the `local-placeholder` values, so real deliveries fail until set |

The first start takes several minutes. Watch it with `scripts/deploy status` (or
`docker compose --env-file .env.prod -f docker-compose.prod.yml logs -f keycloak caddy`) until the
Spring Boot services report `healthy`.

Verify from the server and then from another computer:

```bash
H=203-0-113-10.sslip.io        # your PUBLIC_HOST
curl -si https://api.$H/actuator/health | head -1                                  # HTTP/2 200
curl -s  https://auth.$H/realms/aegis/.well-known/openid-configuration | head -c 200
curl -s  https://app.$H/config.json                                                # your URLs
```

Then open `https://app.<PUBLIC_HOST>` in a browser, sign in as `aegis-dev` with the generated
`DEMO_USER_PASSWORD`, and confirm that the dashboard loads without CORS errors in the browser
console. Provision at least one active template before submitting notifications (the notification
service has no template seed; see the [README](../README.md#4-provision-a-template)).

## Choose where the frontend lives

Default: the frontend container serves the SPA at `https://app.<PUBLIC_HOST>` (compose profile
`frontend`, enabled by `COMPOSE_PROFILES=frontend`). It needs no extra account and the origin
follows `PUBLIC_HOST`.

**One origin only.** The gateway accepts exactly one CORS origin (`SecurityConfig` wraps
`CORS_ALLOWED_ORIGINS` in a single-element list) and the Keycloak client is rendered for one
origin. Choose the SPA's home *before the first deploy*:

- **Served by the VM (default).** Leave `FRONTEND_ORIGIN` unset.
- **Vercel.** Set `COMPOSE_PROFILES=` (empty) and `FRONTEND_ORIGIN=https://<project>.vercel.app` in
  `.env.prod` before the first `scripts/deploy up`. In Vercel, import the repository with root
  directory `aegis-admin-frontend` (it contains a [`vercel.json`](../aegis-admin-frontend/vercel.json)
  that writes `public/config.json` at build time and sets the SPA rewrite and `no-store` header), and
  define the build environment variables `API_BASE_URL=https://api.<PUBLIC_HOST>` and
  `KEYCLOAK_URL=https://auth.<PUBLIC_HOST>`. A custom Vercel domain works the same way; use that
  origin as `FRONTEND_ORIGIN`.
- **S3 + CloudFront.** Build with `pnpm build`, replace `config.json` in
  `dist/aegis-admin-frontend/browser` with `{"apiBaseUrl":"https://api.<PUBLIC_HOST>","keycloakUrl":"https://auth.<PUBLIC_HOST>"}`,
  upload, and put CloudFront in front for HTTPS (S3 website endpoints are HTTP only). Map 403/404
  to `/index.html` with status 200 and set `Cache-Control: no-store` on `config.json`.
  `/silent-check-sso.html` must be served as the real file. Use the CloudFront origin as
  `FRONTEND_ORIGIN`. This route was not exercised.

If you switch the frontend home after the first deploy, the realm import is skipped (see the
previous section): update the client in the admin console or reset the Keycloak database, and
change `FRONTEND_ORIGIN` so the gateway CORS origin follows.

## HTTPS certificates and rate limits

Caddy requests one certificate per hostname (`app.`, `api.`, `auth.`) on first start, using the
ACME HTTP and TLS-ALPN challenges, so ports 80 and 443 must be open to the internet and the
hostname must already resolve to the VM. Certificates and the ACME account live in the
`aegis-caddy-data` volume; **do not delete it** between redeploys.

- **Shared domain risk.** `sslip.io` (and its sibling `nip.io`) is a shared public domain used by
  many people. Let's Encrypt limits how many certificates can be issued per registered domain per
  week, and the nip.io operators themselves note that requests can be rate-limited. Whether
  `sslip.io` is on the Public Suffix List, which would give each IP its own quota, was **not
  verified**. If issuance fails with a rate-limit error, wait (the limits are weekly), or use a
  different IP, or a domain you own.
- **Duplicate limit.** Re-issuing the same set of names repeatedly (for example by deleting
  `aegis-caddy-data` on every redeploy) hits a separate, small per-week limit. Keep the volume.
- **ZeroSSL fallback.** With the default `ACME_DIR=./docker/caddy/acme-default` Caddy uses its
  built-in behaviour: Let's Encrypt first, ZeroSSL if that fails. ZeroSSL needs `ACME_EMAIL`. This
  is Caddy's documented default; the fallback was not exercised here.
- **Staging for rehearsals.** Set `ACME_DIR=./docker/caddy/acme-staging` to use the Let's Encrypt
  staging CA, which has far higher limits but issues certificates browsers do not trust. Use it to
  test the whole procedure, then switch back to `acme-default`, delete the `aegis-caddy-data` volume
  (otherwise staging certificates keep being served) and recreate the `caddy` container:

  ```bash
  docker compose --env-file .env.prod -f docker-compose.prod.yml rm -sf caddy
  docker volume rm aegisnotify-prod_aegis-caddy-data
  docker compose --env-file .env.prod -f docker-compose.prod.yml up -d caddy
  ```

- **Diagnose** with `docker compose --env-file .env.prod -f docker-compose.prod.yml logs caddy`; failed
  orders name the challenge and the CA response.
- **A real domain** removes the shared-domain risk: create `app.`, `api.` and `auth.` A records
  pointing at the Elastic IP and set `PUBLIC_HOST` to the domain. Nothing else changes.

## Update

```bash
cd AegisNotify
git pull
scripts/deploy up        # rebuilds changed images and recreates changed containers
docker image prune -f    # drop dangling images
```

Database volumes are untouched. Flyway applies new notification-service migrations on start.
A change to `docker/keycloak/prod/aegis-realm.json.tpl` does **not** reach an existing realm;
apply it in the admin console or reset the Keycloak database (see above).

## Stop and start to save credits

Stopping the instance stops compute billing. What keeps costing while stopped:

| Item | Cost while the instance is stopped |
| --- | --- |
| EBS volume (30 GiB gp3) | Billed per GiB-month |
| Elastic IP | Billed hourly (see above) |
| Snapshots / AMIs | Billed per GiB-month |
| Compute | Not billed |

Procedure:

1. Optional clean shutdown: `scripts/deploy down` (volumes are kept). It is not required: Docker
   stops containers on shutdown, and PostgreSQL and MongoDB recover from a normal stop.
2. Console, Instance state, Stop instance.
3. To resume: Start instance. Docker is enabled and every service has `restart: unless-stopped`, so
   the stack comes back by itself; allow several minutes for the Spring Boot services to turn
   healthy (`scripts/deploy status`).
4. **Without an Elastic IP the public IP has changed.** Follow
   [the recovery steps](#public-ip-sslipio-and-the-stopstart-problem) before the demo.

Do the first start-after-stop well before a live demo, and open the login page once to confirm it.

## Backups

State lives in named volumes (`docker volume ls`, prefix `aegisnotify-prod_`): PostgreSQL
(`aegisnotify` and `keycloak` databases), MongoDB (audit), Kafka (in-flight messages, rebuildable),
and Caddy (certificates). Back up from the server:

```bash
mkdir -p ~/backups && cd ~/AegisNotify
C="docker compose --env-file .env.prod -f docker-compose.prod.yml"
$C exec -T postgres pg_dump -U aegis -d aegisnotify > ~/backups/aegisnotify-$(date +%F).sql
$C exec -T postgres pg_dump -U aegis -d keycloak    > ~/backups/keycloak-$(date +%F).sql
$C exec -T mongo mongodump --archive --db aegisnotify-audit > ~/backups/audit-$(date +%F).archive
cp .env.prod ~/backups/env.prod-$(date +%F)    # contains secrets: copy it off the server securely
```

Copy `~/backups` off the instance (for example `scp` to your computer) or take an EBS snapshot
of the volume. Restore into an empty stack with `psql` / `mongorestore --archive` after
`scripts/deploy up`, using the same `.env.prod` (in particular the same `AUDIT_ENCRYPTION_KEY`).
Restores were not rehearsed.

## Teardown

Everything can be recreated from this guide and a backup of `.env.prod`.

1. Optional: take the [backups](#backups).
2. On the server: `docker compose --env-file .env.prod -f docker-compose.prod.yml down -v`
   (`-v` deletes all volumes, including certificates and data).
3. AWS console: terminate the instance (this deletes the root volume unless you changed
   "delete on termination").
4. Release the Elastic IP (EC2, Elastic IPs, Release); an unattached address keeps costing.
5. Delete leftover EBS volumes and snapshots, the security group and the key pair if unused.
6. Remove the Vercel project or S3 bucket and CloudFront distribution if you created them.
7. Check Billing for any remaining line items the next day.

To recreate: repeat [Provision](#provision-an-aws-ec2-instance) and [First deploy](#first-deploy).

## Troubleshooting

| Symptom | Likely cause and check |
| --- | --- |
| Browser shows a certificate error, or `caddy` logs ACME errors | Ports 80/443 closed in the security group; hostname does not resolve to the VM (`dig +short app.$H`); rate limit; staging CA still selected (`ACME_DIR`). Read `logs caddy`. |
| `502 Bad Gateway` on `auth.` or `api.` | The service is still starting or restarting. `scripts/deploy status`; Keycloak needs a few minutes on first start. |
| Login: "Invalid parameter: redirect_uri" | Client redirect URIs do not match the SPA origin (IP changed, or `FRONTEND_ORIGIN` mismatch). See the stop/start section. |
| Page loads, API calls fail with CORS errors | `FRONTEND_ORIGIN` / `app.<PUBLIC_HOST>` differs from the origin in the address bar; the gateway allows exactly one origin. Check `docker compose ... exec aegis-api-gateway env`. |
| Page loads but login redirects to `localhost` | `config.json` is missing or cached. Open `https://app.<PUBLIC_HOST>/config.json`; for Vercel, check the build variables. |
| A service restarts repeatedly | `docker inspect --format '{{.State.OOMKilled}}' <container>`: `true` means the memory limit is too low; raise it in the compose file. Otherwise read `logs <service>`. |
| `docker compose` complains about a missing variable | A required value is empty in `.env.prod`; the message names it. Run `scripts/deploy check`. |
| Keycloak fails with "database \"keycloak\" does not exist" | The PostgreSQL volume predates `init-keycloak.sql`. Create it: `exec postgres psql -U aegis -d aegisnotify -c 'CREATE DATABASE keycloak'`, then restart `keycloak`. |
| Keycloak container "unhealthy" but the login page works | The health check probes port 9000 with bash; it was not verified. Dependents only wait for Keycloak to start, so this does not block the stack. |
| Image build is killed (exit 137) | Out of memory during Maven/Angular build. Add swap, build with `COMPOSE_PARALLEL_LIMIT=1`, or build on a larger temporary instance. |
| Notifications stay `PENDING` / fail | Providers use `local-placeholder` credentials, so real sends fail by design; the list and timeline still demonstrate the flow. Set real provider keys only if needed. |
| Disk full | `docker system df`; `docker builder prune`; `docker image prune -a`. |

## Demo script

About five minutes, from a second computer. Prepare beforehand: instance running, one active
template created, the password at hand, a token command ready.

1. **Context (30 s).** "AegisNotify is a notification orchestration service: outbox for
   at-least-once delivery, circuit breakers per provider, automatic fallback, dead-letter queue."
   Show `https://app.<PUBLIC_HOST>` in the browser: HTTPS, no local setup.
2. **Login (30 s).** Sign in as `aegis-dev`. Point out that authentication is Keycloak (Authorization
   Code with PKCE) and the browser talks only to `api.` and `auth.`.
3. **Submit a notification (60 s).** Get a token and call the API (payload fields in
   [`api-guide.md`](api-guide.md)):

   ```bash
   H=<PUBLIC_HOST>
   TOKEN=$(curl -s https://auth.$H/realms/aegis/protocol/openid-connect/token \
     -d grant_type=password -d client_id=aegis-dev-cli \
     -d username=aegis-dev -d "password=<DEMO_USER_PASSWORD>" | jq -r .access_token)
   curl -si https://api.$H/api/v1/notifications -H "Authorization: Bearer $TOKEN" \
     -H 'Content-Type: application/json' -d '<payload from api-guide.md>'   # 202 + notification id
   ```

4. **See it in the UI (60 s).** Notifications list: the new entry and its status timeline (detail
   page). Dashboard and Metrics: counters and latency.
5. **Resilience (60 s).** Providers page: primary/secondary state and circuit breakers. Explain that
   with placeholder credentials the provider call fails, which is exactly what exercises retry,
   fallback and the DLQ; point at the audit trail.
6. **Close (30 s).** Architecture: gateway, Eureka, Kafka, per-service stores, one VM, HTTPS by
   Caddy; mention that the instance is stopped between demos to save credits.

If anything fails live, switch to the recorded walkthrough below.

## Recorded walkthrough as a fallback

The instance will often be stopped, and a stopped instance cannot be shown. Record a 3 to 5 minute
screen capture of the script above on a healthy deployment, with the browser address bar visible
(it proves HTTPS and a real host), and store it somewhere permanent: the repository's release
assets, a YouTube unlisted video, or the portfolio site. Link it from the README or the portfolio
page together with screenshots from [`docs/user-manual/images`](user-manual/images). Re-record after
UI changes. State on the portfolio page that the live demo runs on request.

## Alternatives to AWS

None of these were tested with this stack.

- **Oracle Cloud Always Free.** Ampere A1 (ARM) instances with up to 4 OCPU and 24 GB RAM have been
  offered in the always-free tier; that is enough headroom for this stack and costs nothing while
  you stay in the free limits. Capacity in popular regions can be scarce, idle accounts can be
  reclaimed, and the images must run on arm64. Open ports 80/443 in both the VCN security list and
  the instance firewall (`iptables`). The public IP can be reserved.
- **A cheap VPS (Hetzner, DigitalOcean, Vultr, ...).** Pick 8 GB RAM, Ubuntu 24.04, and use the
  provider's firewall for 22/80/443. A fixed public IP is normally included, which avoids the
  stop/start problem, but these providers bill a powered-off server too.

On any provider the Docker, `scripts/deploy` and Caddy steps are identical.

## Security notes

This is a demo hardened only as far as the effort allows.

- **Keycloak admin console is public** at `https://auth.<PUBLIC_HOST>/admin`. It is protected by
  `KC_BOOTSTRAP_ADMIN_PASSWORD` (generated). Restrict it with a Caddy `@admin path /admin/*` matcher
  and a `remote_ip` rule if you do not want it reachable by everyone.
- **`aegis-dev-cli` is a public client with password grant**, kept so the demo can fetch a token
  with curl. Anyone who knows the demo password can use it. Delete the client after the demo or
  rotate `DEMO_USER_PASSWORD`.
- **The demo user is shared.** Treat `DEMO_USER_PASSWORD` as public while a demo runs and change it
  afterwards (admin console, Users).
- MongoDB and Redis run without authentication, and Kafka uses PLAINTEXT. This is acceptable only
  because none of them publishes a port; do not add `ports:` to them.
- Actuator `health`, `info` and `prometheus` follow the gateway rules in
  [`deployment.md`](deployment.md#secrets-handling); `prometheus` requires a token.
- SSH is limited to your IP in the security group; if your IP changes, update the rule.
- Never commit `.env.prod`; `.gitignore` excludes `.env` and `.env.*` except the `*.example` files.
- Keep the OS patched (`sudo apt update && sudo apt upgrade`) and reboot occasionally.

## Unverified assumptions

Everything below is believed correct but was not observed. Check it on the first deploy.

1. **Memory sizing.** All limits and JVM percentages are estimates for an 8 GiB host. Measure with
   `docker stats`; OOM-killed containers will show `OOMKilled=true`.
2. **Keycloak in production mode behind Caddy.** `start --import-realm` with `KC_HOSTNAME`,
   `KC_PROXY_HEADERS=xforwarded`, `KC_HTTP_ENABLED` and PostgreSQL was not run; only `start-dev`
   with the same hostname variables was. The assumption that the internal calls
   (`http://keycloak:8080` for JWKS and the Admin API) work while `KC_HOSTNAME` is the public URL
   rests on Keycloak's hostname v2 behaviour and the gateway not validating the issuer.
3. **Keycloak health check** (bash `/dev/tcp` to port 9000) and the `JAVA_OPTS_KC_HEAP` override.
4. **ACME.** No certificate was requested. The sslip.io rate-limit exposure, the Public Suffix List
   status and Caddy's Let's Encrypt-to-ZeroSSL fallback are from documentation and recollection.
5. **Realm import.** Verified only for a fresh `start-dev` container; the "import skipped when the
   realm exists" behaviour was observed there and is assumed identical in production mode.
6. **Image builds.** The frontend image (`node:22-alpine`, pnpm via corepack, `caddy:2-alpine`) and
   the six service images were not built in this work; the service Dockerfiles are unchanged.
7. **Kafka** with a single broker, replication 1, and a TCP health check; **topic replication
   environment variables** `NOTIFICATION_KAFKA_TOPIC_*` are taken from `deployment.md`, not
   exercised.
8. **AWS.** Free-plan terms, eligible instance types, the Elastic IP price and the public-IP
   behaviour on stop/start are from the issue and general knowledge; confirm them in the console and
   the pricing pages.
9. **sslip.io availability.** The service is run by volunteers; if it is down, DNS for the demo
   fails. A domain you control avoids that dependency.
