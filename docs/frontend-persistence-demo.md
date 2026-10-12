# Lab 50 — frontend + persistence demo (TODO)

## Stack

Angular → REST (JWT) → Spring Boot → PostgreSQL. Not React or Oracle. Runs locally; production is on k3s.

## Happy path

- Customer: `CUS-1001`
- Correlation: the UI sends a random `X-Correlation-Id` per request; `lab-request-001` only comes from the curl call
- Edit/delete: as `agent1`, edit a timeline interaction and save; as `admin1`, cancel deletion first, then confirm. Each saved change refetches the timeline. On 409, keep the draft and reload before trying again.
- Sign in: `agent1` or `admin1`, passwords from `.env`; the JWT stays in memory only

## Failure path

- `CUS-9999` → 404
- Missing Bearer → 401

## Verify

```powershell
docker compose -p lab50verify up -d
cd backend; mvn -B test
cd ..\frontend; npx ng build
```
