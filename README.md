# ☁️ CloudTask

CloudTask is a production-oriented RESTful Task Management API built with Spring Boot. It demonstrates how to build, secure, test, containerize, deploy, and monitor a real-world backend application.

The system provides REST APIs for task management, authentication, authorization, audit logging, and administration, with PostgreSQL persistence, Flyway migrations, Docker, GitHub Actions CI/CD, AWS EC2 deployment, Prometheus metrics, and Grafana monitoring.

---

## 📋 Table of Contents

- [Overview](#-overview)
- [Architecture](#️-architecture)
- [Tech Stack](#️-tech-stack)
- [Key Features](#-key-features)
- [System Design](#-system-design)
- [Getting Started](#-getting-started)
- [API Documentation](#-api-documentation)
- [Testing](#-testing)
- [Observability](#-observability)
- [CI/CD](#-cicd)
- [AWS EC2 Deployment](#-aws-ec2-deployment)
- [Project Structure](#-project-structure)
- [Interview Talking Points](#-interview-talking-points)
- [Future Enhancements](#-future-enhancements)

---

## 🎯 Overview

CloudTask is designed as a production-oriented monolithic backend for secure task management.

### Core Goals

- Build a layered Spring Boot REST API
- Implement secure JWT authentication and authorization
- Enforce user ownership and data isolation
- Persist data reliably with PostgreSQL and Flyway
- Protect APIs with rate limiting
- Validate behavior with unit and integration testing
- Automate CI/CD using GitHub Actions
- Deploy the application to AWS EC2 using Docker Compose
- Monitor the application using Actuator, Prometheus, and Grafana

---

## 🏗️ Architecture

CloudTask follows a layered architecture with security, persistence, CI/CD, and observability around the application.

```text
┌─────────────────────────┐
│        Client           │
│  REST API / Swagger UI  │
└────────────┬────────────┘
             │ HTTP
             ▼
┌─────────────────────────┐
│   Spring Boot API       │
│                         │
│ JWT Authentication      │
│ Rate Limit Filter       │
│ Spring Security         │
│ Controllers             │
│ Services                │
│ Repositories            │
└───────┬─────────┬───────┘
        │         │
        │         └─────────────────────┐
        ▼                               ▼
┌───────────────────┐        ┌────────────────────┐
│    PostgreSQL     │        │ Spring Boot        │
│                   │        │ Actuator / Metrics │
│ JPA / Hibernate   │        └─────────┬──────────┘
│ Flyway Migrations │                  │
└───────────────────┘                  ▼
                              ┌────────────────────┐
                              │    Prometheus      │
                              │ Metric Collection  │
                              └─────────┬──────────┘
                                        │
                                        ▼
                              ┌────────────────────┐
                              │      Grafana       │
                              │ Monitoring Dashboard│
                              └────────────────────┘
```

### Deployment Architecture

```text
┌──────────────┐
│    GitHub    │
└──────┬───────┘
       │
       ▼
┌──────────────────────┐
│ GitHub Actions CI    │
│ Build + Test         │
└─────────┬────────────┘
          │ success
          ▼
┌──────────────────────┐
│ GitHub Actions CD    │
│ SSH Deployment       │
└─────────┬────────────┘
          │
          ▼
┌─────────────────────────────────┐
│           AWS EC2               │
│                                 │
│        Docker Compose           │
│                                 │
│ ┌─────────────┐ ┌────────────┐ │
│ │ CloudTask   │ │ PostgreSQL │ │
│ │ API :8080   │ │    :5432   │ │
│ └──────┬──────┘ └────────────┘ │
│        │                        │
│        ▼                        │
│ ┌─────────────┐ ┌────────────┐ │
│ │ Prometheus  │ │  Grafana   │ │
│ │    :9090    │ │   :3000    │ │
│ └─────────────┘ └────────────┘ │
└─────────────────────────────────┘
```

---

## 🛠️ Tech Stack

| **Category** | **Technology** | **Purpose** |
|---|---|---|
| **Language** | Java 21 | Application development |
| **Backend** | Spring Boot 3 | REST API and application framework |
| **Web** | Spring Web / Tomcat | HTTP and REST endpoints |
| **Security** | Spring Security | Authentication and authorization |
| **Authentication** | JWT | Stateless access-token authentication |
| **Refresh Tokens** | JWT refresh-token flow | Access-token renewal |
| **Password Security** | BCrypt | Password hashing |
| **Database** | PostgreSQL 18 | Persistent relational storage |
| **Persistence** | Spring Data JPA / Hibernate | ORM and data access |
| **Migration** | Flyway | Version-controlled database migrations |
| **Validation** | Jakarta Validation | Request validation |
| **API Documentation** | OpenAPI / Swagger UI | Interactive API documentation |
| **Rate Limiting** | Bucket4j | Per-user token-bucket rate limiting |
| **Audit Logging** | Custom audit logging | User activity and request auditing |
| **Unit Testing** | JUnit 5 / Mockito | Isolated business-logic testing |
| **Integration Testing** | Spring Boot Test / Testcontainers | Full application and database integration testing |
| **Containerization** | Docker | Application containerization |
| **Orchestration** | Docker Compose | Local and EC2 multi-container deployment |
| **CI/CD** | GitHub Actions | Automated build, test, and deployment |
| **Cloud** | AWS EC2 | Application hosting |
| **Monitoring** | Spring Boot Actuator | Health and application metrics |
| **Metrics** | Micrometer / Prometheus | Metrics instrumentation and collection |
| **Visualization** | Grafana | Monitoring dashboards |
| **Build Tool** | Maven | Dependency and build management |

---

## ✨ Key Features

### 1. **Task Management**

- Create, retrieve, update, and soft-delete tasks
- Task ownership
- Task priority and status
- Pagination
- Sorting
- Dynamic filtering using JPA Specifications
- Search across multiple task fields

### 2. **Authentication & Authorization**

- User registration
- User login
- JWT-based authentication
- Refresh tokens
- Role-based authorization
- `USER` and `ADMIN` roles
- BCrypt password hashing
- Ownership validation for protected resources

### 3. **Security**

- Spring Security
- JWT authentication filter
- User ownership enforcement
- Collection-level task isolation
- Per-user rate limiting with Bucket4j
- Correct `401`, `403`, `404`, and `429` HTTP behavior

### 4. **Audit Logging**

- Audit logging for important actions
- User activity tracking
- IP address capture
- User-Agent capture
- Request context handling

### 5. **Database & Persistence**

- PostgreSQL
- Spring Data JPA
- Hibernate
- Flyway database migrations
- JPA auditing
- Soft delete
- Persistent Docker volume

### 6. **Testing**

- JUnit 5 unit tests
- Mockito-based service tests
- Spring Boot integration tests
- PostgreSQL integration testing with Testcontainers
- Authentication and JWT integration testing
- Task ownership and authorization integration testing
- Rate-limit integration testing
- Actuator health endpoint testing
- Meaningful coverage focused on important behavior and security boundaries

### 7. **CI/CD**

- GitHub Actions CI
- Automated Maven build and test execution
- Continuous deployment after successful CI
- SSH-based EC2 deployment
- `git fetch` + `git reset --hard origin/main`
- Docker Compose rebuild and startup
- Post-deployment application health checks
- Deployment failure when the application does not become healthy

### 8. **Observability**

- Spring Boot Actuator
- `/actuator/health`
- `/actuator/prometheus`
- Prometheus metric collection
- Grafana dashboards
- Application availability monitoring
- HTTP request-rate monitoring
- HTTP 5xx error-rate monitoring
- HTTP response-time monitoring
- JVM memory and heap monitoring
- Process CPU monitoring
- JVM live-thread monitoring
- Failure and recovery verification

---

## 🔧 System Design

### Request Flow

```text
Client
  │
  ▼
JWT Authentication
  │
  ▼
Rate Limit Filter
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
  │
  ▼
HTTP Response
```

### Authentication Flow

```text
Client
  │
  ├── POST /auth/register
  │
  └── POST /auth/login
           │
           ▼
      Authentication
           │
           ▼
      Access Token
           │
           ▼
  Authorization: Bearer <JWT>
           │
           ▼
      Protected API
```

### Refresh Token Flow

```text
Access Token Expires
        │
        ▼
POST /auth/refresh
        │
        ▼
Validate Refresh Token
        │
        ▼
Issue New Token Pair
```

### Rate Limiting Flow

```text
Authenticated Request
        │
        ▼
   RateLimitFilter
        │
        ▼
   User ID Bucket
        │
        ▼
Token Available?
   │          │
  YES         NO
   │          │
   ▼          ▼
Continue    HTTP 429
Request     Retry-After
```

---

## 🚀 Getting Started

### Prerequisites

- Java 21
- Docker
- Docker Compose
- Git
- Maven Wrapper included in the project

### Clone the Repository

```bash
git clone https://github.com/mkalki/cloud-task-api.git
cd cloud-task-api
```

### Environment Variables

Create a `.env` file in the project root:

```env
POSTGRES_DB=cloudtask
POSTGRES_USER=cloudtask
POSTGRES_PASSWORD=your_database_password

JWT_SECRET=your_long_random_jwt_secret
```

Never commit `.env` to GitHub.

Add this to `.gitignore`:

```text
.env
```

---

## 🐳 Running with Docker

Build and start the application stack:

```bash
docker compose up -d --build
```

Check running containers:

```bash
docker compose ps
```

View CloudTask logs:

```bash
docker compose logs app
```

Stop the stack:

```bash
docker compose down
```

The PostgreSQL data is stored in a persistent Docker volume.

To remove the database volume as well:

```bash
docker compose down -v
```

> **Warning:** `docker compose down -v` deletes the PostgreSQL volume and its stored database data.

---

## 📚 API Documentation

CloudTask uses OpenAPI and Swagger UI for interactive API documentation.

Swagger UI provides:

- REST endpoint discovery
- Request and response schemas
- Interactive endpoint testing
- JWT authorization support

### Local

```text
http://localhost:8080/swagger-ui/index.html
```

### AWS EC2

```text
http://<EC2_PUBLIC_IP>:8080/swagger-ui/index.html
```

---

## 🔐 REST API

### Authentication Endpoints

| **Method** | **Endpoint** | **Description** |
|---|---|---|
| POST | `/auth/register` | Register a new user |
| POST | `/auth/login` | Authenticate and receive tokens |
| POST | `/auth/refresh` | Refresh an access token |

### Task Endpoints

| **Method** | **Endpoint** | **Description** |
|---|---|---|
| GET | `/tasks` | Get tasks belonging to the authenticated user |
| POST | `/tasks` | Create a new task |
| GET | `/tasks/{id}` | Get a task by ID |
| PUT | `/tasks/{id}` | Update a task |
| DELETE | `/tasks/{id}` | Soft-delete a task |

Protected endpoints require:

```http
Authorization: Bearer <JWT_TOKEN>
```

---

## 📝 Example Task Request

### Create Task

```text
POST /tasks
```

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

## 🔎 Pagination, Filtering & Sorting

CloudTask supports pagination and sorting:

```text
GET /tasks?page=0&size=10
```

Dynamic filtering is implemented using JPA Specifications.

Supported capabilities:

- Pagination
- Sorting
- Dynamic filtering
- Searching across multiple task fields

---

## 🧪 Testing

CloudTask uses both **unit testing** and **integration testing**.

### Unit Testing

Unit tests focus on isolated service-layer business logic using:

- JUnit 5
- Mockito
- Mocked dependencies
- Behavior-focused assertions

### Integration Testing

Integration tests verify multiple real components working together.

The integration environment includes:

```text
MockMvc
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
PostgreSQL Testcontainer
```

Integration coverage includes:

- Spring application context loading
- Authentication and JWT flows
- Refresh token behavior
- Logout/token revocation
- Task ownership
- Authorization boundaries
- Collection-level task isolation
- Unauthenticated access
- Rate limiting
- Actuator health

Run the complete test suite:

```bash
./mvnw test
```

The full suite has been verified successfully.

---

## 📈 Observability

CloudTask uses **Spring Boot Actuator + Micrometer + Prometheus + Grafana**.

### Actuator

Health endpoint:

```text
http://localhost:8080/actuator/health
```

Prometheus metrics:

```text
http://localhost:8080/actuator/prometheus
```

### Prometheus

Prometheus scrapes CloudTask every 15 seconds using:

```text
app:8080/actuator/prometheus
```

Prometheus is available locally at:

```text
http://localhost:9090
```

### Grafana

Grafana reads metrics from Prometheus and provides a monitoring dashboard.

Grafana is available locally at:

```text
http://localhost:3000
```

### Dashboard Panels

The CloudTask dashboard currently includes:

| **Panel** | **Purpose** |
|---|---|
| CloudTask Availability | Detect whether the application is reachable |
| JVM Memory Usage | Monitor JVM memory consumption |
| HTTP Request Rate | Monitor incoming request traffic |
| HTTP 5xx Error Rate | Monitor server-side failures |
| HTTP Average Response Time | Monitor request latency |
| JVM Heap Usage % | Monitor heap utilization |
| Process CPU Usage % | Monitor application CPU consumption |
| JVM Live Threads | Monitor JVM thread count |

### Observability Architecture

```text
CloudTask
    │
    ▼
Spring Boot Actuator
    │
    ▼
/actuator/prometheus
    │
    ▼
Prometheus
    │
    ▼
Grafana
    │
    ▼
CloudTask Monitoring Dashboard
```

### Failure and Recovery Verification

The monitoring setup was tested by stopping and restarting the CloudTask application container.

```text
Application Running
        │
        ▼
Prometheus up = 1
        │
        ▼
Stop CloudTask
        │
        ▼
Prometheus up = 0
        │
        ▼
Restart CloudTask
        │
        ▼
Prometheus up = 1
```

This verifies that the monitoring system can detect both application failure and recovery.

### Dashboard Configuration

The exported Grafana dashboard is stored in:

```text
grafana/dashboards/cloudtask-dashboard.json
```

Add the dashboard screenshot to the repository, for example:

```text
docs/images/cloudtask-grafana-dashboard.png
```

Then include it in this section:

```md
![CloudTask Grafana Dashboard](docs/images/cloudtask-grafana-dashboard.png)
```

---

## 🔄 CI/CD

### Continuous Integration

GitHub Actions runs the project's automated build and test process.

```text
Git Push
   │
   ▼
GitHub Actions CI
   │
   ▼
Maven Build + Tests
```

### Continuous Deployment

Successful CI triggers deployment for pushes to `main`.

```text
Push to main
      │
      ▼
CI succeeds
      │
      ▼
CD workflow
      │
      ▼
SSH to EC2
      │
      ▼
git fetch origin
      │
      ▼
git reset --hard origin/main
      │
      ▼
docker compose up -d --build
      │
      ▼
Wait for application startup
      │
      ▼
GET /actuator/health
      │
   ┌──┴───┐
  UP      FAIL
   │        │
   ▼        ▼
Success   Deployment fails
```

The CD workflow also collects Docker status and recent application logs when the health check fails.

---

## ☁️ AWS EC2 Deployment

CloudTask is deployed to AWS EC2 using Docker Compose.

### Deployment Flow

```text
GitHub
   │
   ▼
GitHub Actions
   │
   ▼
AWS EC2
   │
   ▼
Docker Compose
   │
   ├── CloudTask API :8080
   ├── PostgreSQL    :5432
   ├── Prometheus    :9090
   └── Grafana       :3000
```

The application is available at:

```text
http://<EC2_PUBLIC_IP>:8080
```

Swagger UI:

```text
http://<EC2_PUBLIC_IP>:8080/swagger-ui/index.html
```

The EC2 Security Group must allow the required inbound ports.

The deployment has been verified for:

- User registration
- User login
- JWT authentication
- Protected REST endpoints
- Task creation
- Task updates
- Task retrieval
- External access through the EC2 public IP
- PostgreSQL persistence after Docker restart
- Post-deployment health verification

---

## 📁 Project Structure

```text
cloud-task-api/
├── .github/
│   └── workflows/
│       ├── ci.yml
│       └── cd.yml
│
├── grafana/
│   └── dashboards/
│       └── cloudtask-dashboard.json
│
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── mkalki/
│   │   │           └── cloudtaskapi/
│   │   │               ├── audit/
│   │   │               ├── config/
│   │   │               ├── context/
│   │   │               ├── controller/
│   │   │               ├── dto/
│   │   │               ├── entity/
│   │   │               ├── exception/
│   │   │               ├── ratelimit/
│   │   │               ├── repository/
│   │   │               ├── security/
│   │   │               ├── service/
│   │   │               └── specification/
│   │   │
│   │   └── resources/
│   │       ├── application.properties
│   │       └── db/
│   │           └── migration/
│   │
│   └── test/
│
├── compose.yaml
├── Dockerfile
├── prometheus.yml
├── pom.xml
└── README.md
```

---

## 🎯 Interview Talking Points

### 1. **Why a layered architecture?**

Layer separation keeps responsibilities focused:

```text
Controller → Service → Repository → Database
```

This improves maintainability, testability, and separation of concerns.

### 2. **Why JWT?**

JWT provides stateless authentication:

- No server-side HTTP session required
- Token carries authenticated identity
- Works well for REST APIs
- Protected endpoints validate the token before access

### 3. **Why refresh tokens?**

Short-lived access tokens reduce exposure if an access token is compromised, while refresh tokens allow clients to obtain new access tokens without forcing frequent login.

CloudTask also supports refresh-token invalidation and reuse protection.

### 4. **Why Testcontainers for integration tests?**

Testcontainers allows the integration tests to run against a real PostgreSQL database rather than a mocked database.

This verifies real interactions across:

```text
Application
→ JPA
→ Hibernate
→ PostgreSQL
```

### 5. **Why meaningful integration testing instead of 100% coverage?**

The goal is to cover:

- Important application behavior
- Security boundaries
- Major success paths
- Important failure paths

Testing every possible parameter combination can increase maintenance cost without proportional value.

### 6. **Why Bucket4j for rate limiting?**

CloudTask uses a token-bucket model where each authenticated user gets a bucket keyed by user ID.

```text
User ID
   ↓
Bucket
   ↓
Token available → request allowed
No token       → 429
```

The production configuration currently allows 100 tokens with a 100-token refill over one minute.

### 7. **Why Prometheus and Grafana?**

Prometheus collects and stores numerical application metrics.

Grafana queries Prometheus and turns those metrics into dashboards.

```text
Application
   ↓
Actuator / Micrometer
   ↓
Prometheus
   ↓
Grafana
```

### 8. **Why health checks in CI/CD?**

A successful Docker startup does not necessarily mean the application is ready to serve traffic.

The CD workflow therefore verifies:

```text
Container Started
      ↓
Application Started
      ↓
/actuator/health
      ↓
Deployment Success
```

---

## 🚀 Future Enhancements

- Nginx reverse proxy
- HTTPS / TLS
- Custom domain configuration
- EC2-hosted Prometheus and Grafana access hardening
- Alerting and notifications
- Additional custom Micrometer metrics
- Redis-based distributed rate limiting
- More advanced performance and load testing
- Production-grade centralized logging
- Distributed tracing if CloudTask evolves into multiple services

---

## 📝 License

CloudTask is currently an educational and portfolio-oriented backend project.

---

## 👤 Author

**Mohan R**
