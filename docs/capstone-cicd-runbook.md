# Capstone CI/CD runbook

## Stack

Angular + Spring Boot + PostgreSQL + GitHub Actions → the course's k3s cluster, namespace `student08` (`kubectl`).  
Not Bitbucket. Adel confirmed k3s for the deploy on 2026-10-08 (R-02 in `docs/risk-register.md`).

## Secret names only

- `KUBECONFIG`: `production` environment secret, the `student08` kubeconfig
- `CRM_DB_PASSWORD`, `CRM_AGENT_PASSWORD`, `CRM_ADMIN_PASSWORD`, `CRM_JWT_PRIVATE_KEY`, `CRM_JWT_PUBLIC_KEY`, and
  optionally `CRM_TLS_CERT` / `CRM_TLS_KEY`: `production` environment secrets; CD writes them into `crm-db`, `crm-auth`,
  `crm-jwt` and `crm-tls` without printing them
- `PLATFORM_HOSTNAME`, `INGRESS_CLASS`, `STORAGE_CLASS`, `INGRESS_NAMESPACE`: `production` environment variables

Never paste cluster credentials into this file.

## PR gates

`.github/workflows/capstone-ci.yml` runs on every PR and every push to `main`. GitHub's `protect-main` ruleset
(checked 2026-10-08) requires a PR, squash merges only, and all seven CI jobs to pass: `frontend`, `backend`, `scan`,
`sast`, `secrets`, `iac` and `image`. Required approvals are 0 for now (plan: 1); stale approvals are dismissed, the branch can't be deleted or force-pushed, and
nobody can bypass. `image` only runs once the other six pass, so a red gate anywhere blocks the merge.

- Angular (`frontend`): Node 22, `npm ci`, `npx ng build --configuration=production`. `ng test` joins once there are
  specs.
- Maven (`backend`): Java 21, `mvn -B -ntp clean verify` against a `postgres:16` service (db `crm`, the same throwaway
  values as `compose.yaml`). Never `-DskipTests`.
