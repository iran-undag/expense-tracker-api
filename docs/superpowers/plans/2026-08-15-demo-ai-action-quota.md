# Demo AI Action Quota Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the demo action limit configurable through `DEMO_ACTION_LIMIT` with a default of 15, charge admitted demo chatbot messages before Azure OpenAI, preserve one-action speech/receipt processing plus one-action expense saving, and refresh the web quota display after chatbot replies.

**Architecture:** The Expense API remains the sole quota authority. It applies one runtime policy to active and future demo sessions, stores idempotent Direct Line activity claims in the demo database, and exposes a current quota snapshot; the chatbot claims each locally valid message immediately before its first model request, while the Vue client only refreshes server-provided quota metadata after a correlated bot reply.

**Tech Stack:** Java 17, Spring Boot 3.3, Spring Security, JPA native SQL, SQL Server, Flyway, JUnit 5, Mockito, MockMvc, Testcontainers, Vue 3, Pinia, TypeScript, Bot Framework Web Chat, Vitest.

**Status:** Pending implementation-plan review; no implementation authorized yet.

## Global Constraints

- Work directly on the existing `main` branches; do not create a worktree or feature branch.
- Do not overwrite or stage unrelated working-tree changes. The API already has changes in `.env.example`, `.env.sample`, and `docker-compose.yml`; the web already has changes in `src/pages/DashboardPage.test.ts`, `src/pages/DashboardPage.vue`, and `src/styles.css`.
- `DEMO_ACTION_LIMIT` maps to `demo.action-limit`, defaults to exactly 15, must be a positive integer, and applies to active and future sessions after application configuration is loaded.
- Preserve stored `used_actions` and `reserved_actions` when the configured limit changes. Remaining actions are `max(0, limit - used - reserved)`.
- Remove deployment-specific database upper bounds, but retain `used_actions >= 0`, `reserved_actions >= 0`, and reservation `cost >= 1` constraints.
- A successful demo speech-token request costs one action, including startup prefetch; saving the resulting expense costs one more.
- Successful receipt processing costs one action; saving the resulting expense costs one more.
- Speech and receipt provider failure releases the reservation and costs zero.
- Each locally valid, rate-limit-admitted demo chatbot message costs one action immediately before the first Azure OpenAI request. The committed action is not released when Azure OpenAI fails.
- Empty, oversized, malformed-identity, locally rate-limited, quota-rejected, and claim-unavailable chatbot messages must not invoke Azure OpenAI.
- The idempotency key is `(demo_session_id, Direct Line activity ID)`. Duplicate delivery costs zero additional actions and must not invoke Azure OpenAI again, including when the original claim consumed the last action.
- Personal sessions remain unmetered.
- The web must use API response metadata and must not perform client-side quota arithmetic for chatbot messages.
- Keep demo speech-token prefetch enabled to match personal-session startup behavior.
- Do not add dependencies.

## File and Interface Map

### Expense API

- `DemoActionPolicy` owns the configured positive action limit and remaining-action calculation.
- `DemoSessionService` uses `DemoActionPolicy` when issuing new, resumed, and renewed grants.
- `DemoQuotaService` uses the same policy for locking, availability checks, mutations, reservations, and snapshots.
- `V12__configure_demo_action_quota.sql` removes static upper constraints and creates `demo_chat_action_claim`.
- `DemoSessionRepository` reads and inserts claim records while the session row is locked.
- `DemoChatActionClaimService` resolves the mapped realm identity and atomically returns `PERSONAL`, `CLAIMED`, or `DUPLICATE`.
- `InternalChatActionController` selects the database realm from the trusted Direct Line prefix before entering the transactional service.
- `DemoSessionController` exposes `GET /api/demo/sessions/current/quota` for the authenticated demo browser.

### Chatbot

- `ExpenseTrackerToolClient.claim(BotActivity)` reuses the authenticated Expense API transport and returns `ChatActionClaimStatus`.
- `HttpExpenseTrackerToolClient` calls `POST /internal/chat-tools/action-claims` and maps identity, quota, and availability failures to typed exceptions.
- `ChatOrchestrator` validates identity, applies the local rate limit, claims the action, and only then calls the model.
- `ChatFailureMessage` supplies fixed safe messages for exhausted quota, duplicate delivery, and claim-service failure.

### Web

- `getCurrentDemoQuota()` calls `GET /api/demo/sessions/current/quota` through `apiFetch`, allowing existing response-header processing to update Pinia.
- `ChatBotWidget` keeps a Direct Line activity subscription until unmount and refreshes quota only for demo bot message replies with `replyToId`.
- `DashboardPage` prefetches the speech token for both personal and demo sessions.

---

### Task 1: Migrate Configurable Quota Constraints and Chat Claim Storage

**Files:**
- Create: `expense-tracker-api/src/main/resources/db/migration/V12__configure_demo_action_quota.sql`
- Modify: `expense-tracker-api/src/test/java/com/example/expensetracker/demo/DemoSchemaMigrationTest.java`

**Interfaces:**
- Produces: `demo_chat_action_claim(demo_session_id UNIQUEIDENTIFIER, activity_id VARCHAR(255), claimed_at DATETIMEOFFSET(6))`.
- Produces: primary key `pk_demo_chat_action_claim` on `(demo_session_id, activity_id)` and cascading foreign key `fk_demo_chat_action_claim_session`.
- Redefines: `ck_demo_used_actions`, `ck_demo_reserved_actions`, and `ck_demo_reservation_cost` without a deployment-specific maximum.
- Removes: `ck_demo_total_actions`; runtime locked transactions become the total-action boundary.

- [ ] **Step 1: Replace the fixed-ten migration assertions with the new schema contract**

Rename `migrationEnforcesTenActionSessionAndReservationLimits` to `migrationKeepsNonnegativeCountsWithoutEncodingRuntimeLimit`. Make it insert `used_actions = 16` and reservation `cost = 16` successfully, then assert negative used/reserved values and zero reservation cost fail with `SQLException`.

Add this assertion to `migrationCreatesDemoSessionTablesAndBusinessOwnershipColumns`:

```java
assertThat(columns("demo_chat_action_claim"))
    .contains("demo_session_id", "activity_id", "claimed_at");
```

Add an idempotency test that inserts one active session and one claim, then asserts the second identical insert fails:

```java
@Test
void migrationEnforcesOneClaimPerSessionAndActivity() throws SQLException {
    try (Connection connection = SQL_SERVER.createConnection("");
         Statement statement = connection.createStatement()) {
        insertSession(statement, "11111111-1111-1111-1111-111111111111", "a");
        statement.executeUpdate("""
            INSERT INTO demo_chat_action_claim (demo_session_id, activity_id, claimed_at)
            VALUES ('11111111-1111-1111-1111-111111111111', 'activity-1', SYSDATETIMEOFFSET())
            """);

        assertThatThrownBy(() -> statement.executeUpdate("""
            INSERT INTO demo_chat_action_claim (demo_session_id, activity_id, claimed_at)
            VALUES ('11111111-1111-1111-1111-111111111111', 'activity-1', SYSDATETIMEOFFSET())
            """)).isInstanceOf(SQLException.class);
    }
}
```

