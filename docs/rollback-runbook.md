# Rollback runbook

Put the last known-good API/UI pair back, through the pipeline, then prove it with smoke. Never by hand.

## Triggers

Roll back when any of these holds after a release:

- Smoke fails in the CD run.
- Readiness keeps failing for more than 3 minutes, or a pod is in `CrashLoopBackOff`.
- Sign-in, customer lookup or recording an interaction returns `5xx` for real users.
- An event-driven flow stops (Kafka consumer lag keeps growing) once messaging ships.

Don't roll back when the problem is data or a migration that already contracted the schema. See [Limits](#limits).

## Authority

Brandon, as release owner, decides. Anyone on the team may start the job if Brandon can't be reached and the demo
is broken; say so in the team channel first.

## Steps

1. Check what's running and what's known-good:
   ```bash
   kubectl -n student08 get configmap crm-release -o jsonpath='{.data}'
   ```
   `release-*` is what was last deployed, `known-good-*` is the last pair whose smoke passed.
2. GitHub → Actions → **Capstone CD** → **Run workflow** → `rollback`. Start a timer.
3. The job sets the known-good API image, waits for readiness, then the UI, then runs `scripts/smoke.sh`. It refuses if
   nothing is recorded yet or the known-good pair is already running.
4. Check the job: `Smoke passed.` in the log, then `crm-release` shows `status: succeeded`.
5. Write down the time from step 2 to the green smoke, the digests, and who checked it, under [Rehearsals](#rehearsals).

If GitHub Actions itself is down, the release owner runs the same script with the same kubeconfig and records why:

```bash
NAMESPACE=student08 bash scripts/release.sh rollback && bash scripts/smoke.sh && bash scripts/release.sh mark succeeded
```

## Limits

- Rollback swaps images. It never runs Flyway backwards or deletes a PVC.
- If the bad release contracted the schema (dropped or renamed a column), the old image may not start. Stop, and fix
  forward with a new tag.
- Data written by the bad release stays. Clean up with a reviewed migration or script, not by hand in production.
- If the data itself is lost or corrupted, the dump the release took before deploying is in the `crm-backup` PVC
  (`last-backup` in `crm-release`). Restoring it over `crm` is a team decision, not part of rollback; the
  `restore-drill` CD job proves the dumps restore without touching `crm`.
- Config and Secrets aren't part of the pair. If a Secret change caused the problem, fix the environment secret and
  release again.

## Rehearsals

| Date | Where | From → to | Time to green smoke | Checked by |
| --- | --- | --- | --- | --- |
| 2026-10-07 | local k3d, `scripts/release.sh` | API that never gets ready → known-good v2 | about 1 s: the old pods never stopped serving | automated local check |
| 2026-10-07 | local k3d, `scripts/release.sh` | released pair that failed smoke → known-good v2, new pods | 18-21 s | automated local check |
| 2026-10-08 | `student08`, CD `rollback` job ([run 37809907764](https://github.com/BrandonRobare/bean-brigade-customer-management-platform/actions/runs/37809907764)) | `v0.1.0` API set by hand as a bad release → known-good `v0.1.1` | 1 min 24 s, including a 51 s approval; the job took 34 s | Brandon |

During one release plus rollback on k3d, 6,910 requests to `/` and readiness all returned 200. Without the 5-second
`preStop` sleep on the API and UI, the same test lost 13 of 5,004 requests to 502s and dropped connections, so keep it.

On `student08` the bad release was `kubectl set image` to the `v0.1.0` API, which has no customer reads. Re-running the
`v0.1.0` CD run would also have applied that commit's older manifests, so only the image was changed. Recovery went
through the pipeline as in [Steps](#steps), with no pod restarts. Traffic wasn't measured on this run.
