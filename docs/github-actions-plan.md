# GitHub Actions Plan

What runs on a pull request, on `main` and on a release tag, what each gate blocks, who owns it, and what evidence it
leaves. Lab 51 implements it. The pipeline's shape is drawn in [Delivery Path](architecture.md#delivery-path), and where
each build lands is in [the environment strategy](environment-strategy.md).

## Rules

1. The pipeline is the only way code reaches the cluster. Nobody deploys from a laptop or edits the cluster by hand.
2. The gate order is fixed: build → verify → scan → publish → deploy.
3. Build once. The image is built and pushed once per `main` commit, from the JAR that the same run verified.
   Production gets that digest, and nothing is rebuilt to deploy.
4. Deploys never run on `pull_request` or on a push to `main`. A person starts every deploy: the course's manual
   approval (Lab 48).
5. Secrets live in GitHub Environment secrets and Kubernetes Secrets. Docs name them and never show values, and
   workflows never echo them.
6. Test and scan reports are kept as run artifacts. The ones that prove a claim are copied to `reports/` with a row in
   `defense/evidence-index.md`. For Dependency-Check (#55), keep JSON/HTML in run artifacts and link their runs without
   committing the reports.
7. A red `main` gets fixed before any new feature merges. A failing check is fixed, never skipped (`-DskipTests`,
   `@Disabled`, a lowered threshold).

## Triggers

| Event                        | Workflow          | Jobs                                                                         | Deploys to                                     |
|------------------------------|-------------------|------------------------------------------------------------------------------|------------------------------------------------|
| `pull_request` to `main`     | `capstone-ci.yml` | frontend, backend, scan, sast, secrets, iac, image (build + scan)      | nothing                                        |
| push to `main`               | `capstone-ci.yml` | frontend, backend, scan, sast, secrets, iac, image (build, scan, push) | nothing                                        |
| tag `v*`                     | `capstone-cd.yml` | promote                                                                      | production                                     |
| manual (`workflow_dispatch`) | `capstone-cd.yml` | rollback, restore-drill, infra-plan or infra-apply                           | production                                     |

## Jobs and Gates

| Job         | Runs on                                               | What it does                                                                                                                                                                                 | Blocks merge                       | Owner                     | Evidence                                                                                                         |
|-------------|-------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------|---------------------------|------------------------------------------------------------------------------------------------------------------|
| `frontend`  | PR, `main`                                            | Node 22 (the brief's stack; the Lab 51 starter says 20) with npm cache, `npm ci`, `ng test --watch=false --browsers=ChromeHeadless` once specs exist, `ng build --configuration production`  | no                                 | Brandon; tests Chad       | run log; `crm-ui-dist` artifact (`dist/` + `SHA256SUMS`) for the `image` job                                                                              |
| `backend`   | PR, `main`                                            | Java 21 (Temurin) with Maven cache, PostgreSQL 16 service, `./mvnw -B -ntp clean verify` (Maven Wrapper, 3.10.0, checksum-pinned)                                                                                                        | no                                 | Brandon                   | `test-reports` (Surefire) artifact on every run, failures included; JAR + `SHA256SUMS`                                                          |
| `scan`      | PR, `main`                                            | Dependency-Check (Maven, #55, blocking); `npm audit --omit=dev --audit-level=high` blocking with per-advisory exceptions (#52)                                    | yes: required `scan`               | Carter                    | `dependency-check-report` and `npm-audit-report` artifacts + job summary; run links in `defense/evidence-index.md`; triage in `docs/security-findings.csv`, accepted findings with owner and expiry |
| `sast`      | PR, `main`                                            | CodeQL (`github/codeql-action` v4.38.2) on `java-kotlin` (traced `mvn compile`), `javascript-typescript` and `actions`, default query suite; results to the Security tab; `check-codeql-sarif.py` fails at security-severity 7.0+ (high/critical) or a missing/failed analysis (#53) | job blocking; required check pending | Brandon | `codeql-report` SARIF artifact, job summary, Security tab alerts; triage in `docs/security-findings.csv` |
| `secrets`   | PR, `main`                                            | gitleaks 8.30.1 (checksum-verified binary) over the full git history, secrets redacted (#56)                                                                                                 | yes: any leak fails                | Brandon                   | `gitleaks-report` artifact + job summary; false positives in `.gitleaksignore` with a reason                     |
| `iac`       | PR, `main`                                            | Trivy 0.75.0 `trivy config` on `k8s/` and `infra/terraform`, fails at HIGH/CRITICAL not listed in `k8s/.trivyignore.yaml` (#57); `terraform fmt -check`, `init -backend=false`, `validate`; `ansible-playbook --syntax-check`, `ansible-lint` | job blocking; required check pending | Brandon | `iac-report` artifact + job summary |
| `image`     | PR (build + scan), `main` (push)                      | after `frontend`, `backend`, `scan`, `sast`, `secrets` and `iac` pass, build `crm-api` from the verified JAR and `crm-ui` from the verified `dist/`, Trivy-scan both on every run; on `main`, push both once to GHCR and record the pair (#49, #68)                                                | fails at critical, not required yet | Brandon                   | `image-report` artifact (`artifact-manifest.json`, `trivy-api.json`, `trivy-ui.json`) and job summary                                 |
| `infra`     | manual (`infra-plan`, `infra-apply`)                  | Terraform plan with a resource summary and digest, Ansible check mode; `infra-apply` re-plans, refuses a different digest, applies, runs Ansible twice and fails unless the second run is `changed=0` | n/a | Brandon | job summary (plan table, digest, Ansible recap) |
| `promote`   | tag `v*`                                              | find the tagged commit's green `main` run, refuse any other manifest, write Secrets, back up the database (refuse the release if that fails), roll API then UI by digest (`scripts/release.sh`), smoke (`scripts/smoke.sh`), record the result | n/a | Brandon | CD summary, `crm-release` ConfigMap |
| `rollback`  | manual (`workflow_dispatch`)                          | restore the last pair whose smoke passed, then smoke again; refuses with no history or when it is already running | n/a | Brandon | CD run, [rollback runbook](rollback-runbook.md) |
| `restore-drill` | manual (`workflow_dispatch`)                      | fresh `pg_dump`, restore into a scratch database, time it, compare row counts, drop it | n/a | Brandon | CD run, `restore-drill-log` artifact |

**Required checks:** as checked on 2026-10-07, the active `protect-main` ruleset requires `scan` and `secrets` from
GitHub Actions. Dependency-Check fails `scan` at CVSS 7 or on scanner errors, blocking merging; `image` requires
successful `frontend`, `backend`, `scan`, `sast`, `secrets` and `iac` jobs. CodeQL makes `sast` fail on high/critical findings or a failed analysis;
its required-check setup remains separate work after confirming CI. Don't rename required job IDs or add path
filters: a skipped required workflow can leave a PR waiting for its check.

**Branch rules (`protect-main`):** PR required, 1 approval from someone other than the author, stale approvals
dismissed on push, squash merge only, no force pushes or deletion, no bypass. The live ruleset requires 0 approvals;
set it to 1.

## Scans

| Scan         | Tool                                                     | When                                 | Fails at      | Status                                                                          |
|--------------|----------------------------------------------------------|--------------------------------------|---------------|---------------------------------------------------------------------------------|
| Dependencies | OWASP Dependency-Check (Maven), `npm audit --omit=dev`   | PR, `main`                           | CVSS 7 / high | Dependency-Check blocking (#55), required `scan`; `npm audit` blocking in `scan` (#52), Carter |
| SAST         | CodeQL (Java, TypeScript, Actions workflows)             | PR, `main`                           | high / critical (7.0+) | CodeQL job blocking; required-check setup pending |
| Secrets      | gitleaks                                                 | PR, `main`                           | any finding   | blocking (#56), Brandon; full history, real hits get rotated                    |
| IaC          | Trivy (`trivy config`) on `k8s/` and `infra/`      | PR, `main`                           | high          | blocking at HIGH/CRITICAL in the `iac` job (#57), Brandon |
| Image        | Trivy                                                    | PR, `main`, after build, before push | critical      | built (#49), Brandon; fails the push at critical                                |
| DAST         | OWASP ZAP baseline + auth/exposure probes on the candidate pair in Actions | PR, `main`, after image scan, before push | high          | blocking in the `image` job (#69), Brandon |

- **Start new scanners in report-only mode**, triage their findings, then make them blocking by CP3.
  Dependency-Check, CodeQL and `npm audit` have completed their initial triage and now fail CI; the IaC gate remains
  separate work.
- **Every accepted finding** gets an owner, a reason and an expiry date (Lab 40). Triage lives in
  `docs/security-findings.csv`; a Dependency-Check suppression in `dependency-check-suppressions.xml` or a Trivy ignore
  in `backend/.trivyignore.yaml` or an npm exception in `.github/npm-audit-exceptions.json` carries the same three fields.
- **`npm audit` skips dev dependencies** (`--omit=dev`): only what ships to the browser is gated. Today that's 4 highs
  in `@angular/*` 19.2.25, fixed only in Angular 22, so they're excepted per advisory until 2026-12-31 (`npm-001`), not force-upgraded (no `npm audit fix --force`).
- **Dependabot alerts stay on** to watch `main` between builds. They don't gate anything; a finding they raise is
  triaged in the same CSV.
- **Dependency-Check uses the NVD API key when it's there** (`NVD_API_KEY`) and caches the NVD data daily either way.
  Dependabot and fork PRs don't get the secret, so they update at the keyless rate limit. Pinned to 12.2.2: 13.0.0
  fails without a key (upstream dependency-check/DependencyCheck#8715).
- **First Dependency-Check triage:** Boot 3.3.5 scanned at 25 Critical / 46 High. Boot 3.5.16 with
  `tomcat.version` 10.1.60 and `postgresql.version` 42.7.13 fixed the reachable ones; the 13 left in Spring Framework
  6.2.19 and Log4j are false positives (module not on the classpath) or unused features accepted until 2026-12-31
  (`dc-001` to `dc-007`). Suppressions match the product's jars at that exact version, so an upgrade brings them back
  for review. The [2026-10-07 main scan](https://github.com/BrandonRobare/bean-brigade-customer-management-platform/actions/runs/37645992051/job/112876759213)
  returned `BUILD SUCCESS` and uploaded JSON/HTML reports. Removing `continue-on-error` enforces the Maven result;
  summaries and reports still run with `if: always()`, including on failure.
- **Dependency-Check gate proof (2026-10-07):** the [PR #97 run](https://github.com/BrandonRobare/bean-brigade-customer-management-platform/actions/runs/37670455515)
  passed `scan` and `image`. Removing only the CVE-2026-47884 suppression in temporary PR #98 made its
  [run](https://github.com/BrandonRobare/bean-brigade-customer-management-platform/actions/runs/37671633077) fail
  `scan` on `spring-core-6.2.19.jar` at CVSS 9.8. `image` was skipped; the findings summary and JSON/HTML report upload
  succeeded. GitHub reported the open, non-draft PR as blocked while required `scan` failed and required `secrets`
  passed. [PR #98](https://github.com/BrandonRobare/bean-brigade-customer-management-platform/pull/98) records that
  evidence and was closed without merging; its test branch was deleted. Before #55 closes, verify a successful
  `main` scan and image job after merging #97 and add that run to `defense/evidence-index.md`.
- **First Semgrep run:** 0 findings in the Java and TypeScript code. `p/owasp-top-ten` also scans workflow
  YAML and found 13: a shell injection in `capstone-cd.yml` (`sg-001`) and 12 actions pinned to a tag instead of a
  commit SHA (`sg-002`). None of the three packs flags a disabled CSRF (`csrf(c -> c.disable())`), so that stays a
  manual SAST check, as in Lab 40.
- **#53 blocking gate, 2026-10-06/07 (local):** fix the digest shell injection, pin all external actions to verified
  commit SHAs, and schedule weekly Dependabot action updates with a 7-day cooldown. On 2026-10-07 the repo went public,
  so the gate moved from Semgrep to CodeQL, which Module 51 (pp.18-19) names and which is free on public repos. The job
  uploads results to the Security tab and keeps the SARIF; `.github/scripts/check-codeql-sarif.py` fails the job at
  security-severity 7.0+ or on a missing, malformed or failed analysis, so it blocks `image` too. Medium, low and
  non-security results are listed in the summary and don't block. There is no exception list yet: a high/critical
  gets fixed, or a scoped, reviewed exception is added to the gate with an owner, reason and expiry in the CSV.
  Dismissing an alert in the Security tab does not change the gate. Required-check enforcement and remote
  failing/passing runs remain pending.
- **First gitleaks run:** 15 commits, no leaks, so the job fails on any finding from the start, with no report-only
  phase. CI on PR #62 agreed: 17 commits on the PR run, 16 on `main`, no leaks. It's a required check, so a leak stops
  the merge. It scans the PR's whole history, so a secret deleted in a later commit still fails: rotate it, then
  rewrite the branch or add it to `.gitleaksignore` with a reason. The `change-me` database password in CI,
  `compose.yaml` and the `application.yml` default isn't flagged: it's a local placeholder.
- **First Trivy image scan:** 0 critical, so the push goes ahead. The Ubuntu base has nothing above medium (45 medium,
  4 low). The 5 highs are all Jackson 2.21.4 inside the JAR, fixed in 2.21.7 (`tv-001`). Dependency-Check missed them:
  Trivy reads the GitHub advisory database, which had them before the NVD did.
- **First `main` image run:** blocked at the gate, so nothing was pushed. Trivy's DB picked up CVE-2026-47884 (XsltView
  RCE in spring-webmvc 6.2.19) between the PR run and the merge. It was already accepted as `dc-006`, but only
  Dependency-Check knew that. `backend/.trivyignore.yaml` (#65) gives the gate the same entry, scoped to 6.2.19 and
  expiring 2026-12-31; the report step still lists it in `trivy-api.json`.

## Artifact Identity

- **JAR:** `SHA256SUMS` with the commit and the run number (Lab 43).
- **Images:** pushed as `ghcr.io/brandonrobare/crm-api:sha-<commit>` and `crm-ui:sha-<commit>` so the `promote` job can find it. It's deployed only as
  `@sha256:<digest>`, and `:latest` is never used.
- **`artifact-manifest.json`:** version, commit, run ID, JAR checksum and API image digest, the shape from Lab 44, plus
  `images.api` / `images.ui` digests, the UI `dist` checksum, `builtAt` and the run URL (M44 Exercise 2). The script
  refuses anything that isn't a `sha256:` digest, so a tag can't end up in the release record.
- **Tags:** `v0.1.0` at CP2, `v1.0.0` for the demo. A tag never moves; a fix gets a new version.

## Secrets

| Name               | Stored as                                                                      | Used by                                                                              |
|--------------------|--------------------------------------------------------------------------------|--------------------------------------------------------------------------------------|
| `GITHUB_TOKEN`     | built in, minted per run                                                       | pushing the image (if the registry is GHCR), with `packages: write` on that job only |
| `KUBECONFIG`       | `production` environment secret: the `student08` kubeconfig (service account token, cluster CA) | `kubectl` in `promote` and `rollback`                                |
| `NVD_API_KEY`      | repository secret                                                              | Dependency-Check (`scan` job), optional                                              |

Runtime secrets (database password, JWT keys, demo passwords) live in Kubernetes Secrets in `student08`, not in GitHub. See the
environment strategy.

**OIDC:** Lab 51 prefers OIDC over stored credentials, but the cluster would have to trust GitHub's issuer, which needs
admin on the instructor's cluster. So `KUBECONFIG` holds the instructor-issued token, in the `production` environment
only (R-07).

**SAST:** CodeQL (#53), since the repo went public on 2026-10-07. Module 51 (p.18) and Lab 51 Step 3 require a SAST gate
that can fail the job; p.19 shows CodeQL's init, build, analyze flow, which this job follows. Semgrep CE was the
private-repo choice (code scanning on a private personal repo needs GitHub Code Security) and its first run is kept as
evidence of `sg-001`/`sg-002`. Leave GitHub's code scanning *default setup* off: it conflicts with this advanced-setup
workflow. Source: [CodeQL action](https://github.com/github/codeql-action), [SARIF security-severity](https://docs.github.com/en/code-security/code-scanning/integrating-with-code-scanning/sarif-support-for-code-scanning).

**Registry:** GHCR (#49). The `image` job pushes with the built-in `GITHUB_TOKEN` (`packages: write` on that job only),
so there's no registry secret in GitHub. Both packages are public, like the repo, so the cluster pulls them without
credentials. Moving to a private registry later means adding a pull secret back to the `crm-runtime` ServiceAccount.

## Open

1. **Kafka in tests:** `@EmbeddedKafka` or Testcontainers. Carter decides; it needs an ADR, and CI adds no Kafka service
   until then.

## Order

1. Real `frontend` and `backend` jobs (#39) for CP1. They go green once `InteractionService` merges.
2. Dependency-Check (#55) blocks CI, merging and image publication; confirm the post-merge `main` run. CodeQL
   `sast` (#53) also gates `image`; its required-check setup remains pending. `npm audit` (#52) blocks in `scan`
   with per-advisory exceptions. Add and triage the IaC scan separately (#57).
3. `iac` validates and lints `infra/` alongside the first Terraform / Ansible files.
4. `image` with its digest and Trivy scan, and the first trivial deploy to `student08` (manual `promote`) now that
   cluster access exists (2026-10-06).
5. `crm-ui` in the `image` job (#68), then `promote`, smoke and `rollback` by CP3, then `infra-plan` / `infra-apply`. Tag `v0.1.0` at CP2 and `v1.0.0` for the demo.
