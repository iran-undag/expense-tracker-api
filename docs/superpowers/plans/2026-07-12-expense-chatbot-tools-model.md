# Expense Chatbot Tools And Model Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add secure, stateless Azure OpenAI orchestration and five user-isolated expense query tools to the deployed Gastos chatbot.

**Architecture:** The API exposes one service-authenticated internal tool endpoint that resolves the existing Direct Line identity mapping before dispatching typed, bounded calls to existing domain services. The chatbot owns a provider-neutral `ChatModelGateway`, uses a deterministic fake locally and in tests, and implements Azure OpenAI through an isolated managed-identity REST adapter so a future Foundry adapter does not affect tool, identity, or Connector code.

**Tech Stack:** Java 17, Spring Boot 3.3, Spring Security OAuth2 Resource Server, Azure Identity 1.15.4, Jackson, Spring `RestClient`, JPA/H2/SQL Server, JUnit 5, MockWebServer, Maven.

## Global Constraints

- Each Direct Line message is stateless; do not add chat memory or persistence.
- The chatbot and API remain separate repositories and deployment units.
- V1 exposes no user-requested create, update, or delete tools. Each expense-aware tool invokes `RecurringExpenseService.generateDueExpenses` once after identity resolution so chatbot results match existing dashboard behavior.
- The expense owner is derived only from an unexpired `(directLineUserId, conversationId)` mapping.
- Browser tokens, user IDs, model arguments, and message text cannot select or override the owner.
- `/internal/chat-tools/**` requires the chatbot service role and must reject ordinary user JWTs.
- Production service-to-service and Azure OpenAI authentication use the chatbot's user-assigned managed identity.
- Automated tests make no Azure calls and incur no model cost.
- Maximums per message: 2,000 user characters, two tool calls, two model requests, 4,000 response characters.
- Do not log prompts, responses, tool bodies, expense descriptions, raw Direct Line identifiers, mapped user IDs, JWTs, or credentials.
- Do not modify the Vue application in this phase.
- Preserve unrelated working-tree changes in both repositories.

---

### Task 1: Add Dedicated Internal-Service Authentication To The API

**Files:**
- Create: `src/main/java/com/example/expensetracker/security/ChatbotServiceSecurityProperties.java`
- Create: `src/main/java/com/example/expensetracker/security/ChatbotServiceSecurityConfig.java`
- Test: `src/test/java/com/example/expensetracker/security/ChatbotServiceSecurityConfigTest.java`
- Modify: `src/main/resources/application.properties`
- Modify: `.env.sample`

**Interfaces:**
- Produces: a priority-1 Spring Security chain for `/internal/chat-tools/**` requiring authority `ROLE_CHATBOT_TOOL_EXECUTOR`.
- Consumes: `chatbot.service.issuer`, `audience`, `jwk-set-uri`, and `required-role` configuration.

- [ ] **Step 1: Write the failing security tests**

Create `ChatbotServiceSecurityConfigTest` with a test controller at `/internal/chat-tools/probe`. Use a mocked `JwtDecoder` qualified as `chatbotServiceJwtDecoder` and cover these exact cases:

```java
@Test
void rejectsMissingBearerToken() throws Exception {
    mockMvc.perform(post("/internal/chat-tools/probe"))
        .andExpect(status().isUnauthorized());
}

@Test
void rejectsOrdinaryAuthenticatedUserWithoutServiceRole() throws Exception {
    mockMvc.perform(post("/internal/chat-tools/probe")
            .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_expense.read"))))
        .andExpect(status().isForbidden());
}

@Test
void acceptsChatbotToolExecutorRole() throws Exception {
    mockMvc.perform(post("/internal/chat-tools/probe")
            .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CHATBOT_TOOL_EXECUTOR"))))
        .andExpect(status().isNoContent());
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```bash
./mvnw -Dtest=ChatbotServiceSecurityConfigTest test
```

Expected: failure because the internal security configuration and qualified decoder do not exist.

- [ ] **Step 3: Implement typed security properties and validators**

Add an immutable configuration record:

```java
@ConfigurationProperties("chatbot.service")
public record ChatbotServiceSecurityProperties(
    String issuer,
    String audience,
    String jwkSetUri,
    String requiredRole
) {}
```

Build a dedicated `NimbusJwtDecoder` from `jwkSetUri`. Combine `JwtValidators.createDefaultWithIssuer(issuer)` with an audience validator that succeeds only when `jwt.getAudience().contains(audience)`. Convert the `roles` claim to `ROLE_` authorities and require `ROLE_CHATBOT_TOOL_EXECUTOR`.

Create this priority-1 chain:

```java
@Bean
@Order(1)
SecurityFilterChain chatbotToolSecurityChain(
        HttpSecurity http,
        @Qualifier("chatbotServiceJwtDecoder") JwtDecoder decoder) throws Exception {
    return http
        .securityMatcher("/internal/chat-tools/**")
        .csrf(AbstractHttpConfigurer::disable)
        .cors(AbstractHttpConfigurer::disable)
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth.anyRequest().hasRole("CHATBOT_TOOL_EXECUTOR"))
        .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.decoder(decoder)))
        .build();
}
```

Keep the existing user-facing dev/prod chains unchanged except for explicit ordering after this chain if Spring reports ambiguous chain order.

- [ ] **Step 4: Add production configuration names without secrets**

Add:

```properties
chatbot.service.issuer=${CHATBOT_SERVICE_ISSUER:}
chatbot.service.audience=${CHATBOT_SERVICE_AUDIENCE:}
chatbot.service.jwk-set-uri=${CHATBOT_SERVICE_JWK_SET_URI:}
chatbot.service.required-role=${CHATBOT_SERVICE_REQUIRED_ROLE:CHATBOT_TOOL_EXECUTOR}
```

Document the same names in `.env.sample` with empty values. Do not add tenant IDs, client IDs, tokens, or keys.

- [ ] **Step 5: Run security and full API tests**

Run:

```bash
./mvnw -Dtest=ChatbotServiceSecurityConfigTest test
./mvnw test
```

Expected: all tests pass; existing browser/API authentication tests remain green.

- [ ] **Step 6: Commit the authentication boundary**

```bash
git add src/main/java/com/example/expensetracker/security \
  src/test/java/com/example/expensetracker/security/ChatbotServiceSecurityConfigTest.java \
  src/main/resources/application.properties .env.sample
