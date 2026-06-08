# Expense Tracker API

A backend API for managing and tracking personal or organizational expenses with local/cloud AI capability to read receipt images.

## Features

- User authentication and secure session management (JWT)
- CRUD (Create, Read, Update, Delete) operations for expenses and categories
- Categorization of expenses (e.g. Food, Travel, Utilities)
- Date-based expense queries and summaries
- Support for multiple users (User-scoped data access)
- Extensible for budgeting and reporting integrations
- RESTful endpoints for easy frontend integration

## Technology Stack

- **Language**: Java (100%)
- **Framework**: Spring Boot
- **Database**: H2 (for now)
- **Build Tool**: Maven
- **Authentication**: Spring Security + JWT

## Getting Started

### Prerequisites

- **Java 17 or newer** (Spring Boot 3.3.0+)
- Maven
- Docker & Docker Compose (for production deployment)
- **AI provider** (optional, choose one for receipt processing):
  - Ollama server (local, default)
  - Azure Document Intelligence resource
  - OpenVINO Vision API server

### Setup

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

   **Azure (Cloud):**
   ```env
   AI_PROVIDER=azure
   AZURE_DOCUMENT_AI_ENDPOINT=https://your-resource.cognitiveservices.azure.com/
   AZURE_DOCUMENT_AI_KEY=your-api-key
   ```

4. **Build the project:**
   ```sh
   ./mvnw clean install
   ```

5. **Start the development server:**

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

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.

---

**Created by [iran-undag](https://github.com/iran-undag)**

## Docker / Production

Run the API with a PostgreSQL database using the Docker Compose plugin (creates a `db` service and the `app` service):

```bash
docker compose --env-file .env up --build -d
```

This composes the app with the `prod` Spring profile. Copy `.env.sample` to `.env` and edit environment values before starting.

**Key environment variables in `.env`:**
- `AI_PROVIDER` — Select `ollama`, `openvino`, or `azure` (default: `ollama`)
- `OLLAMA_BASE_URL` — Ollama server URL (default: `http://host.docker.internal:11434`)
- `OPENVINO_BASE_URL` — OpenVINO server URL (default: `http://host.docker.internal:8001`)
- `AZURE_DOCUMENT_AI_ENDPOINT` & `AZURE_DOCUMENT_AI_KEY` — For Azure provider
- `AUTH_ISSUER_URI` & `ALLOWED_ORIGIN_PATTERNS` — For CORS and auth configuration

The API is published on `http://localhost:8081`, matching the app's internal container port.

To test multiple local API replicas behind a reverse proxy:

```bash
docker compose --env-file .env -f docker-compose.yml -f docker-compose.scale.yml up --build --scale app=3 -d
```

In scaled mode, only the Nginx proxy publishes `localhost:8081`; app replicas stay private on the Docker network.

Local development uses the `dev` profile (H2). To run locally with H2:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev -Dspring-boot.run.jvmArguments="-Dspring.profiles.active=dev"
```
