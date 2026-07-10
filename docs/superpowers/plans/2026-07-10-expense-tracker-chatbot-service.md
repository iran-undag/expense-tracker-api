# Expense Tracker Chatbot Service Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build and deploy a Java/Spring Boot Bot Connector REST service, plus authenticated scale-to-zero warm-up behavior in the existing API and Vue application.

**Architecture:** A new `expense-tracker-chatbot` service validates Azure Bot Connector JWTs, creates a fixed V1 reply, and sends it through the Connector REST API using the bot's user-assigned managed identity. The existing API proxies and rate-limits warm-up calls, while Vue starts warm-up after authentication and exposes its in-memory state in the chatbot widget.

**Tech Stack:** Java 17, Spring Boot 3.3, Maven, Spring Security, Nimbus JOSE JWT, Azure Identity, Spring `RestClient`, JUnit 5, MockWebServer, Vue 3, TypeScript, Pinia, Vitest.

## Global Constraints

- Do not use the archived Java Bot Framework SDK.
- `/api/messages` authentication cannot be disabled in any runnable profile.
- Validate signature, issuer, audience, lifetime, signed `serviceUrl`, and `directline` key endorsement.
- Send Connector tokens only to an HTTPS host on the configured allowlist.
- Do not log Activity text, bearer tokens, Direct Line tokens, or credentials.
- Container Apps replicas: minimum `0`, maximum `1`.
- Warm-up is best-effort and must not block or fail application login.
- Preserve all existing uncommitted changes in `expense-tracker-api` and the unrelated `.gitignore` change in `expense-tracker-web`.

---

### Task 1: Scaffold the Independent Chatbot Service

**Files:**
- Create repository: `/home/user/Documents/vscode-workspace/expense-tracker-chatbot`
- Create: `pom.xml`, `mvnw`, `mvnw.cmd`, `.mvn/wrapper/*`, `.gitignore`
- Create: `src/main/java/com/example/chatbot/ChatbotApplication.java`
- Create: `src/main/resources/application.properties`
- Test: `src/test/java/com/example/chatbot/ChatbotApplicationTest.java`

**Interfaces:**
- Produces: a standalone Spring Boot application on port `8080` with configuration prefix `bot.connector`.

- [ ] **Step 1: Generate the project**

Use Spring Initializr with Java 17, Maven, Spring Boot 3.3.0, and dependencies `web,security,validation,actuator`:

```bash
curl -fsSL 'https://start.spring.io/starter.zip?type=maven-project&language=java&bootVersion=3.3.0&baseDir=expense-tracker-chatbot&groupId=com.example&artifactId=expense-tracker-chatbot&name=expense-tracker-chatbot&packageName=com.example.chatbot&packaging=jar&javaVersion=17&dependencies=web,security,validation,actuator' -o /tmp/expense-tracker-chatbot.zip
unzip /tmp/expense-tracker-chatbot.zip -d /home/user/Documents/vscode-workspace
cd /home/user/Documents/vscode-workspace/expense-tracker-chatbot
git init -b main
```

- [ ] **Step 2: Add required dependencies**

Add `com.azure:azure-identity:1.15.4`, `com.nimbusds:nimbus-jose-jwt`, and test-scoped `com.squareup.okhttp3:mockwebserver` to `pom.xml`. Use Spring Boot dependency management where a version is supplied; pin only Azure Identity and MockWebServer.

- [ ] **Step 3: Write and run the context test**

```java
@SpringBootTest(properties = {
    "bot.connector.app-id=test-app-id",
    "bot.connector.managed-identity-client-id=test-managed-id"
})
class ChatbotApplicationTest {
    @Test void contextLoads() {}
}
```

Run `./mvnw test`. Expected: one passing test.

- [ ] **Step 4: Commit**

```bash
git add .
git commit -m "chore: scaffold chatbot service"
```

---

### Task 2: Model Activities and Fixed Replies

**Files:**
- Create: `src/main/java/com/example/chatbot/activity/BotActivity.java`
- Create: `src/main/java/com/example/chatbot/activity/ChannelAccount.java`
- Create: `src/main/java/com/example/chatbot/activity/ConversationAccount.java`
- Create: `src/main/java/com/example/chatbot/activity/BotReplyFactory.java`
- Test: `src/test/java/com/example/chatbot/activity/BotReplyFactoryTest.java`

**Interfaces:**
- Produces: `BotActivity createReply(BotActivity incoming)` returning a message with swapped sender/recipient, matching conversation, `replyToId`, and text `Gastos Chatbot is online.`

- [ ] **Step 1: Write the failing reply test**

