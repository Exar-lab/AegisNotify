# Admin frontend image for docker-compose.prod.yml (issue #236).
# Build context is ./aegis-admin-frontend:
#   docker build -f docker/frontend/Dockerfile aegis-admin-frontend
#
# One image for any host: the API and Keycloak URLs are not baked in. The
# runtime Caddyfile (docker/frontend/Caddyfile) answers /config.json from the
# API_BASE_URL and KEYCLOAK_URL environment variables (runtime config, #171).

# ---- build stage ----
# Node 22 and pnpm 11 match .github/workflows/ci-frontend.yml.
FROM node:22-alpine AS build
WORKDIR /app
RUN corepack enable
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
RUN pnpm install --frozen-lockfile
COPY . .
RUN pnpm build

# ---- runtime stage ----
FROM caddy:2-alpine
COPY --from=build /app/dist/aegis-admin-frontend/browser /srv
EXPOSE 80
CMD ["caddy", "run", "--config", "/etc/caddy/Caddyfile", "--adapter", "caddyfile"]
