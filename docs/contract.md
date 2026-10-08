# API Contract

## API Endpoints

| Endpoint | Method | Description | Accepts | Returns | Status |
|----------|--------|-------------|---------|---------|--------|
| `/api/v1/customers` | GET | Search customers by name or public ID | `query` | `Customer[]` | 200 OK |
| `/api/v1/customers/{id}` | GET | Retrieve a specific customer by ID | customer public_id | `Customer` | 200 OK |
| `/api/v1/interactions` | GET | Retrieve interactions for a specific customer | `customerId` query parameter (public_id) | List of interactions | 200 OK |
| `/api/v1/interactions` | POST | Create a new interaction for a specific customer | customer public_id and interaction request | Created interaction | 201 Created |
| `/api/v1/auth/login` | POST | Production-profile sign-in | `username`, `password` | `accessToken`, `tokenType`, `expiresIn` | 200; wrong credentials 401 |

As of 2026-10-08, interaction GET/POST and customer search/profile GET are implemented. Search matches part of the
name or the exact public ID, ignoring case; an empty or missing `query` returns every customer.

## CRUD and Lifecycle Scope

[#82](https://github.com/BrandonRobare/bean-brigade-customer-management-platform/issues/82) adds customer
create/update, status changes, customer deletion without interaction history, and interaction edit/delete to
the planned slice. Acceptance criteria belong to CAP-12 and CAP-16 in [the backlog](backlog.md).
These additions are not implemented. Bryan reviews the contract before the API and UI work starts.

| Planned endpoint | Role | Request | Success |
| --- | --- | --- | --- |
| `POST /api/v1/customers` | ADMIN | `fullName` | 201, `Customer`, `Location: /api/v1/customers/{publicId}` |
| `PUT /api/v1/customers/{publicId}` | ADMIN | `fullName`, `version` | 200, updated `Customer` |
| `PATCH /api/v1/customers/{publicId}/status` | ADMIN | `status`, `version` | 200, updated `Customer` |
| `DELETE /api/v1/customers/{publicId}?version={version}` | ADMIN | Current version in the query | 204, no body |
| `PATCH /api/v1/interactions/{id}` | AGENT or ADMIN | `version`, plus `interactionType` and/or `summary` | 200, updated interaction |
| `DELETE /api/v1/interactions/{id}?version={version}` | ADMIN | Current version in the query | 204, no body |

### Customer Writes and Lifecycle

- `fullName` is nonblank and at most 200 characters. Create generates a unique `CUS-` public ID, creation time
  and initial status `PROSPECT`; clients do not supply IDs or timestamps. Existing seeds keep their current IDs.
- A normal update changes the name and preserves the public ID, status and creation time. Status changes use
  the status endpoint and are checked in the service within the same transaction as the write.
- Allowed transitions: `PROSPECT → ACTIVE`, `PROSPECT → CLOSED`, `ACTIVE → CLOSED`. `CLOSED` is terminal.
  A known but disallowed transition, including a same-status transition, returns 409 and changes nothing.
  An unrecognized status returns 400. The capstone keeps its existing three statuses; `SUSPENDED` is not added.
- Customer deletion is a hard delete only when no interactions reference the customer. Otherwise return 409
  and preserve both customer and history. No cascade deletion of interactions; closing a customer also keeps
  its history. A successful deletion removes the customer from search and subsequent profile reads return 404.

### Interaction Edits and Deletes

- An edit changes only type and/or summary. At least one must be supplied; omitted fields stay unchanged.
  Supplied types use the existing enum; supplied summaries are nonblank and at most 1024 characters.
  The ID, customer, creation time and original creation correlation ID remain unchanged.
- ADMIN deletion removes only the selected interaction, never the customer or another interaction. Angular
  asks for confirmation, waits for 204 and refetches the timeline. Cancellation sends no DELETE request.
- These mutations do not emit `CustomerInteractionRecordedV1`; that event describes creation. No new event
  types are added by this scope decision. Request logs still carry the mutation's `X-Correlation-ID` without
  logging the note text.

### Concurrency and Failures

- Planned customer and interaction responses add `version`, a nonnegative integer backed by a new Flyway
  migration and JPA optimistic locking. Current interaction GET/POST responses do not yet contain this field.
  Update, status-change and delete requests must supply the version last read; successful updates increment it.
- Check the version and perform the mutation atomically in the transaction. A stale version returns 409
  without overwriting or deleting the newer row. Angular preserves edit values, shows the conflict and asks
  the user to reload; it does not retry the write automatically.
- Missing/invalid versions, invalid UUIDs or invalid DTO fields return 400. Unknown customer or interaction IDs
  return 404. No/invalid JWT returns 401; a role outside the table returns 403. Failures change no rows.
- New writes use `X-Correlation-ID` for logs and Problem Details. Supplied IDs must be nonblank and at most
  64 characters; generate a request ID if absent. Existing POST header/body/fallback precedence below is unchanged.
- UI success is checked by refetching and querying PostgreSQL, including after an API restart. A toast alone
  does not prove persistence.

## Customer Response

Search returns an array of `Customer` objects; profile returns one object. No search matches returns `[]`;
an unknown profile ID returns 404. All fields below are required. The internal numeric database key is not exposed.

| Field | JSON type | Meaning |
|-------|-----------|---------|
| `publicId` | string | Public customer ID, for example `CUS-1001` |
| `fullName` | string | Customer display name |
| `status` | string | `PROSPECT`, `ACTIVE` or `CLOSED` |
| `createdAt` | string (ISO 8601 UTC timestamp) | Customer creation time |

```json
{
  "publicId": "CUS-1001",
  "fullName": "Amina Khan",
  "status": "ACTIVE",
  "createdAt": "2026-10-06T20:00:00Z"
}
```

## Headers

- `Content-Type: application/json` for request and success-response bodies.
- `Authorization: Bearer <token>`: the `prod` profile validates RS256 JWTs as specified by
  [ADR 0006](adrs/0006-use-self-issued-jwts-for-auth.md). The fixed training token works only in the `dev` profile.
- `X-Correlation-ID`: on POST, a nonblank header overrides body `correlationId`, then the fallback is `lab-request-001`.
  Header names are case-insensitive. Correlation values must fit the 64-character database column; DTO length validation is pending.

## Interaction Request and Response

POST accepts the request fields below and returns one interaction object. GET returns an array of the same
objects, newest `createdAt` first; a known customer with no interactions returns `[]`. No pagination or sorting
parameters are implemented. Customer IDs are public strings such as `CUS-1001`, not the internal database key.

| Field | JSON type | POST request | Response |
|-------|-----------|--------------|----------|
| `id` | string (UUID) | Generated by server | Interaction ID |
| `customerId` | string | Required, nonblank public ID of an existing customer | Public customer ID |
| `interactionType` | string | Required: `CALL`, `EMAIL`, `NOTE` or `MEETING` | Saved value |
| `summary` | string | Required, nonblank, maximum 1024 characters | Saved value |
| `correlationId` | string or null in request; string in response | Optional; header precedence applies | Effective value saved at creation |
| `createdAt` | string (ISO 8601 UTC timestamp) | Generated by server | Creation time |

## Errors

[ADR 0002](adrs/0002-ids-and-errors.md) chooses RFC 9457 Problem Details with `Content-Type: application/problem+json`.
The target body is below; `status` matches the HTTP status and `correlationId` identifies the failing request.

| Status | Meaning |
|--------|---------|
| 400 | Missing required parameter, invalid JSON or request validation failure |
| 401 | Missing or invalid bearer token |
| 403 | Authenticated user lacks the required role (`prod` profile) |
| 404 | Customer does not exist, for example `CUS-9999`; planned interaction mutations also use this for a missing interaction |
| 409 | Planned: invalid lifecycle transition, stale version, or customer deletion blocked by interaction history |

```json
{
  "type": "about:blank",
  "title": "Not Found",
  "status": 404,
  "detail": "Customer CUS-9999 was not found",
  "instance": "/api/v1/interactions",
  "correlationId": "lab-request-001"
}
```

Problem Details mapping is pending: the current 404 body is `{"error":"not-found","correlationId":"lab-request-001"}`
and the training filter sends an empty 401 response.

## Interaction Event Fields

`CustomerInteractionRecordedV1` uses topic `crm.customer.interactions.v1`, keyed by public `customerId`.
The seven existing fields are a record stub; Kafka publication is pending. `eventId` and `actor` are planned
additions for #30.

| Field | JSON type | Meaning |
|-------|-----------|---------|
| `eventType` | string | `CustomerInteractionRecorded` |
| `eventVersion` | string | `"1"` |
| `interactionId` | string (UUID) | Same as HTTP response `id` |
| `customerId` | string | Public customer ID |
| `interactionType` | string | `CALL`, `EMAIL`, `NOTE` or `MEETING` |
| `correlationId` | string | Effective value saved with the interaction |
| `occurredAt` | string (ISO 8601 UTC timestamp) | Event time |
| `eventId` | string (UUID), planned | Stable event ID for deduplication |
| `actor` | string, planned | Authenticated JWT `sub` |

Events exclude the interaction summary and customer names/emails. Optional field additions remain in V1;
removing fields, changing their types or adding required fields after V1 is published requires V2.