Construct an incoming `message` Activity and assert:

```java
BotActivity reply = factory.createReply(incoming);
assertThat(reply.type()).isEqualTo("message");
assertThat(reply.text()).isEqualTo("Gastos Chatbot is online.");
assertThat(reply.from()).isEqualTo(incoming.recipient());
assertThat(reply.recipient()).isEqualTo(incoming.from());
assertThat(reply.conversation()).isEqualTo(incoming.conversation());
assertThat(reply.replyToId()).isEqualTo(incoming.id());
```

- [ ] **Step 2: Verify RED**

Run `./mvnw -Dtest=BotReplyFactoryTest test`. Expected: compilation failure because the types do not exist.

- [ ] **Step 3: Implement immutable records**

Use Jackson-annotated Java records with `@JsonIgnoreProperties(ignoreUnknown = true)`. `BotActivity` contains `type`, `id`, `serviceUrl`, `channelId`, `from`, `recipient`, `conversation`, `text`, and `replyToId`. Validate required routing fields in a compact constructor or service method.

- [ ] **Step 4: Verify GREEN and commit**

Run `./mvnw -Dtest=BotReplyFactoryTest test`, then commit as `feat: model bot activities`.

---

### Task 3: Validate Connector JWTs Completely

**Files:**
- Create: `src/main/java/com/example/chatbot/config/BotConnectorProperties.java`
- Create: `src/main/java/com/example/chatbot/security/BotConnectorKey.java`
- Create: `src/main/java/com/example/chatbot/security/BotConnectorKeyProvider.java`
- Create: `src/main/java/com/example/chatbot/security/BotConnectorJwtValidator.java`
- Create: `src/main/java/com/example/chatbot/security/BotAuthenticationFilter.java`
- Create: `src/main/java/com/example/chatbot/security/SecurityConfig.java`
- Test: `src/test/java/com/example/chatbot/security/BotConnectorJwtValidatorTest.java`
- Test: `src/test/java/com/example/chatbot/security/BotAuthenticationFilterTest.java`

**Interfaces:**
- Consumes: raw bearer token plus parsed `BotActivity`.
- Produces: `void validate(String token, BotActivity activity)` or throws a typed authentication exception.

- [ ] **Step 1: Write JWT rejection/acceptance tests**

Generate an RSA key pair in the test and sign tokens. Cover valid token plus invalid signature, issuer, audience, expired/not-yet-valid token, mismatched `serviceUrl`, missing `directline` endorsement, HTTP service URL, and disallowed host.

- [ ] **Step 2: Verify RED**

Run `./mvnw -Dtest=BotConnectorJwtValidatorTest test`. Expected: compilation failure for missing validator.

- [ ] **Step 3: Implement key discovery and validation**

Fetch OpenID metadata from the fixed HTTPS URL, then its `jwks_uri`. Parse RSA keys and their `endorsements` array with Jackson, cache for at most 24 hours, and refresh once for an unknown `kid`. Use Nimbus `SignedJWT` and `RSASSAVerifier`; require `RS256`, exact issuer/audience/service URL, five-minute maximum skew, `directline` endorsement, HTTPS, and an exact/lowercase host match from `allowed-service-hosts`.

- [ ] **Step 4: Implement request authentication**

Use a `OncePerRequestFilter` only for `/api/messages`. Read the request body through a reusable-body request wrapper, deserialize the Activity once, validate JWT and Activity together, and make the Activity available as a request attribute. Configure `/api/messages` through this filter, `/internal/warmup` through a constant-time shared-key filter, and deny other requests including Actuator paths.

- [ ] **Step 5: Verify and commit**

Run `./mvnw -Dtest=BotConnectorJwtValidatorTest,BotAuthenticationFilterTest test`, then `./mvnw test`. Commit as `feat: authenticate bot connector activities`.

---

### Task 4: Authenticate and Send Connector Replies

**Files:**
- Create: `src/main/java/com/example/chatbot/connector/ConnectorTokenProvider.java`
- Create: `src/main/java/com/example/chatbot/connector/ManagedIdentityConnectorTokenProvider.java`
- Create: `src/main/java/com/example/chatbot/connector/BotConnectorClient.java`
- Create: `src/main/java/com/example/chatbot/connector/ServiceUrlPolicy.java`
- Test: `src/test/java/com/example/chatbot/connector/BotConnectorClientTest.java`
- Test: `src/test/java/com/example/chatbot/connector/ServiceUrlPolicyTest.java`

