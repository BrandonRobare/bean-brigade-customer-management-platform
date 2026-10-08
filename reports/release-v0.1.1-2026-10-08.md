# Release v0.1.1 and rollback rehearsal, 2026-10-08

Sanitized copy of the pipeline evidence, kept here because Actions logs and artifacts expire. Secrets show as `***`
in the source logs; the public host is left out.

## Identity

| | |
| --- | --- |
| Tag | `v0.1.1` (annotated) on `7e9f076b11395fbe74d0e7dd4fae9259be1c0a51` |
| CI run that built it | [37796983006](https://github.com/BrandonRobare/bean-brigade-customer-management-platform/actions/runs/37796983006), green |
| API image | `ghcr.io/brandonrobare/crm-api@sha256:7a79d52aeb572b82d800fff0bb8e2a1a34437a18c1e0c6841069b3956505bc4f` |
| UI image | `ghcr.io/brandonrobare/crm-ui@sha256:338c2f8c571646b3b771a1b6142f0a7a6421dcd375982a42b8206a50a1fec4e3` |
| JAR sha256 | `4cb87be6f7a1ba4239aa908100ecda2cb09e80c62e270105ccc59bcb62949fdb` |
| Manifest | [`artifact-manifest-v0.1.1.json`](artifact-manifest-v0.1.1.json), copied from that run's `image-report` artifact |
| GitHub Release | [v0.1.1](https://github.com/BrandonRobare/bean-brigade-customer-management-platform/releases/tag/v0.1.1) |

## Promote

[CD run 37799296200](https://github.com/BrandonRobare/bean-brigade-customer-management-platform/actions/runs/37799296200),
triggered by the tag push at 15:15:55Z.

- Waited on the `production` environment until Brandon approved: "v0.1.1 on 7e9f076, main CI green (run
  37796983006). Infra plan clean after the import, Ansible changed=0."
- Job ran 15:34:59Z to 15:37:08Z.
- Terraform `plan -detailed-exitcode` returned 0, so nothing was pending.
- Ansible recap: `ok=2 changed=0 unreachable=0 failed=0`.
- Deploy log: `Deployed 7e9f076…: crm-api@sha256:7a79d52a… and crm-ui@sha256:338c2f8c…`. It only deploys digests
  from that run's manifest.

Smoke against the deployed pair, through the Ingress:

```text
15:37:00 PASS readiness over trusted TLS
15:37:00 PASS UI served through the Ingress
15:37:00 PASS HTTP redirects to HTTPS
15:37:00 PASS anonymous API call is 401
15:37:02 PASS wrong password is 401
15:37:03 PASS real login for agent1 and admin1
15:37:03 PASS AGENT metrics is 403
15:37:03 PASS ADMIN metrics is 200
15:37:04 PASS read CUS-1001
15:37:04 PASS read CUS-1002
15:37:04 PASS record an interaction with lab-request-001
15:37:04 PASS unknown customer is 404
```

With smoke at 12/12, `crm-release` recorded this pair as known-good.

## Rollback rehearsal

The bad release was faked by setting v0.1.0's API image (`sha256:5f368577…`, no customer reads) by hand at 16:34:25Z.
It was serving by 16:34:47Z. Recovery went through the pipeline, not `kubectl`:
[CD rollback run 37809907764](https://github.com/BrandonRobare/bean-brigade-customer-management-platform/actions/runs/37809907764),
dispatched 16:34:51Z and approved by Brandon (about 51 s wait). The job took 34 s.

```text
16:36:10 Rolled back to 7e9f076…: crm-api@sha256:7a79d52a… and crm-ui@sha256:338c2f8c…
16:36:10 PASS readiness over trusted TLS
16:36:11 PASS UI served through the Ingress
16:36:11 PASS HTTP redirects to HTTPS
16:36:11 PASS anonymous API call is 401
16:36:12 PASS wrong password is 401
16:36:13 PASS real login for agent1 and admin1
16:36:13 PASS AGENT metrics is 403
16:36:13 PASS ADMIN metrics is 200
16:36:14 PASS read CUS-1001
16:36:14 PASS read CUS-1002
16:36:14 PASS record an interaction with lab-request-001
16:36:14 PASS unknown customer is 404
```

From the dispatch to green smoke took 1 min 24 s (target under 5 min). No pod restarts, and no gap in serving,
since `maxUnavailable` is 0. See [`docs/rollback-runbook.md`](../docs/rollback-runbook.md) Rehearsals.