git commit -m "feat: secure internal chatbot tools"
```

---

### Task 2: Define And Validate The Internal Tool Contract

**Files:**
- Create: `src/main/java/com/example/expensetracker/chattool/ChatToolName.java`
- Create: `src/main/java/com/example/expensetracker/chattool/ChatToolRequest.java`
- Create: `src/main/java/com/example/expensetracker/chattool/ChatToolResponse.java`
- Create: `src/main/java/com/example/expensetracker/chattool/ChatToolArguments.java`
- Create: `src/main/java/com/example/expensetracker/chattool/MonthlySummaryArguments.java`
- Create: `src/main/java/com/example/expensetracker/chattool/CategoryBreakdownArguments.java`
- Create: `src/main/java/com/example/expensetracker/chattool/SpendingTrendArguments.java`
- Create: `src/main/java/com/example/expensetracker/chattool/BudgetStatusArguments.java`
- Create: `src/main/java/com/example/expensetracker/chattool/ExpenseLookupArguments.java`
- Create: `src/main/java/com/example/expensetracker/chattool/ChatToolRequestValidator.java`
- Test: `src/test/java/com/example/expensetracker/chattool/ChatToolRequestValidatorTest.java`

**Interfaces:**
- Produces: `ValidatedChatToolRequest validate(ChatToolRequest request)` with a typed arguments record.
- Tool enum values: `MONTHLY_SUMMARY`, `CATEGORY_BREAKDOWN`, `SPENDING_TREND`, `BUDGET_STATUS`, `EXPENSE_LOOKUP`.

- [ ] **Step 1: Write parameterized failing validation tests**

Cover valid DTOs plus these failures: non-`dl_` ID, ID lengths above 128/255, year outside 2000-2100, month outside 1-12, reversed dates, date span above 366 days, trend months outside 1-24, text above 100 characters, negative amounts, minimum above maximum, page outside 0-100, and size outside 1-20.

Representative test:

```java
@Test
void rejectsExpenseRangeLongerThan366Days() {
    ChatToolRequest request = request(
        ChatToolName.EXPENSE_LOOKUP,
        objectMapper.valueToTree(new ExpenseLookupArguments(
            LocalDate.parse("2025-01-01"), LocalDate.parse("2026-01-02"),
            null, null, null, null, 0, 20)));

    assertThatThrownBy(() -> validator.validate(request))
        .isInstanceOf(ChatToolValidationException.class)
        .hasMessageContaining("366 days");
}
```

- [ ] **Step 2: Run the validator test and verify RED**

```bash
./mvnw -Dtest=ChatToolRequestValidatorTest test
```

Expected: compilation failure because the contract types do not exist.

- [ ] **Step 3: Implement strict polymorphic conversion**

Keep `arguments` as `JsonNode` in the transport envelope. Use an `ObjectMapper` copy configured with `FAIL_ON_UNKNOWN_PROPERTIES=true`, select the target record only through a `switch` over `ChatToolName`, and validate all bounds in `ChatToolRequestValidator`.

```java
public ValidatedChatToolRequest validate(ChatToolRequest request) {
    validateIdentity(request.directLineUserId(), request.conversationId());
    ChatToolArguments arguments = switch (request.tool()) {
        case MONTHLY_SUMMARY -> convert(request.arguments(), MonthlySummaryArguments.class);
        case CATEGORY_BREAKDOWN -> convert(request.arguments(), CategoryBreakdownArguments.class);
        case SPENDING_TREND -> convert(request.arguments(), SpendingTrendArguments.class);
        case BUDGET_STATUS -> convert(request.arguments(), BudgetStatusArguments.class);
        case EXPENSE_LOOKUP -> convert(request.arguments(), ExpenseLookupArguments.class);
    };
    validateBounds(arguments);
    return new ValidatedChatToolRequest(
        request.directLineUserId(), request.conversationId(), request.tool(), arguments);
}
```

Do not use a class name supplied in JSON, default typing, reflection-based dispatch, or an arbitrary map of handlers.

- [ ] **Step 4: Run focused tests and commit**

```bash
./mvnw -Dtest=ChatToolRequestValidatorTest test
git add src/main/java/com/example/expensetracker/chattool \
  src/test/java/com/example/expensetracker/chattool/ChatToolRequestValidatorTest.java