**Interfaces:**
- Produces: `void reply(BotActivity incoming, BotActivity reply)`.
- Token interface: `String getToken()`; production uses `ManagedIdentityCredential` and scope `https://api.botframework.com/.default`.

- [ ] **Step 1: Write failing policy and client tests**

With MockWebServer, assert URL-encoded conversation/activity path segments, `Authorization: Bearer connector-token`, JSON reply body, and timeout/status mapping. Assert rejection of HTTP, user-info, non-default ports, suffix-confusion hosts, and hosts outside the allowlist.

- [ ] **Step 2: Verify RED**

Run `./mvnw -Dtest=ServiceUrlPolicyTest,BotConnectorClientTest test`. Expected: compilation failure.

- [ ] **Step 3: Implement minimal production code**

Build `ManagedIdentityCredential` with `.clientId(properties.managedIdentityClientId())`. Request a token with `new TokenRequestContext().addScopes(properties.scope())`. Build the reply URI from the already-validated base URI and encoded path segments; use `RestClient` with configured 5-second connect and 15-second read timeouts.

- [ ] **Step 4: Verify and commit**

Run the focused tests and `./mvnw test`. Commit as `feat: send authenticated connector replies`.

---

### Task 5: Expose the Bot Message Endpoint and Container

**Files:**
- Create: `src/main/java/com/example/chatbot/message/BotActivityService.java`
- Create: `src/main/java/com/example/chatbot/message/BotMessageController.java`
- Create: `src/main/java/com/example/chatbot/error/ApiExceptionHandler.java`
- Test: `src/test/java/com/example/chatbot/message/BotMessageControllerTest.java`
- Create: `Dockerfile`, `compose.yaml`, `.env.sample`, `README.md`
- Modify: `src/main/resources/application.properties`

**Interfaces:**
- Produces: authenticated `POST /api/messages` and key-protected `GET /internal/warmup`.

- [ ] **Step 1: Write failing MVC tests**

Assert unsigned request is rejected; malformed Activity returns `400`; authenticated `message` invokes one Connector reply; authenticated unsupported type returns success without a reply; Connector timeout maps to `503`; and `/internal/warmup` rejects missing/incorrect keys while returning only `{ "status": "ready" }` for the configured key.

- [ ] **Step 2: Verify RED**

Run `./mvnw -Dtest=BotMessageControllerTest test`. Expected: missing controller failures.

- [ ] **Step 3: Implement endpoint and safe errors**

Return `202 Accepted` after a successful reply or acknowledged unsupported Activity. Log correlation ID, type, channel, SHA-256 conversation hash prefix, latency, and outcome only.

- [ ] **Step 4: Add runtime packaging**

Use a multi-stage Eclipse Temurin 17 Dockerfile, non-root runtime user, port 8080, layered jar, read-only-friendly temporary directory, and a TCP Container Apps health probe. Deny public Actuator access. Document all environment variables and local test commands.

- [ ] **Step 5: Verify and commit**

Run `./mvnw test`, `./mvnw -DskipTests package`, `docker build -t expense-tracker-chatbot:local .`, and a secret scan. Commit as `feat: expose chatbot message endpoint`.

---

### Task 6: Add Authenticated API Warm-Up

**Files:**
- Create: `expense-tracker-api/src/main/java/com/example/expensetracker/dto/BotWarmupResponseDto.java`
- Create: `expense-tracker-api/src/main/java/com/example/expensetracker/service/BotWarmupService.java`
- Create: `expense-tracker-api/src/main/java/com/example/expensetracker/controller/BotWarmupController.java`
- Test: `expense-tracker-api/src/test/java/com/example/expensetracker/service/BotWarmupServiceTest.java`
- Test: `expense-tracker-api/src/test/java/com/example/expensetracker/controller/BotWarmupControllerTest.java`
- Modify: `.env.sample`, `README.md`, `docker-compose.yml`, `src/main/resources/application.properties`

**Interfaces:**
- Produces: authenticated `POST /api/bot/warmup` returning `{ status: "ready" | "delayed" }` with `Cache-Control: no-store`; downstream calls include `X-Chatbot-Warmup-Key`.

- [ ] **Step 1: Preserve the existing API baseline**

Run `git diff`, `./mvnw test`, and confirm the current uncommitted Direct Line files belong to the chatbot work. Commit only those verified existing changes as `feat: add direct line token broker` before starting warm-up edits; do not discard or overwrite them.

- [ ] **Step 2: Write failing service/controller tests**

Assert authentication is required, one downstream liveness request per user per five-minute window, different users can warm independently, downstream 2xx maps to `ready`, and timeout/non-2xx maps to `delayed` without throwing into login behavior.

