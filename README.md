# Expense Tracker API

Expense Tracker API is the Spring Boot backend for a multi-user personal finance software-as-a-service application. It provides authenticated, user-scoped APIs for recording and filtering expenses, managing categories and monthly budgets, generating recurring expenses, importing and exporting CSV data, and producing the summaries and trends displayed by the web dashboard.

The service also supports the application's assisted expense-capture features that implements Azure AI capabilities. It can send receipt images to a configurable local (Ollama/OpenVino) or Azure-based processor (Azure AI Document Intelligence), issue short-lived Azure Speech and Direct Line tokens to the browser, and expose secured tools used by the expense chatbot (Azure Bot + OpenAI). The API can run locally with H2 and local AI providers or use SQL Server and Azure services in production-oriented environments.

## Features

- User authentication and secure session management (JWT)
- CRUD (Create, Read, Update, Delete) operations for expenses, categories, budgets, and recurring expenses
- User-managed expense categories with default seed categories, colors, active/inactive state, and soft delete
- Expense search and filtering by date range, category, amount range, and description query
- Date-based expense queries, monthly summaries, category breakdowns, and spending trends
- Monthly budget management with budget-vs-actual summaries
- Recurring expense rules with generate-on-read materialization and duplicate-safe occurrence tracking
- CSV import/export for expense records
- Short-lived Azure Speech token endpoint for browser voice expense capture
- Support for multiple users (User-scoped data access)
- RESTful endpoints for easy frontend integration
- Upload receipt images for AI OCR extraction.
- Request correlation with `X-Correlation-Id` for cross-service log lookup

## Technology Stack

- **Language**: Java 17
- **Framework**: Spring Boot
- **Database**: H2 for development/tests; SQL Server for production-like local runs and Azure SQL Database
- **Build Tool**: Maven or the included maven wrapper
- **Authentication**: Spring Security + JWT

## User Identity

Protected expense endpoints derive ownership from the authenticated token. For JWT principals, the API uses the first non-empty claim in this order:

```text
oid -> userId -> sub
```

`oid` is preferred for Microsoft Entra External ID because it is the stable object identifier for the user in the tenant. Local/mock tokens can continue using `userId` or `sub`.

## Getting Started

### Prerequisites

- **Java 17 or newer** (Spring Boot 3.3.0+)
- Maven
- Docker & Docker Compose (for production-like local runs)
- **AI provider** (optional, choose one for receipt processing):
  - Ollama server (local, default)
  - OpenVINO Vision API server
  - Expense Tracker receipt processor function backed by Azure Document Intelligence
- **Azure Speech resource** (optional, only for frontend voice expense capture)

### Setup (profile=dev)

1. **Clone the repository:**
   ```sh
   git clone https://github.com/iran-undag/expense-tracker-api.git
   cd expense-tracker-api
   ```

2. **Configure Environment:**
   ```sh
   cp .env.sample .env
   ```
   Edit `.env` and set your AI provider and other configuration variables.

   AI provider calls use configurable timeouts:
   ```env
   AI_PROVIDER_CONNECT_TIMEOUT=5s
   AI_PROVIDER_READ_TIMEOUT=30s
   ```
   These apply to Ollama, OpenVINO, and Azure receipt processing. Increase the read timeout if your receipt processor regularly takes longer than 30 seconds.