- IaC (`iac`): Trivy config on `k8s/` and `infra/terraform`, `terraform validate`, Ansible syntax check and
  `ansible-lint`. Applying is a separate CD job ([Infrastructure](#infrastructure)).

Same checks locally, before pushing:

```bash
cd frontend && npm ci && npx ng build --configuration=production
```

```bash
docker compose up -d && cd backend && mvn -B clean verify
```

```bash
cd infra/terraform && terraform fmt -check -recursive && terraform init -backend=false && terraform validate
```

```bash
cd infra/ansible && ansible-galaxy collection install -r requirements.yml && ansible-playbook -i inventory.yml --syntax-check configure.yml
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
runs the CI script tests first and keeps their output in the `ci-script-tests` artifact; the gate and artifacts run
with `if: always()`.

Run the gate tests locally from the repository root (Python 3, no packages):

```bash
python3 -B -m unittest discover -s .github/scripts/tests -v
```

Update `docs/security-findings.csv` when fixing or triaging findings. A CSV row or a dismissed alert does not change the
gate: a high/critical is fixed, or a scoped exception goes into `.github/codeql-exceptions.json` in a reviewed PR
(rule + file, `id`, `owner`, `reason`, `until`) with a matching CSV row. Excepted results still show in the summary;
an expired or malformed entry fails the gate. Keep code scanning default setup off. Workflow actions are pinned to full SHAs;
`.github/dependabot.yml` proposes weekly updates after a 7-day cooldown.

`sast` is a required check (#53, PR #95). A deliberate failing run (a string-built JDBC query on a throwaway branch)
is still to do, with the failing-test drill.

## npm audit gate (#52)

The `scan` job runs `npm audit --omit=dev --audit-level=high --json` on the frontend lockfile, then
`python3 .github/scripts/check-npm-audit.py frontend/npm-audit.json` writes the summary and fails on any high/critical
advisory in a production dependency, or on a missing or failed audit. Dev dependencies are not gated. Moderate and low
advisories show in the summary and don't block. No `npm audit fix --force`: it jumps majors.

A high/critical is fixed, or excepted per advisory in `.github/npm-audit-exceptions.json` in a reviewed PR (GHSA `advisory`,
`id`, `owner`, `reason`, `until`) with a matching row in `docs/security-findings.csv`. An expired entry stops applying,
so the advisory blocks again. Today that's the 6 Angular 19 advisories under `npm-001`, fixed only in Angular 22.
The gate tests run with the other CI script tests above.

## Image

On every run, `image` waits for all six other jobs to pass, then checks the JAR and the Angular `dist/` against their
`SHA256SUMS` and builds `backend/Dockerfile` and `frontend/Dockerfile` around them, with no rebuild. Trivy (#49) scans
both images: a critical fails the job before the push, unless it's triaged in `backend/.trivyignore.yaml` with a
matching row in `docs/security-findings.csv`. Highs show in the report and get a CSV row too (img-001, img-002).
Then [DAST](#dast-69) runs.

On `main` only, it then pushes `ghcr.io/brandonrobare/crm-api:sha-<commit>` and `crm-ui:sha-<commit>` and writes
`artifact-manifest.json` (version, commit, run ID, JAR and `dist/` checksums, both image digests). The manifest and
`trivy-api.json` / `trivy-ui.json` are in the `image-report` artifact, and the run summary prints both digests.

Deploy by `@sha256:<digest>` only, never by tag.

## DAST (#69)

After the Trivy gate and before the push, `image` runs `scripts/dast.sh` against the candidate pair it just built. It
starts Postgres, the API (prod profile, throwaway keys and passwords) and the UI, all read-only and non-root, behind
an nginx edge that routes like the Ingress. Then:

- Probes: readiness; anonymous and forged-token reads are 401; anonymous metrics is 401; `env`, `beans`,
  `configprops`, `heapdump`, `loggers` and `mappings` are not exposed; malformed JSON gets no stack trace; a
  preflight from another origin gets no CORS grant; AGENT login, customer read 200, metrics 403. Any FAIL stops the job.
- ZAP baseline (spider plus passive scan, image pinned by digest) through the edge. `check-zap-report.py` writes the
  summary and fails on a high alert not excepted in `.github/zap-exceptions.json` (`id`, `plugin`, `match`, `owner`,
  `reason`, `until`), with a matching row in `docs/security-findings.csv`. Medium and lower show in the summary.

Probes, `zap.json`, `zap.html` and the ZAP log are in the `dast-report` artifact. A failure means no push, so CD has
nothing to promote. Kafka isn't started: the API doesn't publish yet (#30), so the event path isn't covered.

```bash
API_IMAGE=crm-api:local UI_IMAGE=crm-ui:local bash scripts/dast.sh && python3 .github/scripts/check-zap-report.py
```

## Infrastructure

Terraform owns the NetworkPolicies and the two PVCs; Ansible owns `crm-api-config`
([plan](terraform-ansible-plan.md)). State is the `tfstate-default-crm` Secret, locked by a Lease.

1. Actions > Capstone CD > Run workflow on `main`, action `infra-plan`. Approve the `production` deployment.
2. Read the job summary: every resource and its action, and the plan digest. Ansible check mode shows the ConfigMap diff.
3. Run again with `infra-apply` and that digest. A different digest means something changed since review: plan again.
4. The apply job runs Ansible twice and fails unless the second run reports `changed=0`.

Never apply from a laptop. A local plan is fine: in `infra/terraform`, copy `terraform.tfvars.example` to
`terraform.tfvars` with the real values, set `KUBE_CONFIG_PATH` and `KUBE_CTX`, then `terraform init` and
`terraform plan`. The plan takes the Lease lock for a moment.

## Promote

Push a `v*` tag on a commit whose `main` run is green (production only, no staging: R-01). `capstone-cd.yml` then:

1. finds that commit's successful `main` CI run and downloads its `artifact-manifest.json`;
2. Terraform plans against the namespace and stops the release if it differs from `infra/terraform`; then Ansible
   applies `crm-api-config` ([Infrastructure](#infrastructure));
3. `scripts/release.sh secrets` writes the app Secrets from the environment secrets;
4. `scripts/release.sh deploy` refuses a manifest from another commit, a non-digest or an image outside GHCR, applies
   the manifests, waits for PostgreSQL, Kafka and the topic Job, then rolls the API, then the UI, by digest;
5. `scripts/smoke.sh` runs (it can't be skipped), and `release.sh mark` records the result in the `crm-release` ConfigMap.
   A pair becomes the rollback target only when its smoke passes.

Gates, approvers and database rules: [release plan](release-plan.md) and [checklist](release-checklist.md).

## Smoke

`scripts/smoke.sh` through the Ingress host, never `localhost`: trusted TLS, readiness, the UI, HTTP→HTTPS, anonymous
401, wrong password 401, real login, AGENT metrics 403, ADMIN metrics 200, `CUS-1001` and `CUS-1002` reads, a
`lab-request-001` write and an unknown-customer 404. It prints every check and exits 1 if any fail.

CD keeps the evidence: the deploy or rollback output (commit and both digests) and the smoke output go into one
`release-log` artifact on the run, kept 90 days. It holds no host, password or token.

```bash
SMOKE_URL=https://<host> SMOKE_AGENT_PASSWORD=... SMOKE_ADMIN_PASSWORD=... bash scripts/smoke.sh
```

## TLS certificate

The Ingress serves the certificate in Secret `crm-tls`. k3s gives us no certificate of its own (Traefik's default is
self-signed), so we hold a free Let's Encrypt certificate for `PLATFORM_HOSTNAME`, issued 2026-10-08 and **valid until
2027-01-06. Renew it by mid-December 2026.** CD never overwrites `crm-tls`: `release.sh secrets` only writes it when the
`CRM_TLS_CERT` / `CRM_TLS_KEY` environment secrets are set, and they aren't.

It was issued with an HTTP-01 challenge from inside `student08`, without cluster-admin. To renew, repeat it:

1. Apply a temporary Service `acme-challenge` (port 8080) and an Ingress on the `web` entrypoint that routes only
   `http://<PLATFORM_HOSTNAME>/.well-known/acme-challenge` to it. That path is longer than `crm-http`'s `/`, so Traefik
   picks it first.
2. Run a pod labeled for that Service with two containers sharing an `emptyDir` at `/data`: `goacme/lego` (pinned by
   digest, non-root) with
   `run --accept-tos --server letsencrypt-staging --path /data --domains <PLATFORM_HOSTNAME> --http --http.address :8080 --http.delay 20s`,
   and a `busybox` container that just sleeps, so the files can be copied out after lego exits.
   Without `--http.delay`, Let's Encrypt checks before Traefik has registered the pod and gets a 503.
3. When staging succeeds, delete the pod and run it again with `--server letsencrypt`.
4. Copy `certificates/<host>.crt` and `.key` out of the busybox container into a private temp directory, check that the
   key matches the certificate, then
   `kubectl -n student08 create secret tls crm-tls --cert=... --key=... --dry-run=client -o yaml | kubectl apply -f -`.
   Traefik switches over without a restart. Delete the local copies.
5. Delete the pod, Service and Ingress, and check `curl https://<PLATFORM_HOSTNAME>/actuator/health/readiness` without
   `-k`.

Renewing needs Let's Encrypt's Subscriber Agreement accepted by the release owner. `sslip.io` isn't on the Public Suffix
List, so its users share one Let's Encrypt rate limit; if issuance is refused, retry later or switch hostname. If the
instructor installs cert-manager, move to it instead, since it renews automatically.

## Rollback

Actions → Capstone CD → Run workflow → `rollback` restores the last known-good pair and reruns smoke. Triggers,
authority, limits and rehearsal times: [rollback runbook](rollback-runbook.md).
