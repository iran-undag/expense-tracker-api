# Expense Tracker API

Demo Spring Boot service for the Expense Tracker demo application. It supports local/cloud AI capability to read receipt images.

## Features

- User authentication and secure session management (JWT)
- CRUD (Create, Read, Update, Delete) operations for expenses and categories
- Categorization of expenses (e.g. Food, Travel, Utilities)
- Date-based expense queries and summaries
- Support for multiple users (User-scoped data access)
- Extensible for budgeting and reporting integrations
- RESTful endpoints for easy frontend integration
- Upload receipt images for AI OCR extraction.
- Request correlation with `X-Correlation-Id` for cross-service log lookup

## Technology Stack

- **Language**: Java 17
- **Framework**: Spring Boot
- **Database**: H2 for development/tests; SQL Server for production-like local runs and Azure SQL Database
- **Build Tool**: Maven or the included maven wrapper
- **Authentication**: Spring Security + JWT

## Getting Started

### Prerequisites

- **Java 17 or newer** (Spring Boot 3.3.0+)
- Maven
- Docker & Docker Compose (for production-like local runs)
- **AI provider** (optional, choose one for receipt processing):
  - Ollama server (local, default)
  - OpenVINO Vision API server
  - Expense Tracker receipt processor function backed by Azure Document Intelligence

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
- `GET /api/expenses` — List all expenses for the authenticated user
- `POST /api/expenses` — Add a new expense
- `PUT /api/expenses/{id}` — Update an existing expense
- `DELETE /api/expenses/{id}` — Delete an expense

> For a detailed API reference, see the Swagger docs or consult the source code.

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
- `AUTH_ISSUER_URI` — JWT issuer expected by the API; this must exactly match the access token `iss`, for example `http://localhost:9000`
- `JWK_SET_URI` — Container-reachable JWK endpoint for verifying tokens, for example `http://host.docker.internal:9000/oauth2/jwks`
- `ALLOWED_ORIGIN_PATTERNS` — Browser origins allowed by CORS, for example `http://localhost:5173`
- `MSSQL_SA_PASSWORD` — Local SQL Server `sa` password used by the compose `db` service and API datasource

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
