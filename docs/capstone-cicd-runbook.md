# Capstone CI/CD runbook — TODO (Lab 51)

## Stack

Angular + Spring Boot + PostgreSQL + GitHub Actions → OpenShift (`oc`).  
Not Bitbucket. Not k3s/`kubectl`.

## Secret names only

- `OC_SERVER` — TODO
- `OC_TOKEN` — TODO

Never paste cluster credentials into this file.

## PR gates

`.github/workflows/capstone-ci.yml` runs on every PR and every push to `main`. `frontend` and `backend` are required
checks: a red one blocks the merge.

- Angular (`frontend`): Node 22, `npm ci`, `npx ng build --configuration=production`. `ng test` joins once there are
  specs.
- Maven (`backend`): Java 21, `mvn -B -ntp clean verify` against a `postgres:16` service (db `crm`, the same throwaway
  values as `compose.yaml`). Never `-DskipTests`.

Same checks locally, before pushing:

```bash
cd frontend && npm ci && npx ng build --configuration=production
```

```bash
docker compose up -d && cd backend && mvn -B clean verify
```

## Package once

On `main` only, and only if `verify` passed, the `backend` job keeps the JAR it just tested (#49):

- `SHA256SUMS`: the JAR's SHA-256, plus the commit and run number (Lab 43 format)
- both uploaded as the artifact `crm-api-jar`: Actions → the `main` run → Artifacts

Nothing rebuilds it. PR runs only check, and `v*` tags deploy what `main` already built. To prove a downloaded JAR is
the tested one, unzip the artifact and run this in that folder:

```bash
shasum -a 256 -c SHA256SUMS
```

The JAR line must say `OK`. The `commit=` and `run=` lines print a format warning; that's expected.

Still to do (#49): build the `crm-api` image from this JAR, Trivy scan, push once, record the digest in
`artifact-manifest.json`. Needs the backend Dockerfile and a registry.

## Promote

Digest from CI → `oc set image` — TODO env names (`crm-test` / `crm-staging` / `crm-prod`)

## Smoke

`CUS-1001` via Route + `lab-request-001` — TODO

## Rollback

`oc rollout undo` — TODO
