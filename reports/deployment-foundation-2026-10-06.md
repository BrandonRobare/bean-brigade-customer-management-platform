# Steps 2–4 deployment foundation — 2026-10-06

Local implementation on branch `feat/capstone-k3s`, based on `063bb2f`. No GitHub push, image publication or live
deployment occurred. The original checkout remains on `main`. Infrastructure validation used the authorized
`student08` context only in server dry-run mode; subsequent inspection found no application resources.

## Implemented

- `c76979e`: Actuator probes and production JWT/login/RBAC, retaining the training token only for development.
- `73cd7a1`: separately packaged non-root nginx UI, relative API URLs and an Angular development proxy.
- `919f93e`: namespace-scoped k3s workloads, disks, configuration, Ingress, NetworkPolicies and Kafka topic Job.

## Verification evidence

| Check | Observed result |
| --- | --- |
| Java 21 `mvn -B -ntp -f backend/pom.xml clean verify` | 10 tests; zero failures, errors or skipped tests; tested executable JAR produced |
| Health failure case | Closed real Hikari pool yields readiness 503 and liveness 200; status-only output |
| Production security | Real login/JWT works; anonymous/fixed token/bad signature/expired/wrong issuer get 401; AGENT metrics and ADMIN-only mutations get 403; ADMIN metrics get 200 |
| Invalid login privacy | Oversized password and invalid username return 400; submitted password appears in neither captured logs nor response |
| Required production configuration | Actual application process rejects missing database URL, private signing key and demo-agent password |
| Node 22 production Angular build | Successful build at `frontend/dist/lab50-crm-ui/browser` |
| `bash scripts/check-ui.sh` | Real non-root/read-only UI container serves health and deep links, rejects API/static-file fallback and redirects forwarded HTTP to HTTPS |
| Angular dev proxy | Real development server forwards readiness JSON and authenticated interaction GET to the local API |
| Packaged API runtime | Real non-root/read-only container reads root-owned, group-readable mounted keys; forwarded HTTPS login and AGENT/ADMIN metrics checks pass |
| `kubectl kustomize openshift` | Successful render; changing only source platform settings propagates to Ingress, TLS, PVC classes and policy namespace |
| Strict Kubernetes server dry run | All 23 resources accepted; no resources persisted |
| Resource budget | Steady: requests 1350m / 1920 MiB, limits 2750m / 3840 MiB. API surge + topic Job: requests 1950m / 2816 MiB, limits 4000m / 5632 MiB; within checked quota |
| PostgreSQL image/configuration | Non-root/read-only startup and configured readiness pass; an inserted synthetic row survives restart |
| Kafka image/configuration | Non-root combined KRaft startup/readiness pass; exact topic Job command runs twice safely; topic metadata and a synthetic record survive restart |

Backend tests were written and observed failing before the behavior was implemented: missing readiness returned
404, production login returned 403, and a planned ADMIN mutation incorrectly reached MVC as 404. The UI container
check initially failed because no UI Dockerfile existed, then passed after implementation.
Fresh review found that Spring's default invalid-login validation logged rejected passwords. A regression reproduced
the disclosure before a controller-local sanitized error handler was added; the full 10-test backend run then passed.
The updated packaged API also returned 400 for an oversized password without writing it to container logs.

Runtime container checks ran locally on Apple Silicon. The pinned upstream image indexes include Linux amd64,
but actual amd64 scheduling and image pulls on the course cluster remain live-deployment evidence. Disk ownership
was prepared locally to simulate kubelet fsGroup setup; this does not validate the cluster's CSI implementation.
Long logs and temporary verification scripts remain in the ignored local execution workspace. Temporary runtime
containers, test volumes, networks and generated signing keys are removed after checking.

Final review fix: `2c91c5d`; local batch completion recorded on 2026-10-07. All temporary test containers,
volumes, networks and generated keys were removed. Final namespace inspection remained empty.

## Still pending

- Instructor confirmation of k3s as the OpenShift assignment substitution.
- Approved public hostname, trusted certificate, StorageClass, IngressClass, controller namespace/entrypoints,
  DNS namespace and NetworkPolicy enforcement. Placeholders remain explicit; the bundle is not ready to apply.
- Verified API/UI CI digest pair, scans/gates, GitHub push/review, CD, public smoke and rollback rehearsal (Steps 5–8).
- Angular real login/API calls/customer journey and Kafka application producer/consumer work. Broker persistence
  and manual CLI records are infrastructure evidence, not evidence that the application publishes/consumes events.
- Existing frontend dependency findings: Step 1 reported 32 high and 4 critical; triage belongs to Step 5.
- Full runbook/defense reconciliation in Step 7. This report does not establish a public release, trusted live TLS,
  OpenShift SCC compatibility, backup, replication or high availability.

See [API runtime](../docs/api-runtime.md), [UI ADR 0007](../docs/adrs/0007-serve-the-ui-from-its-own-nginx-image.md)
and [Kubernetes walkthrough](../openshift/README.md) for implementation and safe verification commands.
