# Environment Strategy

Where the CRM runs, how one build moves between environments, and what changes between them. The jobs that move it
are in [the GitHub Actions plan](github-actions-plan.md).

## Environments

| Environment | Where                                                                 | What gets there                                          | Gate                                       | Data                                                            |
|-------------|-----------------------------------------------------------------------|----------------------------------------------------------|--------------------------------------------|-----------------------------------------------------------------|
| local       | laptop: `docker compose up -d`, `mvn spring-boot:run`, `ng serve`     | the working tree                                         | none                                       | Flyway seeds (Amina, Ravi); `docker compose down -v` wipes them |
| ci          | GitHub-hosted runner with a PostgreSQL 16 service                     | every PR and every push to `main`                        | required checks `frontend` and `backend`   | an empty database each run, seeded by Flyway                    |
| staging     | skipped: we have one namespace (R-01)                                 |                                                          |                                            |                                                                 |
| production  | k3s namespace `student08` on the course cluster, the demo environment | `v*` tags only, with the digest a green `main` run built | release owner's tag; smoke; rollback ready | Flyway seeds; synthetic data only                               |

Lab 48 names four environments: dev, test, stage and prod-like. Ours map to them as dev = local, test = ci,
prod-like = `student08`. Stage is skipped.

**One namespace.** The course cluster is k3s, not OpenShift, and each of us gets one namespace with a service account
token. Production uses Brandon's, `student08`; we can't create others. Staging and production don't share it: Lab 48
warns against one project for every environment, and the two would share a quota and a token. So staging is skipped,
and the gap is R-01 in the [risk register](risk-register.md). If Adel gives us a second namespace, staging comes back
as a `staging` GitHub Environment with its own token, and `promote` doesn't change.

## Promotion

1. A PR passes `frontend` and `backend` in ci and gets an approval.
2. The merge to `main` builds the image once, with digest `D`, and scans it before the push. Nothing deploys on a merge.
3. The release owner tags that commit `vX.Y.Z`.
4. Production deploys `D`, and the smoke test runs.
5. If smoke fails: `rollback` returns to the previous digest and reruns smoke, and the fix goes through a new PR.

With no staging, the `main` run (`mvn verify` against PostgreSQL 16, then the image scan) is the last check before
production; smoke and rollback cover what it can't. The artifact is never rebuilt to deploy; only configuration
changes. Tags never move, and nothing is deployed as `:latest`.

## What Changes per Environment

| Setting                                           | local                                       | ci                                      | production                               |
|---------------------------------------------------|---------------------------------------------|-----------------------------------------|------------------------------------------|
| `SPRING_DATASOURCE_URL`, `_USERNAME`, `_PASSWORD` | `.env` → compose PostgreSQL                 | workflow env, test-only values          | Secret `crm-db`                          |
| Kafka bootstrap servers                           | `localhost:9092`                            | open (see the Actions plan)             | ConfigMap `crm-api-config`               |
| `JWT_PRIVATE_KEY`, `JWT_PUBLIC_KEY`               | `.env` → `file:.keys/...`                   | the `test` profile generates a key pair | Secret `crm-jwt`, mounted as files       |
| `DEMO_AGENT_PASSWORD`, `DEMO_ADMIN_PASSWORD`      | `.env`                                      | test-only values                        | Secret `crm-demo-users`                  |
| CORS allowed origins                              | `http://localhost:4200`                     | not used                                | not used: one origin through the Ingress |
| Spring profile                                    | `dev` (accepts `lab-demo-token` until 10/6) | `test`                                  | none                                     |
| Angular API base URL                              | `http://localhost:8080`                     | build only                              | relative, same origin as the UI          |

**One Angular build for every environment.** `environment.ts` hardcodes `apiBaseUrl: 'http://localhost:8080'`, and
there is no production file replacement, so a production build would call the agent's own laptop. The production build
therefore uses a relative API URL. In the cluster, `crm-ui` is its own nginx image, and one Ingress host sends `/api`
to `crm-api` and everything else to `crm-ui` ([ADR 0007](adrs/0007-serve-the-ui-from-its-own-nginx-image.md), #68).
The UI and API share one origin, so production needs no CORS. Building the UI once per environment is not an option.

**Missing config fails closed.** Outside `dev`, the app refuses to start without the database URL, the JWT keys or the
demo passwords. There are no fallback defaults.

## Where Config Lives

- **Non-secret settings:** ConfigMap `crm-api-config` in `student08`, passed to the Deployment as env vars.
- **Secrets:** Kubernetes Secrets `crm-db`, `crm-jwt` and `crm-demo-users`. The CD workflow creates them from GitHub
  Environment secrets (proposed), so nobody creates them by hand. The Terraform / Ansible plan may take this over.
- **Registry pull secret:** GHCR is private, so each Deployment lists `ghcr-pull` under `imagePullSecrets`: a
  `docker-registry` Secret holding a classic token with `read:packages` only. Brandon creates it once with
  `kubectl create secret docker-registry`; the token is never in Git (R-08).

## Access and Approvals

- **GitHub Environment:** `production`, deployment tags `v*`.
- **Who deploys:** the release owner (Brandon), by pushing a `v*` tag or running CD.
- **Cluster credentials:** the instructor-issued `student08` kubeconfig, stored as the `production` environment secret
  `KUBECONFIG`. Its service account token doesn't expire (R-07).

GitHub Pro gives a private repo environments, environment secrets and branch / tag rules, but not required reviewers or
wait timers. So every deploy is started by hand, which is the course's manual approval (Lab 48): the release owner
pushes the tag once the `main` run is green. That the approval isn't enforced by GitHub is R-04 in the risk register.
Teammates only have their own namespaces; if they need to read pods and logs in `student08`, a read-only RoleBinding
for their service account covers it, never write access.

## Cluster Constraints

- **k3s, not OpenShift.** No Routes or Projects: plain Deployments, Services and one Ingress (Traefik), driven with
  `kubectl`. The manifests still live in `openshift/`, the brief's folder name (R-02).
- **Non-root images.** k3s doesn't force a random UID the way OpenShift does, but the images still run as a non-root
  numeric user and the manifests don't set `runAsUser`, so they'd run on OpenShift unchanged.
- **Quota:** 15 pods, 2 CPU and 3Gi of requests, 4 CPU and 6Gi of limits. A container without its own gets 250m /
  256Mi requested and a 500m / 512Mi limit, so every container sets its own, and a rolling update needs room for one
  extra pod (R-06).
- **Probes** use Actuator liveness and readiness. The only other Actuator endpoint is `metrics`, ADMIN only (#54).
- **Names:** `crm-api`, `crm-ui`, `crm-postgres`. The `lab50-crm` names are renamed before anything is deployed.
- **Database:** `crm-postgres` runs in the namespace on a PVC. Fine for a demo with synthetic data, not how production
  would run it (R-03).

## Data and Migrations

- Flyway migrates the database when the app starts.
- **Migrations are expand-before-contract** (Lab 44), so the previous digest still runs after a rollback. Dropping or
  renaming a column breaks digest rollback, so that kind of change takes two releases.
- Synthetic fixtures only, in every environment.
