# Release v0.1.4, 2026-10-09

Sanitized copy of the pipeline evidence, kept here because Actions logs and artifacts expire. Secrets show as `***`
in the source logs; the public host is left out.

## Identity

| | |
| --- | --- |
| Tag | `v0.1.4` on `b718eeb0ba2233ed99fc231ea6c62e4b3be68818` (#139 merge) |
| CI run that built it | [37934695166](https://github.com/BrandonRobare/bean-brigade-customer-management-platform/actions/runs/37934695166), green |
| API image | `ghcr.io/brandonrobare/crm-api@sha256:03c8320db36d531242a2e374d4402b0c461afc87edd5098b67497b9d995a590f` |
| UI image | `ghcr.io/brandonrobare/crm-ui@sha256:34210e3ed732561e021d5d7c638c32d39b3b72da44495d92080c71570e3ff514` |
| JAR sha256 | `ce88c9af5353101442d0d7695a68fe98f7fae59ca8194ea0c90b15b113ccab41` |
| Manifest | [`artifact-manifest-v0.1.4.json`](artifact-manifest-v0.1.4.json), copied from that run's `image-report` artifact |
| GitHub Release | [v0.1.4](https://github.com/BrandonRobare/bean-brigade-customer-management-platform/releases/tag/v0.1.4) |

Ships #137 (real login), #138 (two API replicas, pre-release backup), #139 (UI theme) and #140 (correlation ID
in the MDC).

## Drift gate refused the first attempt

[CD run 37936816681](https://github.com/BrandonRobare/bean-brigade-customer-management-platform/actions/runs/37936816681),
attempt 1, 13:26:30Z to 13:27:14Z. #138 added Terraform resources that weren't applied yet, so
`terraform plan -detailed-exitcode` returned 2 and promote stopped before Configure:

```text
The namespace doesn't match infra/terraform. Run CD with infra-plan, review it, then infra-apply.
```

Nothing was deployed; v0.1.3 kept serving.

## Infrastructure

- [infra-plan run 37937333292](https://github.com/BrandonRobare/bean-brigade-customer-management-platform/actions/runs/37937333292),
  approved by Brandon. Plan digest `a2f153c09ee512d2`; Ansible check `changed=0`.

  | Resource | Action |
  | --- | --- |
  | `kubernetes_network_policy_v1.db_backup_egress` | create |
  | `kubernetes_network_policy_v1.postgres_ingress` | update |
  | `kubernetes_persistent_volume_claim_v1.backup` | create |

- [infra-apply run 37937872514](https://github.com/BrandonRobare/bean-brigade-customer-management-platform/actions/runs/37937872514)
  applied exactly that digest, 13:35:15Z to 13:36:11Z. Both Ansible runs `ok=2 changed=0`.

## Promote

Attempt 2 of the same CD run, 13:37:22Z to 13:40:16Z, approved by Brandon. The drift check passed.

```text
13:38:12 Backed up crm to /backup/crm-20261009T133806Z.dump.
13:38:28 job.batch/crm-kafka-topics condition met
13:39:55 deployment "crm-api" successfully rolled out
13:40:01 deployment "crm-ui" successfully rolled out
13:40:02 Deployed b718eeb…: crm-api@sha256:03c8320d… and crm-ui@sha256:34210e3e…
```

The backup ran before anything was applied. The API went from one 500m pod to two 250m pods inside the
`student08` quota.

Smoke against the deployed pair, through the Ingress:

```text
13:40:02 PASS readiness over trusted TLS
13:40:03 PASS UI served through the Ingress
13:40:03 PASS HTTP redirects to HTTPS
13:40:03 PASS anonymous API call is 401
13:40:04 PASS wrong password is 401
13:40:06 PASS real login for agent1 and admin1
13:40:07 PASS AGENT metrics is 403
13:40:07 PASS ADMIN metrics is 200
13:40:08 PASS read CUS-1001
13:40:09 PASS read CUS-1002
13:40:11 PASS record an interaction with lab-request-001
13:40:11 PASS unknown customer is 404
```

With smoke at 12/12, `crm-release` recorded `b718eeb` as known-good; v0.1.3 is the previous pair.

## Not covered by this release

- No `restore-drill` against `student08` yet; the timed restores so far are on local k3d.
- No request-under-load check during this rollout; the zero-error counts are from k3d (#138).
- Correlation IDs in production logs weren't sampled.
