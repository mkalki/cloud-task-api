# CloudTask

CloudTask is a production-oriented RESTful Task Management API built with Spring Boot.

The project provides REST APIs for task management, user authentication, authorization, and administration. It focuses on building a secure backend using Spring Security, JWT authentication, PostgreSQL, Flyway database migrations, Docker, and AWS EC2.

CloudTask follows a layered architecture and exposes interactive API documentation through OpenAPI/Swagger UI.

---

## Features

### Task Management

- Create, retrieve, update, and soft-delete tasks
- Task ownership
- Pagination
- Sorting
- Dynamic filtering using JPA Specifications
- Search tasks across multiple fields
- Task priority support
- Task status support

### Authentication and Authorization

- User registration
- User login
- JWT-based authentication
- Refresh tokens
- Role-based authorization
- `USER` and `ADMIN` roles
- Password encryption using BCrypt
- User ownership validation

### Database

- PostgreSQL database
- Spring Data JPA
- Hibernate
- Flyway database migrations
- JPA Specifications for dynamic filtering
- Database auditing with creation and update timestamps
- Soft delete support

### Security

- Spring Security
- JWT authentication
- Role-based access control
- Refresh token support
- User ownership validation
- Rate limiting using Bucket4j

### Audit Logging

- Audit logging for important actions
- User activity tracking
- IP address capture
- User-Agent capture
- Request context handling

### Testing

- JUnit 5
- Mockito
- Service-layer unit tests
- Meaningful unit testing

### Deployment

- Docker containerization
- Docker Compose
- PostgreSQL container
- Persistent Docker volume
- Environment-based configuration
- AWS EC2 deployment

---

## Architecture

CloudTask follows a layered backend architecture:

```text
Client
   │
   ▼
Controller
   │
   ▼
Service
   │
   ▼
Repository
   │
   ▼
PostgreSQL
```

### Security Architecture

```text
Client
   │
   ▼
JWT Authentication Filter
   │
   ▼
Spring Security
   │
   ▼
Controller
   │
   ▼
Service
   │
   ▼
Repository
   │
   ▼
PostgreSQL
```

### Deployment Architecture

```text
Internet
   │
   ▼
AWS EC2
   │
   ▼
Docker Compose
   │
   ├── Spring Boot Application
   │
   └── PostgreSQL
```

---

## Tech Stack

| Category | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot |
| Security | Spring Security |
| Authentication | JWT |
| Database | PostgreSQL |
| ORM | Spring Data JPA / Hibernate |
| Database Migration | Flyway |
| API Documentation | OpenAPI / Swagger UI |
| Validation | Jakarta Validation |
| Testing | JUnit 5, Mockito |
| Rate Limiting | Bucket4j |
| Containerization | Docker |
| Container Orchestration | Docker Compose |
| Cloud | AWS EC2 |
| Build Tool | Maven |

---

## REST API

CloudTask exposes RESTful HTTP endpoints for user authentication and task management.

### Authentication Endpoints

| Method | Endpoint | Description |
|---|---|---|
| POST | `/auth/register` | Register a new user |
| POST | `/auth/login` | Authenticate and receive a JWT |
| POST | `/auth/refresh` | Refresh an access token |

### Task Endpoints

| Method | Endpoint | Description |
|---|---|---|
| GET | `/tasks` | Get tasks belonging to the authenticated user |
| POST | `/tasks` | Create a new task |
| GET | `/tasks/{id}` | Get a task by ID |
| PUT | `/tasks/{id}` | Update a task |
| DELETE | `/tasks/{id}` | Soft-delete a task |

Protected endpoints require a JWT token:

```http
Authorization: Bearer <JWT_TOKEN>
```

---

## API Documentation

CloudTask uses OpenAPI and Swagger UI for interactive API documentation and endpoint testing.

Swagger UI can be used to:

- Explore available REST endpoints
- View request and response schemas
- Test API endpoints interactively
- Authorize protected endpoints using a JWT token

Once the application is running locally:

```text
http://localhost:8080/swagger-ui/index.html
```

For AWS EC2 deployment:

```text
http://<EC2_PUBLIC_IP>:8080/swagger-ui/index.html
```

---

## Project Structure

```text
src
├── main
│   ├── java
│   │   └── com
│   │       └── ...
│   │           ├── controller
│   │           ├── service
│   │           ├── repository
│   │           ├── model
│   │           ├── dto
│   │           ├── security
│   │           ├── config
│   │           ├── exception
│   │           ├── specification
│   │           ├── audit
│   │           └── context
│   │
│   └── resources
│       ├── application.properties
│       └── db
│           └── migration
│
└── test
```