3. **Select an AI Provider:**

   **Ollama (Local, Recommended):**
   ```env
   AI_PROVIDER=ollama
   OLLAMA_BASE_URL=http://localhost:11434
   ```
   Ensure Ollama is running on your machine.

   **OpenVINO (Local):**
   ```env
   AI_PROVIDER=openvino
   OPENVINO_BASE_URL=http://localhost:8001
   OPENVINO_CHAT_PATH=/api/vision/chat
   ```
   Ensure OpenVINO Vision API is running on your machine.

   **Azure Receipt Processor Function:**
   ```env
   AI_PROVIDER=azure
   RECEIPT_PROCESSOR_URL=http://localhost:7071/api/process-receipt
   RECEIPT_PROCESSOR_FUNCTION_KEY=
   ```
   Start `expense-tracker-receipt` locally or point `RECEIPT_PROCESSOR_URL` to the deployed Azure Function.

   **Azure Speech Voice Input:**
   ```env
   AZURE_SPEECH_KEY=replace-me
   AZURE_SPEECH_REGION=southeastasia
   AZURE_SPEECH_TOKEN_URL=
   ```
   `AZURE_SPEECH_KEY` and `AZURE_SPEECH_REGION` enable `POST /api/speech/token`. `AZURE_SPEECH_TOKEN_URL` is optional; leave it blank to use `https://<AZURE_SPEECH_REGION>.api.cognitive.microsoft.com/sts/v1.0/issueToken`.

4. **Build the project:**
   ```sh
   ./mvnw clean install
   ```

5. **Start the development server:**

   Local development uses the `dev` profile (H2). To run locally with H2 (already inside run-dev.sh):

   ```bash
   ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev -Dspring-boot.run.jvmArguments="-Dspring.profiles.active=dev"
   ```

   **Linux/macOS:**
   ```sh
   ./run-dev.sh
   ```

   **Windows:**
   ```cmd
   run-dev.bat
   ```

The API will be available at `http://localhost:8081`.

## Demo sessions

Production runs one API against two SQL Server databases. Authenticated personal requests use the
primary database; opaque demo tokens select a separate demo database. The databases must not share
the same JDBC URL. Demo rows therefore cannot appear in personal queries even if an application
scope check regresses.

Required production variables:

```env
SPRING_DATASOURCE_URL=jdbc:sqlserver://primary-host:1433;databaseName=expensedb;encrypt=true
SPRING_DATASOURCE_USERNAME=
SPRING_DATASOURCE_PASSWORD=
DEMO_DATASOURCE_URL=jdbc:sqlserver://demo-host:1433;databaseName=expensedb_demo;encrypt=true
DEMO_DATASOURCE_USERNAME=
DEMO_DATASOURCE_PASSWORD=
DEMO_DATASOURCE_MAX_POOL_SIZE=3
DEMO_TOKEN_HMAC_KEY=replace-with-at-least-32-random-bytes
```

See [.env.example](.env.example) for connection timeout/retry settings. Keep
`DEMO_TOKEN_HMAC_KEY` server-side; changing it invalidates current demo access and resume tokens.

The fixed demo limits are two concurrent sessions, 20 write/paid actions per session, and a
six-hour session lifetime. Expired session data is deleted on the next demo login; there is no
background cleanup requirement. When no sessions are active, the protected seed is refreshed for
the current month: 85 expenses across six months, 16 categories, five budgets, and three recurring
rules. Seed rows are readable but cannot be changed by demo users.

Manual smoke test:

```bash
curl -i -c /tmp/expense-demo-cookie.txt -X POST http://localhost:8081/api/demo/sessions
# Copy accessToken from the response, then:
curl -i http://localhost:8081/api/expenses \
  -H "Authorization: Bearer dmo_replace_me"
curl -i -b /tmp/expense-demo-cookie.txt -X POST http://localhost:8081/api/demo/sessions
curl -i -X DELETE http://localhost:8081/api/demo/sessions/current \
  -H "Authorization: Bearer dmo_replace_me"
curl -s http://localhost:8081/actuator/prometheus | grep '^demo_'
```

