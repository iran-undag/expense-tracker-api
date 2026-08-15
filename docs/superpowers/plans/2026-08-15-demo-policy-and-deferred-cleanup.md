# Demo Policy and Deferred Cleanup Implementation Plan

**Status:** Completed and verified on 2026-08-15.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enforce 15-action, one-hour demo sessions and move expensive session-owned data deletion from logout to non-blocking login-triggered cleanup.

**Architecture:** Logout performs only transactional session invalidation; authentication already rejects tokens whose parent session is not active. `DemoSessionFacade` schedules a single-flight cleanup job after every database-backed login attempt, and a dedicated transactional service deletes expired/logged-out data in the demo realm. The web app waits only for fast invalidation and explicitly navigates successful demo logout to the existing Signed out route.

**Tech Stack:** Java 17, Spring Boot 3.3, JPA, SQL Server/Flyway, JUnit 5/Mockito/Testcontainers, Vue 3, Pinia, Vue Router, Vitest.

## Global Constraints

- Demo session lifetime is exactly one hour and does not slide on resume.
- Used plus reserved actions cannot exceed 15.
- Logout must invalidate the server session and clear the resume cookie before the UI claims Signed out.
- Data cleanup runs only after a database-backed demo login attempt and never delays its response.
- Cleanup is single-flight per application instance; failure is recorded and a later login can retry.
- Personal/Entra logout behavior remains unchanged.
- Preserve unrelated uncommitted configuration and Budget UI changes.

---

### Task 1: Enforce the one-hour and 15-action policy

**Files:**
- Create: `src/main/resources/db/migration/V10__reduce_demo_session_limits.sql`
- Modify: `src/main/java/com/example/expensetracker/demo/session/DemoSessionService.java`
- Test: `src/test/java/com/example/expensetracker/demo/DemoSchemaMigrationTest.java`
- Test: `src/test/java/com/example/expensetracker/demo/session/DemoSessionConcurrencyTest.java`
- Test: `src/test/java/com/example/expensetracker/demo/quota/DemoQuotaConcurrencyTest.java`
- Test: `src/test/java/com/example/expensetracker/demo/DemoEndToEndIntegrationTest.java`
- Test: `src/test/java/com/example/expensetracker/demo/security/DemoAuthenticationIntegrationTest.java`
- Test: `src/test/java/com/example/expensetracker/demo/session/DemoSessionControllerTest.java`
- Test: `src/test/java/com/example/expensetracker/demo/quota/DemoSessionHeadersAdviceTest.java`

**Interfaces:**
- Produces: `DemoSessionService.ACTION_LIMIT == 15`; new sessions use `now.plusHours(1)`.
- Produces: SQL constraints `ck_demo_used_actions`, `ck_demo_reserved_actions`, `ck_demo_total_actions`, and `ck_demo_reservation_cost` with maximum 15.

- [ ] **Step 1: Change policy assertions before production values**

Add an integration assertion to `DemoSessionConcurrencyTest`:

```java
DemoSessionService.SessionGrant grant = facade.createOrResume(null, "198.51.100.40");
UUID sessionId = sessionIdForAccessToken(grant.response().accessToken());
assertThat(grant.response().actionLimit()).isEqualTo(15);
assertThat(jdbc.queryForObject("""
    SELECT DATEDIFF(SECOND, created_at, expires_at)
    FROM demo_session WHERE id = ?
    """, Integer.class, sessionId)).isEqualTo(3_600);
```

Update quota-boundary expectations so action 15 succeeds, action 16 fails, and totals remain 15. Update controller/auth/header response fixtures from limit 20 to 15 and cookie max-age fixtures from 21,600 to 3,600 seconds.

Extend `DemoSchemaMigrationTest` with JDBC inserts that accept total/cost 15 and reject 16 using `assertThatThrownBy`.

- [ ] **Step 2: Run focused tests and confirm RED**

Run:

```bash
./mvnw -Dtest=DemoSessionControllerTest,DemoAuthenticationIntegrationTest,DemoSessionHeadersAdviceTest test
```

Expected: failures reporting the existing limit 20 and six-hour cookie fixture.

When Docker is available, run:

```bash
./mvnw -Dtest=DemoSchemaMigrationTest,DemoSessionConcurrencyTest,DemoQuotaConcurrencyTest,DemoEndToEndIntegrationTest test
```