---

## Getting Started

### Prerequisites

Make sure the following are installed:

- Java 21
- Docker
- Docker Compose
- Git

### Clone the Repository

```bash
git clone https://github.com/mkalki/cloud-task-api.git
cd cloud-task-api
```

---

## Environment Variables

Create a `.env` file in the project root:

```env
POSTGRES_DB=cloudtask
POSTGRES_USER=cloudtask
POSTGRES_PASSWORD=your_database_password

JWT_SECRET=your_long_random_jwt_secret
```

Never commit your `.env` file to GitHub.

Add the following to `.gitignore`:

```text
.env
```

---

## Running with Docker

Build and start the application:

```bash
docker compose up -d --build
```

Check running containers:

```bash
docker compose ps
```

View application logs:

```bash
docker compose logs app
```

Stop the application:

```bash
docker compose down
```

The PostgreSQL database uses a Docker volume, so data persists across container restarts.

To completely remove containers and database volumes:

```bash
docker compose down -v
```

> **Warning:** `docker compose down -v` removes the PostgreSQL volume and deletes the database data.

---

## Authentication Flow

### 1. Register

```text
POST /auth/register
```

Example request:

```json
{
  "username": "testuser",
  "password": "password123"
}
```

### 2. Login

```text
POST /auth/login
```

Example request:

```json
{
  "username": "testuser",
  "password": "password123"
}
```

The login endpoint returns a JWT access token.

### 3. Access Protected Endpoints

Use the JWT token in the `Authorization` header:

```http
Authorization: Bearer <JWT_TOKEN>
```

Example:

```text
GET /tasks
```

---

## Example Task Request

### Create Task

```text
POST /tasks
```

Example request:

```json
{
  "title": "Deploy CloudTask",
  "description": "Deploy the application on AWS EC2",
  "priority": "HIGH"
}
```

Example response:

```json
{
  "id": 1,
  "title": "Deploy CloudTask",
  "description": "Deploy the application on AWS EC2",
  "status": "TODO",
  "priority": "HIGH",
  "ownerId": 1
}
```

---

## Pagination, Filtering and Sorting

CloudTask supports pagination and sorting for task retrieval.

Example:

```text
GET /tasks?page=0&size=10
```

Dynamic filtering is implemented using JPA Specifications.

Supported capabilities include:

- Dynamic filtering using JPA Specifications
- Searching across multiple task fields
- Pagination
- Sorting

---

## Docker Deployment

CloudTask runs using Docker Compose with two main services:

```text
cloud-task-api
      │
      ▼
Docker Compose
      │
      ├── Spring Boot Application
      │
      └── PostgreSQL
```

The Spring Boot application communicates with PostgreSQL through the Docker network.

PostgreSQL data is stored in a persistent Docker volume.

---

## AWS EC2 Deployment

CloudTask is deployed on an AWS EC2 instance using Docker and Docker Compose.

Deployment flow:

```text
GitHub
   │
   ▼
AWS EC2
   │
   ▼
Docker Compose
   │
   ├── CloudTask API
   │
   └── PostgreSQL
```

The Spring Boot application is exposed on:

```text
Port 8080
```

The application can be accessed using:

```text
http://<EC2_PUBLIC_IP>:8080
```

The AWS Security Group must allow inbound TCP traffic on port `8080`.

The deployment was verified by testing:

- User registration
- User login
- JWT authentication
- Protected REST endpoints
- Task creation
- Task updates
- Task retrieval
- External access through the EC2 public IP
- PostgreSQL persistence after Docker restart

---

## Testing

Run tests using:

```bash
./mvnw test
```

The project uses:

- JUnit 5
- Mockito
- Service-layer unit tests

---

## Project Status

CloudTask is actively being developed.

### Completed Milestones

- REST API development
- PostgreSQL persistence
- Spring Data JPA
- Validation
- Global exception handling
- Pagination and sorting
- Dynamic filtering with JPA Specifications
- Search across multiple task fields
- Soft delete
- JPA auditing
- Spring Security
- JWT authentication
- Refresh tokens
- Role-based authorization
- User ownership
- Audit logging
- Rate limiting
- Unit testing
- Flyway database migrations
- Docker containerization
- Docker Compose
- AWS EC2 deployment
- OpenAPI / Swagger API documentation
