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

- Java 8 or newer
- Maven
- Database server (H2 for now)
- Installed Ollama server
- Azure Document Intelligence resource
- Local OpenVINO Vision API server

### Setup

1. **Clone the repository:**
   ```sh
   git clone https://github.com/iran-undag/expense-tracker-api.git
   cd expense-tracker-api
   ```

2. **Configure Environment:**
   - Edit `src/main/resources/application.properties`.
   - Set environment variables for the parameters in applications.properties.<br>
     Example: <br>
     (Windows) `set AI_PROVIDER=azure`, `set AI_PROVIDER=ollama`, or `set AI_PROVIDER=openvino`<br>
     (Linux/macOS) `export AI_PROVIDER=azure`, `export AI_PROVIDER=ollama`, or `export AI_PROVIDER=openvino`
     
3. **Build the project:**
   ```sh
   ./mvnw clean install
   ```
  
4. **Start the server:**
   ```sh
   ./mvnw spring-boot:run
   ```
   
4. **AI Provider:**

   Local: Install ollama and update applications.properties to reference the AI.<br>
          Set environment variable: `set AI_PROVIDER=ollama` before running the API.

   OpenVINO: Start the OpenVINO Vision API and set environment variables: `AI_PROVIDER=openvino`, `OPENVINO_BASE_URL=http://localhost:8001`. The API posts receipts to `/api/vision/chat` by default.

   Cloud: Create an Azure Document Intelligence resource. Set environment variables: AI_PROVIDER=azure, AZURE_DOCUMENT_AI_ENDPOINT=your_endpoint, AZURE_DOCUMENT_AI_KEY=your_key
   
The API will be available by default at `http://localhost:8080`.

## API Documentation

Swagger/OpenAPI documentation is available at `http://localhost:8080/swagger-ui/index.html`.

### Testing with Swagger

> **TIP:** To test the secured endpoints in Swagger:
> 1. Launch the application and go to the Swagger UI (`/swagger-ui/index.html`).
> 2. Call `POST /api/auth/login` with a JSON payload like `{"username": "your_test_user"}`.
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

Local development uses the `dev` profile (H2). To run locally with H2:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```
