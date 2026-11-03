# Troubleshooting and FAQ

[Manual index](README.md) | Previous: [Glossary](glossary.md)

Each answer below describes behavior verified in the platform source code. Where the cause depends
on how your administrators set up the platform, the answer says so and tells you what to ask them.
The console shows the same generic "Couldn't load ..." message for many different causes, so
several entries below help you tell them apart.

**Contents**

- [I keep being sent back to the sign-in page](#i-keep-being-sent-back-to-the-sign-in-page)
- [The dashboard is empty or shows errors](#the-dashboard-is-empty-or-shows-errors)
- [A notification is stuck in PENDING or QUEUED](#a-notification-is-stuck-in-pending-or-queued)
- [What does FAILED_CRITICAL mean?](#what-does-failed_critical-mean)
- [A circuit breaker is OPEN](#a-circuit-breaker-is-open)
- [Error 401 or 403](#error-401-or-403)
- [A template is not found or is inactive](#a-template-is-not-found-or-is-inactive)
- [A user cannot sign in](#a-user-cannot-sign-in)
- [Users screen fails to load or saving a user fails](#users-screen-fails-to-load-or-saving-a-user-fails)
- [The Metrics screen shows zeros or unusual numbers](#the-metrics-screen-shows-zeros-or-unusual-numbers)
- [The top bar title says "Dashboard" on the Templates or Users screen](#the-top-bar-title-says-dashboard-on-the-templates-or-users-screen)
- [The search box or the bell in the top bar does nothing](#the-search-box-or-the-bell-in-the-top-bar-does-nothing)

## I keep being sent back to the sign-in page

On opening, the console checks for an existing sign-in session. If there is none, it sends you to
the Keycloak sign-in page, and after you sign in Keycloak sends you back to the console. A loop
means this round trip does not complete.

What you can do:

1. Close the browser tab, open the console address again, and sign in once.
2. Clear the site data (cookies) for both the console address and the sign-in page address, or try a
   private window. Then try again.
3. Check that you open the console at the address your administrators gave you. The supplied
   Keycloak client only accepts sign-in redirects to specific addresses (`http://localhost:4200` in
   the supplied realm file). Opening the console from a different address can fail at the
   sign-in step.

If it persists, ask your administrators to check the console client in Keycloak: its valid redirect
addresses and web origins must match the address you use, and the console's `config.json` must
point to a reachable Keycloak address. The exact cause in your environment cannot be determined
from the console itself.

## The dashboard is empty or shows errors

| What you see | Meaning and next step |
| --- | --- |
| "Couldn't load dashboard KPIs", "Couldn't load circuit breaker status", "Couldn't load recent notifications" | The request failed. Use **Retry**. If it keeps failing, the API gateway or the notification service may be down, or your sign-in may lack the `notification:read` scope (see [Error 401 or 403](#error-401-or-403)). On the [Settings](settings.md) screen, check the **API Gateway** address. |
| "No notifications in the last 24h" | The request worked but there is nothing to show. This is expected on a system with no recent traffic. |
| "No circuit breakers reporting." | The request worked but returned no providers. Ask your administrators. |
| Indicators show "No prior baseline" | There was no data for the previous day to compare against. This is expected on a new system. |

The key indicators load once when you open the page. Only the circuit-breaker table refreshes by
itself (every 10 seconds). Reload the page to refresh the rest.

## A notification is stuck in PENDING or QUEUED

A notification moves forward only when background parts of the platform do their work:

- `PENDING` to `QUEUED` is done by the **outbox worker**, which reads saved requests and publishes
  them to the message broker.
- `QUEUED` to `PROCESSING` is done by the **consumer**, which picks messages up from the broker.

In the notification service both are switched off by default (settings
`notification.kafka.relay.enabled` and `notification.kafka.consumer.enabled`) and are switched on
in the full Docker setup supplied with the project. A notification will therefore stay `PENDING`
or `QUEUED` if either is not running, if the message broker (Kafka) is unavailable, or if the
notification service is down. These are administrator tasks. When you report it, give the
notification ID and its current status.

Other things to check:

- The timeline on the [detail view](notifications.md#notification-detail) shows the last event
  recorded and its time.
- A template that was deactivated after the request was accepted can prevent processing. See
  [A template is not found or is inactive](#a-template-is-not-found-or-is-inactive).
- If the aggregation feature is enabled by your administrators, a notification may be held and
  grouped with others before it is sent. The console does not show whether aggregation is enabled.

The console cannot cancel or retry a notification. The platform has API operations for this
(cancel is only allowed for `PENDING` and `QUEUED`, retry only for `FAILED`), but they are not
available from the screens.

## What does FAILED_CRITICAL mean?

`FAILED_CRITICAL` means the primary provider and the fallback provider both failed to deliver the
message. The message was **not delivered**, and the platform routed it to the dead-letter queue
(DLQ) so that someone can follow up manually. The detail view shows the notice "Delivery failed
critically. Check the timeline for dead-letter queue routing details."

What to do:

1. Open the notification and read **Provider attempts**. Each row shows the provider, its role, the
   result code, and the error message.
2. Check [Providers](providers.md) to see whether the providers for that channel are healthy.
3. Report the notification ID and correlation ID to your administrators. The console cannot
   resend the message.

`FAILED` is different. It means delivery failed and the notification can be retried through the
platform's API. See the [glossary](glossary.md#notification-states).

## A circuit breaker is OPEN

An `OPEN` circuit breaker means a provider has been failing or responding too slowly, so the
platform stopped calling it for a while. For that channel, messages go to the fallback provider.
You will see `SENT_VIA_FALLBACK` statuses and `CIRCUIT_OPEN` results in Provider attempts. These
messages are still delivered.

What to expect:

- After the waiting period (60 seconds in the platform defaults) the breaker moves to `HALF_OPEN`
  and lets a few test calls through. It returns to `CLOSED` if they succeed.
- You cannot open or close a breaker from the console.
- If the fallback provider also fails, messages become `FAILED_CRITICAL`.
- `FORCED_OPEN` does not recover by itself. It means someone forced the breaker open. Ask your
  administrators.

See [Providers](providers.md) and the [glossary](glossary.md#circuit-breaker-states).

## Error 401 or 403

These are standard web responses that the console does not translate for you. You normally see a
generic message such as "Couldn't load ..." and **Retry**. Your browser's network tools show the
actual code.

| Code | Meaning | What to do |
| --- | --- | --- |
| **401** | The platform could not accept your sign-in token: it is missing, expired, or malformed. | Sign out and sign in again. If it recurs, tell your administrators. |
| **403** | Your token is valid, but it does not contain the scope the action needs. | Ask your administrators to grant the scope. See [Roles and permissions](roles-and-permissions.md). |

Notes:

- When the API gateway returns 403 it names the required scope in the response body. Services behind
  the gateway return a plain 403 with no detail by design.
- A 403 can also come from the gateway's browser-origin (CORS) check if the console is served from
  an address the gateway does not allow. The allowed address is set by administrators. The default
  is `http://localhost:4200`.
- Scope checks happen only after sign-in succeeds, so a 403 caused by a missing scope always means your token itself was accepted.

## A template is not found or is inactive

- **A notification request is rejected with "template not found" (HTTP 404).** The platform looks up
  the template by name and only accepts an **active** one. The name may be misspelled, the template
  may not exist, or it may be `Inactive` on the [Templates](templates.md) screen. Names must match
  exactly. Activate the template, or correct the name used in the request.
- **A notification fails after being accepted.** The platform checks for an active template again
  when it processes the message. If the template was deactivated or removed in between, processing
  fails with a template-not-found error. Reactivate the template. What status such a notification
  then shows was not verified.
- **"Missing required template variables".** A request for the template did not supply every name
  listed in the template's **Variables** field. Either supply the values in the request or correct
  the **Variables** list. The error message lists the missing names.
- **Creating a template fails with `A template named "..." already exists.`** Names are unique.
  Choose another name, or open the existing template.
- **Template screen reads "Template not found".** The template ID in the address no longer exists.
  Return to the list.

## A user cannot sign in

Check, in this order, on the [Users](users-and-roles.md) screen:

1. **Status.** If the user shows `Disabled`, select **Enable**.
2. **Password.** A user created from the console has no password until you set one. Use **Reset
   password**. If you left **Temporary password** ticked, the user must choose a new password on
   first sign-in.
3. **Username.** The sign-in name is the **Username** column. It cannot be changed from the
   console.

If all of these are correct, the cause is in the Keycloak configuration (for example, required
actions or other realm policies), which the console does not show. Ask your Keycloak
administrators.

A user who can sign in but sees errors on a screen is a permissions question. See
[Error 401 or 403](#error-401-or-403).

## Users screen fails to load or saving a user fails

The Users screen is served by a separate user service that calls Keycloak. When that service
cannot reach Keycloak it reports "The user directory is temporarily unavailable" (HTTP 502), which
the console shows as a generic failure. Use **Retry** after a moment. If it persists, ask your
administrators.

Other messages and their causes:

| Message | Cause |
| --- | --- |
| "A user with that username or email already exists." | Creating a user whose username or email is already taken. |
| "Couldn't update user roles. Please try again." | Can mean a role in the ticked list no longer exists in Keycloak (the service answers with an error naming the missing role), or a service failure. Close the dialog, reopen **Roles** to reload the list, and try again. |
| "No available realm roles found in Keycloak." | The realm has no roles to assign, or the list could not be loaded. |
| "Password must be at least 8 characters." | The new password is too short. |

## The Metrics screen shows zeros or unusual numbers

The Metrics figures come from live counters inside the notification service rather than from the
stored notification records. They reflect what that running service has counted. Numbers that look
unrealistic can also come from a demonstration setting that fills the counters with sample figures.
That setting is off by default and is meant only for local demos. If you see suspiciously neat
figures in a real environment, ask your administrators whether it is enabled. How the counters
behave across a restart of the service was not verified for this manual.

## The top bar title says "Dashboard" on the Templates or Users screen

This is a known display issue. The top bar derives its title from the address, and it has no entry
for Templates and Users, so it falls back to "Dashboard". The sidebar highlight and the page's own
heading show the real screen. It does not affect functionality.

## The search box or the bell in the top bar does nothing

The top-bar search box is not connected to any search in the current build, and the bell panel
always reads "No new notifications". Use the filters on the [Notifications](notifications.md) and
[Users](users-and-roles.md) screens.
