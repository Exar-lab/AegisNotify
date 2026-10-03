# AegisNotify Admin Frontend — Requirements

Single source of truth for visual identity, stack decisions, and screen
definitions for `aegis-admin-frontend`. Any new screen or component must
conform to this document; if it doesn't fit, update this doc first, then
build.

> **Note on the `design/*-claude-preview.png` mockups**: those used a generic
> blue/slate "SaaS admin" palette. That palette was explicitly rejected —
> **do not use blue as the brand/primary color**. The mockups still define
> valid *layout and screen structure* (§5), just not the colors in §2.

## 1. Stack

- **Framework**: Angular 22 (standalone components, no NgModules).
- **Styling**: Tailwind CSS v4 — installed (`tailwindcss`,
  `@tailwindcss/postcss`). Entry point is `src/styles.tailwind.css`
  (loaded before `src/styles.scss` in `angular.json`'s `styles` array).
  Tailwind utility colors (`bg-brand`, `text-status-danger`, etc.) are mapped
  via `@theme inline` to the CSS custom properties below, so there is one
  source of truth, not two competing color systems.
- **Theming**: `core/theme/theme.service.ts` toggles `[data-theme="dark"]` on
  `<html>` (signal-based, persisted in `localStorage`). Tailwind's dark
  variant is remapped in `styles.tailwind.css` via
  `@custom-variant dark (&:where([data-theme="dark"], [data-theme="dark"] *))`
  to match — do not use Tailwind's default `.dark` class convention here.
- **Auth**: Keycloak via `keycloak-angular` (already integrated in
  `core/auth/auth.service.ts`).
- **State**: Angular signals for local/UI state; RxJS for streams coming from
  HTTP/WebSocket (notification status updates, circuit breaker state).
- **Testing**: Vitest (already configured).

## 2. Color Palette

This is the real, implemented palette — CSS custom properties defined in
`src/styles.scss` (`:root` = light, `[data-theme="dark"]` = dark override),
bridged into Tailwind via `src/styles.tailwind.css`. **Edit the palette in
`styles.scss`, never hardcode a hex value in a component stylesheet** — the
sidebar/topbar had stray hardcoded blues from before this was formalized;
those were removed.

### 2.1 Brand / Primary — crimson red

Not blue (mockup palette, rejected), not teal (first pass, superseded).
Sampled from the actual pixels of the Angular CLI default favicon
(`public/favicon.ico`, the one visible in the browser tab) — picked the most
saturated red in its pink→red→purple gradient (`#F4094C`).

| Token | CSS var | Light | Dark | Usage |
|---|---|---|---|---|
| `brand` | `--color-accent` | `#F4094C` | `#F4094C` | Primary actions, active nav, logo mark, links |
| `brand-hover` | `--color-accent-hover` | `#D1073F` | `#FF4D80` | Hover/pressed state |
| `brand-subtle` | `--color-accent-subtle` | `rgba(244,9,76,.12)` | `rgba(244,9,76,.18)` | Badge backgrounds, selected-row tint |

`status-danger` (§2.3) was moved to orange specifically so it doesn't
compete with this red — don't reintroduce a red status color without
re-checking that contrast.

### 2.2 Base neutrals (slate)

| Token | CSS var | Light | Dark |
|---|---|---|---|
| `surface-app` | `--bg-main` | `#F1F5F9` | `#0B0D14` |
| `surface` | `--bg-surface` | `#FFFFFF` | `#1F2937` |
| `surface-sidebar` | `--bg-sidebar` | `#F8FAFC` | `#11151F` |
| `surface-topbar` | `--bg-topbar` | `#FFFFFF` | `#181E2B` |
| `border` | `--border-color` | `#E2E8F0` | `#374151` |
| `text-primary` | `--text-primary` | `#0F172A` | `#F8FAFC` |
| `text-secondary` | `--text-secondary` | `#64748B` | `#9CA3AF` |

### 2.3 Semantic / Status

Map directly to notification lifecycle states and circuit breaker states —
never introduce a new color for a new status without updating this table
first. `status-danger` is **orange**, not red — the brand is red (§2.1), so
danger was moved off red to avoid the two clashing (a `FAILED_CRITICAL`
badge next to a branded button used to read as "two different reds").
Success/warning/fallback keep their conventional meaning.

| Token | CSS var | Hex (light) | Hex (dark) | Meaning | Used for |
|---|---|---|---|---|---|
| `status-success` | `--status-online` | `#10B981` | `#10B981` | Healthy / delivered | `SENT`, circuit `CLOSED` |
| `status-success-bg` | `--status-success-bg` | `rgba(16,185,129,.12)` | `rgba(16,185,129,.16)` | — | Success badge background |
| `status-danger` | `--status-offline` | `#EA580C` | `#FB923C` | Failure / blocking | `FAILED_CRITICAL`, circuit `OPEN` |
| `status-danger-bg` | `--status-danger-bg` | `rgba(234,88,12,.12)` | `rgba(251,146,60,.16)` | — | Danger badge background |
| `status-warning` | `--status-warning` | `#D97706` | `#FBBF24` | Degraded / in progress | `PROCESSING`, circuit `HALF_OPEN` |
| `status-warning-bg` | `--status-warning-bg` | `rgba(217,119,6,.12)` | `rgba(251,191,36,.16)` | — | Warning badge background |
| `status-fallback` | `--status-fallback` | `#8B5CF6` | Fallback activated | `SENT_VIA_FALLBACK` |
| `status-fallback-bg` | `--status-fallback-bg` | `rgba(139,92,246,.12)` | — | Fallback badge background |
| `status-neutral` | `--status-neutral` | `#64748B` | Queued / pending | `PENDING`, `QUEUED` |
| `status-neutral-bg` | `--status-neutral-bg` | `rgba(100,116,139,.12)` | — | Neutral badge background |

### 2.4 Channel Icon Colors

Use `brand-subtle`/`brand` for EMAIL (it's the primary channel), and
`status-fallback`/`status-fallback-bg` (violet) for SMS/WhatsApp/Push icon
tiles so channels read as distinct from both brand and status colors.

| Channel | Icon bg | Icon color |
|---|---|---|
| EMAIL | `--color-accent-subtle` | `--color-accent` |
| SMS / PUSH | `--status-fallback-bg` | `--status-fallback` |
| WHATSAPP | `--status-success-bg` | `--status-success` |

### 2.5 Dark Mode

Implemented (not speculative) — see `theme.service.ts` and the `[data-theme="dark"]`
block in `styles.scss`. Every token in §2.1–2.3 has a dark-mode value; use the
CSS var or Tailwind semantic utility (never a hardcoded hex) so components
pick up the active theme automatically.

## 3. Typography

- Font: `'Inter', system-ui, -apple-system, ...` (`--font-family-base` in
  `styles.scss`) — already set, keep it, don't fall back to the mockups'
  plain system stack.
- Page title: `text-2xl font-bold` on `text-primary`.
- Section/card title: `text-sm font-semibold` on `text-primary`.
- Body/table text: `text-sm` on `text-primary`.
- Meta/label text (uppercase column headers, timestamps): `text-xs font-medium uppercase tracking-wide` on `text-secondary`.
- Big stat numbers (dashboard KPI cards): `text-3xl font-bold` on `text-primary`.

## 4. Layout Shell

- Fixed left sidebar, `260px` wide (`.sidebar` in `sidebar.component.scss`),
  `surface-sidebar` background, full height. Contains: logo mark + app name,
  nav items (icon + label), version/status footer pinned to bottom.
- Main content area: `surface-app` background, `24px` padding, page header
  (title + subtitle on the left, primary action/search on the right) followed
  by content (KPI cards grid, tables, detail panels).
- Cards: `surface` background, `rounded-xl`, `1px solid border`, `p-5`, no
  heavy shadows — flat cards with a hairline border, not elevation.
- Tables: borderless rows separated by a hairline `border`, uppercase column
  headers in `text-secondary`, row hover tinted with `brand-subtle`.

## 5. Screens (structure from mockups, colors from §2)

### 5.1 Dashboard (`dashboard-claude-preview.png`)
- 4 KPI cards: Total Requests (24h), Success Rate, Fallback Activations, Avg
  Delivery Latency — each with a trend delta line.
- Provider Circuit Breakers panel: one row per provider, name + role
  ("Primary provider" / "Fallback: X"), status pill (`CLOSED` / `OPEN` /
  `HALF_OPEN`).
- Recent Notifications panel: compact table (ID, Channel, Priority, Status,
  Timestamp) with a "View all" link to the Notifications screen.

### 5.2 Notifications (`notifications-list-claude-preview.png`)
- Header: title + count ("X notifications in the last 24 hours"), primary
  button "New Notification".
- Filter bar: search by ID/recipient/template, Channel/Priority/Status
  dropdowns, date range, export icon button.
- Table columns: Notification (icon + ID + channel), Recipient, Priority
  badge, Status badge, Provider used, Timestamp, row action menu (`...`).
- Pagination footer.
- Row click (or detail panel per `detail-claude-preview.png`) opens the full
  audit trail timeline backed by `GET /api/v1/notifications/{id}/status`.

### 5.3 Providers (`providers-claude-preview.png`)
- 4 summary cards: Healthy count, Degraded count, Circuit Open count, Calls
  Today.
- Grouped by channel (EMAIL, SMS, WHATSAPP, PUSH), each group showing
  primary + fallback provider cards side by side.
- Each provider card: icon, name, role (PRIMARY/FALLBACK), status pill,
  Failure Rate / Slow Call Rate / Calls (24h) stats, success-rate progress
  bar (color = semantic status), last state change, "View Logs" link.
- Circuit-open cards get a red left border/ring to stand out at a glance.

### 5.4 Metrics — not yet mocked up
Backed by `/actuator/prometheus` data surfaced through a backend aggregation
endpoint. Needs its own mockup before implementation; don't build ahead of
design.

### 5.5 Settings — not yet mocked up
Same as above — placeholder nav entry only until a mockup exists.

## 6. Open Items

- Tailwind v4 is installed and wired (`styles.tailwind.css` + `angular.json`).
  Not yet verified against a real `ng build` in this environment — local Node
  is v22.20.0 and the Angular 22 CLI requires ≥22.22.3/24.15.0/26.0.0. Run
  `ng build` after upgrading Node to confirm end-to-end.
- Stray hardcoded blue values in `sidebar.component.scss` /
  `topbar.component.scss` were replaced with the teal brand tokens — if more
  hardcoded hex colors turn up elsewhere, replace them with the CSS vars/
  Tailwind semantic utilities from §2, don't leave them as one-offs.
- Metrics and Settings screens have no design yet; treat as blocked on new
  mockups, not as free-form implementation.
