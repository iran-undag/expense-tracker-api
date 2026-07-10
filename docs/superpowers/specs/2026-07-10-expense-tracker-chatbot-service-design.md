# Expense Tracker Chatbot Service Design

## Purpose

Create `expense-tracker-chatbot` as an independent Java 17/Spring Boot service. The first release proves the complete authenticated message path from the Vue application through Direct Line and Azure Bot Service to the chatbot and back. It does not call a language model or expense-data tools yet.

The service integrates with Azure Bot Service through the Bot Connector REST protocol. It must not depend on the archived Java Bot Framework SDK.

## Scope

This implementation cycle spans three repositories:

- New `expense-tracker-chatbot`: receive, authenticate, and reply to Bot Connector activities.
- Existing `expense-tracker-api`: provide an authenticated, rate-limited chatbot warm-up endpoint.
- Existing `expense-tracker-web`: initiate best-effort warm-up after login and show startup state in the widget.

Model integration, expense tools, Adaptive Cards, charts, conversation persistence, and proactive messages are deferred.

## Repository And Runtime

`expense-tracker-chatbot` is an independent Git repository and Azure Container App with its own deployment lifecycle. It uses Java 17, Spring Boot, Maven, Spring Security, Spring Web, Spring Boot Actuator, and Azure Identity.

Expected structure:

```text
expense-tracker-chatbot/
├── src/main/java/.../controller/BotMessageController.java
├── src/main/java/.../security/BotConnectorJwtValidator.java
├── src/main/java/.../service/BotActivityService.java
├── src/main/java/.../service/BotConnectorClient.java
├── src/main/java/.../model/Activity.java
├── src/main/resources/application.properties
├── src/test/...
├── Dockerfile
├── compose.yaml
├── .env.sample
└── README.md
```

Classes may be divided further when doing so creates a clear protocol, security, or configuration boundary. The service models only the Bot Activity fields required by the first release and tolerates unknown JSON fields for protocol compatibility.

## Message Flow

1. Vue Web Chat sends a user Activity through Direct Line.
2. Azure Bot Service posts the Activity and a Connector bearer JWT to `POST /api/messages`.
3. Spring Security and the custom Connector validator authenticate the request.
4. `BotMessageController` validates the supported Activity structure.
5. `BotActivityService` handles supported activity types. A text message produces a fixed Gastos Chatbot greeting or echo response.
6. `BotConnectorClient` acquires an outbound token through the configured user-assigned managed identity.
7. The client posts the reply to the incoming Activity's authenticated `serviceUrl` using the Bot Connector reply endpoint.
8. Azure Bot Service returns the reply through Direct Line to Vue Web Chat.

The first release supports text `message` activities. Other valid activity types are acknowledged without a reply unless a minimal conversation-update greeting is explicitly covered by tests.

## Inbound Connector Authentication

Authentication cannot be disabled in any runnable profile. Every `/api/messages` request must satisfy all of these requirements:

- The token is supplied with the HTTP Bearer scheme.
- Its RS256 signature validates against keys discovered from `https://login.botframework.com/v1/.well-known/openidconfiguration`.
- The issuer is exactly `https://api.botframework.com`.
- The audience is the configured bot Microsoft App ID, initially `be00de44-8076-4759-b889-3dd6d5b5c7f9`.
- `nbf` and `exp` are valid with no more than five minutes of clock skew.
- The signed `serviceUrl` claim exactly matches the root `serviceUrl` in the Activity body.
- The signing key carries the required endorsement for the Activity channel, initially `directline`.
- The Activity service URL uses HTTPS and its host matches a narrow configurable Bot Connector host allowlist.

OpenID metadata and signing keys are cached and refreshed at least every 24 hours, while an unknown key ID triggers a bounded refresh. Authentication failures return `401` or `403` without revealing validation internals.

Request bodies have a conservative size limit. The service does not log message text, bearer tokens, full Activities, or credentials.

## Outbound Connector Authentication

The Container App attaches the same user-assigned managed identity referenced by the Azure Bot resource. `ManagedIdentityCredential` is configured with that identity's client ID and requests `https://api.botframework.com/.default`.

Azure Identity manages token caching and refresh. The outbound token is sent only to a service URL that passed the signed-claim and host checks. Connector calls use bounded connection and response timeouts.

The reply uses:

```text
POST {serviceUrl}/v3/conversations/{conversationId}/activities/{activityId}
Authorization: Bearer <managed-identity access token>
```

Conversation and activity path values are encoded as individual URL path segments.

## Warm-Up And Scale-To-Zero

The chatbot Container App uses minimum replicas `0` and maximum replicas `1`. Warm-up is best-effort and must never block or fail application login.

After authenticated session restoration or login:

1. Vue calls authenticated `POST /api/bot/warmup` on `expense-tracker-api`.
2. The API applies a per-user five-minute cooldown.
3. The API starts a bounded call to the chatbot's key-protected `GET /internal/warmup` endpoint.
4. Vue tracks `idle`, `warming`, `ready`, or `delayed` in memory only.

