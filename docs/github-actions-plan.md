# GitHub Actions Plan

What runs on a pull request, on `main` and on a release tag, what each gate blocks, who owns it, and what evidence it
leaves. Lab 51 implements it. The pipeline's shape is drawn in [Delivery Path](architecture.md#delivery-path), and where
each build lands is in [the environment strategy](environment-strategy.md).

## Rules

1. The pipeline is the only way code reaches OpenShift. Nobody deploys from a laptop or edits the cluster by hand.
2. The gate order is fixed: build → verify → scan → publish → deploy.
3. Build once. The image is built and pushed once per `main` commit, from the JAR that the same run verified. Staging
   and production get the same digest, and nothing is rebuilt to deploy.
4. Deploys never run on `pull_request` or on a push to `main`. A person starts every deploy: the course's manual
   approval (Lab 48).
5. Secrets live in GitHub Environment secrets and OpenShift Secrets. Docs name them and never show values, and
   workflows never echo them.
6. Test and scan reports are kept as run artifacts. The ones that prove a claim are copied to `reports/` with a row in
   `defense/evidence-index.md`.
7. A red `main` gets fixed before any new feature merges. A failing check is fixed, never skipped (`-DskipTests`,
   `@Disabled`, a lowered threshold).

## Triggers

| Event                        | Workflow          | Jobs                                                                         | Deploys to                                     |
|------------------------------|-------------------|------------------------------------------------------------------------------|------------------------------------------------|
| `pull_request` to `main`     | `capstone-ci.yml` | frontend, backend, scan, sast, secrets, iac-check, image (build + scan)      | nothing                                        |
| push to `main`               | `capstone-ci.yml` | frontend, backend, scan, sast, secrets, iac-check, image (build, scan, push) | nothing                                        |
| tag `v*`                     | `capstone-cd.yml` | promote                                                                      | production                                     |
| manual (`workflow_dispatch`) | `capstone-cd.yml` | iac-plan, promote or rollback                                                | the chosen environment (staging or production) |

## Jobs and Gates