git commit -m "feat: define bounded chatbot tool contract"
```

---

### Task 3: Resolve Identity And Execute Expense-Aware Domain Tools

**Files:**
- Create: `src/main/java/com/example/expensetracker/chattool/ChatToolService.java`
- Create: `src/main/java/com/example/expensetracker/chattool/ChatExpenseResult.java`
- Test: `src/test/java/com/example/expensetracker/chattool/ChatToolServiceIntegrationTest.java`

**Interfaces:**
- Produces: `ChatToolResponse execute(ChatToolRequest request, Instant now)`.
- Consumes: the validator, identity mapping, `ReportService`, `BudgetService`, and `ExpenseService`.

- [ ] **Step 1: Write integration tests for identity isolation**

Use H2 and create two expense users with distinct mappings and expenses. Prove:

```java
assertThat(service.execute(monthlyRequest("dl_user_a", "conversation_a"), now))
    .extracting(ChatToolResponse::result)
    .satisfies(result -> assertThat(result.toString()).contains("10.00").doesNotContain("99.00"));

assertThatThrownBy(() ->
    service.execute(monthlyRequest("dl_user_a", "conversation_b"), now))
    .isInstanceOf(ChatIdentityNotFoundException.class);
```

Also test expired mapping rejection and verify the relevant domain service is not invoked when resolution fails.

- [ ] **Step 2: Write one passing-shape test per tool**

Assert exact response fields for monthly summary, category breakdown, spending trend, budget status, and expense lookup. For expense lookup, assert the serialized result contains `id`, `description`, `amount`, `date`, and `category`, and does not contain `userid`.

Create a due recurring rule for the mapped user and assert the first tool request generates its occurrence before calculating the result. Repeat the same request and assert no duplicate occurrence or expense is created. Create a due rule for a second user and assert it remains untouched. For an expired or mixed `(directLineUserId, conversationId)` pair, verify `RecurringExpenseService.generateDueExpenses` is never invoked.

- [ ] **Step 3: Run the integration test and verify RED**

```bash
./mvnw -Dtest=ChatToolServiceIntegrationTest test
```

Expected: compilation failure because `ChatToolService` and result DTOs do not exist.

- [ ] **Step 4: Implement minimal typed dispatch**

Resolve the mapped user before the switch:

```java
String userId = mappingService.resolveUserId(
        validated.directLineUserId(), validated.conversationId(), now)
    .orElseThrow(ChatIdentityNotFoundException::new);

recurringExpenseService.generateDueExpenses(userId, LocalDate.now(clock));

Object result = switch (validated.arguments()) {
    case MonthlySummaryArguments args ->
        reportService.getMonthlySummary(userId, args.year(), args.month());
    case CategoryBreakdownArguments args ->
        reportService.getCategoryBreakdown(userId, args.fromDate(), args.toDate());
    case SpendingTrendArguments args ->
        reportService.getSpendingTrend(
            userId, args.year(), args.month(), args.months(), args.category());
    case BudgetStatusArguments args ->
        budgetService.getBudgetSummary(userId, args.year(), args.month());
    case ExpenseLookupArguments args -> expenseResult(userId, args);
};
```

Call recurring generation exactly once before the dispatch switch, not once per tool branch. If Java 17 preview pattern-switch support would be required, use an ordinary enum switch and explicit casts instead. Do not enable preview features.

Build `PageRequest.of(page, size, Sort.by(DESC, "date"))` internally. Map expenses to `ChatExpenseResult`; never reuse `ExpenseResponseDto` because it exposes `userid`.

- [ ] **Step 5: Verify and commit**

```bash
./mvnw -Dtest=ChatToolServiceIntegrationTest test
./mvnw test
git add src/main/java/com/example/expensetracker/chattool \
  src/test/java/com/example/expensetracker/chattool/ChatToolServiceIntegrationTest.java
git commit -m "feat: execute user-isolated chatbot tools"
```

---

### Task 4: Expose The Bounded Internal Tool Endpoint

**Files:**
- Create: `src/main/java/com/example/expensetracker/controller/InternalChatToolController.java`
- Create: `src/main/java/com/example/expensetracker/exception/ChatToolExceptionHandler.java`
- Create: `src/main/java/com/example/expensetracker/chattool/InternalChatToolRequestSizeFilter.java`
- Create: `src/main/java/com/example/expensetracker/chattool/ChatToolRateLimiter.java`
- Test: `src/test/java/com/example/expensetracker/controller/InternalChatToolControllerTest.java`
- Modify: `src/main/resources/application.properties`

**Interfaces:**
- Produces: `POST /internal/chat-tools/execute` returning `ChatToolResponse`.
- Error codes: `INVALID_TOOL_REQUEST`, `CHAT_IDENTITY_NOT_FOUND`, `CHAT_TOOL_RATE_LIMITED`, `CHAT_TOOL_UNAVAILABLE`.

- [ ] **Step 1: Write failing MVC contract tests**

Cover authorized success; 400 invalid arguments; 401 missing token; 403 wrong role; 404 missing mapping; 413 body over 16 KiB; and a stable 503 domain failure. Verify every error body contains only `code` and `message`.

```java
mockMvc.perform(post("/internal/chat-tools/execute")
        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CHATBOT_TOOL_EXECUTOR")))
        .contentType(APPLICATION_JSON)
        .content(validRequestJson))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.tool").value("MONTHLY_SUMMARY"))
    .andExpect(jsonPath("$.result.year").value(2026));