The API, rather than the browser, calls the chatbot warm-up endpoint. It supplies a random 256-bit `X-Chatbot-Warmup-Key` value stored in Key Vault and injected into both server-side services. The chatbot compares the supplied value in constant time and rejects missing or invalid values. Repeated accepted calls within the cooldown do not produce another downstream wake request.

While warm-up is pending, the widget displays:

> Gastos Chatbot is waking up. This may take a moment…

If the widget opens before warm-up completes, it remains open and continues waiting. A timeout changes the state to `delayed` and allows recovery or retry; it does not permanently disable chat. Page refresh and logout clear the client-side state.

The warm-up response exposes only readiness status and no environment, configuration, or dependency details. Actuator endpoints are not exposed through public ingress; Container Apps uses a TCP health probe on port 8080.

## Error Handling And Observability

- Missing or invalid Connector authentication: `401` or `403`.
- Invalid Activity JSON or missing required routing fields: `400`.
- Unsupported authenticated Activity: acknowledge successfully without a reply.
- Connector authentication, timeout, or reply failure: safe `502` or `503` response.
- Warm-up failure: report delayed state to the authenticated caller without affecting login.

Logs include a correlation ID, activity type, channel ID, hashed conversation identifier, duration, and outcome. They exclude prompt text, response text, Activities, identity tokens, Direct Line tokens, and credentials.

Actuator endpoints are not publicly exposed.

## Configuration

The chatbot service accepts configuration equivalent to:

```properties
bot.connector.app-id=${AZURE_BOT_APP_ID}
bot.connector.managed-identity-client-id=${AZURE_BOT_MANAGED_IDENTITY_CLIENT_ID}
bot.connector.openid-metadata-url=https://login.botframework.com/v1/.well-known/openidconfiguration
bot.connector.scope=https://api.botframework.com/.default
bot.connector.allowed-service-hosts=${AZURE_BOT_ALLOWED_SERVICE_HOSTS:directline.botframework.com}
bot.connector.connect-timeout=5s
bot.connector.read-timeout=15s
```

The API receives the chatbot warm-up URL, warm-up key, and timeout through server-side configuration. The shared warm-up key is stored in Key Vault and never appears in source, Vue environment variables, browser storage, or logs. No bot identity secret or Connector token is stored by the application.

## Azure Deployment

Deploy `expense-tracker-chatbot` as a separate Azure Container App with:

- External HTTPS ingress.
- Target port `8080`.
- Minimum replicas `0`.
- Maximum replicas `1` for the first release.
- The Azure Bot resource's user-assigned managed identity attached.
- Non-secret bot App ID, identity client ID, host allowlist, and timeout settings as environment variables.
- `CHATBOT_WARMUP_KEY` as a Key Vault secret reference in both the chatbot and API Container Apps.

After deployment, configure the Azure Bot messaging endpoint as:

```text
https://<chatbot-container-app-domain>/api/messages
```

Direct Line sites remain unchanged during this cycle. The custom `expense-tracker-web` site remains the application's active site, while Default Site is retained until end-to-end testing is complete.

## Testing

### Chatbot service

- Generated test RSA keys verify valid Connector JWT acceptance.
- Reject missing Bearer authentication, invalid signature, issuer, audience, time window, service URL, and channel endorsement.
- Validate malformed and oversized Activities.
- Verify supported messages create the expected reply Activity.
- Verify unsupported valid Activities are acknowledged without outbound calls.
- Use a local mock HTTP server for Connector replies, timeouts, and safe error mapping.
- Verify access tokens and Activity text never appear in captured logs.
- Verify `/internal/warmup` rejects missing/incorrect keys, accepts the configured key, and exposes no details.

### API

- Reject unauthenticated warm-up requests.
- Accept one warm-up per user per five-minute window.
- Avoid duplicate downstream calls during the cooldown.
- Bound downstream time and map ready/delayed responses safely.
- Confirm warm-up failure does not affect unrelated authenticated API behavior.

### Web

- Trigger warm-up after login/session restoration once per page lifecycle.
- Render warming, ready, and delayed states.
- Keep login successful when warm-up fails.
- Clear warm-up state on logout/page refresh.
- Preserve existing Direct Line token and conversation behavior.

### Azure smoke test

- The Container App scales from zero after the API performs an authenticated, key-protected warm-up.
- An unsigned request to `/api/messages` is rejected.
- A Vue Direct Line message reaches the chatbot.
- The attached managed identity obtains a Connector token.
- `Gastos Chatbot is online` is returned to Vue Web Chat.

## Success Criteria

The implementation is complete when login starts a safe best-effort warm-up, the widget communicates cold-start state, valid Direct Line messages receive a fixed Gastos Chatbot response through the Spring service, invalid Connector requests are rejected, and no SDK, model credential, bot token, or user message content is exposed or logged.