| Job         | Runs on                                               | What it does                                                                                                                                                                                 | Blocks merge                       | Owner                     | Evidence                                                                                                         |
|-------------|-------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------|---------------------------|------------------------------------------------------------------------------------------------------------------|
| `frontend`  | PR, `main`                                            | Node 22 (the brief's stack; the Lab 51 starter says 20) with npm cache, `npm ci`, `ng test --watch=false --browsers=ChromeHeadless` once specs exist, `ng build --configuration production`  | no                                 | Brandon; tests Chad       | run log; `dist/` artifact on `main`                                                                              |
| `backend`   | PR, `main`                                            | Java 21 (Temurin) with Maven cache, PostgreSQL 16 service, `mvn -B -ntp clean verify`                                                                                                        | no                                 | Brandon                   | Surefire reports artifact; JAR + `SHA256SUMS` on `main`                                                          |
| `scan`      | PR, `main`                                            | Dependency-Check (Maven, #55), `npm audit --omit=dev --audit-level=high` (#52), `trivy config` on `openshift/` and `infra/` (#57)                                                            | report-only until triage, then yes | Carter                    | `dependency-check-report` artifact + job summary, copied to `reports/` when it proves a claim; triage in `docs/security-findings.csv`, accepted findings with owner and expiry |
| `sast`      | PR, `main`                                            | Semgrep CE 1.178.0 with `p/java`, `p/typescript` and `p/owasp-top-ten` over the whole repo (#53)                                                                                             | report-only until triage, then yes | Brandon                   | `semgrep-report` artifact + job summary; triage in `docs/security-findings.csv`                                  |
| `secrets`   | PR, `main`                                            | gitleaks 8.30.1 (checksum-verified binary) over the full git history, secrets redacted (#56)                                                                                                 | yes: any leak fails                | Brandon                   | `gitleaks-report` artifact + job summary; false positives in `.gitleaksignore` with a reason                     |
| `iac-check` | PR, `main`                                            | `terraform fmt -check`, `terraform init -backend=false`, `terraform validate`; `ansible-playbook --syntax-check`                                                                             | no                                 | Brandon                   | run log                                                                                                          |
| `image`     | PR (build + scan), `main` (push)                      | build the `crm-api` image from the verified JAR and scan it with Trivy on every run; on `main`, push once to GHCR and record the digest (#49)                                                | fails at critical, not required yet | Brandon                   | `image-report` artifact (`artifact-manifest.json`, `trivy.json`) and job summary                                 |
| `iac-plan`  | manual                                                | `terraform plan` and `ansible-playbook --check` against the chosen project, from the [Terraform / Ansible plan](terraform-ansible-plan.md); apply only by the release owner, never from a PR | n/a                                | Brandon                   | plan output artifact                                                                                             |
| `promote`   | tag `v*` (production), manual (staging or production) | find the digest built for the commit, `oc set image` by digest, `oc rollout status`, smoke                                                                                                   | n/a                                | Brandon (release owner)   | smoke output in the run log; GitHub Release notes                                                                |
| `rollback`  | manual                                                | `oc rollout undo`, then rerun smoke                                                                                                                                                          | n/a                                | release owner             | run log                                                                                                          |

**Required checks:** `secrets`, enforced by the `protect-main` ruleset, so that job ID must not be renamed. `scan` and
`sast` join once the current findings are triaged. Don't put path filters on a required job: a PR that skips it waits
on the check forever.

**Branch rules (`protect-main`):** PR required, 1 approval from someone other than the author, stale approvals
dismissed on push, squash merge only, no force pushes or deletion, no bypass. The live ruleset requires 0 approvals;
set it to 1.

## Scans

| Scan         | Tool                                                     | When                                 | Fails at      | Status                                                                          |
|--------------|----------------------------------------------------------|--------------------------------------|---------------|---------------------------------------------------------------------------------|
| Dependencies | OWASP Dependency-Check (Maven), `npm audit --omit=dev`   | PR, `main`                           | CVSS 7 / high | Dependency-Check report-only (#55); `npm audit` planned (#52), Carter           |
| SAST         | Semgrep CE (`p/java`, `p/typescript`, `p/owasp-top-ten`) | PR, `main`                           | ERROR         | report-only (#53), Brandon; CodeQL needs GitHub Code Security on a private repo |
| Secrets      | gitleaks                                                 | PR, `main`                           | any finding   | blocking (#56), Brandon; full history, real hits get rotated                    |
| IaC          | Trivy (`trivy config`) on `openshift/` and `infra/`      | PR, `main`                           | high          | planned (#57), Brandon                                                          |
| Image        | Trivy                                                    | PR, `main`, after build, before push | critical      | built (#49), Brandon; fails the push at critical                                |
| DAST         | OWASP ZAP baseline against the staging Route             | after a staging `promote`            | advisory      | not planned                                                                     |

- **Start in report-only mode.** Dependabot already lists 45 alerts on `main` (1 critical, 22 high). Gating today would
  turn every PR red. Carter triages them first, then the gate becomes blocking, by CP3.
- **Every accepted finding** gets an owner, a reason and an expiry date (Lab 40). Triage lives in
  `docs/security-findings.csv`; a Dependency-Check suppression in `dependency-check-suppressions.xml` or a Trivy ignore
  in `backend/.trivyignore.yaml` carries the same three fields.
- **`npm audit` skips dev dependencies** (`--omit=dev`): only what ships to the browser is gated. Today that's 4 highs
  in `@angular/*` 19.2.25, fixed only in Angular 22, so they're triaged, not force-upgraded (no `npm audit fix --force`).
- **Dependabot alerts stay on** to watch `main` between builds. They don't gate anything; a finding they raise is
  triaged in the same CSV.
- **Dependency-Check uses the NVD API key when it's there** (`NVD_API_KEY`) and caches the NVD data weekly either way.
  Dependabot and fork PRs don't get the secret, so they update at the keyless rate limit. Pinned to 12.2.2: 13.0.0
  fails without a key (upstream dependency-check/DependencyCheck#8715).
- **First Dependency-Check triage:** Boot 3.3.5 scanned at 25 Critical / 46 High. Boot 3.5.16 with
  `tomcat.version` 10.1.60 and `postgresql.version` 42.7.13 fixed the reachable ones; the 13 left in Spring Framework
  6.2.19 and Log4j are false positives (module not on the classpath) or unused features accepted until 2026-12-31
  (`dc-001` to `dc-007`). Suppressions match the product's jars at that exact version, so an upgrade brings them back
  for review. The backend gate passes, so it can go blocking once CI confirms it.
- **First Semgrep run:** 0 findings in the Java and TypeScript code. `p/owasp-top-ten` also scans workflow
  YAML and found 13: a shell injection in `capstone-cd.yml` (`sg-001`) and 12 actions pinned to a tag instead of a
  commit SHA (`sg-002`). None of the three packs flags a disabled CSRF (`csrf(c -> c.disable())`), so that stays a
  manual SAST check, as in Lab 40.
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
  expiring 2026-12-31; the report step still lists it in `trivy.json`.

## Artifact Identity

- **JAR:** `SHA256SUMS` with the commit and the run number (Lab 43).
- **Image:** pushed as `ghcr.io/brandonrobare/crm-api:sha-<commit>` so the `promote` job can find it. It's deployed only as
  `@sha256:<digest>`, and `:latest` is never used.
- **`artifact-manifest.json`:** version, commit, run ID, JAR checksum and image digest, the shape from Lab 44.
- **Tags:** `v0.1.0` at CP2, `v1.0.0` for the demo. A tag never moves; a fix gets a new version.

## Secrets

| Name               | Stored as                                                                      | Used by                                                                              |
|--------------------|--------------------------------------------------------------------------------|--------------------------------------------------------------------------------------|
| `GITHUB_TOKEN`     | built in, minted per run                                                       | pushing the image (if the registry is GHCR), with `packages: write` on that job only |
| `OPENSHIFT_SERVER` | `staging` and `production` environment secret                                  | `oc login`                                                                           |
| `OPENSHIFT_TOKEN`  | `staging` and `production` environment secret, one service account per project | `oc login`                                                                           |
| `NVD_API_KEY`      | repository secret                                                              | Dependency-Check (`scan` job), optional                                              |

Runtime secrets (database password, JWT keys, demo passwords) live in OpenShift Secrets, not in GitHub. See the
environment strategy.

**OIDC:** OIDC over stored credentials. We use a service account token per project, stored as an environment secret and revoked after.

**SAST:** Semgrep CE (#53). CodeQL code scanning on a private personal repo needs GitHub Code Security, which Pro
doesn't include. The Module 40 deck (p.29) lists Semgrep with SonarQube and CodeQL as SAST tools, and Lab 51 leaves the
tool to the instructor. If the repo goes public, CodeQL can run beside it.

**Registry:** GHCR (#49). The `image` job pushes with the built-in `GITHUB_TOKEN` (`packages: write` on that job only),
so there's no registry secret. The package is private like the repo, so OpenShift pulls it with a pull secret (a token
with `read:packages`) in each project. Moving to the OpenShift registry or ECR later changes only the push step.

## Open

1. **Frontend image:** nginx, or served by `crm-api`. Chad decides, with Brandon; it's tied to the API URL rule in the
   environment strategy.
2. **Kafka in tests:** `@EmbeddedKafka` or Testcontainers. Carter decides; it needs an ADR, and CI adds no Kafka service
   until then.

## Order

1. Real `frontend` and `backend` jobs (#39) for CP1. They go green once `InteractionService` merges.
2. `scan` (#55, #52, #53) in report-only
3. `iac-check` alongside the first Terraform / Ansible files.
4. `image` with its digest and Trivy scan, and the first trivial staging deploy (manual `promote`) as soon as `oc`
   access exists.
5. `promote`, smoke, `rollback` and `iac-plan` by CP3. Tag `v0.1.0` at CP2 and `v1.0.0` for the demo.
