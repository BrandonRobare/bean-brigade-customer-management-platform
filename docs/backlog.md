# Capstone backlog

Request and response fields follow the
[API contract](contract.md). Use synthetic fixtures: `CUS-1001` Amina Khan (ACTIVE), `CUS-1002` Ravi Singh
(PROSPECT), `CUS-9999` (not found), and correlation ID `lab-request-001`.

## CAP-12 — Record a customer interaction

[Issue #21](https://github.com/BrandonRobare/bean-brigade-customer-management-platform/issues/21)

As an agent, I want to record an interaction so another agent can see what happened.

| Given | When | Then |
| --- | --- | --- |
| An authenticated agent and `CUS-1001` | The agent submits a valid interaction with `X-Correlation-ID: lab-request-001` | The API returns 201 with the saved interaction and that correlation ID; the row persists in PostgreSQL and appears in the customer's timeline. |
| The saved interaction | The event is published and consumed | `CustomerInteractionRecordedV1` carries the interaction ID, customer ID and `lab-request-001`, keyed by customer ID; the consumer logs the same correlation ID. |
| An authenticated agent | An interaction is submitted for `CUS-9999` | The API returns 404; no interaction or event is created. |
| An authenticated agent and `CUS-1001` | The summary is blank or exceeds 1024 characters | The API returns 400; no interaction or event is created. |
| AGENT or ADMIN and an existing interaction with its current version | The user edits the type and/or summary | The API returns 200; PostgreSQL and the refetched timeline show the edit. The interaction ID, customer, creation time and creation correlation ID stay unchanged. |
| ADMIN and an existing interaction with its current version | The user confirms deletion | The API returns 204; refetching the timeline shows the interaction is gone. PostgreSQL confirms only that interaction was deleted; the customer remains. |
| A delete confirmation dialog | The user cancels | No DELETE request is sent and no row changes. |
| An edit or delete with a stale version | The request is submitted | The API returns 409 and preserves the newer row. The UI keeps edit values and asks the user to reload without retrying the write automatically. |
| AGENT or ADMIN | An edit has no editable fields, invalid type/summary, an invalid UUID, or a missing/invalid version | The API returns 400 and no row changes. |
| An authorized user and a valid but unknown interaction ID | Edit or delete is attempted | The API returns 404 and no row changes. |
| A valid AGENT token | Interaction deletion is attempted directly | The API returns 403 and the interaction remains. |
| No token, an expired token or an invalid signature | Interaction edit/delete is attempted directly | The API returns 401 and no row changes. |
| A completed interaction edit or delete | Logs and Kafka evidence are checked | Logs carry the mutation's correlation ID without the summary; no new `CustomerInteractionRecordedV1` is emitted for that mutation. |

## CAP-13 — Search customers

[Issue #22](https://github.com/BrandonRobare/bean-brigade-customer-management-platform/issues/22)

As an agent, I want to find a customer by name or public ID so I can open the right record.

| Given | When | Then |
| --- | --- | --- |
| An authenticated agent and the seeded customers | The agent searches for `Amina` or `CUS-1001` | The API returns 200 with Amina's public ID, name and status; the UI offers a link to her profile. |
| An authenticated agent | The agent searches for `CUS-9999` | The API returns 200 with `[]`; the UI shows an empty state. |
| A search in progress | The API fails | The UI clears the loading state and shows an error with a retry action. |

## CAP-14 — Customer profile

[Issue #23](https://github.com/BrandonRobare/bean-brigade-customer-management-platform/issues/23)

As an agent, I want to see a customer's profile and interaction history so I have context before contacting them.

| Given | When | Then |
| --- | --- | --- |
| An authenticated agent and `CUS-1001` with saved interactions | The agent opens the profile | The profile shows the customer's public ID, name and status, with interactions newest first. |
| An authenticated agent and a customer with no interactions | The agent opens the profile | The customer details load and the timeline shows an empty state. |
| An authenticated agent | The agent opens `CUS-9999` | The API returns 404 and the UI shows a not-found state. |
| A profile request in progress | The API fails | The UI clears the loading state and shows an error with a retry action. |

## CAP-15 — Sign in with a role

[Issue #24](https://github.com/BrandonRobare/bean-brigade-customer-management-platform/issues/24)

As an agent or admin, I want to sign in so I can use the functions my role allows.

| Given | When | Then |
| --- | --- | --- |
| Valid demo-user credentials | The user signs in | The API issues a signed, expiring JWT with AGENT or ADMIN; Angular attaches it to protected API calls. |
| Invalid credentials | The user tries to sign in | The API returns 401 and the UI shows a sign-in error without creating a session. |
| No token, an expired token or an invalid signature | A protected API is called directly | The API returns 401; Angular clears an expired session and returns to sign-in. |
| A valid AGENT token | An ADMIN-only API is called directly | The API returns 403; hiding a button is not the authorization check. |

## CAP-16 — Create and update a customer (ADMIN)

[Issue #84](https://github.com/BrandonRobare/bean-brigade-customer-management-platform/issues/84)

As an admin, I want to create and update customer records so agents can work with current information.

| Given | When | Then |
| --- | --- | --- |
| A valid ADMIN token and a nonblank name of at most 200 characters | The admin creates a customer | The API returns 201 with a generated public ID, initial `PROSPECT` status, creation time and version, plus a profile Location header; PostgreSQL, search and profile show the customer. |
| A valid ADMIN token and an existing customer's current version | The admin updates the name | The API returns 200 with the new version; reopening the profile shows the saved name. The public ID, status and creation time stay unchanged. |
| A valid AGENT token | Customer create/update/status-change/delete is attempted directly | The API returns 403 and the database is unchanged. |
| A valid ADMIN token | An update, status change or delete targets `CUS-9999` | The API returns 404 and no row changes. |
| A valid ADMIN token | A name is blank/too long, a status is unrecognized, or an update/status-change/delete lacks a valid version | The API returns 400 and no partial change is saved. |
| ADMIN and `CUS-1002` in `PROSPECT` with its current version | The admin activates Ravi | The API returns 200; PostgreSQL and the refreshed profile show `ACTIVE` with the next version. |
| ADMIN and a `PROSPECT` or `ACTIVE` customer with its current version | The admin changes the status to `CLOSED` | The API returns 200 and persists `CLOSED`; existing interactions remain readable. |
| ADMIN and an existing customer's current version | The admin attempts `ACTIVE → PROSPECT`, any transition from `CLOSED`, or a same-status transition | The API returns 409 and preserves the previous status and version. |
| ADMIN and a customer with interaction history | Customer deletion is confirmed with the current version | The API returns 409 and preserves the customer and every interaction. |
| ADMIN and a customer with no interactions and its current version | Customer deletion is confirmed | The API returns 204; PostgreSQL confirms deletion, search omits the customer and a subsequent profile read returns 404. |
| A customer delete confirmation dialog | The admin cancels | No DELETE request is sent and no row changes. |
| ADMIN and an outdated customer version | Name update, status change or deletion is attempted | The API returns 409; the newer row is preserved and the UI asks for a reload while keeping edit values. |
| No token, an expired token or an invalid signature | A customer write is attempted directly | The API returns 401 and no row changes. |
| A successful customer or interaction mutation | The API is restarted and the data is read again | The committed change remains visible in PostgreSQL and through the API/UI; deleted records remain absent. |
