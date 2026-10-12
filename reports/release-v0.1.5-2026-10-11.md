# Release v0.1.5, 2026-10-11

Sanitized copy of the pipeline evidence, kept here because Actions logs and artifacts expire. Secrets show as `***`
in the source logs; the public host is left out.

## Identity

| | |
| --- | --- |
| Tag | `v0.1.5` on `08052395a09ea59f99d93a83b6cc97be41500f0c` (#150 merge) |
| CI run that built it | [38155665063](https://github.com/BrandonRobare/bean-brigade-customer-management-platform/actions/runs/38155665063), green: 34 backend tests, 20 Angular specs |
| API image | `ghcr.io/brandonrobare/crm-api@sha256:9478f1c9759bf4c3ba774d59b6523d2b31e5eef0701040e79c466397247537f4` |
| UI image | `ghcr.io/brandonrobare/crm-ui@sha256:5de2425cff4ec9d3d69bf3c95ebc692c466eeeb53e57544ee8deef7dd6f3d24b` |
| JAR sha256 | `ce88c9af5353101442d0d7695a68fe98f7fae59ca8194ea0c90b15b113ccab41`, same as v0.1.4: no backend code changed |
| Manifest | [`artifact-manifest-v0.1.5.json`](artifact-manifest-v0.1.5.json), copied from that run's `image-report` artifact |
| GitHub Release | [v0.1.5](https://github.com/BrandonRobare/bean-brigade-customer-management-platform/releases/tag/v0.1.5) |

Ships #147 (search page), #148 (profile page), #149 (interaction form), #150 (SCSS), #146 (DLT topic), #143
(Angular tests in CI), #144 and #145 (clean-clone setup) and #141 (daily NVD cache).

## Promote

[CD run 38157113545](https://github.com/BrandonRobare/bean-brigade-customer-management-platform/actions/runs/38157113545),
attempt 1, 16:50:30Z to 16:52:31Z, approved by Brandon. No migrations and no Terraform changes since v0.1.4, so the
drift check passed and no infra-plan or infra-apply was needed. Ansible `ok=2 changed=0`.

```text
16:51:04 Backed up crm to /backup/crm-20261011T165100Z.dump.
16:51:29 job.batch/crm-kafka-topics condition met
16:52:11 deployment "crm-api" successfully rolled out
16:52:17 deployment "crm-ui" successfully rolled out
16:52:18 Deployed 0805239…: crm-api@sha256:9478f1c9… and crm-ui@sha256:5de2425c…
```

The topic Job created `crm.customer.interactions.v1.DLT` during Deploy. Checked afterwards with `kafka-topics.sh
--describe`: 1 partition, replication factor 1, `retention.ms=604800000`.

Smoke against the deployed pair, through the Ingress:

```text
16:52:18 PASS readiness over trusted TLS
16:52:18 PASS UI served through the Ingress
16:52:19 PASS HTTP redirects to HTTPS
16:52:19 PASS anonymous API call is 401
16:52:20 PASS wrong password is 401
16:52:22 PASS real login for agent1 and admin1
16:52:22 PASS AGENT metrics is 403
16:52:22 PASS ADMIN metrics is 200
16:52:23 PASS read CUS-1001
16:52:24 PASS read CUS-1002
16:52:26 PASS record an interaction with lab-request-001
16:52:27 PASS unknown customer is 404
```

With smoke at 12/12, `crm-release` recorded `0805239` as known-good; v0.1.4 is the previous pair.

## UI journey on production

First run of the new pages on `student08`, in a browser, 2026-10-11 about 17:00Z, signed in as `agent1`:

| Step | Result |
| --- | --- |
| Signed out: `/customers`, `/customers/CUS-1001`, `/interactions` | all go to `/login` |
| Sign in | lands on `/customers` with CUS-1001 and CUS-1002 |
| Search `ravi`, `cus-1001`, `zzzz` | Ravi Singh; Amina Khan; `No customers match "zzzz".` |
| Profile CUS-1001 | customer, status and timeline, newest first |
| Profile CUS-1002 | `No interactions yet.` |
| `/customers/CUS-9999` | `Customer CUS-9999 was not found.` (API 404) |
| Submit with an empty summary | `Summary is required.`, no request sent |
| Record a CALL with `<b>` in the summary | POST 201, timeline refreshed, the tag shown as text, form reset to 0 / 1024 |
| Kafka | the CALL event is on `crm.customer.interactions.v1` |

## Not covered by this release

- The UI sends the fixed `X-Correlation-Id: lab-request-001` on every request (`correlation.interceptor.ts`, unchanged
  since the Lab 48-52 starter). The API accepts it as valid, so every UI request and event shares one ID. The
  production event for the CALL above carries `lab-request-001`.
- Every release's smoke adds a `release smoke check` note to CUS-1001, so that profile's timeline is mostly smoke rows.
- Nothing consumes the events yet (#31).
- No `restore-drill` against `student08` yet.
