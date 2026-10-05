# Environment Strategy

Where the CRM runs, how one build moves between environments, and what changes between them. The jobs that move it
are in [the GitHub Actions plan](github-actions-plan.md).

## Environments

| Environment | Where                                                             | What gets there                                                  | Gate                                       | Data                                                            |
|-------------|-------------------------------------------------------------------|------------------------------------------------------------------|--------------------------------------------|-----------------------------------------------------------------|
| local       | laptop: `docker compose up -d`, `mvn spring-boot:run`, `ng serve` | the working tree                                                 | none                                       | Flyway seeds (Amina, Ravi); `docker compose down -v` wipes them |
| ci          | GitHub-hosted runner with a PostgreSQL 16 service                 | every PR and every push to `main`                                | required checks `frontend` and `backend`   | an empty database each run, seeded by Flyway                    |
| staging     | OpenShift project `crm-staging`                                   | a digest from a green `main` run, when the release owner runs CD | manual dispatch; smoke                     | Flyway seeds; synthetic data only                               |
| production  | OpenShift project `crm-prod`, the demo environment                | `v*` tags only, with the digest staging already ran              | release owner's tag; smoke; rollback ready | the same fixtures                                               |

Lab 48 names four environments: dev, test, stage and prod-like. Ours map to them as dev = local, test = ci,
stage = `crm-staging`, prod-like = `crm-prod`. Dev and test aren't OpenShift projects unless we get more than two; if
Adel gives us four, they become `crm-dev` and `crm-test`, deployed the same way as staging.

The project names are proposed until Adel confirms them. If we only get **one** project, production deploys there from
tags, staging is skipped, and the gap goes in the risk register. Lab 48 warns against one project for every environment.

## Promotion

1. A PR passes `frontend` and `backend` in ci and gets an approval.
2. The merge to `main` builds the image once, with digest `D`. Nothing deploys on a merge.
3. The release owner runs CD for staging with `D`, and the smoke test passes.
4. The release owner tags that commit `vX.Y.Z`.
5. Production deploys the same `D`, and the smoke test runs again.
6. If smoke fails: `rollback` returns to the previous digest and reruns smoke, and the fix goes through a new PR.

The artifact never changes between environments; only configuration does. Tags never move, and nothing is deployed
as `:latest`.

## What Changes per Environment

| Setting                                           | local                                       | ci                                      | staging and production                       |
|---------------------------------------------------|---------------------------------------------|-----------------------------------------|----------------------------------------------|
| `SPRING_DATASOURCE_URL`, `_USERNAME`, `_PASSWORD` | `.env` → compose PostgreSQL                 | workflow env, test-only values          | OpenShift Secret `crm-db`                    |
| Kafka bootstrap servers                           | `localhost:9092`                            | open (see the Actions plan)             | ConfigMap `crm-api-config`                   |
| `JWT_PRIVATE_KEY`, `JWT_PUBLIC_KEY`               | `.env` → `file:.keys/...`                   | the `test` profile generates a key pair | OpenShift Secret `crm-jwt`, mounted as files |
| `DEMO_AGENT_PASSWORD`, `DEMO_ADMIN_PASSWORD`      | `.env`                                      | test-only values                        | OpenShift Secret `crm-demo-users`            |
| CORS allowed origins                              | `http://localhost:4200`                     | not used                                | ConfigMap: the Route's host                  |
| Spring profile                                    | `dev` (accepts `lab-demo-token` until 10/6) | `test`                                  | none                                         |
| Angular API base URL                              | `http://localhost:8080`                     | build only                              | relative, same origin as the UI              |

**One Angular build for every environment.** `environment.ts` hardcodes `apiBaseUrl: 'http://localhost:8080'`, and
there is no production file replacement, so a production build would call the agent's own laptop. The production build
therefore uses a relative API URL, and in OpenShift the UI and API share one origin: the API serves the UI, nginx routes
`/api` to `crm-api`, or a path-based Route does. That also removes CORS from production. Which of the three is open,
decided with the frontend image. Building the UI once per environment is not an option.

**Missing config fails closed.** Outside `dev`, the app refuses to start without the database URL, the JWT keys or the
demo passwords. There are no fallback defaults.

## Where Config Lives

- **Non-secret settings:** ConfigMap `crm-api-config` in each project, passed to the Deployment as env vars.
- **Secrets:** OpenShift Secrets `crm-db`, `crm-jwt` and `crm-demo-users`. The CD workflow creates them from GitHub
  Environment secrets (proposed), so nobody creates them by hand. The Terraform / Ansible plan may take this over.
- **Registry pull secret:** if the image registry is private, a pull secret is linked to the project's default service
  account.

## Access and Approvals

|                    | staging                                    | production                                                       |
|--------------------|--------------------------------------------|------------------------------------------------------------------|
| GitHub Environment | `staging`, deployment branch `main`        | `production`, deployment tags `v*`                               |
| Who deploys        | the release owner (Brandon), by running CD | the release owner (Brandon), by pushing a `v*` tag or running CD |
| `oc` credentials   | a service account token for `crm-staging`  | a separate service account token for `crm-prod`                  |

GitHub Pro gives a private repo environments, environment secrets and branch / tag rules, but not required reviewers or
wait timers. So every deploy is started by hand, which is the course's manual approval (Lab 48): for staging, the
release
owner runs CD; for production, the release owner pushes the tag after a green staging smoke. That the approval isn't
enforced by GitHub is a row in the risk register. Teammates use their own `oc login` to read logs and pods, never to
change anything.

## OpenShift Constraints

- **Pods run as a random UID** under the restricted security context, so images can't need root, and manifests don't set
  `runAsUser`.
- **Probes** use Actuator liveness and readiness. The Route doesn't expose any other Actuator endpoint.
- **Names:** `crm-api`, plus `crm-ui` if the UI gets its own image. The `lab50-crm` names are renamed before anything is
  deployed.

## Data and Migrations

- Flyway migrates each environment's database when the app starts.
- **Migrations are expand-before-contract** (Lab 44), so the previous digest still runs after a rollback. Dropping or
  renaming a column breaks digest rollback, so that kind of change takes two releases.
- Synthetic fixtures only, in every environment.