Expected: failures because the service and database still allow 20 actions and six hours.

- [ ] **Step 3: Implement the policy and forward migration**

Set:

```java
public static final int ACTION_LIMIT = 15;
private static final int SESSION_HOURS = 1;
```

Create V10 to discard transient reservations, reset reserved counters, cap stale used counters, drop the four V9 constraints, and recreate them with 15:

```sql
DELETE FROM demo_quota_reservation;
UPDATE demo_session
SET reserved_actions = 0,
    used_actions = CASE WHEN used_actions > 15 THEN 15 ELSE used_actions END;

ALTER TABLE demo_session DROP CONSTRAINT ck_demo_used_actions;
ALTER TABLE demo_session DROP CONSTRAINT ck_demo_reserved_actions;
ALTER TABLE demo_session DROP CONSTRAINT ck_demo_total_actions;
ALTER TABLE demo_quota_reservation DROP CONSTRAINT ck_demo_reservation_cost;

ALTER TABLE demo_session ADD CONSTRAINT ck_demo_used_actions
    CHECK (used_actions BETWEEN 0 AND 15);
ALTER TABLE demo_session ADD CONSTRAINT ck_demo_reserved_actions
    CHECK (reserved_actions BETWEEN 0 AND 15);
ALTER TABLE demo_session ADD CONSTRAINT ck_demo_total_actions
    CHECK (used_actions + reserved_actions <= 15);
ALTER TABLE demo_quota_reservation ADD CONSTRAINT ck_demo_reservation_cost
    CHECK (cost BETWEEN 1 AND 15);
```

- [ ] **Step 4: Run focused policy tests and confirm GREEN**

Run both focused commands from Step 2. Expected: all selected tests pass.

- [ ] **Step 5: Commit the policy slice**

Stage only Task 1 files and commit with `feat: reduce demo session limits`.

---

### Task 2: Make logout invalidate without deleting owned data

**Files:**
- Modify: `src/main/java/com/example/expensetracker/demo/session/DemoSessionService.java`
- Test: `src/test/java/com/example/expensetracker/demo/session/DemoSessionConcurrencyTest.java`

**Interfaces:**
- Consumes: `DemoSessionRepository.markLoggedOut(UUID, String)`.
- Produces: `DemoSessionService.logout(UUID)` invalidates status/expiry/resume digest but retains owned rows for deferred cleanup.

- [ ] **Step 1: Change the logout integration test**

Replace the existing immediate-deletion expectations with:

```java
facade.logout(sessionId);

assertThat(ownedRowCount("expense", sessionId)).isEqualTo(1);
assertThat(ownedRowCount("budget", sessionId)).isEqualTo(1);
assertThat(ownedRowCount("expense_category", sessionId)).isEqualTo(1);
assertThat(jdbc.queryForObject(
    "SELECT status FROM demo_session WHERE id = ?", String.class, sessionId
)).isEqualTo("LOGGED_OUT");
```

Also authenticate with the old bearer token after logout and assert HTTP 401 in the existing authentication/end-to-end coverage.

- [ ] **Step 2: Run the logout test and confirm RED**

Run:

```bash
./mvnw -Dtest=DemoSessionConcurrencyTest test
```

Expected: retained-row assertions fail because logout still deletes owned data.

- [ ] **Step 3: Remove synchronous owned-data deletion**

Change `DemoSessionService.logout` to lock the active session and call only `markLoggedOut`, metrics updates, and active-count refresh. Do not change controller cookie clearing.

- [ ] **Step 4: Run the logout/authentication tests and confirm GREEN**

Run:

```bash
./mvnw -Dtest=DemoSessionConcurrencyTest,DemoAuthenticationIntegrationTest,DemoSessionControllerTest test
```

Expected: logout invalidates access while owned rows remain.

- [ ] **Step 5: Commit the logout slice**

Stage only Task 2 files and commit with `feat: defer demo data deletion`.

---

### Task 3: Schedule single-flight cleanup after login attempts

