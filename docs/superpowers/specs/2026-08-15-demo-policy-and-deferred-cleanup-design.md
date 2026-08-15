# Demo Policy and Deferred Cleanup Design

## Goal

Reduce each demo session to 15 actions and one hour, and make logout responsive without weakening server-side session invalidation.

## Session policy

- New demo sessions expire one hour after creation. Resuming a session does not extend its expiry.
- Each session permits at most 15 used plus reserved actions.
- A forward Flyway migration replaces the database constraints that currently allow 20 actions. It also normalizes transient quota state before applying the stricter constraints so an existing database can migrate safely.
- API responses, quota headers, automated tests, and user-facing demo instructions use the new values.

## Logout

Logout remains a confirmed server operation. The API locks the active session, marks it `LOGGED_OUT`, sets its expiry to the database time, and rotates its resume-token digest. The response clears the HTTP-only resume cookie.

Logout no longer deletes session-owned expenses, budgets, categories, recurring expenses, chat mappings, reservations, or access-token rows. Existing bearer tokens become unusable immediately because demo authentication requires their parent session to remain `ACTIVE` and unexpired. Once the lightweight invalidation response succeeds, the frontend clears its local session and displays the existing Signed out page.

## Deferred cleanup

Every database-backed demo login attempt schedules cleanup after its transaction completes. This includes an attempt rejected because its resume cookie belongs to an expired session, ensuring that a later attempt is not permanently blocked by that stale row. Cleanup runs asynchronously, so the login response does not wait for deletion. A single-flight guard prevents concurrent logins in one application instance from running overlapping cleanup jobs.

The cleanup transaction deletes owned data for all expired or logged-out sessions using the existing dependency order, then deletes their session records. Session-scoped queries keep this stale data invisible to other sessions while it awaits deletion. Logged-out sessions do not count toward the two-session capacity limit.

Login already requires a database transaction. Scheduling cleanup only after that transaction completes means the database has completed its wake-up before cleanup starts. If the database becomes unavailable during cleanup, the transaction rolls back, the failure is recorded, and a later login attempt schedules another cleanup. Cleanup failure never changes the login result.

## Failure behavior

- Logout invalidation failure leaves the frontend authenticated and surfaces the existing request failure; it does not falsely display Signed out.
- Cleanup failure leaves an already invalid or expired session's isolated data in place until a later login retries cleanup.
- If no further demo login occurs, stale demo data may remain in storage. This is accepted because the requested trigger is login-only; it does not permit access to that data.

## Verification

- Service tests prove a grant has a 15-action limit and an expiry one hour after database time.
- Quota tests prove action 15 succeeds and action 16 is rejected.
- Migration tests prove the SQL constraints reject totals or reservation costs above 15.
- Logout integration tests prove the session becomes unusable while owned rows remain until cleanup.
- Deferred-cleanup tests prove work is scheduled after a database-backed login attempt, does not block the response, is single-flight, and can be retried by a later login.
- Frontend tests prove successful demo logout navigates to Signed out and failed invalidation preserves the current session.