```

- [ ] **Step 2: Run the controller test and verify RED**

```bash
./mvnw -Dtest=InternalChatToolControllerTest test
```

Expected: 404 because the endpoint does not exist.

- [ ] **Step 3: Implement controller, exception mapping, and size bound**

The controller accepts `@Valid @RequestBody ChatToolRequest`, delegates with `Instant.now(clock)`, returns `Cache-Control: no-store`, and never accepts a user ID parameter.

Configure `chatbot.tools.max-request-bytes=${CHATBOT_TOOLS_MAX_REQUEST_BYTES:16384}`.

Enforce the 16 KiB bound specifically for the internal endpoint with a request-size filter so the existing receipt upload limit is unaffected.

- [ ] **Step 4: Add a bounded per-service rate limiter**

Implement an in-memory fixed-window limiter for the single chatbot identity with default 60 requests per minute. Return 429 without executing the tool. Configuration:

```properties
chatbot.tools.requests-per-minute=${CHATBOT_TOOLS_REQUESTS_PER_MINUTE:60}
```

Inject `Clock` so rollover is deterministic in tests. Do not use user or conversation IDs as metric labels or unbounded map keys.

- [ ] **Step 5: Verify API and commit**

```bash
./mvnw -Dtest=InternalChatToolControllerTest,ChatToolServiceIntegrationTest test
./mvnw test
git diff --check
git add src/main/java/com/example/expensetracker/controller/InternalChatToolController.java \
  src/main/java/com/example/expensetracker/exception/ChatToolExceptionHandler.java \
  src/main/java/com/example/expensetracker/chattool \
  src/test/java/com/example/expensetracker/controller/InternalChatToolControllerTest.java \
  src/main/resources/application.properties
git commit -m "feat: expose internal chatbot tool endpoint"
```

---

### Task 5: Create Provider-Neutral Model And Tool Types In The Chatbot

**Files:**
- Create: `../expense-tracker-chatbot/src/main/java/com/example/chatbot/model/ChatModelGateway.java`
- Create: `../expense-tracker-chatbot/src/main/java/com/example/chatbot/model/ModelRequest.java`
- Create: `../expense-tracker-chatbot/src/main/java/com/example/chatbot/model/ModelTurn.java`
- Create: `../expense-tracker-chatbot/src/main/java/com/example/chatbot/model/TextTurn.java`
- Create: `../expense-tracker-chatbot/src/main/java/com/example/chatbot/model/ToolCallTurn.java`
- Create: `../expense-tracker-chatbot/src/main/java/com/example/chatbot/model/ToolCall.java`
- Create: `../expense-tracker-chatbot/src/main/java/com/example/chatbot/model/ToolDefinition.java`
- Create: `../expense-tracker-chatbot/src/main/java/com/example/chatbot/model/ToolResult.java`
- Create: `../expense-tracker-chatbot/src/test/java/com/example/chatbot/model/ChatModelGatewayContract.java`
- Create: `../expense-tracker-chatbot/src/test/java/com/example/chatbot/model/ScriptedChatModelGatewayTest.java`

**Interfaces:**
- Produces: `ModelTurn complete(ModelRequest request)` with no provider SDK types.
- Produces: a reusable abstract contract test for future Azure, Ollama, and Foundry adapters.

- [ ] **Step 1: Write the contract test first**

Define contract cases for a text response, one tool call, two tool calls, malformed tool arguments, provider timeout mapping, and usage metadata. The scripted adapter test must prove the application types can represent each turn without Spring AI or Azure classes.

```java
protected abstract ChatModelGateway gatewayReturning(ModelTurn turn);

@Test
void returnsProviderNeutralToolCall() {
    ToolCall expected = new ToolCall(
        "call-1", "monthly_summary", "{\"year\":2026,\"month\":7}");
    ModelTurn actual = gatewayReturning(new ToolCallTurn(List.of(expected), usage()))
        .complete(request());
    assertThat(actual).isInstanceOfSatisfying(
        ToolCallTurn.class,
        turn -> assertThat(turn.toolCalls()).containsExactly(expected));
}
```

- [ ] **Step 2: Verify RED, implement records, and verify GREEN**

```bash
cd ../expense-tracker-chatbot
./mvnw -Dtest=ScriptedChatModelGatewayTest test
```

Expected RED: missing model types. Implement sealed application records with constructor null/blank checks, then rerun for PASS.

- [ ] **Step 3: Commit the provider boundary**

```bash
git add src/main/java/com/example/chatbot/model src/test/java/com/example/chatbot/model
git commit -m "feat: define provider-neutral chatbot model gateway"
```

---

### Task 6: Add The Chatbot's Authenticated Expense Tool Client

**Files:**
- Create: `../expense-tracker-chatbot/src/main/java/com/example/chatbot/expense/ExpenseToolName.java`
- Create: `../expense-tracker-chatbot/src/main/java/com/example/chatbot/expense/ExpenseToolRequest.java`
- Create: `../expense-tracker-chatbot/src/main/java/com/example/chatbot/expense/ExpenseToolResponse.java`
- Create: `../expense-tracker-chatbot/src/main/java/com/example/chatbot/expense/ExpenseToolRegistry.java`
- Create: `../expense-tracker-chatbot/src/main/java/com/example/chatbot/expense/ExpenseTrackerToolClient.java`
- Create: `../expense-tracker-chatbot/src/main/java/com/example/chatbot/expense/ExpenseApiTokenProvider.java`
- Create: `../expense-tracker-chatbot/src/main/java/com/example/chatbot/expense/ManagedIdentityExpenseApiTokenProvider.java`
- Test: `../expense-tracker-chatbot/src/test/java/com/example/chatbot/expense/ExpenseToolRegistryTest.java`
- Test: `../expense-tracker-chatbot/src/test/java/com/example/chatbot/expense/ExpenseTrackerToolClientTest.java`
- Modify: `../expense-tracker-chatbot/src/main/resources/application.properties`

**Interfaces:**
- Produces: `ToolResult execute(BotActivity activity, ToolCall call)`.
- Token provider: `String getToken()` for scope `${EXPENSE_API_SCOPE}`.

- [ ] **Step 1: Write registry validation tests**

Use the same names and bounds as the API. Prove unknown tools and invalid JSON fail before MockWebServer receives a request. Map model names exactly:

```text
monthly_summary -> MONTHLY_SUMMARY
category_breakdown -> CATEGORY_BREAKDOWN
spending_trend -> SPENDING_TREND
budget_status -> BUDGET_STATUS
expense_lookup -> EXPENSE_LOOKUP
```

- [ ] **Step 2: Write HTTP client tests**

With MockWebServer, assert `Authorization: Bearer expense-api-token`, `X-Correlation-Id`, `/internal/chat-tools/execute`, the Activity's `from.id` and `conversation.id`, 10-second timeout mapping, stable handling of 400/404/429/503, and absence of tokens/bodies from captured logs.

- [ ] **Step 3: Verify RED and implement minimal client**

```bash
./mvnw -Dtest=ExpenseToolRegistryTest,ExpenseTrackerToolClientTest test
```

Implement `ManagedIdentityExpenseApiTokenProvider` with the already-present Azure Identity dependency:

```java
AccessToken token = credential.getToken(
    new TokenRequestContext().addScopes(expenseApiScope)).block();