Use a private `insertSession(Statement statement, String id, String digestCharacter)` helper so each test creates distinct owner IDs and resume digests.

```java
private void insertSession(
    Statement statement,
    String id,
    String digestCharacter
) throws SQLException {
    statement.executeUpdate("""
        INSERT INTO demo_session
            (id, shared_account_id, persistence_owner_id, status, created_at, expires_at,
             used_actions, reserved_actions, resume_token_digest)
        VALUES
            ('%s', 'shared', 'demo:%s', 'ACTIVE', SYSDATETIMEOFFSET(),
             DATEADD(HOUR, 1, SYSDATETIMEOFFSET()), 16, 0, REPLICATE('%s', 64))
        """.formatted(id, id, digestCharacter));
}
```

- [ ] **Step 2: Run the migration test and verify it fails**

Run:

```bash
./mvnw -Dtest=DemoSchemaMigrationTest test
```

Expected: FAIL because values above ten are rejected and `demo_chat_action_claim` does not exist.

- [ ] **Step 3: Add the forward-only migration**

Create `V12__configure_demo_action_quota.sql`:

```sql
ALTER TABLE demo_session DROP CONSTRAINT ck_demo_used_actions;
ALTER TABLE demo_session DROP CONSTRAINT ck_demo_reserved_actions;
ALTER TABLE demo_session DROP CONSTRAINT ck_demo_total_actions;
ALTER TABLE demo_quota_reservation DROP CONSTRAINT ck_demo_reservation_cost;

ALTER TABLE demo_session ADD CONSTRAINT ck_demo_used_actions
    CHECK (used_actions >= 0);
ALTER TABLE demo_session ADD CONSTRAINT ck_demo_reserved_actions
    CHECK (reserved_actions >= 0);
ALTER TABLE demo_quota_reservation ADD CONSTRAINT ck_demo_reservation_cost
    CHECK (cost >= 1);

CREATE TABLE demo_chat_action_claim (
    demo_session_id UNIQUEIDENTIFIER NOT NULL,
    activity_id VARCHAR(255) NOT NULL,
    claimed_at DATETIMEOFFSET(6) NOT NULL,
    CONSTRAINT pk_demo_chat_action_claim PRIMARY KEY (demo_session_id, activity_id),
    CONSTRAINT fk_demo_chat_action_claim_session FOREIGN KEY (demo_session_id)
        REFERENCES demo_session(id) ON DELETE CASCADE
);
```

Do not edit `V9`, `V10`, or `V11`, and do not rewrite existing action counts.

- [ ] **Step 4: Run the migration test and verify it passes**

Run `./mvnw -Dtest=DemoSchemaMigrationTest test`.

Expected: PASS; the database accepts counts above 10, rejects negative counts and zero-cost reservations, and rejects a duplicate session/activity claim.

- [ ] **Step 5: Commit the migration slice**

```bash
git add src/main/resources/db/migration/V12__configure_demo_action_quota.sql src/test/java/com/example/expensetracker/demo/DemoSchemaMigrationTest.java
git commit -m "feat: migrate configurable demo quota storage"
```

### Task 2: Introduce and Apply the Runtime Action Policy

**Files:**
- Create: `expense-tracker-api/src/main/java/com/example/expensetracker/demo/quota/DemoActionPolicy.java`
- Create: `expense-tracker-api/src/test/java/com/example/expensetracker/demo/quota/DemoActionPolicyTest.java`
- Create: `expense-tracker-api/src/test/java/com/example/expensetracker/demo/quota/DemoQuotaServiceTest.java`
- Modify: `expense-tracker-api/src/main/java/com/example/expensetracker/demo/session/DemoSessionService.java`
- Modify: `expense-tracker-api/src/main/java/com/example/expensetracker/demo/quota/DemoQuotaService.java`
- Modify: `expense-tracker-api/src/main/resources/application-prod.properties`
- Modify only the new setting hunk: `expense-tracker-api/.env.sample`
- Modify only the new setting hunk: `expense-tracker-api/docker-compose.yml`
- Modify: `expense-tracker-api/src/test/java/com/example/expensetracker/demo/session/DemoSessionServiceTest.java`
- Modify: `expense-tracker-api/src/test/java/com/example/expensetracker/demo/quota/DemoQuotaConcurrencyTest.java`
- Modify: `expense-tracker-api/src/test/java/com/example/expensetracker/demo/quota/DemoExternalOperationQuotaTest.java`
- Modify: `expense-tracker-api/src/test/java/com/example/expensetracker/demo/DemoEndToEndIntegrationTest.java`

**Interfaces:**
- Produces: `DemoActionPolicy(@Value("${demo.action-limit:15}") int actionLimit)`.
- Produces: `int limit()` and `int remaining(DemoSession session)`.
- Changes: `DemoQuotaService.QuotaSnapshot` to `(int limit, int used, int remaining, OffsetDateTime expiresAt)`.
- Produces package-private quota primitives `lockActive(UUID)` and `ensureAvailable(DemoSession, int, DemoMetrics.Operation)` for the claim transaction.

- [ ] **Step 1: Write failing policy tests**

Create `DemoActionPolicyTest` with Spring's `ApplicationContextRunner`:

```java
class DemoActionPolicyTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(DemoActionPolicy.class);

    @Test
    void defaultsToFifteenActions() {
        contextRunner.run(context ->
            assertThat(context.getBean(DemoActionPolicy.class).limit()).isEqualTo(15));
    }

    @Test
    void acceptsPositiveOverride() {
        contextRunner.withPropertyValues("demo.action-limit=23").run(context ->
            assertThat(context.getBean(DemoActionPolicy.class).limit()).isEqualTo(23));
    }

    @Test
    void rejectsNonpositiveOverride() {
        contextRunner.withPropertyValues("demo.action-limit=0").run(context ->
            assertThat(context).hasFailed());
    }

    @Test
    void rejectsMalformedOverride() {
        contextRunner.withPropertyValues("demo.action-limit=not-a-number").run(context ->
            assertThat(context).hasFailed());
    }

    @Test
    void clampsRemainingAtZeroWithoutRewritingUsage() {
        DemoSession session = DemoSession.builder().usedActions(17).reservedActions(2).build();
        assertThat(new DemoActionPolicy(15).remaining(session)).isZero();
        assertThat(session.getUsedActions()).isEqualTo(17);
        assertThat(session.getReservedActions()).isEqualTo(2);
    }
}
```

Create `DemoQuotaServiceTest` with a mocked repository and metrics:

```java
class DemoQuotaServiceTest {
    private static final UUID SESSION_ID =
        UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final OffsetDateTime EXPIRES_AT =
        OffsetDateTime.parse("2026-08-15T16:00:00Z");

    @Test
    void lowerRuntimeLimitAppliesWithoutRewritingExistingUsage() {
        DemoSessionRepository repository = mock(DemoSessionRepository.class);
        DemoMetrics metrics = mock(DemoMetrics.class);
        DemoSession session = DemoSession.builder()
            .id(SESSION_ID).status("ACTIVE").expiresAt(EXPIRES_AT)
            .usedActions(4).reservedActions(1).build();
        when(repository.findActiveSession(SESSION_ID)).thenReturn(Optional.of(session));
        when(repository.lockActiveSession(SESSION_ID)).thenReturn(Optional.of(session));
        when(repository.databaseNow()).thenReturn(EXPIRES_AT.minusMinutes(10));
        DemoQuotaService service = new DemoQuotaService(
            Optional.of(repository), metrics, new DemoActionPolicy(3));

        assertThat(service.current(SESSION_ID))
            .isEqualTo(new DemoQuotaService.QuotaSnapshot(3, 4, 0, EXPIRES_AT));
        assertThatThrownBy(() -> service.lockForMutation(SESSION_ID, 1))
            .isInstanceOfSatisfying(DemoSessionException.class,
                exception -> assertThat(exception.code())
                    .isEqualTo("DEMO_QUOTA_EXHAUSTED"));
        assertThat(session.getUsedActions()).isEqualTo(4);
        assertThat(session.getReservedActions()).isEqualTo(1);
    }
}
```

This proves a lower deployed limit takes effect for an already-existing row without rewriting it.

In `DemoSessionServiceTest`, construct the service with `new DemoActionPolicy(15)` and replace constant assertions with exact `15` and `10` remaining for a session with three used and two reserved actions.

Update `DemoEndToEndIntegrationTest` to expect `actionLimit = 15`, perform exactly 15 successful mutations, and reject the sixteenth.

In `DemoQuotaConcurrencyTest`, change the parallel mutation latch, thread pool, loop bound, future range, used/total/expense assertions, recurring generated count, and next-run offset from 10 to 15. In `DemoExternalOperationQuotaTest`, rename `reservationCanConsumeTenActionsAndRejectsTheNextAction` to `reservationCanConsumeConfiguredActionsAndRejectsTheNextAction`, reserve 15, and assert `(used, reserved) = (0, 15)` before the rejection. These are policy-bound values; do not change unrelated dates, timeouts, page sizes, or fixture values that happen to equal 10.

- [ ] **Step 2: Run focused policy and quota tests and verify they fail**

Run:

```bash
./mvnw -Dtest=DemoActionPolicyTest,DemoQuotaServiceTest,DemoSessionServiceTest,DemoQuotaConcurrencyTest,DemoExternalOperationQuotaTest,DemoEndToEndIntegrationTest test
```

Expected: compilation fails because `DemoActionPolicy` and the four-field `QuotaSnapshot` do not exist, or assertions still observe the fixed limit of 10.

- [ ] **Step 3: Implement the minimal policy**

Create `DemoActionPolicy`:

```java
@Component
public class DemoActionPolicy {
    private final int actionLimit;

    public DemoActionPolicy(@Value("${demo.action-limit:15}") int actionLimit) {
        if (actionLimit < 1) {
            throw new IllegalArgumentException("Demo action limit must be positive");
        }
        this.actionLimit = actionLimit;
    }

    public int limit() {
        return actionLimit;
    }

    public int remaining(DemoSession session) {
        return Math.max(0, actionLimit - session.getUsedActions() - session.getReservedActions());
    }
}
```

A malformed integer already fails Spring property conversion; do not add custom parsing.

- [ ] **Step 4: Replace both hardcoded service constants with the policy**

Inject `DemoActionPolicy` into `DemoSessionService` and `DemoQuotaService`. Delete both `ACTION_LIMIT` constants.

In `DemoSessionService.issueAccessToken`, use:

```java
int remainingActions = actionPolicy.remaining(session);
return new SessionGrant(
    new DemoSessionResponse(
        accessToken,
        accessTokenExpiresAt,
        session.getExpiresAt(),
        actionPolicy.limit(),
        session.getUsedActions(),
        remainingActions
    ),
    resumeToken,
    Math.max(0, Duration.between(now, session.getExpiresAt()).getSeconds())
);
```

Refactor `DemoQuotaService` without changing existing callers:

```java
DemoSession lockActive(UUID sessionId) {
    DemoSession session = repository().lockActiveSession(sessionId)
        .orElseThrow(DemoSessionException::sessionExpired);
    repository().reclaimExpiredReservations(session, repository().databaseNow());
    return session;
}

void ensureAvailable(DemoSession session, int cost, DemoMetrics.Operation operation) {
    validateCost(cost);
    if (session.getUsedActions() + session.getReservedActions() + cost > actionPolicy.limit()) {
        metrics.quotaRejected(operation);
        throw DemoSessionException.quotaExhausted();
    }
}

DemoSession lockForMutation(UUID sessionId, int cost, DemoMetrics.Operation operation) {
    DemoSession session = lockActive(sessionId);
    ensureAvailable(session, cost, operation);
    return session;
}
```

Make `current(UUID)` return:

```java
return new QuotaSnapshot(
    actionPolicy.limit(),
    session.getUsedActions(),
    actionPolicy.remaining(session),
    session.getExpiresAt()
);
```

Validate cost against `actionPolicy.limit()`.

- [ ] **Step 5: Expose the deployment setting**

Add to `application-prod.properties`:

```properties
demo.action-limit=${DEMO_ACTION_LIMIT:15}
```

Add under the demo settings in `.env.sample`:

```properties
DEMO_ACTION_LIMIT=15
```

Pass it to the API container in `docker-compose.yml`:

```yaml
DEMO_ACTION_LIMIT: ${DEMO_ACTION_LIMIT:-15}
```

Before staging, inspect the pre-existing diffs with `git diff -- .env.sample docker-compose.yml`. Stage only the `DEMO_ACTION_LIMIT` hunks with `git add -p`; never stage the deleted `.env.example` or unrelated environment/Docker edits.

- [ ] **Step 6: Prove processing plus saving costs two actions**

Add this integration-level quota test to `DemoExternalOperationQuotaTest`:

```java
@Test
void successfulExternalProcessingThenSaveConsumesTwoActions() {
    TestSession session = createSession("198.51.100.85");

    UUID reservationId = inDemoRealm(() ->
        reservationService.reserve(session.authentication(), 1));
    inDemoRealm(() -> reservationService.finalize(reservationId));
    inDemoRealm(() -> mutationExecutor.execute(
        session.authentication(), 1, () -> "saved"));

    assertThat(actions(session.sessionId())).containsExactly(2, 0);
}
```

Retain the controller tests that separately prove speech and receipt success finalize reservations and provider failures release them.

- [ ] **Step 7: Run focused tests and verify they pass**

Run:

```bash
./mvnw -Dtest=DemoActionPolicyTest,DemoQuotaServiceTest,DemoSessionServiceTest,DemoQuotaConcurrencyTest,DemoExternalOperationQuotaTest,DemoEndToEndIntegrationTest,SpeechControllerTest,ExpenseControllerTest test
```

Expected: PASS with 15 as the default and two used actions after external processing plus save.

- [ ] **Step 8: Commit only the runtime-policy slice**

Stage all clean files normally. Use interactive hunk staging for `.env.sample` and `docker-compose.yml`, then inspect `git diff --cached` before committing:

```bash
git add src/main/java/com/example/expensetracker/demo/quota/DemoActionPolicy.java src/main/java/com/example/expensetracker/demo/quota/DemoQuotaService.java src/main/java/com/example/expensetracker/demo/session/DemoSessionService.java src/main/resources/application-prod.properties src/test/java/com/example/expensetracker/demo/quota/DemoActionPolicyTest.java src/test/java/com/example/expensetracker/demo/quota/DemoQuotaServiceTest.java src/test/java/com/example/expensetracker/demo/quota/DemoQuotaConcurrencyTest.java src/test/java/com/example/expensetracker/demo/quota/DemoExternalOperationQuotaTest.java src/test/java/com/example/expensetracker/demo/session/DemoSessionServiceTest.java src/test/java/com/example/expensetracker/demo/DemoEndToEndIntegrationTest.java
git add -p .env.sample docker-compose.yml
git diff --cached --check
git commit -m "feat: configure demo action limit"
```

### Task 3: Expose the Current Demo Quota Snapshot

**Files:**
- Modify: `expense-tracker-api/src/main/java/com/example/expensetracker/demo/session/DemoSessionController.java`
- Modify: `expense-tracker-api/src/main/java/com/example/expensetracker/demo/quota/DemoSessionHeadersAdvice.java`
- Modify: `expense-tracker-api/src/test/java/com/example/expensetracker/demo/session/DemoSessionControllerTest.java`
- Modify: `expense-tracker-api/src/test/java/com/example/expensetracker/demo/quota/DemoSessionHeadersAdviceTest.java`
- Modify: `expense-tracker-api/src/test/java/com/example/expensetracker/demo/security/DemoAuthenticationIntegrationTest.java`

**Interfaces:**
- Produces: authenticated `GET /api/demo/sessions/current/quota`.
- Returns: `DemoQuotaService.QuotaSnapshot(limit, used, remaining, expiresAt)` with `Cache-Control: no-store`.
- Reuses: `Demo-Actions-Limit`, `Demo-Actions-Remaining`, and `Demo-Session-Expires-At` headers from the same snapshot instance.

- [ ] **Step 1: Write failing controller and advice tests**

In `DemoSessionControllerTest`, mock `DemoQuotaService`, authenticate with a `DemoPrincipal`, and assert:

```java
when(quotaService.current(sessionId)).thenReturn(
    new DemoQuotaService.QuotaSnapshot(15, 4, 11, NOW.plusHours(1)));

mockMvc.perform(get("/api/demo/sessions/current/quota")
        .principal(new TestingAuthenticationToken(principal, null)))
    .andExpect(status().isOk())
    .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
    .andExpect(jsonPath("$.limit").value(15))
    .andExpect(jsonPath("$.used").value(4))
    .andExpect(jsonPath("$.remaining").value(11));
```

Update `DemoSessionHeadersAdviceTest` for the four-field snapshot and add a test whose response body is a `QuotaSnapshot`; verify the advice emits headers without calling `quotaService.current`.

Update existing `QuotaSnapshot` constructors in `DemoAuthenticationIntegrationTest`.

- [ ] **Step 2: Run the focused tests and verify they fail**

Run:

```bash
./mvnw -Dtest=DemoSessionControllerTest,DemoSessionHeadersAdviceTest,DemoAuthenticationIntegrationTest test
```

Expected: FAIL because the GET endpoint and snapshot-body advice path do not exist.

- [ ] **Step 3: Add the authenticated endpoint**

Inject `DemoQuotaService` into `DemoSessionController` and add:

```java
@GetMapping("/current/quota")
public ResponseEntity<DemoQuotaService.QuotaSnapshot> currentQuota(Authentication authentication) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(quotaService.current(sessionId(authentication)));
}
```

Do not permit this route in `ProdSecurityConfig`; its existing `anyRequest().authenticated()` rule must protect it.

- [ ] **Step 4: Reuse the endpoint body in response advice**

In `DemoSessionHeadersAdvice.beforeBodyWrite`, select the snapshot without a second database read:

```java
DemoQuotaService.QuotaSnapshot quota = body instanceof DemoQuotaService.QuotaSnapshot snapshot
    ? snapshot
    : quotaServiceProvider.getObject().current(demo.sessionId());
```

Keep the existing successful-response and logout guards.

- [ ] **Step 5: Run the focused tests and verify they pass**

Run `./mvnw -Dtest=DemoSessionControllerTest,DemoSessionHeadersAdviceTest,DemoAuthenticationIntegrationTest test`.

Expected: PASS; body and headers report the same snapshot and personal/unauthenticated access remains unchanged.

- [ ] **Step 6: Commit the quota endpoint**

```bash
git add src/main/java/com/example/expensetracker/demo/session/DemoSessionController.java src/main/java/com/example/expensetracker/demo/quota/DemoSessionHeadersAdvice.java src/test/java/com/example/expensetracker/demo/session/DemoSessionControllerTest.java src/test/java/com/example/expensetracker/demo/quota/DemoSessionHeadersAdviceTest.java src/test/java/com/example/expensetracker/demo/security/DemoAuthenticationIntegrationTest.java
git commit -m "feat: expose current demo quota"
```

### Task 4: Add the Authoritative Chat Action Claim API

**Files:**
- Create: `expense-tracker-api/src/main/java/com/example/expensetracker/chattool/ChatActionClaimRequest.java`
- Create: `expense-tracker-api/src/main/java/com/example/expensetracker/chattool/ChatActionClaimResponse.java`
- Create: `expense-tracker-api/src/main/java/com/example/expensetracker/chattool/ChatActionClaimStatus.java`
- Create: `expense-tracker-api/src/main/java/com/example/expensetracker/demo/quota/DemoChatActionClaimService.java`
- Create: `expense-tracker-api/src/main/java/com/example/expensetracker/controller/InternalChatActionController.java`
- Create: `expense-tracker-api/src/test/java/com/example/expensetracker/demo/quota/DemoChatActionClaimServiceTest.java`
- Create: `expense-tracker-api/src/test/java/com/example/expensetracker/demo/quota/DemoChatActionClaimConcurrencyTest.java`
- Create: `expense-tracker-api/src/test/java/com/example/expensetracker/controller/InternalChatActionControllerTest.java`
- Modify: `expense-tracker-api/src/main/java/com/example/expensetracker/demo/session/DemoSessionRepository.java`
- Modify: `expense-tracker-api/src/main/java/com/example/expensetracker/config/DemoMetrics.java`
- Modify: `expense-tracker-api/src/main/java/com/example/expensetracker/exception/GlobalExceptionHandler.java`
- Modify: `expense-tracker-api/src/test/java/com/example/expensetracker/config/DemoMetricsTest.java`

**Interfaces:**
- Consumes: `DemoQuotaService.lockActive`, `ensureAvailable`, and `recordUsed` from Task 2.
- Produces: `POST /internal/chat-tools/action-claims` under the existing chatbot-service JWT security matcher and role.
- Consumes request: `ChatActionClaimRequest(String directLineUserId, String conversationId, String activityId)`.
- Produces response: `ChatActionClaimResponse(ChatActionClaimStatus status)` where status is `PERSONAL`, `CLAIMED`, or `DUPLICATE`.
- Produces repository methods `boolean chatActionClaimExists(UUID, String)` and `void saveChatActionClaim(UUID, String, OffsetDateTime)`.
- Adds metrics operation: `DemoMetrics.Operation.CHAT`.