- [ ] **Step 3: Implement cooldown and bounded call**

Use `ConcurrentHashMap<String, Instant>` with injected `Clock`, remove stale entries opportunistically, and reserve the cooldown before the downstream call to collapse concurrent requests. Configure `chatbot.warmup.url`, `chatbot.warmup.key=${CHATBOT_WARMUP_KEY}`, 2-second connect timeout, and 10-second read timeout. Send the key only in `X-Chatbot-Warmup-Key`, never log it, and hash user IDs before cooldown-map keys or logs.

- [ ] **Step 4: Verify and commit**

Run focused tests and `./mvnw test`. Commit as `feat: add chatbot warmup endpoint`.

---

### Task 7: Warm the Chatbot After Vue Authentication

**Files:**
- Modify: `expense-tracker-web/src/api/bot.ts`
- Create: `expense-tracker-web/src/stores/chatbotWarmupStore.ts`
- Create: `expense-tracker-web/src/stores/chatbotWarmupStore.test.ts`
- Modify: `expense-tracker-web/src/App.vue`
- Modify: `expense-tracker-web/src/App.test.ts`
- Modify: `expense-tracker-web/src/components/ChatBotWidget.vue`
- Modify: `expense-tracker-web/src/components/ChatBotWidget.test.ts`

**Interfaces:**
- Produces: `warmupChatbot(): Promise<{status: 'ready' | 'delayed'}>` and store state `'idle' | 'warming' | 'ready' | 'delayed'`.

- [ ] **Step 1: Create an isolated frontend worktree and verify baseline**

Preserve the main checkout's unrelated `.gitignore` change. Create `.worktrees/chatbot-warmup` on `feature/chatbot-warmup`, install dependencies, and run `npm test -- --run`.

- [ ] **Step 2: Write failing API/store tests**

Assert `startWarmup()` deduplicates concurrent/page-lifecycle calls, transitions `idle → warming → ready|delayed`, treats rejected requests as `delayed`, and `reset()` returns to `idle`.

- [ ] **Step 3: Implement API and store**

Use the existing `apiFetch` helper for `POST /api/bot/warmup`. Keep state only in Pinia memory; do not use browser storage.

- [ ] **Step 4: Write failing integration/widget tests**

Assert authenticated `App` invokes warm-up once, unauthenticated `App` does not, widget loading copy is `Gastos Chatbot is waking up. This may take a moment…` while warming/delayed, and existing Direct Line initialization/retry tests remain valid.

- [ ] **Step 5: Implement UI integration**

Watch the authenticated state in `App.vue` with `{ immediate: true }`; start warm-up without awaiting it. Reset the warm-up store when authentication becomes false. The widget reads the store and changes startup copy without blocking its existing token/Web Chat initialization.

- [ ] **Step 6: Verify and commit**

Run `npm test -- --run`, `npm run typecheck`, `npm run build`, secret scan, and `git diff --check`. Commit as `feat: warm chatbot after login`.

---

### Task 8: Deploy and Run the Azure Smoke Test

**Files:**
- Modify: `expense-tracker-chatbot/README.md`

**Interfaces:**
- Produces: a deployed Container App and Azure Bot messaging endpoint.

- [ ] **Step 1: Build and publish the image**

Build an immutable image tag from the chatbot commit and push it to the existing registry. Do not use `latest` for the deployed revision.

- [ ] **Step 2: Create/configure Container App**

Configure external HTTPS ingress on 8080, min replicas 0, max replicas 1, a TCP health probe, attach the bot's user-assigned identity, and set `AZURE_BOT_APP_ID`, `AZURE_BOT_MANAGED_IDENTITY_CLIENT_ID`, and the Direct Line host allowlist. Generate a random 256-bit warm-up key, store it in Key Vault, and reference it as `CHATBOT_WARMUP_KEY` from both Container Apps.

- [ ] **Step 3: Configure Azure Bot**

Set messaging endpoint to `https://<chatbot-fqdn>/api/messages`. Retain both Direct Line sites during testing.

- [ ] **Step 4: Verify security and message flow**

Confirm unsigned `/api/messages` is rejected. Log into Vue, confirm warm-up scales the revision from zero, open Gastos Chatbot, send a message, and observe `Gastos Chatbot is online.` Check logs for correlation/outcome metadata and absence of text/tokens.

- [ ] **Step 5: Final repository verification**

Run chatbot/API Maven tests and frontend tests/typecheck/build from clean checkouts. Record Azure resource names and operational commands in `expense-tracker-chatbot/README.md` without secrets.
