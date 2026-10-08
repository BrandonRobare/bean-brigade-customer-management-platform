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

Requires Java 21, Maven, Node 22 and Docker.

```bash
docker compose up -d
```

Copy `.env.example` to `.env` and change the two `DEMO_` passwords to your own, then make the signing keys:

```bash
mkdir -p backend/.keys
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out backend/.keys/jwt-private.pem
openssl pkey -in backend/.keys/jwt-private.pem -pubout -out backend/.keys/jwt-public.pem
```

```bash
cd backend && mvn -B test && mvn spring-boot:run
```

```bash
cd frontend && npm ci && npx ng serve
```

Sign in as `agent1` (AGENT) or `admin1` (ADMIN) with the passwords from `.env`.
Seeded customers: `CUS-1001` Amina Khan, `CUS-1002` Ravi Singh. Synthetic data only; never commit `.env` or credentials.