- [ ] **Step 1: Write failing unit tests for claim ordering**

Create `DemoChatActionClaimServiceTest` with mocked `ChatIdentityMappingService`, `DemoSessionRepository`, `DemoQuotaService`, and `Clock`. Cover these exact cases:

```java
@Test
void personalMappingIsUnmetered() {
    when(mappingService.resolveDataScope("dl_user", "conversation", NOW_INSTANT))
        .thenReturn(Optional.of(UserDataScope.personal("owner")));

    assertThat(service.claim(request("dl_user", "activity-1")).status())
        .isEqualTo(ChatActionClaimStatus.PERSONAL);
    verifyNoInteractions(quotaService, sessionRepository);
}

@Test
void duplicateIsReturnedBeforeQuotaCheck() {
    DemoSession session = demoSession();
    stubDemoMapping(session);
    when(quotaService.lockActive(session.getId())).thenReturn(session);
    when(sessionRepository.chatActionClaimExists(session.getId(), "activity-1"))
        .thenReturn(true);

    assertThat(service.claim(request("dl_demo_user", "activity-1")).status())
        .isEqualTo(ChatActionClaimStatus.DUPLICATE);
    verify(quotaService, never()).ensureAvailable(any(), anyInt(), any());
    verify(quotaService, never()).recordUsed(any(), anyInt());
}

@Test
void newDemoActivityIsRecordedAndChargedAtomically() {
    DemoSession session = demoSession();
    stubDemoMapping(session);
    when(quotaService.lockActive(session.getId())).thenReturn(session);

    assertThat(service.claim(request("dl_demo_user", "activity-1")).status())
        .isEqualTo(ChatActionClaimStatus.CLAIMED);
    InOrder writes = inOrder(quotaService, sessionRepository);
    writes.verify(quotaService).ensureAvailable(session, 1, DemoMetrics.Operation.CHAT);
    writes.verify(sessionRepository).saveChatActionClaim(session.getId(), "activity-1", NOW_OFFSET);
    writes.verify(quotaService).recordUsed(session, 1);
}
```

Also assert blank or overlong user, conversation, and activity IDs throw `IllegalArgumentException` before repository access, and a missing/expired mapping throws `ChatIdentityNotFoundException`.

- [ ] **Step 2: Write failing controller and concurrency tests**

Create `InternalChatActionControllerTest` with:

```java
@WebMvcTest(InternalChatActionController.class)
@Import({ChatbotServiceSecurityConfig.class, GlobalExceptionHandler.class,
    DataRealmExecutor.class})
class InternalChatActionControllerTest {
    @Autowired MockMvc mockMvc;
    @MockBean DemoChatActionClaimService service;
    @MockBean JwtDecoder jwtDecoder;
}
```

For each status, stub `service.claim(any())` and send the request with:

```java
.with(jwt().authorities(
    new SimpleGrantedAuthority("ROLE_CHATBOT_TOOL_EXECUTOR")))
```

Assert an authorized service JWT receives `Cache-Control: no-store` and `$.status` for `PERSONAL`, `CLAIMED`, and `DUPLICATE`. Assert missing bearer authentication is rejected by the existing `/internal/chat-tools/**` security chain. Stub `IllegalArgumentException` for invalid input and expect 400; `ChatIdentityNotFoundException` and expect 404; and `DemoSessionException.quotaExhausted()` and expect 429 with code `DEMO_QUOTA_EXHAUSTED`.

In `DemoChatActionClaimConcurrencyTest`, migrate one SQL Server, create a demo session and matching `dl_demo_` identity mapping, and race 20 unique activity IDs against the default limit. Assert exactly 15 `CLAIMED`, five quota failures, `used_actions = 15`, and 15 claim rows. Redeliver one claimed activity after exhaustion and assert `DUPLICATE` with counts unchanged.

Add a second concurrency test that races 20 deliveries of the same activity ID against a fresh session. Assert exactly one `CLAIMED`, 19 `DUPLICATE`, `used_actions = 1`, and one claim row. Each test cleanup must execute `DELETE FROM demo_chat_action_claim` before deleting `demo_session` rows.

- [ ] **Step 3: Run claim tests and verify they fail**

Run:

```bash
./mvnw -Dtest=DemoChatActionClaimServiceTest,InternalChatActionControllerTest,DemoChatActionClaimConcurrencyTest test
```

Expected: compilation fails because the request, response, status, service, controller, and repository methods do not exist.

- [ ] **Step 4: Add the request and response contract**

Create these records and enum in `chattool`:

```java
public record ChatActionClaimRequest(
    String directLineUserId,
    String conversationId,
    String activityId
) {}

public record ChatActionClaimResponse(ChatActionClaimStatus status) {}

public enum ChatActionClaimStatus {
    PERSONAL,
    CLAIMED,
    DUPLICATE
}
```

- [ ] **Step 5: Add claim persistence under the session lock**

Add to `DemoSessionRepository`:

```java
public boolean chatActionClaimExists(UUID sessionId, String activityId) {
    Number count = (Number) entityManager.createNativeQuery("""
        SELECT COUNT(*) FROM demo_chat_action_claim
        WHERE demo_session_id = :sessionId AND activity_id = :activityId
        """)
        .setParameter("sessionId", sessionId)
        .setParameter("activityId", activityId)
        .getSingleResult();
    return count.intValue() > 0;
}

public void saveChatActionClaim(UUID sessionId, String activityId, OffsetDateTime claimedAt) {
    entityManager.createNativeQuery("""
        INSERT INTO demo_chat_action_claim (demo_session_id, activity_id, claimed_at)
        VALUES (:sessionId, :activityId, :claimedAt)
        """)
        .setParameter("sessionId", sessionId)
        .setParameter("activityId", activityId)
        .setParameter("claimedAt", claimedAt)
        .executeUpdate();
}
```

The cascading foreign key handles expiry cleanup; do not add manual claim deletion queries.

- [ ] **Step 6: Implement the transactional claim service**

Create `DemoChatActionClaimService` with `@Transactional` on `claim` and these operations in order:

```java
public ChatActionClaimResponse claim(ChatActionClaimRequest request) {
    validate(request);
    UserDataScope scope = mappingService.resolveDataScope(
            request.directLineUserId(), request.conversationId(), Instant.now(clock))
        .orElseThrow(ChatIdentityNotFoundException::new);
    if (!scope.demo()) {
        return response(ChatActionClaimStatus.PERSONAL);
    }

    DemoSession session = quotaService.lockActive(scope.demoSessionId());
    if (sessionRepository.chatActionClaimExists(session.getId(), request.activityId())) {
        return response(ChatActionClaimStatus.DUPLICATE);
    }
    quotaService.ensureAvailable(session, 1, DemoMetrics.Operation.CHAT);
    sessionRepository.saveChatActionClaim(
        session.getId(), request.activityId(), sessionRepository.databaseNow());
    quotaService.recordUsed(session, 1);
    return response(ChatActionClaimStatus.CLAIMED);
}
```