**Files:**
- Create: `src/main/java/com/example/expensetracker/demo/session/DemoSessionCleanupService.java`
- Create: `src/main/java/com/example/expensetracker/demo/session/DemoSessionCleanupScheduler.java`
- Modify: `src/main/java/com/example/expensetracker/demo/session/DemoSessionFacade.java`
- Modify: `src/main/java/com/example/expensetracker/demo/session/DemoSessionService.java`
- Test: `src/test/java/com/example/expensetracker/demo/session/DemoSessionCleanupSchedulerTest.java`
- Test: `src/test/java/com/example/expensetracker/demo/session/DemoSessionFacadeTest.java`
- Test: `src/test/java/com/example/expensetracker/demo/session/DemoSessionConcurrencyTest.java`
- Test: `src/test/java/com/example/expensetracker/demo/session/DemoSessionServiceTest.java`

**Interfaces:**
- Produces: `DemoSessionCleanupService.cleanupExpiredSessions(): void`, transactional in the demo realm.
- Produces: `DemoSessionCleanupScheduler.schedule(): void`, non-blocking and single-flight.
- Consumes: Spring Boot's `applicationTaskExecutor`, `DataRealmExecutor`, `DemoSessionRepository.deleteExpiredData()`, and `DemoMetrics`.

- [ ] **Step 1: Write scheduler and facade boundary tests**

Use a queued test executor that stores, but does not immediately run, the submitted `Runnable`. Assert:

```java
scheduler.schedule();
verifyNoInteractions(cleanupService);
queuedRunnable.run();
verify(cleanupService).cleanupExpiredSessions();
```

Call `schedule()` twice before running the queued job and assert only one task is submitted. Add facade tests that verify `schedule()` after successful creation and after `DemoSessionException.sessionExpired()`, but not when `ensureMigrated()` fails.

- [ ] **Step 2: Run the new unit tests and confirm RED**

Run:

```bash
./mvnw -Dtest=DemoSessionCleanupSchedulerTest,DemoSessionFacadeTest test
```

Expected: test compilation fails because the cleanup service/scheduler do not exist and the facade has no scheduler dependency.

- [ ] **Step 3: Implement cleanup service and scheduler**

Implement the cleanup service:

```java
@Service
@Profile("prod")
public class DemoSessionCleanupService {
    @Transactional
    public void cleanupExpiredSessions() {
        metrics.cleanedSessions(sessionRepository.deleteExpiredData());
    }
}
```

Implement the scheduler with `@Qualifier("applicationTaskExecutor") TaskExecutor`, `DataRealmExecutor`, an `AtomicBoolean running`, and `taskExecutor.execute`. Run the service inside `realmExecutor.inRealm(DataRealm.DEMO, ...)`; catch runtime failures, increment `databaseFailure`, log the error, and always reset `running`.

- [ ] **Step 4: Trigger scheduling at the facade boundary**

Inject `DemoSessionCleanupScheduler` into `DemoSessionFacade`. In `createOrResume`, set a local `databaseReady` flag immediately after `ensureMigrated()` and call `cleanupScheduler.schedule()` in `finally` only when that flag is true. This boundary runs after the transactional service invocation returns or throws.

Remove synchronous `deleteExpiredData()` calls from `DemoSessionService.createOrResume` and `issueAccessToken`; update `DemoSessionServiceTest` so expiry rejection no longer expects synchronous cleanup.

- [ ] **Step 5: Run unit tests and confirm GREEN**

Run:

```bash
./mvnw -Dtest=DemoSessionCleanupSchedulerTest,DemoSessionFacadeTest,DemoSessionServiceTest test
```

Expected: all selected tests pass without Docker.

- [ ] **Step 6: Prove deferred deletion end to end**

Update `DemoSessionConcurrencyTest` to log out a session, verify rows remain, trigger a new login, and use Awaitility-style bounded polling implemented with repeated JDBC checks (no fixed long sleep) until the old session row disappears. Assert the new grant returned before cleanup completion using a controllable executor in unit coverage rather than timing production threads.

Run:

```bash
./mvnw -Dtest=DemoSessionConcurrencyTest test
```

Expected: the old session and owned rows are eventually deleted after login.

- [ ] **Step 7: Commit the cleanup slice**

Stage only Task 3 files and commit with `feat: clean demo sessions after login`.

---

### Task 4: Navigate successful demo logout to Signed out

