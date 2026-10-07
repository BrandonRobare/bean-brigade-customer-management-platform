# Capstone CI/CD runbook — TODO (Lab 51)

## Stack

Angular + Spring Boot + PostgreSQL + GitHub Actions → OpenShift (`oc`).  
Not Bitbucket. Not k3s/`kubectl`.

## Secret names only

- `OC_SERVER` — TODO
- `OC_TOKEN` — TODO

Never paste cluster credentials into this file.

## PR gates

`.github/workflows/capstone-ci.yml` runs on every PR and every push to `main`. Merge enforcement is configured in
GitHub's `protect-main` ruleset. The live 2026-10-06 snapshot requires only `secrets`; requiring `frontend` and
`backend` remains #25 work. The #53 branch adds a blocking `sast` job; adding it as a required merge check remains
a GitHub settings step after CI verification.

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

If `verify` passed, the `backend` job keeps the JAR it just tested (#49):

- `SHA256SUMS`: the JAR's SHA-256, plus the commit, run number and version (Lab 43 format)
- both uploaded as the artifact `crm-api-jar`: Actions → the run → Artifacts. `main` keeps it 90 days, PRs 7

Nothing rebuilds it. The `image` job builds from this artifact, and `v*` tags deploy what `main` already built. To
prove a downloaded JAR is the tested one, unzip the artifact and run this in that folder:

```bash
shasum -a 256 -c SHA256SUMS
```

The JAR line must say `OK`. The `commit=`, `run=` and `version=` lines print a format warning; that's expected.

## SAST gate (#53)

CodeQL analyzes Java (traced `mvn compile`), TypeScript and the workflow files on PRs and `main`. Results go to the
Security tab and the `codeql-report` artifact keeps the SARIF. `python3 .github/scripts/check-codeql-sarif.py
codeql-results/*.sarif` writes a severity table to the job summary and fails at security-severity 7.0+ (high/critical)
or a missing, malformed or failed analysis. Medium, low and non-security results are listed and don't block. The job
runs the CI script tests first; the gate and artifact run with `if: always()`.

Run the gate tests locally from the repository root (Python 3, no packages):

```bash
python3 -B -m unittest discover -s .github/scripts/tests -v
```

Update `docs/security-findings.csv` when fixing or triaging findings. A CSV row or a dismissed alert does not change the
gate: a high/critical is fixed, or a scoped exception goes into `.github/codeql-exceptions.json` in a reviewed PR
(rule + file, `id`, `owner`, `reason`, `until`) with a matching CSV row. Excepted results still show in the summary;
an expired or malformed entry fails the gate. Keep code scanning default setup off. Workflow actions are pinned to full SHAs;
`.github/dependabot.yml` proposes weekly updates after a 7-day cooldown.

To finish #53 on GitHub:

1. Push/open a PR, run CI and add the observed `sast` status check to `protect-main`, keeping `secrets` required.
2. On a disposable PR branch, add a SQL-injection fixture (string-built JDBC query). Confirm `sast` fails, the summary
   and SARIF survive, merging is blocked and `image` is skipped. Remove the fixture and confirm green.
3. Keep the failing/passing run URLs in the evidence index, merge the clean change and verify `main` before
   closing #53 and moving its card to Done. A PR never publishes images, so a PR run alone is not proof of a gate.

## Image

On every run, `image` waits for `backend` and `sast` to succeed, then checks the JAR against `SHA256SUMS`, builds
`backend/Dockerfile` around it and scans the image with Trivy (#49). A critical finding fails the job before the push, unless it's triaged in
`backend/.trivyignore.yaml` with a matching row in `docs/security-findings.csv`.

On `main` only, it then pushes `ghcr.io/brandonrobare/crm-api:sha-<commit>` and writes `artifact-manifest.json`
(version, commit, run ID, JAR checksum, image digest). The manifest and `trivy.json` are in the `image-report`
artifact, and the run summary prints the digest.

Deploy by `@sha256:<digest>` only, never by tag.

## Promote

Digest from CI → `oc set image` — TODO env names (`crm-test` / `crm-staging` / `crm-prod`)

## Smoke

`CUS-1001` via Route + `lab-request-001` — TODO

## Rollback

`oc rollout undo` — TODO