Validation requires nonblank values with maximum lengths 128 for `directLineUserId` and 255 for both `conversationId` and `activityId`. It must run before identity lookup.

- [ ] **Step 7: Select the realm before opening the transaction**

Create `InternalChatActionController`:

```java
@RestController
@RequestMapping("/internal/chat-tools/action-claims")
public class InternalChatActionController {
    private final DemoChatActionClaimService service;
    private final DataRealmExecutor realmExecutor;

    @PostMapping
    public ResponseEntity<ChatActionClaimResponse> claim(
        @RequestBody ChatActionClaimRequest request
    ) {
        DataRealm realm = request != null
            && request.directLineUserId() != null
            && request.directLineUserId().startsWith("dl_demo_")
            ? DataRealm.DEMO : DataRealm.PRIMARY;
        ChatActionClaimResponse response = realmExecutor.inRealm(
            realm, () -> service.claim(request));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
    }
}
```

Because the proxied service is invoked inside `inRealm`, Spring opens its transaction after the realm is selected. Keep the endpoint under `/internal/chat-tools/**` so the existing chatbot-service issuer, audience, and role checks apply unchanged.

Add a global `@ExceptionHandler(ChatIdentityNotFoundException.class)` that returns 404 with code `CHAT_IDENTITY_NOT_FOUND`. Existing `DemoSessionException` and `IllegalArgumentException` handlers provide the 429 and 400 responses.

- [ ] **Step 8: Add the fixed chat metric dimension**

Add `CHAT` to `DemoMetrics.Operation`. Update `DemoMetricsTest` to call `metrics.quotaRejected(DemoMetrics.Operation.CHAT)` and assert one `demo.quota.rejections{operation="chat"}` count. Do not include user, conversation, session, or activity IDs in metric tags.

- [ ] **Step 9: Run all claim tests and verify they pass**

Run:

```bash
./mvnw -Dtest=DemoChatActionClaimServiceTest,InternalChatActionControllerTest,DemoChatActionClaimConcurrencyTest,ChatbotServiceSecurityConfigTest,DemoMetricsTest test
```

Expected: PASS; concurrent unique claims stop at 15, concurrent duplicate deliveries charge once, and a duplicate after exhaustion remains `DUPLICATE`.

- [ ] **Step 10: Commit the authoritative claim API**

```bash
git add src/main/java/com/example/expensetracker/chattool/ChatActionClaimRequest.java src/main/java/com/example/expensetracker/chattool/ChatActionClaimResponse.java src/main/java/com/example/expensetracker/chattool/ChatActionClaimStatus.java src/main/java/com/example/expensetracker/config/DemoMetrics.java src/main/java/com/example/expensetracker/controller/InternalChatActionController.java src/main/java/com/example/expensetracker/demo/quota/DemoChatActionClaimService.java src/main/java/com/example/expensetracker/demo/session/DemoSessionRepository.java src/main/java/com/example/expensetracker/exception/GlobalExceptionHandler.java src/test/java/com/example/expensetracker/config/DemoMetricsTest.java src/test/java/com/example/expensetracker/controller/InternalChatActionControllerTest.java src/test/java/com/example/expensetracker/demo/quota/DemoChatActionClaimConcurrencyTest.java src/test/java/com/example/expensetracker/demo/quota/DemoChatActionClaimServiceTest.java
git commit -m "feat: claim demo chatbot actions"
```

### Task 5: Add the Chatbot Claim Transport

**Files:**
- Create: `expense-tracker-chatbot/src/main/java/com/example/chatbot/expense/ChatActionClaimStatus.java`
- Create: `expense-tracker-chatbot/src/main/java/com/example/chatbot/expense/ExpenseQuotaException.java`
- Modify: `expense-tracker-chatbot/src/main/java/com/example/chatbot/expense/ExpenseTrackerToolClient.java`
- Modify: `expense-tracker-chatbot/src/main/java/com/example/chatbot/expense/HttpExpenseTrackerToolClient.java`
- Modify: `expense-tracker-chatbot/src/test/java/com/example/chatbot/expense/HttpExpenseTrackerToolClientTest.java`

**Interfaces:**
- Consumes: API `POST /internal/chat-tools/action-claims` from Task 4.
- Produces: `ChatActionClaimStatus claim(BotActivity activity)` on `ExpenseTrackerToolClient`.
- Maps: HTTP 404 to `ExpenseIdentityException`, HTTP 429 with `DEMO_QUOTA_EXHAUSTED` to `ExpenseQuotaException`, and transport/other response failures to `ExpenseToolUnavailableException`.

- [ ] **Step 1: Write failing HTTP client tests**

Add MockWebServer tests that assert `claim(activity())` sends:

```json
{
  "directLineUserId": "dl_user",
  "conversationId": "conversation",
  "activityId": "activity"
}
```

to `/internal/chat-tools/action-claims` with the bearer token, and maps each JSON status `PERSONAL`, `CLAIMED`, and `DUPLICATE` to the same enum value.

Add exact failure tests:

```java
server.enqueue(jsonResponse(429,
    "{\"code\":\"DEMO_QUOTA_EXHAUSTED\",\"message\":\"limit\"}"));
assertThatThrownBy(() -> client.claim(activity()))
    .isInstanceOf(ExpenseQuotaException.class);

server.enqueue(jsonResponse(404,
    "{\"code\":\"CHAT_IDENTITY_NOT_FOUND\",\"message\":\"missing\"}"));
assertThatThrownBy(() -> client.claim(activity()))
    .isInstanceOf(ExpenseIdentityException.class);
```

Also assert a 503, malformed success body, or missing activity identity becomes `ExpenseToolUnavailableException` or `ExpenseToolValidationException` without returning a status.

- [ ] **Step 2: Run the HTTP client test and verify it fails**

Run:

```bash
./mvnw -Dtest=HttpExpenseTrackerToolClientTest test
```

Expected: compilation fails because `claim`, `ChatActionClaimStatus`, and `ExpenseQuotaException` do not exist.

- [ ] **Step 3: Extend the existing authenticated client**

Create the enum:

```java
public enum ChatActionClaimStatus {
    PERSONAL,
    CLAIMED,
    DUPLICATE
}
```

Create `ExpenseQuotaException`:

```java
public class ExpenseQuotaException extends RuntimeException {
    public ExpenseQuotaException() {
        super("Demo chatbot quota is exhausted");
    }
}
```

Add to `ExpenseTrackerToolClient`:

```java
default ChatActionClaimStatus claim(BotActivity activity) {
    return ChatActionClaimStatus.PERSONAL;
}
```

The default preserves the explicitly disabled local Expense API configuration; production already fails startup unless `expense.api.enabled=true`.

In `HttpExpenseTrackerToolClient`, retain the existing `/execute` URI and add `/action-claims`. Implement `claim` using the same `RestClient` and token provider:

```java
ClaimResponse response = restClient.post()
    .uri(claimEndpoint)
    .headers(headers -> headers.setBearerAuth(tokenProvider.getToken()))
    .contentType(MediaType.APPLICATION_JSON)
    .body(new ClaimRequest(
        activity.from().id(), activity.conversation().id(), activity.id()))
    .retrieve()
    .body(ClaimResponse.class);
if (response == null || response.status() == null) {
    throw new ExpenseToolUnavailableException("Expense API returned an invalid claim response");
}
return response.status();
```

