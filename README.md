# Customer Management Platform

Bean Brigade's capstone for the Java & Angular Fullstack Bootcamp (Labs 48-52): a CRM slice where a service agent finds a customer and records an interaction.

Angular → Spring Boot REST → PostgreSQL, with Kafka events, delivered through GitHub Actions to the course k3s cluster.

## Layout

| Path | What |
| --- | --- |
| `backend/` | Spring Boot API (Java 21, Maven), Flyway migrations |
| `frontend/` | Angular 19 app |
| `docs/` | architecture, plans, backlog, runbooks |
| `defense/` | final presentation packet |
| `.github/workflows/` | CI and CD |
| `compose.yaml` | local PostgreSQL and Kafka |

## Run locally

Requires Java 21, Node 22, Docker, and OpenSSL. The Maven Wrapper is included in the repository.

### 1. Create the local environment file and JWT keys

From the repository root (Git Bash on Windows, so openssl is available):

```bash
bash scripts/setup-local.sh
```

This copies `.env.example` to `.env` and creates the JWT signing keys in `backend/.keys/`. Both are gitignored.
The defaults work as-is; change `DEMO_AGENT_PASSWORD` and `DEMO_ADMIN_PASSWORD` if you like.

### 2. Start PostgreSQL and Kafka

```bash
docker compose up -d
```

### 3. Start the backend

Run it from `backend/` so it finds `../.env` and `.keys/`.

Windows PowerShell:
```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

Git Bash/macOS/Linux:
```bash
cd backend
./mvnw spring-boot:run
```

The API runs on http://localhost:8080.

### 4. Start the frontend

In a second terminal:

```bash
cd frontend
npm ci
npx ng serve
```

Open http://localhost:4200. The Angular development server forwards /api requests to the backend on port 8080.

### 5. Verify the setup
Sign in as `agent1` (AGENT) or `admin1` (ADMIN) with the passwords from `.env`.

Seeded customers: `CUS-1001` Amina Khan, `CUS-1002` Ravi Singh.

Synthetic data only; never commit `.env` or credentials.