The complete behavior and threat-boundary rationale live in the web repository's
[limited demo-session design](https://github.com/iran-undag/expense-tracker-web/blob/main/docs/superpowers/specs/2026-08-07-demo-session-design.md).

## Testing and coverage

Run the test suite without coverage instrumentation:

```bash
./mvnw test
```

Generate the JaCoCo XML report for SonarQube:

```bash
./mvnw clean verify -Pcoverage
```

The report is written to `target/site/jacoco/jacoco.xml`. SonarScanner for
Maven detects this standard location automatically.

To generate coverage and run SonarQube analysis in one Maven invocation:

```bash
./mvnw clean verify -Pcoverage \
  org.sonarsource.scanner.maven:sonar-maven-plugin:5.5.0.6356:sonar \
  -Dsonar.projectKey=expense-tracker-api
```

Provide `SONAR_HOST_URL` and `SONAR_TOKEN` through the environment. Coverage
thresholds are managed by the SonarQube quality gate; this Maven profile only
generates the report.

## API Documentation

Swagger/OpenAPI documentation is available at `http://localhost:8081/swagger-ui/index.html`.

### Testing with Swagger

> **TIP:** To test the secured endpoints in Swagger:
> 1. Launch the application and go to the Swagger UI (`/swagger-ui/index.html`).
> 2. Call `POST /api/auth/login` with a JSON payload like `{"userId": "your_test_user"}`.
> 3. Copy the generated JWT token from the response.
> 4. Click the "Authorize" button at the top of the Swagger page and enter your token (no 'Bearer ' prefix needed in the input field).
> 5. You can now securely test the protected `/api/expenses` endpoints!

#### Example Endpoints

- `POST /api/auth/login` — Authenticate and obtain a mock JWT token
- `GET /api/expenses` — List expenses for the authenticated user, with optional filters
- `POST /api/expenses` — Add a new expense
- `PUT /api/expenses/{id}` — Update an existing expense
- `DELETE /api/expenses/{id}` — Delete an expense
- `GET /api/budgets?year=2026&month=6` — List budgets for a month
- `GET /api/budgets/summary?year=2026&month=6` — Compare budgeted and actual spending
- `GET /api/categories` — List active categories
- `GET /api/categories?includeInactive=true` — List all categories for management
- `GET /api/reports/monthly-summary?year=2026&month=6` — Monthly totals and averages
- `GET /api/reports/category-breakdown?fromDate=2026-06-01&toDate=2026-06-30` — Category analytics
- `GET /api/reports/spending-trend?year=2026&month=6&months=6` — Rolling trend data
- `GET /api/recurring-expenses` — List recurring expense rules and generate due expenses first
- `POST /api/recurring-expenses` — Create a recurring expense rule
- `GET /api/import-export/export?fromDate=2026-06-01&toDate=2026-06-30` — Download expense records as CSV
- `POST /api/import-export/import` — Import expense records from CSV
- `POST /api/speech/token` — Issue a short-lived Azure Speech token for browser voice input
- `POST /api/bot/direct-line/token` — Exchange the server-side Direct Line secret for a conversation-scoped browser token
- `POST /api/bot/warmup` — Best-effort authenticated wake-up for the scale-to-zero chatbot service

> For a detailed API reference, see the Swagger docs or consult the source code.

## Voice Input

The API supports the frontend Voice button through `POST /api/speech/token`.

- The API exchanges `AZURE_SPEECH_KEY` for a short-lived Azure Speech authorization token.
- The response includes the token, the configured speech region, and `expiresInSeconds`.
- The frontend uses the token directly with Azure Speech SDK microphone recognition.
- Raw microphone audio does not pass through this API.

Required environment:

```env
AZURE_SPEECH_KEY=replace-me
AZURE_SPEECH_REGION=southeastasia
```

Optional override:

```env
AZURE_SPEECH_TOKEN_URL=https://southeastasia.api.cognitive.microsoft.com/sts/v1.0/issueToken
```

If `AZURE_SPEECH_TOKEN_URL` is blank, the API builds the token endpoint from `AZURE_SPEECH_REGION`.

## Recurring Expenses

Recurring expenses are stored as rules in `recurring_expense`. The API uses generate-on-read rather than a background scheduler:

- Read endpoints call `RecurringExpenseService.generateDueExpenses(userId, LocalDate.now())`.
- Due rules create normal expense rows dated with each occurrence date.
- `recurring_expense_occurrence` records `recurring_expense_id + occurrence_date` so repeated reads do not create duplicates.
- Rules advance `nextRunDate` after generation and are deactivated after their `endDate` is passed.

This approach is friendly to free-tier or sleep-prone hosting because generation happens when the user opens or refreshes the app.

## Default Categories

The API lazily reconciles the default category set when a user lists categories. The defaults are Food, Groceries, Transport, Electricity, Water, Internet, Phone, Healthcare, Shopping, Travel, Entertainment, Mortgage, Rent, Insurance, Tuition, and Other.

Reconciliation is name-based and does not replace or reactivate an existing custom category with the same name. This lets existing users receive newly introduced defaults without duplicating categories or overwriting their choices.

## Import/Export

`/api/import-export/export` returns expense records as `text/csv` for a required date range.

CSV columns:

- `date`
- `description`
- `category`
- `amount`

`/api/import-export/import` accepts the same CSV format. Imported rows are appended as expenses. Invalid rows are reported in the response without rejecting the entire import file.

## Observability

Every HTTP request is associated with an `X-Correlation-Id`.

- If the caller sends `X-Correlation-Id`, the API reuses it.
- If the caller omits it, the API generates a UUID.
- The same value is returned in the response header.
- Logs include `correlationId=...`.
- OpenVINO and Azure receipt-processing calls propagate the same `X-Correlation-Id` header.

Example:

```bash
curl -i http://localhost:8081/actuator/health \
  -H "X-Correlation-Id: manual-test-123"
```

Search API logs for:

```text
correlationId=manual-test-123
```

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.

---

**Created by [iran-undag](https://github.com/iran-undag)**

## Docker (profile=prod)

Run the API with a SQL Server database using the Docker Compose plugin (creates a `db` service and the `app` service):

```bash
docker compose --env-file .env up --build -d
```

This composes the app with the `prod` Spring profile. Copy `.env.sample` to `.env` and edit environment values before starting.

**Key environment variables in `.env`:**
- `AI_PROVIDER` — Select `ollama`, `openvino`, or `azure`; `azure` calls the external receipt processor function (default: `ollama`)
- `OLLAMA_BASE_URL` — Ollama server URL (default: `http://host.docker.internal:11434`)
- `OPENVINO_BASE_URL` — OpenVINO server URL (default: `http://host.docker.internal:8001`)
- `RECEIPT_PROCESSOR_URL` — Receipt processor function endpoint for `AI_PROVIDER=azure`
- `RECEIPT_PROCESSOR_FUNCTION_KEY` — Function key for deployed receipt processor functions; leave blank for local function testing
- `AI_PROVIDER_CONNECT_TIMEOUT` — Connection timeout for Ollama, OpenVINO, and Azure receipt processing calls (default: `5s`)
- `AI_PROVIDER_READ_TIMEOUT` — Read timeout for Ollama, OpenVINO, and Azure receipt processing calls (default: `30s`)
- `AZURE_SPEECH_KEY` — Azure Speech resource key used server-side to mint short-lived browser tokens
- `AZURE_SPEECH_REGION` — Azure Speech resource region, for example `southeastasia`
- `AZURE_SPEECH_TOKEN_URL` — Optional Speech token endpoint override; defaults to `https://<AZURE_SPEECH_REGION>.api.cognitive.microsoft.com/sts/v1.0/issueToken`
- `AZURE_BOT_DIRECT_LINE_SECRET` — Direct Line channel secret used only by the authenticated server-side token broker
- `AZURE_BOT_DIRECT_LINE_TOKEN_URL` — Direct Line token generation endpoint; use the regional endpoint when the bot is regionalized
- `AZURE_BOT_DIRECT_LINE_TRUSTED_ORIGINS` — Comma-separated Web Chat origins embedded in generated tokens
- `CHATBOT_WARMUP_URL` — Server-to-server chatbot warm-up endpoint
- `CHATBOT_WARMUP_KEY` — Shared 256-bit warm-up key stored in Key Vault and never exposed to the browser
- `CHATBOT_WARMUP_COOLDOWN` — Per-user downstream warm-up cooldown, default `5m`
- `CHATBOT_WARMUP_CONNECT_TIMEOUT` — Chatbot wake-up connection timeout, default `2s`
- `CHATBOT_WARMUP_READ_TIMEOUT` — Chatbot wake-up response timeout, default `10s`
- `AUTH_ISSUER_URI` — JWT issuer expected by the API; this must exactly match the access token `iss`, for example `http://localhost:9000`
- `JWK_SET_URI` — Container-reachable JWK endpoint for verifying tokens, for example `http://host.docker.internal:9000/oauth2/jwks`
- `ALLOWED_ORIGIN_PATTERNS` — Browser origins allowed by CORS, for example `http://localhost:5173`
- `MSSQL_SA_PASSWORD` — Local SQL Server `sa` password used by the compose `db` service and API datasource

### Chatbot Integration

The API owns all browser-facing chatbot credentials and warm-up calls:

- `POST /api/bot/direct-line/token` requires the normal bearer token and exchanges the server-side Direct Line secret for a conversation-scoped token.
- Direct Line user IDs are opaque `dl_` identifiers backed by a short-lived user mapping; raw application user IDs are not sent to Direct Line.
- If the authenticated JWT contains `given_name`, the API sanitizes it, limits it to 50 Unicode code points, and includes it only as the optional Direct Line `user.name` used by the welcome message. The API does not infer a name from other claims.
- The optional first name is not returned as a new browser response field, persisted in the identity mapping, used for authorization, or written to logs; diagnostic serialization records only whether a name is present.
- `POST /api/bot/warmup` requires the normal bearer token and calls the chatbot's protected `/internal/warmup` endpoint.
- Warm-up calls are limited to one downstream request per user during the configured five-minute cooldown.
- Warm-up failures return `delayed` and do not fail login or other API behavior.
- `CHATBOT_WARMUP_KEY` is server-to-server only and must match the chatbot service value.

For local Docker Compose integration with the chatbot on host port 8082:

```env
CHATBOT_WARMUP_URL=http://host.docker.internal:8082/internal/warmup
CHATBOT_WARMUP_KEY=<same-local-value-used-by-expense-tracker-chatbot>
```

Store both the Direct Line secret and chatbot warm-up key in Key Vault and configure the API Container App through secret references. Configure the warm-up endpoint with the environment-specific chatbot URL:

```text
https://<chatbot-container-app-fqdn>/internal/warmup
```

The exact deployed resource names and URLs belong in the chatbot repository's `docs/AGENT_HANDOFF.md`, not in this portable setup guide.

The Direct Line site used by the web application is `expense-tracker-web`. `AZURE_BOT_DIRECT_LINE_TRUSTED_ORIGINS` must contain the exact HTTPS browser origin with no path or trailing slash.

The Azure Portal has been observed displaying or copying an incomplete Direct Line secret. A valid regenerated secret may contain two long segments separated by a period. If Direct Line returns `403 BadArgument: Invalid token or secret`, rotate one site key through the Azure management API, store the complete returned value in Key Vault, and create or restart the API Container App revision.

The token broker returns a conversation ID for server-side identity mapping, but a new Web Chat client should call `createDirectLine` with the token only. Passing that conversation ID makes Direct Line JS attempt to reconnect to a conversation that has not yet been started.

### Internal chatbot tools

The chatbot service calls `POST /internal/chat-tools/execute` with its own service bearer token. Browser and ordinary user tokens cannot use this endpoint. The API resolves the supplied Direct Line user/conversation pair through the unexpired server-side mapping before deriving the expense owner.

The eight supported query tools are monthly summary, category breakdown, spending trend, budget status, bounded expense lookup, recurring-expense status, category listing, and daily/weekly spending by period. Recurring status returns each rule's stored `nextRunDate`; it does not project a future schedule. Category listing defaults to active categories and can explicitly include inactive categories. Recurring and category results are capped at 100 records and report `totalCount` and `truncated` metadata.

`spending_by_period` supports inclusive ranges of at most 366 days, optional case-insensitive category filtering, zero-filled buckets, calendar days, and Monday-to-Sunday weeks with partial first and last weeks clipped to the requested range. `expense_lookup` exposes optional `minAmount`, `maxAmount`, `sortBy` (`DATE` or `AMOUNT`), and `sortDirection` (`ASC` or `DESC`) while retaining bounded pagination and deterministic tie-breakers.

Each expense-aware request generates due recurring expenses once for the mapped owner before reading data, matching the existing dashboard behavior. Both services strictly validate tool names, fields, enums, and bounds. The model cannot supply an application user ID, URL, API path, SQL, arbitrary sort property, or write operation. General personal-finance answers and unrelated-topic refusal are enforced by the chatbot service; this expense API remains the authorization and user-isolation boundary for expense data. Retrieved monetary values remain PHP amounts and the chatbot formats them with `₱` without conversion.

This expansion keeps the single `POST /internal/chat-tools/execute` endpoint and requires no frontend, Azure configuration, dependency, or database migration change. The chatbot remains a separate application in its own chatbot Container App; it is not part of the expense API Container App. For Azure rollout, deploy the expense API revision first and the chatbot revision second.

The chatbot service rejects messages over 1,000 Unicode code points, missing conversation identity, and requests beyond its six-message rolling per-conversation limit before calling Azure OpenAI or this API. These controls add no expense API endpoint or configuration. The expense API continues to enforce its own internal tool request-size, request-rate, app-role, mapping, and ownership checks independently.

Production service authentication uses `CHATBOT_SERVICE_ISSUER`, `CHATBOT_SERVICE_AUDIENCE`, `CHATBOT_SERVICE_JWK_SET_URI`, and the `CHATBOT_TOOL_EXECUTOR` app role. For local-only RSA testing, generate ignored keys with:

```bash
./scripts/generate-local-chatbot-keys.sh
```

Then set `CHATBOT_SERVICE_PUBLIC_KEY_LOCATION=file:.local/chatbot-keys/public.pem` with the local issuer and audience. Never enable the local public-key configuration in production.

The chatbot repository no longer provides a `local-chatbot` runtime profile. This API's local public-key configuration remains available for API-side authentication tests, while the chatbot suite uses test-only gateways and controlled token providers. The automated suites are the authoritative local verification of tool selection, ownership, validation, and orchestration; full end-to-end chat validation runs through isolated Azure revisions.

For Azure, deploy the API revision before the chatbot revision. The API must validate the chatbot managed identity's Entra token and require the `CHATBOT_TOOL_EXECUTOR` app role. A browser token or ordinary user token must not authorize `/internal/chat-tools/**`.

For Microsoft Entra External ID, set `AUTH_ISSUER_URI` and `JWK_SET_URI` from the tenant's OpenID Connect metadata. The frontend must request the API scope so calls include a bearer access token intended for this API.

`ALLOWED_ORIGIN_PATTERNS` must be the browser origin shown in devtools, not a container URL. For local frontend runs, use `http://localhost:5173`; do not use `http://host.docker.internal:5173` for CORS.

Browser clients may send `X-Correlation-Id`; the API CORS configuration allows and exposes this header.

The API is published on `http://localhost:8081`, matching the app's internal container port.

Verify CORS after changing `.env` and recreating the API container:

```bash
curl -i -X OPTIONS \
  -H "Origin: http://localhost:5173" \
  -H "Access-Control-Request-Method: POST" \
  -H "Access-Control-Request-Headers: authorization,content-type,x-correlation-id" \
  http://localhost:8081/api/expenses
```

Expected result: the response includes `Access-Control-Allow-Origin: http://localhost:5173`. A browser `NetworkError` on create/update/receipt requests usually means this preflight is failing.

### Refresh Local Prod Database

This deletes the local API SQL Server volume. Use only when you intentionally want to remove all local prod expense data.

Check the volume name first:

```bash
docker volume ls | grep expense-tracker-api
```

Then refresh from the `expense-tracker-api` directory:

```bash
docker compose --env-file .env down
docker volume rm expense-tracker-api_sqldata
docker compose --env-file .env up --build -d
```