Inspect the JSON error `code` for 429. Never include the server response body in exception messages.

Use this private helper in the `RestClientResponseException` catch block:

```java
private String errorCode(RestClientResponseException exception) {
    try {
        return objectMapper.readTree(exception.getResponseBodyAsByteArray())
            .path("code").asText("");
    } catch (Exception ignored) {
        return "";
    }
}
```

The catch ordering is exact: 404 throws `ExpenseIdentityException`; 429 with code `DEMO_QUOTA_EXHAUSTED` throws `ExpenseQuotaException`; every other HTTP response throws `ExpenseToolUnavailableException`.

- [ ] **Step 4: Run the HTTP client test and verify it passes**

Run `./mvnw -Dtest=HttpExpenseTrackerToolClientTest test`.

Expected: PASS for all three statuses and typed failure mappings.

- [ ] **Step 5: Commit the chatbot transport**

```bash
git add src/main/java/com/example/chatbot/expense/ChatActionClaimStatus.java src/main/java/com/example/chatbot/expense/ExpenseQuotaException.java src/main/java/com/example/chatbot/expense/ExpenseTrackerToolClient.java src/main/java/com/example/chatbot/expense/HttpExpenseTrackerToolClient.java src/test/java/com/example/chatbot/expense/HttpExpenseTrackerToolClientTest.java
git commit -m "feat: call chatbot action claim API"
```

### Task 6: Gate Azure OpenAI Behind the Chat Claim

**Files:**
- Modify: `expense-tracker-chatbot/src/main/java/com/example/chatbot/expense/ExpenseToolRegistry.java`
- Modify: `expense-tracker-chatbot/src/main/java/com/example/chatbot/orchestration/ChatOrchestrator.java`
- Modify: `expense-tracker-chatbot/src/main/java/com/example/chatbot/orchestration/ChatFailureMessage.java`
- Modify: `expense-tracker-chatbot/src/test/java/com/example/chatbot/orchestration/ChatOrchestratorTest.java`

**Interfaces:**
- Consumes: `ExpenseTrackerToolClient.claim(BotActivity)` from Task 5.
- Produces: `ChatActionClaimStatus ExpenseToolRegistry.claim(BotActivity)` as a narrow delegate.
- Guarantees: local validation, then local rate limit, then claim, then first model call.

- [ ] **Step 1: Add failing orchestration tests for every claim outcome**

Enhance the test client to queue a claim result or exception and count claim calls. Add tests that prove:

```java
@Test
void claimsAfterRateLimitAndBeforeFirstModelRequest() {
    OrderedClient client = new OrderedClient(ChatActionClaimStatus.CLAIMED, events);
    OrderedGateway model = new OrderedGateway(events,
        new TextTurn("Answer", ModelUsage.ZERO));

    assertThat(orchestrator(model, client, allowingLimiter()).answer(activity("Question")))
        .isEqualTo("Answer");
    assertThat(events).containsExactly("rate-limit", "claim", "model");
}

@Test
void duplicateNeverCallsModel() {
    CapturingClient client = clientWithClaim(ChatActionClaimStatus.DUPLICATE);
    QueueGateway model = new QueueGateway();

    assertThat(orchestrator(model, client).answer(activity("Question")))
        .isEqualTo(ChatFailureMessage.DUPLICATE);
    assertThat(model.requests).isEmpty();
}
```

Add cases for:

- `PERSONAL` and `CLAIMED`: each proceeds to the model once.
- `ExpenseQuotaException`: returns `ChatFailureMessage.QUOTA` and model count remains zero.
- `ExpenseIdentityException`: returns `ChatFailureMessage.IDENTITY` and model count remains zero.
- `ExpenseToolUnavailableException` during claim: returns `ChatFailureMessage.CLAIM_UNAVAILABLE` and model count remains zero.
- Model failure after `CLAIMED`: claim count is one and the existing model failure response is returned; no release call exists.
- A tool-assisted answer that makes both the initial and final Azure OpenAI requests: claim count remains exactly one for the Direct Line message.
- Blank text, oversized text, missing activity ID, missing sender ID, missing conversation, and local rate rejection: claim and model counts both remain zero.

- [ ] **Step 2: Run the orchestrator test and verify it fails**

Run:

```bash
./mvnw -Dtest=ChatOrchestratorTest test
```

Expected: FAIL because the orchestrator does not claim messages and the new failure constants do not exist.

- [ ] **Step 3: Add the registry delegate and safe messages**

Add to `ExpenseToolRegistry`:

```java
public ChatActionClaimStatus claim(BotActivity activity) {
    return client.claim(activity);
}
```

Add fixed messages:

```java
public static final String QUOTA =
    "This demo session has no actions remaining. Sign in to continue using Gastos Chatbot.";
public static final String DUPLICATE =
    "I already processed that message. Please send a new question.";
public static final String CLAIM_UNAVAILABLE =
    "I can't verify this message right now. Please try again in a moment.";
```

- [ ] **Step 4: Insert the claim at the model boundary**

After text-length and complete identity validation, retain the existing rate-limit check, then add:

```java
try {
    ChatActionClaimStatus claim = toolRegistry.claim(activity);
    if (claim == ChatActionClaimStatus.DUPLICATE) {
        return ChatFailureMessage.DUPLICATE;
    }
} catch (ExpenseQuotaException exception) {
    return ChatFailureMessage.QUOTA;
} catch (ExpenseIdentityException exception) {
    return ChatFailureMessage.IDENTITY;
} catch (ExpenseToolUnavailableException exception) {
    return ChatFailureMessage.CLAIM_UNAVAILABLE;
}
```

Only then construct `ModelRequest.initial`. Expand identity validation to require nonblank `activity.id()`, `activity.from().id()`, and `activity.conversation().id()`. Keep tool-call exceptions inside the existing model/tool try-catch so a later tool failure does not alter claim behavior.

- [ ] **Step 5: Run orchestration and activity-service tests**

Run:

```bash
./mvnw -Dtest=ChatOrchestratorTest,BotActivityServiceTest test
```

Expected: PASS; welcome and typing behavior remains uncharged, every admitted message has exactly one pre-model claim, and no failure path accidentally invokes the model.

- [ ] **Step 6: Commit the chatbot gate**

```bash
git add src/main/java/com/example/chatbot/expense/ExpenseToolRegistry.java src/main/java/com/example/chatbot/orchestration/ChatFailureMessage.java src/main/java/com/example/chatbot/orchestration/ChatOrchestrator.java src/test/java/com/example/chatbot/orchestration/ChatOrchestratorTest.java
git commit -m "feat: gate chatbot model calls by demo quota"
```

### Task 7: Keep Demo Speech Prefetch and Refresh Quota After Bot Replies

