# Release checklist

Fill one copy per `v*` tag in the GitHub release notes. Every line needs evidence, a link or a command output, not
"looks good". Flow and approvers are in the [release plan](release-plan.md).

## GO / NO-GO

| # | Check | Pass when | Evidence |
| - | ----- | --------- | -------- |
| 1 | Release identity | the tag's commit has a green `main` CI run, and its `artifact-manifest.json` lists both digests | run URL |
| 2 | Automated gates | `frontend`, `backend`, `sast`, `secrets` green; `scan` triaged in `security-findings.csv` | run URL |
| 3 | Image scan | Trivy: no untriaged CRITICAL in `crm-api` or `crm-ui` | `image-report` artifact |
| 4 | Migrations | none, or Bryan confirmed the previous image runs on the new schema | PR link |
| 5 | Platform | hostname, certificate and quota checked (`kubectl describe resourcequota`) | command output |
| 6 | Rollback target | `crm-release` ConfigMap has a `known-good` pair, or this is the first release | command output |
| 7 | Smoke | `scripts/smoke.sh` passes through the Ingress host: TLS, readiness, login, 401/403, `CUS-1001`, `CUS-1002`, `lab-request-001` | CD run URL |
| 8 | Owner | release owner on hand for the 30-minute watch window | name |

- Decision: GO / NO-GO
- Approver and timestamp:
- Evidence links:

## Automatic NO-GO

Any of these stops the release, whatever else passed:

- gitleaks finds a secret. Rotate it first.
- The digest doesn't match the manifest, or isn't a digest. CD refuses on its own.
- Readiness doesn't pass within the rollout timeout. CD stops before the UI and keeps the old pods.
- A migration drops or renames something the previous image still reads.
- Smoke fails. The release stays marked `failed` and doesn't become the rollback target.

## Evidence pack (Lab 44 Step 9)

| # | Confirm | Where |
| - | ------- | ----- |
| 1 | `artifact-manifest.json` filled | `image-report` artifact of the `main` run |
| 2 | deployed digests | CD summary and `kubectl -n student08 get deploy -o wide` |
| 3 | smoke with `CUS-1001`, `CUS-1002`, `lab-request-001` | CD `Smoke` step |
| 4 | rollback rehearsal: time and who checked it | [rollback runbook](rollback-runbook.md#rehearsals) |
| 5 | residual risks with owners | [risk register](risk-register.md) |
