# Frontend + persistence demo (Lab 50)

How to run the Angular UI against the local API and PostgreSQL, walk the record, edit and delete script, and run the
browser tests. Every result below is from a local run on 2026-10-12 at `59cc608`. The API side, with curl and
Kafka checks, is in [the backend demo](backend-demo.md).

## Stack

Angular 19 → REST with a Bearer JWT → Spring Boot → PostgreSQL 16, plus Kafka for events. Not React or Oracle
([ADR 0001](adrs/0001-freeze-the-taught-stack.md)). Production runs the same images on the course k3s cluster.

| Piece | Where |
| --- | --- |
| Routes | `/login`, `/customers` (search), `/customers/:publicId` (profile and timeline); both customer routes need a session (`authGuard`) |
| Services | `CustomerApiService`, `InteractionApiService` over `HttpClient` |
| Interceptors | `correlationInterceptor` sends a new `X-Correlation-Id` per request; `authInterceptor` adds the Bearer token; `unauthorizedInterceptor` sends a 401 back to sign-in |
| Session | `AuthSessionService` keeps the JWT in memory only, so a page refresh signs you out |
| Schema | Flyway V1 to V5 (see [the backend demo](backend-demo.md#persistence)); V4 and V5 add the `version` columns the edit and delete use for optimistic locking |

## Run it

Start PostgreSQL, Kafka and the API as in [the backend demo](backend-demo.md#run-it), then in a second terminal:

```bash
cd frontend
npm ci
npx ng serve
```

Open http://localhost:4200. The dev server forwards `/api` to the API on 8080.

## Fixtures

| Fixture | Value |
| --- | --- |
| Customers | `CUS-1001` Amina Khan, ACTIVE; `CUS-1002` Ravi Singh, PROSPECT |
| Users | `agent1` (AGENT), `admin1` (ADMIN); passwords from `.env` |
| Unknown customer | `CUS-9999` |

## Demo script

| # | Step | Result on 2026-10-12 |
| --- | --- | --- |
| 1 | Sign in as `agent1` with a wrong password | Stays on sign-in: "Wrong username or password." |
| 2 | Sign in as `agent1` | Lands on Customers, Amina and Ravi listed |
| 3 | Search `CUS-9999` | "No customers match "CUS-9999"." |
| 4 | Search `Amina`, open her profile | `CUS-1001`, ACTIVE, timeline newest first |
| 5 | Record a Meeting, "Demo 71: reviewed statement options" | `POST` 201, timeline refetched, new item on top |
| 6 | Check the row and the event | Row saved with the UI's own correlation ID `b5c6387b-...`; the consumer logged `Handled interaction event` under that same ID |
| 7 | Edit the summary, save | `PATCH` 200, timeline refetched with the new text. AGENT sees Edit but no Delete |
| 8 | Same edit again with the old version (API) | 409 "Interaction version is stale"; the row keeps the newer text |
| 9 | Log out, sign in as `admin1`, open Amina, Delete, then Cancel | No request sent; item stays |
| 10 | Delete, then OK | `DELETE` 204, timeline refetched, item gone |

The row check in step 6:

```bash
docker exec crm-postgres psql -U crm -d crm -c "select i.interaction_id, c.public_id, i.interaction_type, i.correlation_id from customer_interaction i join customer c using (customer_id) where i.summary like 'Demo 71%';"
```

```text
            interaction_id            | public_id | interaction_type |            correlation_id
--------------------------------------+-----------+------------------+--------------------------------------
 86f38e12-370c-46b3-9216-dfd12eed066c | CUS-1001  | MEETING          | b5c6387b-a845-4ccf-89ba-9ee96c3b2798
```

Edit and delete logs carry their own correlation ID and never the summary:

```text
[correlationId=b7e80fcc-...] InteractionService : Edited interaction interactionId=86f38e12-370c-46b3-9216-dfd12eed066c correlationId=b7e80fcc-...
[correlationId=06a9fd0e-...] InteractionService : Deleted interaction interactionId=86f38e12-370c-46b3-9216-dfd12eed066c correlationId=06a9fd0e-...
```

## Failure paths

| Case | Result |
| --- | --- |
| Wrong password | 401, "Wrong username or password.", no session |
| Empty summary | "Summary is required.", nothing sent (Selenium) |
| Search `CUS-9999` | Empty state |
| Profile `/customers/CUS-9999` | Not found state; the API returns 404 Problem Details |
| Stale edit | 409; the UI keeps the draft and asks you to reload |
| AGENT delete through the API | 403; the row stays |
| No token | 401; the UI goes back to sign-in |

## UI tests

Selenium drives headless Chrome against the running UI, selecting by `data-testid` (`username`, `password`,
`sign-in`, `customer-search-input`, `customer-result-link`, `interaction-type`, `interaction-summary`,
`interaction-submit`, `interaction-timeline-item`, `interaction-edit`, `interaction-delete`, and others). With the API
and `ng serve` running, from `backend/`:

```bash
set -a; . ../.env; set +a
./mvnw -B -ntp test -Dtest=CustomerUiSeleniumIT,CustomerUiFullSeleniumIT -Dui.baseUrl=http://localhost:4200
```

2026-10-12: 16 tests, 0 failures. `CustomerUiSeleniumIT` is the critical path (sign in, search, profile, rejected
empty summary, recorded interaction) and runs on every PR in the `ui-e2e` job. `CustomerUiFullSeleniumIT` adds 15
more: wrong password, signed-out redirect, admin search for Ravi, empty search, Back to search, unknown and malformed
IDs, an EMAIL interaction, agent edit, admin delete, cancelled delete, the admin customer lifecycle, no customer
controls for AGENT, delete blocked by history, and a stale customer edit. It runs on every push to `main`.

```bash
cd frontend
npx ng test --watch=false --browsers=ChromeHeadless
```

2026-10-12: 79 of 79 passed, including `auth-session.service.spec.ts` (a failed 401 sign-in leaves no token).

## Known limits

- The session is in memory: refreshing the page signs you out. No refresh tokens.
- Delete asks through the browser's own `confirm()` dialog.
- Searching is by name or public ID only.
- The local stack is shared by every checkout on the machine, so Amina's timeline will hold earlier test rows.
