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

### 1. Start PostgreSQL and Kafka

```bash
docker compose up -d
```
### 2. Create the local environment file

Copy `.env.example` to `.env`

Git Bash:
```bash
cp .env.example .env
```

PowerShell:
```powershell
Copy-Item .env.example .env
```
Set `DEMO_AGENT_PASSWORD` and `DEMO_ADMIN_PASSWORD` to local passwords of your choice.

Leave the local database password as `change-me`.

Do not commit `.env` or credentials.

### 3. Generate JWT signing keys

Run these commands from the repository root. Windows users should use Git Bash so openssl is available.

```bash
mkdir -p backend/.keys
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out backend/.keys/jwt-private.pem
openssl pkey -in backend/.keys/jwt-private.pem -pubout -out backend/.keys/jwt-public.pem
```

The generated .pem files are ignored by Git and should not be committed.

### 4. Start the backend

Windows PowerShell:
```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

Git Bash/macOS/Linux:
``` bash
cd backend
./mvnw spring-boot:run
```

The API runs on http://localhost:8080.

### 5. Start the frontend

In a second terminal:

```bash
cd frontend
npm ci
npx ng serve
```

Open http://localhost:4200. The Angular development server forwards /api requests to the backend on port 8080.

### 6. Verify the setup
Sign in as `agent1` (AGENT) or `admin1` (ADMIN) with the passwords from `.env`.

Seeded customers: `CUS-1001` Amina Khan, `CUS-1002` Ravi Singh.

Synthetic data only; never commit `.env` or credentials.
