# Expense Tracker API

A backend API for managing and tracking personal or organizational expenses and local/cloud AI capability to read receipt images.

## Features

- User authentication and secure session management (TODO)
- CRUD (Create, Read, Update, Delete) operations for expenses and categories
- Categorization of expenses (e.g. Food, Travel, Utilities)
- Date-based expense queries and summaries
- Support for multiple users (TODO)
- Extensible for budgeting and reporting integrations
- RESTful endpoints for easy frontend integration

## Technology Stack

- **Language**: Java (100%)
- **Framework**: Spring Boot
- **Database**: H2 (for now)
- **Build Tool**: Maven
- **Authentication**: TODO

## Getting Started

### Prerequisites

- Java 8 or newer
- Maven
- Database server (H2 for now)
- Installed Ollama server
- Azure Document Intelligence resource

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
     (Windows) `set AI_PROVIDER=azure` or `set AI_PROVIDER=ollama`<br>
     (Linux/macOS) `export AI_PROVIDER=azure` or `export AI_PROVIDER=ollama`
     
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

   Cloud: Create an Azure Document Intelligence resource. Set environment variables: AI_PROVIDER=azure, AZURE_DOCUMENT_AI_ENDPOINT=your_endpoint, AZURE_DOCUMENT_AI_KEY=your_key
   
The API will be available by default at `http://localhost:8080`.

## API Documentation

Swagger/OpenAPI documentation is available at `http://localhost:8080/swagger-ui/index.html`.

#### Example Endpoints

- `POST /api/auth/register` — Register a new user (TODO)
- `POST /api/auth/login` — Authenticate and obtain token (TODO)
- `GET /api/expenses` — List all expenses for authenticated user
- `POST /api/expenses` — Add a new expense
- `PUT /api/expenses/{id}` — Update an expense
- `DELETE /api/expenses/{id}` — Delete an expense (TODO)

> For a detailed API reference, see the Swagger docs or consult the source code.

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.

---

**Created by [iran-undag](https://github.com/iran-undag)**