if (token == null) throw new ExpenseToolUnavailableException();
return token.getToken();
```

Never accept the API base URL from a model call. Require one configured HTTPS base URL in production; allow HTTP only under `local-chatbot`.

- [ ] **Step 4: Add exact configuration**

```properties
expense.api.base-url=${EXPENSE_API_BASE_URL:http://localhost:8081}
expense.api.scope=${EXPENSE_API_SCOPE:}
expense.api.managed-identity-client-id=${AZURE_BOT_MANAGED_IDENTITY_CLIENT_ID:}
expense.api.connect-timeout=${EXPENSE_API_CONNECT_TIMEOUT:2s}
expense.api.read-timeout=${EXPENSE_API_READ_TIMEOUT:10s}
```

- [ ] **Step 5: Verify and commit**

```bash
./mvnw -Dtest=ExpenseToolRegistryTest,ExpenseTrackerToolClientTest test
./mvnw test
git add src/main/java/com/example/chatbot/expense \
  src/test/java/com/example/chatbot/expense src/main/resources/application.properties
git commit -m "feat: call authenticated expense tools"
```

---

### Task 7: Implement Bounded Stateless Orchestration

**Files:**
- Create: `../expense-tracker-chatbot/src/main/java/com/example/chatbot/orchestration/ChatOrchestrator.java`
- Create: `../expense-tracker-chatbot/src/main/java/com/example/chatbot/orchestration/ChatSystemPrompt.java`
- Create: `../expense-tracker-chatbot/src/main/java/com/example/chatbot/orchestration/ChatFailureMessage.java`
- Test: `../expense-tracker-chatbot/src/test/java/com/example/chatbot/orchestration/ChatOrchestratorTest.java`
- Modify: `../expense-tracker-chatbot/src/main/java/com/example/chatbot/activity/BotReplyFactory.java`
- Modify: `../expense-tracker-chatbot/src/main/java/com/example/chatbot/message/BotActivityService.java`
- Modify: `../expense-tracker-chatbot/src/test/java/com/example/chatbot/activity/BotReplyFactoryTest.java`
- Modify: `../expense-tracker-chatbot/src/test/java/com/example/chatbot/message/BotActivityServiceTest.java`

**Interfaces:**
- Produces: `String answer(BotActivity activity)`.
- Changes: `BotReplyFactory.createReply(BotActivity incoming, String text)`.

- [ ] **Step 1: Write orchestration tests for every branch**

Cover regular text with zero tools; one and two tool calls; unknown/invalid tool; model requesting a third tool; model timeout; tool 404 identity failure; tool 503 failure; 2,001-character input; and 4,001-character output.

Verify exact call bounds:

```java
verify(modelGateway, times(2)).complete(any());
verify(toolRegistry, atMost(2)).execute(any(), any());
verifyNoMoreInteractions(modelGateway, toolRegistry);
```

- [ ] **Step 2: Verify RED**

```bash
./mvnw -Dtest=ChatOrchestratorTest test
```

Expected: missing orchestrator.

- [ ] **Step 3: Implement the explicit two-turn state machine**

Do not delegate loop control to a provider framework:

```java
ModelTurn first = modelGateway.complete(ModelRequest.initial(
    ChatSystemPrompt.TEXT, truncateUser(activity.text()), toolDefinitions));
if (first instanceof TextTurn text) return truncateReply(text.text());

ToolCallTurn calls = requireAtMostTwo(first);
List<ToolResult> results = calls.toolCalls().stream()
    .map(call -> toolRegistry.execute(activity, call))
    .toList();
ModelTurn second = modelGateway.complete(ModelRequest.withResults(
    ChatSystemPrompt.TEXT, truncateUser(activity.text()), toolDefinitions, results));
return requireFinalText(second);
```

Execute tool calls sequentially with an ordinary loop, not a parallel stream. Map failures to the exact user messages in the approved spec.

- [ ] **Step 4: Replace the fixed reply without changing transport**

`BotActivityService` calls the orchestrator only for nonblank `message` activities and passes its text to `BotReplyFactory`. `BotConnectorClient`, send-to-conversation routing, and authentication remain unchanged.

- [ ] **Step 5: Verify and commit**

```bash
./mvnw -Dtest=ChatOrchestratorTest,BotActivityServiceTest,BotReplyFactoryTest test
./mvnw test
git add src/main/java/com/example/chatbot/orchestration \
  src/main/java/com/example/chatbot/activity/BotReplyFactory.java \
  src/main/java/com/example/chatbot/message/BotActivityService.java \
  src/test/java/com/example/chatbot
git commit -m "feat: orchestrate bounded chatbot tool calls"
```

---

### Task 8: Add Deterministic Local Model Configuration

**Files:**
- Create: `../expense-tracker-chatbot/src/main/java/com/example/chatbot/model/ScriptedChatModelGateway.java`
- Create: `../expense-tracker-chatbot/src/main/java/com/example/chatbot/model/ChatModelConfiguration.java`
- Test: `../expense-tracker-chatbot/src/test/java/com/example/chatbot/model/ChatModelConfigurationTest.java`
- Modify: `../expense-tracker-chatbot/src/main/resources/application.properties`
- Modify: `../expense-tracker-chatbot/.env.sample`

**Interfaces:**
- Produces: fake provider only under `test` and `local-chatbot`.
- Production startup rejects `CHAT_MODEL_PROVIDER=fake`.

- [ ] **Step 1: Write profile-gating tests**

Assert test/local contexts can create the deterministic gateway; prod with `chat.model.provider=fake` fails context startup; prod without required Azure settings fails startup.

- [ ] **Step 2: Implement deterministic behavior**

The local fake returns fixed, non-sensitive text and supports scripted test turns through constructor injection. It must not parse natural language or imitate production tool selection.

```java
@Profile({"test", "local-chatbot"})
@Bean
ChatModelGateway scriptedChatModelGateway() {
    return request -> new TextTurn("Local chatbot model is ready.", ModelUsage.ZERO);
}
```

- [ ] **Step 3: Verify and commit**

```bash
./mvnw -Dtest=ChatModelConfigurationTest,ChatOrchestratorTest test
git add src/main/java/com/example/chatbot/model \
  src/test/java/com/example/chatbot/model \
  src/main/resources/application.properties .env.sample
git commit -m "feat: add cost-free local chatbot model"
```

---

### Task 9: Implement The Azure OpenAI Gateway Adapter

**Files:**
- Create: `../expense-tracker-chatbot/src/main/java/com/example/chatbot/model/azure/AzureOpenAiProperties.java`
- Create: `../expense-tracker-chatbot/src/main/java/com/example/chatbot/model/azure/AzureOpenAiTokenProvider.java`
- Create: `../expense-tracker-chatbot/src/main/java/com/example/chatbot/model/azure/ManagedIdentityAzureOpenAiTokenProvider.java`
- Create: `../expense-tracker-chatbot/src/main/java/com/example/chatbot/model/azure/AzureOpenAiChatModelGateway.java`
- Test: `../expense-tracker-chatbot/src/test/java/com/example/chatbot/model/azure/AzureOpenAiChatModelGatewayTest.java`
- Modify: `../expense-tracker-chatbot/pom.xml`
- Modify: `../expense-tracker-chatbot/src/main/resources/application.properties`
- Modify: `../expense-tracker-chatbot/.env.sample`

**Interfaces:**
- Implements: `ChatModelGateway`.
- Azure scope: `https://cognitiveservices.azure.com/.default`.
- Endpoint path: configured Azure OpenAI chat-completions deployment endpoint; no URL comes from prompts or model output.

- [ ] **Step 1: Write MockWebServer adapter contract tests**

Extend `ChatModelGatewayContract`. Assert the request contains system/user messages, five JSON-schema tool definitions, temperature `0.2`, and maximum output tokens `800`. Assert bearer authentication, text parsing, one/two tool-call parsing, token usage parsing, malformed response rejection, 429 mapping, 5xx mapping, and 30-second timeout mapping.

- [ ] **Step 2: Verify RED**

```bash
./mvnw -Dtest=AzureOpenAiChatModelGatewayTest test
```

Expected: missing Azure adapter.

- [ ] **Step 3: Implement managed-identity token acquisition**

Reuse `azure-identity`; do not add Spring AI to this service in this phase. This avoids upgrading Spring Boot solely for rapidly changing Azure/Foundry adapter APIs, while `ChatModelGateway` preserves the approved provider boundary.

```java
new TokenRequestContext().addScopes("https://cognitiveservices.azure.com/.default")
```

Cache only through `DefaultAzureCredential`/`ManagedIdentityCredential`; never persist or log the returned token.

- [ ] **Step 4: Implement strict request and response mapping**

Use Jackson records for the exact fields consumed. Ignore unknown provider response fields, but reject missing choice/message content, blank tool names, missing call IDs, and non-object tool arguments. Encode tool results as tool-role messages tied to the provider call ID.

Use one configured HTTPS endpoint and deployment. Validate the endpoint at startup: HTTPS, no user-info, no query, and host ending in `.openai.azure.com` or the explicitly approved Foundry host suffix. Do not follow redirects.

- [ ] **Step 5: Add production configuration**

```properties
chat.model.provider=${CHAT_MODEL_PROVIDER:fake}
chat.model.azure.endpoint=${AZURE_OPENAI_ENDPOINT:}
chat.model.azure.deployment=${AZURE_OPENAI_DEPLOYMENT:}
chat.model.azure.api-version=${AZURE_OPENAI_API_VERSION:2024-10-21}
chat.model.azure.managed-identity-client-id=${AZURE_BOT_MANAGED_IDENTITY_CLIENT_ID:}
chat.model.azure.connect-timeout=${AZURE_OPENAI_CONNECT_TIMEOUT:5s}
chat.model.azure.read-timeout=${AZURE_OPENAI_READ_TIMEOUT:30s}
chat.model.azure.max-output-tokens=${AZURE_OPENAI_MAX_OUTPUT_TOKENS:800}
chat.model.azure.temperature=${AZURE_OPENAI_TEMPERATURE:0.2}
```

Confirm the API version against the deployed Azure resource before the live integration test; changing this configuration value must not change application code.

- [ ] **Step 6: Verify and commit**

```bash
./mvnw -Dtest=AzureOpenAiChatModelGatewayTest,ChatModelConfigurationTest test
./mvnw test
./mvnw -DskipTests package
git add pom.xml src/main/java/com/example/chatbot/model/azure \
  src/test/java/com/example/chatbot/model/azure \
  src/main/resources/application.properties .env.sample
git commit -m "feat: add Azure OpenAI chatbot adapter"
```

---

### Task 10: Add Privacy-Safe Observability And Regression Tests

**Files:**
- Create: `../expense-tracker-chatbot/src/main/java/com/example/chatbot/orchestration/ChatMetrics.java`
- Test: `../expense-tracker-chatbot/src/test/java/com/example/chatbot/orchestration/ChatLoggingRedactionTest.java`
- Test: `src/test/java/com/example/expensetracker/chattool/ChatToolLoggingRedactionTest.java`
- Modify: `../expense-tracker-chatbot/src/main/java/com/example/chatbot/orchestration/ChatOrchestrator.java`
- Modify: `../expense-tracker-chatbot/src/main/java/com/example/chatbot/expense/ExpenseTrackerToolClient.java`
- Modify: `src/main/java/com/example/expensetracker/chattool/ChatToolService.java`

**Interfaces:**
- Produces Micrometer counters/timers labeled only by provider, tool enum, and stable outcome.

- [ ] **Step 1: Write redaction tests before adding logs**

Capture logs while using sentinel prompt, response, expense description, Direct Line ID, user ID, JWT, and token values. Assert none occur. Assert correlation ID, tool enum, duration/outcome, and hashed conversation prefix do occur.

- [ ] **Step 2: Add bounded metrics and sanitized logs**

Use fixed label sets:

```text
chat.model.requests{provider,outcome}
chat.model.duration{provider,outcome}
chat.tool.requests{tool,outcome}
chat.tool.duration{tool,outcome}
chat.identity.rejections{reason}
chat.orchestration.rejections{reason}
```

Never add correlation, conversation, call ID, exception message, or user-controlled strings as metric labels.

- [ ] **Step 3: Verify both repositories and commit separately**

```bash
cd /home/user/Documents/vscode-workspace/expense-tracker-api
./mvnw -Dtest=ChatToolLoggingRedactionTest test
./mvnw test
git add src/main/java/com/example/expensetracker/chattool/ChatToolService.java \
  src/test/java/com/example/expensetracker/chattool/ChatToolLoggingRedactionTest.java
git commit -m "test: verify chatbot tool log redaction"

cd /home/user/Documents/vscode-workspace/expense-tracker-chatbot
./mvnw -Dtest=ChatLoggingRedactionTest test
./mvnw test
git add src/main/java/com/example/chatbot/orchestration/ChatMetrics.java \
  src/main/java/com/example/chatbot/orchestration/ChatOrchestrator.java \
  src/main/java/com/example/chatbot/expense/ExpenseTrackerToolClient.java \
  src/test/java/com/example/chatbot/orchestration/ChatLoggingRedactionTest.java
git commit -m "feat: add safe chatbot observability"
```

---

### Task 11: Document And Verify The Local End-To-End Flow

**Files:**
- Create: `scripts/generate-local-chatbot-keys.sh`
- Create: `src/main/java/com/example/expensetracker/security/LocalChatbotJwtDecoderConfig.java`
- Test: `src/test/java/com/example/expensetracker/security/LocalChatbotJwtDecoderConfigTest.java`
- Test: `src/test/java/com/example/expensetracker/chattool/LocalChatToolFlowIntegrationTest.java`
- Create: `../expense-tracker-chatbot/src/main/java/com/example/chatbot/expense/LocalExpenseApiTokenProvider.java`
- Test: `../expense-tracker-chatbot/src/test/java/com/example/chatbot/expense/LocalExpenseApiTokenProviderTest.java`
- Test: `../expense-tracker-chatbot/src/test/java/com/example/chatbot/LocalChatbotFlowIntegrationTest.java`
- Modify: `README.md`
- Modify: `.gitignore`
- Modify: `src/main/resources/application.properties`
- Modify: `../expense-tracker-chatbot/README.md`
- Modify: `../expense-tracker-chatbot/compose.yaml`
- Modify: `../expense-tracker-chatbot/.gitignore`
- Modify: `../expense-tracker-chatbot/src/main/resources/application.properties`

**Interfaces:**
- Produces: local RSA key files under ignored `.local/chatbot-keys/`, a locally signed short-lived service JWT, and a deterministic no-Azure test flow.

- [ ] **Step 1: Add a safe local key-generation script**

The script uses `openssl` to generate a 2048-bit RSA private key and public key beneath `.local/chatbot-keys/` with permissions 600/644. It refuses to overwrite existing keys without an explicit `--rotate` argument and never prints key material.

- [ ] **Step 2: Wire the explicit local profile**

The chatbot signs a maximum five-minute JWT containing local issuer, API audience, and `roles: ["CHATBOT_TOOL_EXECUTOR"]`. The API verifies only the public key. Both local providers must be annotated with `@Profile("local-chatbot")`; production must not load them.

Use these local-only properties:

```properties
chatbot.service.local.issuer=http://expense-tracker-chatbot.local
chatbot.service.local.audience=expense-tracker-api
chatbot.service.local.public-key-path=${CHATBOT_LOCAL_PUBLIC_KEY_PATH:.local/chatbot-keys/public.pem}
expense.api.local.private-key-path=${CHATBOT_LOCAL_PRIVATE_KEY_PATH:.local/chatbot-keys/private.pem}
expense.api.local.token-lifetime=5m
```

Use Nimbus `SignedJWT` with `RS256`, a random `jti`, `iat`, `nbf`, `exp`, exact issuer/audience, and the one required role. Do not accept a configurable role list in the local signer.

- [ ] **Step 3: Add a local smoke test script or documented curl flow**

Add an API integration test that persists a `ChatIdentityMapping` fixture directly through `ChatIdentityMappingRepository`, posts a monthly-summary internal request with the locally signed service token, and asserts the response contains no `userid`. Add a chatbot integration test that uses the deterministic gateway, the local service-token provider, MockWebServer for the API and Connector, and a signed Activity fixture to assert one final Connector reply.

- [ ] **Step 4: Run the full local gate**

```bash
cd /home/user/Documents/vscode-workspace/expense-tracker-api
./mvnw test
./mvnw -DskipTests package

cd /home/user/Documents/vscode-workspace/expense-tracker-chatbot
./mvnw test
./mvnw -DskipTests package
docker build -t expense-tracker-chatbot:local .
```

Expected: all tests pass, both JARs build, and the chatbot image builds without an Azure call.

- [ ] **Step 5: Commit documentation in each repository**

```bash
cd /home/user/Documents/vscode-workspace/expense-tracker-api
git add scripts/generate-local-chatbot-keys.sh README.md .gitignore \
  src/main/java/com/example/expensetracker/security/LocalChatbotJwtDecoderConfig.java \
  src/test/java/com/example/expensetracker/security/LocalChatbotJwtDecoderConfigTest.java \
  src/test/java/com/example/expensetracker/chattool/LocalChatToolFlowIntegrationTest.java \
  src/main/resources/application.properties
git commit -m "docs: add local chatbot tool testing"

cd /home/user/Documents/vscode-workspace/expense-tracker-chatbot
git add README.md compose.yaml .env.sample .gitignore \
  src/main/java/com/example/chatbot/expense/LocalExpenseApiTokenProvider.java \
  src/test/java/com/example/chatbot/expense/LocalExpenseApiTokenProviderTest.java \
  src/test/java/com/example/chatbot/LocalChatbotFlowIntegrationTest.java \
  src/main/resources/application.properties
git commit -m "docs: add local chatbot model testing"
```

---

### Task 12: Run The Azure Integration Gate Without Routing Production Traffic

**Files:**
- Modify: `README.md`
- Modify: `../expense-tracker-chatbot/README.md`

**Interfaces:**
- Produces: non-production API and chatbot revisions authenticated by managed identity, plus one Azure OpenAI deployment contract result.

- [ ] **Step 0: Obtain explicit deployment authorization**

Present the locally verified commits, exact Azure resources to be changed, expected paid model calls, and rollback commands to the user. Do not change Entra roles, Container App configuration, model deployments, or production traffic until the user explicitly authorizes those external changes.

- [ ] **Step 1: Configure Entra authorization**

Expose the expense API application audience, define app role `CHATBOT_TOOL_EXECUTOR`, assign it only to the chatbot user-assigned managed identity, and configure issuer/audience/JWKS values in the API Container App. Record resource names but no IDs or secrets in README.

- [ ] **Step 2: Grant model access**

Assign the chatbot identity the narrow Azure OpenAI user role on the selected model resource. Set endpoint and deployment configuration through Container App environment values; do not add an API key.

- [ ] **Step 3: Run one paid adapter contract smoke test**

Invoke one regular finance message and one monthly-summary tool message. Verify no more than two model requests per message, tool ownership, safe logs, and token metrics. Stop if the configured budget alert or rate limit triggers.

- [ ] **Step 4: Run Direct Line security smoke tests**

Verify mapped-user success, expired/unknown mapping refusal, mixed user/conversation refusal, write refusal, and ordinary user-token rejection at `/internal/chat-tools/execute`.

- [ ] **Step 5: Run final regression commands**

```bash
cd /home/user/Documents/vscode-workspace/expense-tracker-api
./mvnw test
./mvnw -DskipTests package
git status --short

cd /home/user/Documents/vscode-workspace/expense-tracker-chatbot
./mvnw test
./mvnw -DskipTests package
docker build -t expense-tracker-chatbot:local .
git status --short

cd /home/user/Documents/vscode-workspace/expense-tracker-web
npm test -- --run
npm run typecheck
npm run build
git status --short
```

Expected: all tests/builds pass; web has no chatbot changes from this phase; only known pre-existing untracked or unrelated files remain.

- [ ] **Step 6: Document verified state and commit per repository**

Update the API and chatbot READMEs with the actual verified model deployment alias, auth topology, local commands, safe rollback steps, and the fact that messages remain stateless. Never record subscription IDs, tenant IDs, client IDs, tokens, keys, or expense data.
