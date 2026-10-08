# Northstar CRM Capstone Architecture

## Scope

One vertical slice of a CRM for service agents:

- **CAP-12:** record a customer interaction
- edit interactions; ADMIN can delete an interaction
- customer search and profile
- sign-in with the roles AGENT and ADMIN
- customer create/update, lifecycle transitions and deletion without interaction history (ADMIN)

The CRUD/lifecycle additions are planned in [the contract](contract.md#crud-and-lifecycle-scope) under
[#82](https://github.com/BrandonRobare/bean-brigade-customer-management-platform/issues/82).

Fixtures only: `CUS-1001` Amina Khan (ACTIVE), `CUS-1002` Ravi Singh (PROSPECT), `CUS-9999` (does not exist),
correlation ID `lab-request-001`.

## Stack

| Layer         | Technology                                                                                           |
|---------------|------------------------------------------------------------------------------------------------------|
| UI            | Angular 19, TypeScript, signals + RxJS, `HttpClient` with functional interceptors                    |
| API           | Spring Boot 3.5 on Java 21 (Maven), REST / JSON, Bean Validation, Problem Details errors             |
| Security      | Spring Security OAuth2 resource server, self-issued RS256 JWTs, roles AGENT / ADMIN (ADR 0006)       |
| Data          | PostgreSQL 16, Spring Data JPA, Flyway migrations                                                    |
| Messaging     | Apache Kafka via `spring-kafka`, versioned events                                                    |
| Delivery      | GitHub Actions (build, test, SAST), Docker images, k3s course cluster (Deployment, Service, Ingress) |
| Observability | Spring Boot Actuator (health, metrics), SLF4J logs with the correlation ID in the MDC                |

## Non-Goals

- **Not used anywhere, including diagrams and alternatives:** Bitbucket Pipelines, Oracle, React, SOAP, laptop k3s.
- **Not in this slice:** real customer data or PII, more than one backend service (one Spring Boot app, `crm-api`,
  serves the API and runs the Kafka consumer), manual changes on the cluster (only the pipeline deploys).

## Context

Who uses the system and what it depends on.

```mermaid
flowchart LR
    agent(["Service agent<br/>role AGENT"])
    admin(["Team admin<br/>role ADMIN"])
    team(["Bean Brigade developers"])
    crm["Northstar CRM<br/>search customers, view profiles,<br/>record interactions"]
    gh["GitHub<br/>repo, Actions, board"]
    reg["GHCR<br/>crm-api, crm-ui images"]
    ocp["k3s cluster<br/>(instructor-hosted, namespace student08)"]
    agent -->|" browser, HTTPS "| crm
    admin -->|" browser, HTTPS "| crm
    team -->|" commits, PRs "| gh
    gh -->|" pushes image by digest "| reg
    gh -->|" deploys "| ocp
    ocp -->|" pulls image "| reg
    ocp -->|" runs "| crm
```

## Containers

The deployable parts and how they talk.

```mermaid
flowchart LR
    subgraph browser["Agent's browser"]
        ui["Angular SPA<br/>guard + interceptors"]
    end
    subgraph cluster["k3s namespace student08 (locally: compose + ng serve)"]
        ing["Ingress<br/>/api → crm-api, / → crm-ui"]
        web["crm-ui<br/>nginx serving the Angular build"]
        api["crm-api<br/>Spring Boot<br/>security chain, controllers,<br/>services, repositories,<br/>Kafka producer + consumer, Actuator"]
        db[("PostgreSQL 16<br/>crm schema, Flyway")]
        kafka[["Kafka<br/>crm.customer.interactions.v1<br/>+ dead-letter topic"]]
    end

    ui -->|" one host "| ing
    ing -->|" / "| web
    ing -->|" /api: REST / JSON<br/>Authorization: Bearer JWT<br/>X-Correlation-ID "| api
    api -->|" JDBC as crm_app "| db
    api -->|" publish after commit "| kafka
    kafka -->|" consume, dedupe on eventId "| api
```

| Container  | Technology        | Responsibility                                                                | Runs locally as                         |
|------------|-------------------|-------------------------------------------------------------------------------|-----------------------------------------|
| `crm-ui`   | Angular 19, nginx | agent screens, calls the API; never decides access                            | `npx ng serve` on :4200                 |
| `crm-api`  | Spring Boot 3.5   | authentication and authorization, business rules, persistence, events, health | `mvn spring-boot:run` on :8080          |
| PostgreSQL | 16                | customers, interactions, processed events                                     | `docker compose` on :5432               |
| Kafka      | broker + topics   | interaction events, dead-letter topic                                         | `docker compose` on :9092 (to be added) |

In the cluster, `crm-ui` is its own nginx image, and the Ingress sends `/api` to `crm-api` and everything else to
`crm-ui`, so the UI and API share one origin (ADR 0007, #68). PostgreSQL runs as `crm-postgres`.

## Trust Boundaries

| Boundary         | Rule                                                                                                                                                                                                                                                                                      |
|------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Browser → API    | **The API is the security boundary, not Angular.** Anyone can call the API directly, so every `/api/**` request is authenticated (JWT) and authorized by role on the server. The Angular guard only hides screens that would fail anyway. CORS allows the UI's origin but isn't security. |
| Token signing    | Only the token endpoint uses the private signing key, which lives in a Kubernetes Secret (locally, a `.env` path to a gitignored file). Checking a token needs only the public key.                                                                                                       |
| API → PostgreSQL | The app connects as a least-privilege `crm_app` login with data access only (planned in `V2`); Flyway migrates as the schema owner. Credentials come from environment variables or a Kubernetes Secret, never from Git.                                                                   |
| API → Kafka      | Internal network only. Events carry IDs, types and the correlation ID, never the interaction's free-text summary.                                                                                                                                                                         |
| GitHub → cluster | Only the pipeline deploys. Deploy credentials are GitHub Environment secrets; the production environment accepts only `v*` tags.                                                                                                                                                          |
| Actuator         | Liveness and readiness are open for the Kubernetes probes; `metrics` is ADMIN only; nothing else (`env`, `heapdump`, `info`) is exposed (#54).                                                                                                                                            |

## Runtime Path

One request end to end: CAP-12 for `CUS-1001`.

```mermaid
sequenceDiagram
    actor Agent
    participant UI as crm-ui (Angular)
    participant Sec as Security filter chain
    participant Ctl as InteractionController
    participant Svc as InteractionService
    participant DB as PostgreSQL
    participant K as Kafka
    participant Con as Consumer (in crm-api)
    Agent ->> UI: record a note for CUS-1001
    UI ->> Sec: POST /api/v1/interactions<br/>Bearer JWT, X-Correlation-ID
    Sec ->> Sec: validate token, check role AGENT or ADMIN
    Sec ->> Ctl: authenticated request
    Ctl ->> Ctl: @Valid CreateInteractionRequest
    Ctl ->> Svc: create(request, correlationId)
    Svc ->> DB: find CUS-1001 (404 Problem Details if missing)
    Svc ->> DB: insert customer_interaction
    Svc -->> Ctl: InteractionResponse
    Ctl -->> UI: 201 Created
    UI -->> Agent: timeline shows the new note
    Svc ->> K: after commit: CustomerInteractionRecordedV1, key CUS-1001
    K ->> Con: deliver event
    Con ->> DB: record eventId (skip if seen), audit
```

- Every hop logs the same correlation ID (`lab-request-001` in the demo), so one search follows the request from the UI
  to the consumer.
- Failures return Problem Details: 400 validation, 401 no or bad token, 403 wrong role, 404 unknown customer, 409
  invalid state.
- Publishing **after the commit** is **open** (ADR 0005: after commit, or a transactional outbox).

## Security View

```mermaid
sequenceDiagram
    actor Agent
    participant UI as crm-ui
    participant Int as Interceptors
    participant Sec as Security filter chain
    participant API as Controllers
    Agent ->> UI: sign in as agent1 or admin1
    UI ->> API: POST /api/v1/auth/login (public)
    API -->> UI: RS256 JWT: sub, roles, exp in 30 min
    UI ->> UI: keep the token in a signal (memory only)
    UI ->> Int: any API call
    Int ->> Sec: adds Authorization: Bearer and X-Correlation-ID
    Sec ->> Sec: check signature with the public key, then expiry and role
    alt no token, bad signature or expired
        Sec -->> Int: 401
        Int -->> UI: clear session, go to /login
    else valid token, wrong role
        Sec -->> UI: 403, error state
    else valid token and role
        Sec ->> API: request
        API -->> UI: response
    end
```

| Endpoint                                                           | AGENT    | ADMIN  |
|--------------------------------------------------------------------|----------|--------|
| `POST /api/v1/auth/login`                                          | public   | public |
| `GET /api/v1/customers?query=`, `GET /api/v1/customers/{publicId}` | yes      | yes    |
| `GET` / `POST /api/v1/interactions`                                | yes      | yes    |
| `PATCH /api/v1/interactions/{id}` (planned)                         | yes      | yes    |
| `DELETE /api/v1/interactions/{id}` (planned)                        | no (403) | yes    |
| Customer create/update/status-change/delete (planned)              | no (403) | yes    |
| `/actuator/health/liveness`, `/actuator/health/readiness`          | public   | public |
| `/actuator/metrics`                                                | no (403) | yes    |

Tokens are self-issued (**decided**, ADR 0006). `POST /api/v1/auth/login` checks one of two in-memory demo users and
returns a JWT signed with the API's private RSA key (RS256). Spring Security's resource server checks every other
`/api/**` call with the public key, and anything not in the table above is denied. The ADMIN-only endpoints also carry
`@PreAuthorize("hasRole('ADMIN')")`, so the role check sits on the endpoint as well as in the URL rules. Until the login
page lands (Tue 10/6), the starter's fixed demo token keeps working under the `dev` profile only.

## Messaging View

```mermaid
flowchart LR
    svc["InteractionService<br/>after commit"] -->|" CustomerInteractionRecordedV1<br/>key = customer ID "| topic[["crm.customer.interactions.v1"]]
    topic --> con["Consumer in crm-api"]
    con -->|" eventId not seen "| work["record eventId, write audit,<br/>log with correlation ID"]
    con -->|" eventId already seen "| skip["skip: no duplicate side effects"]
    con -->|" fails after bounded retries "| dlt[["dead-letter topic"]]
```

- **Event contract** (proposed, ADR 0003): `eventId`, `eventType`, `eventVersion`, `occurredAt`, `correlationId`,
  `actor`, `customerId`, `interactionId`, `interactionType`. The starter record has seven of these; `eventId` and
  `actor` are added.
- **Key = customer ID,** so one customer's events stay in order.
- **Versioning:** only additive changes inside V1; a breaking change is a new `V2` event.
- **Delivery is at least once,** so the consumer is idempotent: it records each `eventId` and skips repeats. Poison
  messages go to the dead-letter topic after bounded retries.

## Data View

```mermaid
erDiagram
    CUSTOMER ||--o{ CUSTOMER_INTERACTION: "has"
    CUSTOMER {
        bigserial customer_id PK
        varchar public_id UK "CUS-1001"
        varchar full_name
        varchar status "PROSPECT, ACTIVE, CLOSED"
        varchar email UK "planned in V2"
        timestamptz created_at
        timestamptz updated_at "planned in V2"
        bigint version "planned, optimistic locking"
    }
    CUSTOMER_INTERACTION {
        uuid interaction_id PK
        bigint customer_id FK
        varchar interaction_type "CALL, EMAIL, NOTE, MEETING"
        varchar summary "up to 1024 chars"
        varchar correlation_id
        timestamptz created_at
        bigint version "planned, optimistic locking"
    }
    PROCESSED_EVENT {
        uuid event_id PK "planned, consumer dedupe"
        timestamptz processed_at
    }
```

- `V1__crm_schema.sql` (exists) creates `customer` and `customer_interaction`, with CHECK constraints on status and
  type, an index on (customer, newest first), and seeds Amina and Ravi.
- The API exposes customers only by `public_id` (`CUS-1001`), never the internal key (proposed, ADR 0002).
- Merged migrations are never edited; every change is a new `V` file.

## Delivery Path

```mermaid
flowchart LR
    pr["PR to main"] --> ci["CI<br/>frontend: npm ci, tests, ng build<br/>backend: mvn verify on PostgreSQL<br/>scan: Dependency-Check, npm audit, Semgrep"]
    ci --> merge["review + squash merge"]
    merge --> img["build image once<br/>record sha256 digest"]
    img --> reg["GHCR, public"]
    reg --> tag["tag v*"]
    tag --> cd["CD: deploy the digest<br/>to student08 (k3s)"]
    cd --> smoke["smoke through the Ingress:<br/>readiness, then CUS-1001"]
    smoke -->|" fails "| undo["kubectl rollout undo<br/>then fix in code, new PR"]
```

- The pipeline is the only way changes reach the cluster; nobody edits the cluster by hand.
- The image is built once and promoted by digest, never rebuilt at deploy.
- Environments and promotion rules: `docs/environment-strategy.md`. Gates and jobs: `docs/github-actions-plan.md`.
