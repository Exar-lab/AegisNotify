# Roles and permissions

[Manual index](README.md) | Previous: [Settings](settings.md) | Next: [Glossary](glossary.md)

This page explains who can do what in the admin console. It is derived only from the platform
source code and the supplied Keycloak realm file (`docker/keycloak/aegis-realm.json`), not from the
screenshots. It does not describe any role that is not present in those sources.

## Summary

- **Access in AegisNotify is controlled by OAuth2 scopes, not by named roles.** A scope is a
  permission written into your sign-in token, for example `notification:read`. The API gateway and
  each service check for the scope that each request needs.
- **The console does not hide or disable anything based on your access.** Every sidebar link is
  always shown, and every button is always shown. The only check the console makes itself is that
  you are signed in. If your token lacks a required scope, the screen or action fails, and the
  console shows a generic error message rather than a message about permissions.
- **The role label in the top bar is fixed text.** It always reads "Administrator", whatever your
  access is. The `ADMIN`, `OPERATOR`, and `VIEWER` roles in the manual's screenshots are sample data
  from the mocked environment used to take them. They are not defined in the supplied realm file,
  and nothing in the source code gives them a meaning.
- **Realm roles assigned on the [Users](users-and-roles.md) screen do not change API access in the
  source code.** The services do not read roles from the token. No source file checks a role name
  to allow or deny a request.

## The scopes

The scope contract is in [OAuth2 scopes](../security/scopes.md). The five scopes are:

| Scope | Allows |
| --- | --- |
| `notification:read` | Reading notifications, the dashboard data, metrics, and templates |
| `notification:write` | Submitting, cancelling, and retrying notifications, and creating, editing, and activating or deactivating templates |
| `audit:read` | Reading audit trails (`/api/v1/audit/**`). No console screen uses this scope. |
| `user:read` | Reading users and the list of realm roles |
| `user:admin` | Creating and editing users, assigning roles, resetting passwords, enabling and disabling users |

Two rules matter for the console:

- **The scopes are not hierarchical.** `user:admin` does not include `user:read`. A token that has
  only `user:admin` cannot list users, and therefore cannot use the Users screen, even though it
  could create one.
- **Anything without a named scope still requires a valid sign-in.** Routes the gateway has no
  specific rule for require authentication only. Health and info endpoints are public.

## How scopes are granted

In the supplied realm file, the console's own client (`aegis-admin-frontend`) has all five scopes as
default client scopes, and the file defines no realm roles and no mapping from roles or users to
scopes. With that configuration, every user who signs in through the console receives all five
scopes, so every screen and action in the matrix below is available to every signed-in user.

Your administrators may have changed the Keycloak configuration. If scopes were restricted, the
matrix below shows which scope each action needs. To find out what a particular account can do,
ask the Keycloak administrators which scopes the console client grants to that account.

## Permission matrix

"Needs" is the scope the backend requires for the request the console makes. With the supplied
realm configuration described above, every signed-in user holds all of these scopes.

| Screen | Action | Needs | Enforced by |
| --- | --- | --- | --- |
| All screens | Open the console | A valid sign-in only | Console |
| [Dashboard](dashboard.md) | View key indicators, circuit breakers, recent notifications | `notification:read` | Gateway and notification service |
| [Notifications](notifications.md) | Search and filter the list | `notification:read` | Gateway and notification service |
| Notifications | Open a notification's detail view | `notification:read` | Gateway and notification service |
| [Providers](providers.md) | View provider health | `notification:read` | Gateway and notification service |
| [Metrics](metrics.md) | View metrics | `notification:read` | Gateway and notification service |
| [Templates](templates.md) | View the list and open a template | `notification:read` | Gateway and notification service |
| Templates | Create a template | `notification:write` | Gateway and notification service |
| Templates | Edit a template | `notification:write` | Gateway and notification service |
| Templates | Activate or deactivate a template | `notification:write` | Gateway and notification service |
| [Users](users-and-roles.md) | View and search the user list | `user:read` | Gateway and user service |
| Users | Open the Roles dialog (loads the list of realm roles) | `user:read` | Gateway and user service |
| Users | Save roles for a user | `user:admin` | Gateway and user service |
| Users | Create a user | `user:admin` | Gateway and user service |
| Users | Edit a user | `user:admin` | Gateway and user service |
| Users | Reset a password | `user:admin` | Gateway and user service |
| Users | Disable or enable a user | `user:admin` | Gateway and user service |
| [Settings](settings.md) | View runtime parameters | Nothing (the screen makes no API call) | Console |

Not available in the console, but part of the platform: submitting a notification, cancelling a
notification, and retrying a notification (`notification:write`), and reading audit trails
(`audit:read`). The console has no screen for them.

Defense in depth: the gateway checks the scope, and the downstream service checks it again. A
request must pass both.

## What you see when access is missing

The console does not interpret permission errors. The result depends on the screen:

| Situation | Typical result in the console |
| --- | --- |
| Missing `notification:read` | The affected section or screen shows its "Couldn't load ..." message with a **Retry** button. |
| Missing `notification:write` | Saving or toggling a template fails: "Couldn't update <name>. Please try again." or the form's generic save error. |
| Missing `user:read` | The Users screen shows "Couldn't load users". |
| Missing `user:admin` | Create, edit, roles, password, and status actions fail with the generic message of that dialog. |
| Expired or invalid token | See [401 and 403](troubleshooting.md#error-401-or-403). |

These messages look the same as a network or server failure. To tell them apart, see
[Troubleshooting](troubleshooting.md#error-401-or-403).