**Files:**
- Create: `expense-tracker-web/src/api/demoQuota.ts`
- Modify: `expense-tracker-web/src/types/demoSession.ts`
- Modify: `expense-tracker-web/src/components/ChatBotWidget.vue`
- Modify: `expense-tracker-web/src/components/ChatBotWidget.test.ts`
- Modify only the speech-prefetch hunk: `expense-tracker-web/src/pages/DashboardPage.vue`
- Modify only the matching test hunk: `expense-tracker-web/src/pages/DashboardPage.test.ts`

**Interfaces:**
- Consumes: API `GET /api/demo/sessions/current/quota` from Task 3.
- Produces: `getCurrentDemoQuota(): Promise<DemoQuotaSnapshot>` through `apiFetch`.
- Produces type: `DemoQuotaSnapshot { limit: number; used: number; remaining: number; expiresAt: string }`.
- Guarantees: only authenticated demo bot `message` activities with nonblank `replyToId` trigger refresh.

- [ ] **Step 1: Write failing API and widget tests**

Create `src/api/demoQuota.ts` only after first adding its test expectations to `ChatBotWidget.test.ts`. Mock `@/api/demoQuota` and set the Pinia auth store to demo mode. Add tests that emit activities through the existing Direct Line test observer and assert:

```ts
emitDirectLineActivity?.({
  type: 'message',
  from: { id: 'gastos-bot' },
  replyToId: 'activity-1',
  text: 'Answer',
})
await flushPromises()
expect(getCurrentDemoQuota).toHaveBeenCalledOnce()
```

Then emit a welcome message without `replyToId`, a `typing` activity, and a message from `tokenResponse.userId`; assert none calls the quota endpoint. Repeat the reply test in personal mode and assert no call.

Mock `getCurrentDemoQuota` to reject and assert the bot reply remains rendered/handled with no widget error state. Update the lifecycle test: the Direct Line activity subscription is not unsubscribed after welcome, but is unsubscribed exactly once on component unmount.

In `DashboardPage.test.ts`, change the demo prefetch expectation from `not.toHaveBeenCalled()` to `toHaveBeenCalledOnce()`.

- [ ] **Step 2: Run the focused web tests and verify they fail**

Run:

```bash
npm run test -- src/components/ChatBotWidget.test.ts src/pages/DashboardPage.test.ts
```

Expected: FAIL because the quota API module and persistent reply observer do not exist, and demo speech prefetch is currently skipped.

- [ ] **Step 3: Add the quota API wrapper**

Add to `src/types/demoSession.ts`:

```ts
export interface DemoQuotaSnapshot {
  limit: number
  used: number
  remaining: number
  expiresAt: string
}
```

Create `src/api/demoQuota.ts`:

```ts
import { apiFetch } from '@/api/http'
import type { DemoQuotaSnapshot } from '@/types/demoSession'

export function getCurrentDemoQuota() {
  return apiFetch<DemoQuotaSnapshot>('/api/demo/sessions/current/quota')
}
```

Do not update Pinia from the response body. `apiFetch` already calls `authStore.applyDemoHeaders(response)` with the authoritative headers.

- [ ] **Step 4: Keep one persistent Direct Line activity subscription**

In `ChatBotWidget.vue`, import `getCurrentDemoQuota` and `useAuthStore`. Replace the welcome-only subscription with one `activitySubscription` that remains active until initialization is abandoned or the component unmounts.

Its observer must perform both responsibilities:

```ts
if (activity.type === 'message' && activity.from?.id !== directLineUserId) {
  if (initializationState.value === 'connecting') {
    finishWelcomeWait()
  }
  if (authStore.isDemo && activity.replyToId) {
    void getCurrentDemoQuota().catch(() => undefined)
  }
}
```

`finishWelcomeWait` clears only the welcome timer and transitions to ready. A separate cleanup function unsubscribes during initialization failure and `onBeforeUnmount`. Welcome and typing activities never refresh quota.

- [ ] **Step 5: Enable speech prefetch for demo sessions**

Change the existing mounted hook to:

```ts
onMounted(() => {
  window.addEventListener('keydown', clearAnalyticsCategoryOnEscape)
  prefetchSpeechToken()
})
```

This intentionally means a successful demo prefetch consumes one action even if Voice is never opened. The `getSpeechToken` cache continues to prevent a second token charge when Voice opens before expiry.

- [ ] **Step 6: Run focused web tests and typecheck**

Run:

```bash
npm run test -- src/components/ChatBotWidget.test.ts src/pages/DashboardPage.test.ts src/stores/authStore.test.ts
npm run typecheck
```

Expected: PASS; demo replies refresh quota, welcome/typing/personal flows do not, refresh failure is nonblocking, and demo speech prefetch occurs once.

- [ ] **Step 7: Commit only feature-related web hunks**

Stage the new and clean chatbot files normally. Stage only the speech-prefetch changes from the already-dirty Dashboard files and verify the cached diff excludes `src/styles.css` and unrelated Dashboard edits:

```bash
git add src/api/demoQuota.ts src/types/demoSession.ts src/components/ChatBotWidget.vue src/components/ChatBotWidget.test.ts
git add -p src/pages/DashboardPage.vue src/pages/DashboardPage.test.ts
git diff --cached --check
git commit -m "feat: refresh demo quota after chatbot replies"
```

### Task 8: Run Cross-Repository Verification

**Files:**
- No source files are changed in this task.

**Interfaces:**
- Verifies the complete API → chatbot → web contract and confirms unrelated worktree changes remain unstaged.

- [ ] **Step 1: Run the full Expense API suite**

Run from `expense-tracker-api`:

```bash
./mvnw clean test
```

Expected: BUILD SUCCESS, including Flyway/Testcontainers migration, 15-action concurrency, idempotent chat claim, existing receipt/speech release behavior, security, and realm isolation.

- [ ] **Step 2: Run the full chatbot suite and package build**

Run from `expense-tracker-chatbot`:

```bash
./mvnw clean test
./mvnw package -DskipTests
```

Expected: both commands report BUILD SUCCESS.

- [ ] **Step 3: Run the full web suite, typecheck, and production build**

Run from `expense-tracker-web`:

```bash
npm run test -- --run
npm run typecheck
npm run build
```

Expected: all Vitest tests pass, TypeScript reports no errors, and Vite completes the production build.

- [ ] **Step 4: Audit the final contract and working trees**

Run:

```bash
rg -n "ACTION_LIMIT = 10|BETWEEN 0 AND 10|cost BETWEEN 1 AND 10" expense-tracker-api/src
rg -n "DEMO_ACTION_LIMIT|demo.action-limit" expense-tracker-api/.env.sample expense-tracker-api/docker-compose.yml expense-tracker-api/src/main/resources/application-prod.properties
git -C expense-tracker-api status --short
git -C expense-tracker-chatbot status --short
git -C expense-tracker-web status --short
```

Expected: the fixed-ten search returns no matches; all three configuration surfaces show default 15; the API still shows only the user's pre-existing `.env.example`, `.env.sample`, and `docker-compose.yml` differences not included in feature commits; the web still shows only pre-existing Dashboard/style hunks not included in the feature commit; the chatbot tree is clean.

- [ ] **Step 5: Review commit boundaries without merging or deploying**

Run `git log --oneline -8` in each repository and confirm each task is represented by its own focused commit. Stop after reporting results; do not push, deploy, or create a pull request without separate user authorization.