**Files:**
- Modify: `expense-tracker-web/src/App.vue`
- Modify: `expense-tracker-web/src/App.test.ts`
- Modify: `expense-tracker-web/src/stores/authStore.test.ts`

**Interfaces:**
- Consumes: existing `authStore.logout(): Promise<void>` confirmed invalidation behavior.
- Produces: `handleLogout(): Promise<void>` routes demo users to `{ name: 'logout' }` only after logout succeeds.

- [ ] **Step 1: Write the navigation regression test**

Mock `useRouter()` with `replace: vi.fn()`, mount an authenticated demo `App`, return a delayed 204 from fetch, and trigger Sign out. Assert `replace` is not called before the response resolves, then resolve it and assert:

```ts
expect(routerReplace).toHaveBeenCalledWith({ name: 'logout' })
expect(authStore.isAuthenticated).toBe(false)
```

Keep the existing auth-store failure test proving a rejected DELETE preserves demo state.

- [ ] **Step 2: Run the focused web tests and confirm RED**

Run:

```bash
npm test -- --run src/App.test.ts src/stores/authStore.test.ts
```

Expected: no router replacement occurs after demo logout.

- [ ] **Step 3: Implement explicit demo logout navigation**

In `App.vue`, import `useRouter`, create `const router = useRouter()`, and replace the click expression with:

```ts
async function handleLogout() {
  const wasDemo = isDemo.value
  await authStore.logout()
  if (wasDemo) {
    await router.replace({ name: 'logout' })
  }
}
```

Bind `@click="handleLogout"`. Do not alter the personal redirect path.

- [ ] **Step 4: Run focused web tests and confirm GREEN**

Run the Step 2 command. Expected: all selected tests pass.

- [ ] **Step 5: Commit the web logout slice**

Commit only `src/App.vue` and its related tests in the web repository with `fix: show signed-out page after demo logout`.

---

### Task 5: Align frontend fixtures and demo documentation

**Files:**
- Modify: `expense-tracker-web/src/App.test.ts`
- Modify: `expense-tracker-web/src/stores/authStore.test.ts`
- Modify: `expense-tracker-web/src/pages/DashboardPage.test.ts`
- Modify: `expense-tracker-web/README.md`
- Modify: `expense-tracker-web/USER_TEST.md`
- Modify policy-specific API test fixtures found by the final literal scan.

**Interfaces:**
- Consumes: API-provided dynamic quota and expiry metadata.
- Produces: documentation and representative fixtures consistently describing 15 actions and one hour.

- [ ] **Step 1: Update representative web expectations**

Set demo grants/metadata to `actionLimit: 15`, adjust used/remaining arithmetic, and expect `15 actions remaining`. Change the README from six hours to one hour. Change manual verification so action 15 succeeds and action 16 is blocked.

- [ ] **Step 2: Run literal scans**

Run in each repository:

```bash
rg -n -i --glob '!node_modules/**' --glob '!target/**' --glob '!dist/**' "six hours|6 hours|20 actions|action 20|action 21|plusHours\(6\)|ACTION_LIMIT = 20|Max-Age=21600"
```

Classify remaining `20` values. Keep only the historical V9 migration and unrelated domain/test values; update policy-specific occurrences.

- [ ] **Step 3: Run focused frontend tests**

Run:

```bash
npm test -- --run src/App.test.ts src/stores/authStore.test.ts src/pages/DashboardPage.test.ts
```

Expected: all selected tests pass.

- [ ] **Step 4: Commit documentation/fixture alignment**

Stage only Task 5 files and commit with `docs: update demo session policy` in the applicable repositories.

---

### Task 6: Full verification

**Files:**
- Verify only; make no unrelated edits.

- [ ] **Step 1: Verify API**

Run:

```bash
./mvnw test
```

Expected: all unit and SQL Server Testcontainers tests pass.

- [ ] **Step 2: Verify web**

Run:

```bash
npm test -- --run
npm run typecheck
npm run build
```

Expected: all tests pass, typecheck exits zero, and Vite production build exits zero. Existing chunk-size warnings are non-blocking.

- [ ] **Step 3: Verify diffs and working-tree ownership**

Run `git diff --check`, `git status --short`, and policy literal scans in both repositories. Confirm existing `.env`/Docker and Budget UI changes remain intact and are not included in policy commits.
