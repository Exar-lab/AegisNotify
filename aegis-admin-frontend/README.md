# Aegis Admin Frontend

Administrative web dashboard for the AegisNotify notification orchestration platform.

## Table of Contents

- [Purpose](#purpose)
- [Tech Stack](#tech-stack)
- [Getting Started](#getting-started)
  - [Prerequisites](#prerequisites)
  - [Installation](#installation)
  - [Development Server](#development-server)
  - [Build](#build)
  - [Testing](#testing)
- [Environment Configuration](#environment-configuration)
- [Authentication Flow](#authentication-flow)
- [Project Structure](#project-structure)
- [Architectural Decisions](#architectural-decisions)
- [Changelog](#changelog)

---

## Purpose

`aegis-admin-frontend` serves as the centralized management and monitoring interface for AegisNotify. It enables operators to:
- Monitor notification dispatch lifecycles and real-time statuses.
- Inspect notification details and audit trails.
- Track delivery provider health, secondary failover, and circuit breaker states.
- Review delivery performance metrics and failure analytics.
- Manage system settings and operational configurations.

## Tech Stack

- **Framework**: Angular 22 (Standalone Components, Signals)
- **Styling**: SCSS
- **Package Manager**: pnpm
- **Authentication**: Keycloak / OIDC (Authorization Code Flow + PKCE via `keycloak-angular`, Bearer JWT Authentication)
- **Testing**: Vitest

## Getting Started

### Prerequisites

- Node.js (v20+ recommended)
- pnpm (`npm install -g pnpm`)

### Installation

```bash
pnpm install
```

### Development Server

Start the local development server on `http://localhost:4200/`:

```bash
pnpm start
# or
ng serve
```

### Build

Compile the application with production optimizations into `dist/`:

```bash
pnpm build
# or
ng build
```

### Testing

Run unit test suites with Vitest:

```bash
pnpm test
# or
ng test
```

## Environment Configuration

Configuration files are located in `src/environments/` and the project root:

| File | Environment | Description |
| --- | --- | --- |
| `src/environments/environment.ts` | Production / Default | Base API and Keycloak connection settings |
| `src/environments/environment.development.ts` | Development | Development overrides and local endpoint mappings |
| `.env` / `.env.development` | Local Tooling | Environment variable declarations for CLI / CI |

### Default Configuration Parameters

- **`apiBaseUrl`**: `http://localhost:8080` (Aegis API Gateway)
- **`keycloakUrl`**: `http://localhost:8088` (Keycloak Identity Provider)
- **`keycloakRealm`**: `aegis`
- **`keycloakClientId`**: `aegis-admin-frontend`

## Authentication Flow

The frontend implements a secure OpenID Connect (OIDC) authentication flow leveraging `keycloak-angular` and `keycloak-js`:

1. **Initialization (`check-sso`)**: On startup, `provideKeycloak` initializes with `onLoad: 'check-sso'` and uses `/silent-check-sso.html` for non-intrusive session checks.
2. **Route Protection (`authGuard`)**: The root layout (`AdminShellComponent`) and its child feature routes are protected by a functional `authGuard` (`createAuthGuard`).
3. **Authorization Code + PKCE**: Unauthenticated access attempts redirect users to the Keycloak login page at `http://localhost:8088` using the Authorization Code Flow with Proof Key for Code Exchange (PKCE) under client `aegis-admin-frontend`.
4. **Token Refresh**: Token lifecycles and background refreshes are maintained automatically via `withAutoRefreshToken`.
5. **Gateway Authorization (Bearer Token)**: The `includeBearerTokenInterceptor` intercepts outgoing HTTP requests targeting `http://localhost:8080/api/...` and attaches the `Authorization: Bearer <access_token>` header, forwarding the Bearer JWT to the API Gateway while leaving Keycloak calls intact.

## Project Structure

The project adheres to a modular, feature-first structure using Angular Standalone Components:

```text
src/
├── app/
│   ├── layouts/
│   │   ├── admin-shell/             # Main application layout wrapper
│   │   ├── sidebar/                 # Dark navigation sidebar with active route highlighting
│   │   └── topbar/                  # Header bar with user profile & status indicators
│   ├── features/
│   │   ├── dashboard/
│   │   │   └── pages/               # Dashboard overview page
│   │   ├── notifications/
│   │   │   └── pages/               # Notification list & detail pages
│   │   ├── providers/
│   │   │   └── pages/               # Provider status & circuit breakers
│   │   ├── metrics/
│   │   │   └── pages/               # Prometheus & delivery metrics
│   │   └── settings/
│   │       └── pages/               # System and UI settings
│   ├── core/
│   │   └── interceptors/            # Auth & HTTP interceptors
│   ├── app.config.ts                # Application providers & global config
│   ├── app.routes.ts                # Lazy-loaded feature routes nested under AdminShell
│   └── app.ts                       # Root application component
├── environments/
│   ├── environment.ts               # Production environment config
│   └── environment.development.ts   # Development environment config
└── styles.scss                      # Global styles & resets
```

## Architectural Decisions

1. **Standalone Components**: All components are standalone (`standalone: true` or default in modern Angular), eliminating the overhead of NgModules.
2. **Lazy Loading by Feature**: Routes in `app.routes.ts` dynamically load component bundles via `loadComponent: () => import(...)` to optimize initial page load.
3. **Hexagonal Backend Alignment**: Frontend feature modules mirror the backend domain architecture (`notifications`, `audit`, `providers`, `metrics`).
4. **Environment Isolation**: Explicit environment configurations separate local gateway endpoints and Keycloak realm definitions from deployment artifacts.
5. **Layout Shell Architecture**: The `AdminShellComponent` hosts the dark sidebar (`#0F172A`), topbar, and scrollable content area, utilizing Angular Router active route matching.

## Changelog

### [Unreleased]
- Initialized base project documentation following standard structure.
- Configured environment files (`environment.ts`, `environment.development.ts`, `.env`, `.env.development`).
- Scaffolded initial standalone page components for `dashboard`, `notifications`, `providers`, `metrics`, and `settings` features.
- Implemented visual admin layout (`AdminShellComponent`, `SidebarComponent`, `TopbarComponent`) with dark sidebar `#0F172A`, active route highlighting, and top bar status indicators.
- Configured nested route hierarchy in `app.routes.ts` and global CSS resets in `styles.scss`.
