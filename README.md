# Customer Management Platform

Bean Brigade's capstone for the Java & Angular Fullstack Bootcamp (Labs 48-52): a CRM slice where a service agent finds a customer and records an interaction.

Angular → Spring Boot REST → PostgreSQL, with Kafka events, delivered through GitHub Actions to OpenShift.

## Layout

| Path | What |
| --- | --- |
| `backend/` | Spring Boot API (Java 21, Maven), Flyway migrations |
| `frontend/` | Angular 19 app |
| `docs/` | architecture, plans, backlog, runbooks |
| `defense/` | final presentation packet |
| `.github/workflows/` | CI and CD |
| `compose.yaml` | local PostgreSQL |

## Run locally

Requires Java 21, Maven, Node 22 and Docker.

```bash
docker compose up -d
```

```bash
cd backend && mvn -B test && mvn spring-boot:run
```

```bash
cd frontend && npm ci && npx ng serve
```

Seeded customers: `CUS-1001` Amina Khan, `CUS-1002` Ravi Singh. Synthetic data only; never commit `.env` or credentials.
