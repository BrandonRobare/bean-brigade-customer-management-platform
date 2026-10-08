# Failure experiments, 2026-10-08

Lab 51 failure experiments 1-3, Lab 44 failure experiments 1-5 and Lab 44 Step 9, done on purpose to show the
pipeline stops what it should. The two red CI runs come from draft [PR #126](https://github.com/BrandonRobare/bean-brigade-customer-management-platform/pull/126), closed unmerged, branch deleted.

| # | Experiment | How | What stopped it | Status |
| --- | --- | --- | --- | --- |
| L51-1 | Break a unit test | Backend assertion flipped locally (no Angular specs yet) | `backend` red, `image` skipped, merge blocked | done: local + [run 37833750321](https://github.com/BrandonRobare/bean-brigade-customer-management-platform/actions/runs/37833750321) |
| L51-2 | Deploy rebuilds the JAR | Design + tests | CD has no Maven; it deploys only the digests in the tagged commit's `main` manifest | done |
| L51-3 | Skip environment approval | Approval records + environment rules | `production` needs Brandon's approval, and only `main` and `v*` can deploy | done |
| L44-1 | Promote a wrong digest | Release script tests | `release.sh deploy` refuses another commit's manifest, a tag instead of a digest, and images outside GHCR | done |
| L44-2 | Tabletop NO-GO | - | - | skipped (practice step) |
| L44-3 | Roll back to the prior digest | Live on `student08` | 1 min 24 s to smoke 12/12 | done, see [release v0.1.1 report](release-v0.1.1-2026-10-08.md) |
| L44-4 | Use `:latest` once | Release script tests | Refused: not a digest; CI never pushes `latest` | done |
| L44-5 | Skip smoke | Workflow review | Smoke has no skip input; a pair only becomes known-good when it passes | done |
| SAST | SQL injection reaches the gate | CodeQL CLI locally, same version and suite as CI | `java/sql-injection` high (8.8): `sast` red, `image` skipped, merge blocked | done: local + [run 37834042916](https://github.com/BrandonRobare/bean-brigade-customer-management-platform/actions/runs/37834042916) |
| L44-9 | Peer dry-run of the rollback runbook | - | - | skipped (practice step) |

## L51-1: failing-test drill

Throwaway commit: in `HealthApiIT`, expect readiness to be 200 while the database is down (a real regression
pattern: the probe stops reporting the outage). Ran the same command as the `backend` job against a throwaway
Postgres 16:

```text
[ERROR] Tests run: 1, Failures: 1, Errors: 0, Skipped: 0 <<< FAILURE! -- in com.northstar.crm.api.HealthApiIT
java.lang.AssertionError: Status expected:<200> but was:<503>
[ERROR]   HealthApiIT.databaseOutageRemovesReadinessWithoutFailingLivenessOrLeakingDetails:30 Status expected:<200> but was:<503>
[ERROR] Tests run: 16, Failures: 1, Errors: 0, Skipped: 0
```

`target/surefire-reports/TEST-com.northstar.crm.api.HealthApiIT.xml` holds the failure (the same files CI uploads as
`test-reports`), and no JAR was built, so CI's `image` job would have nothing to package. Reverted, ran again:
16 tests, 0 failures, JAR back.

On GitHub, draft PR #126 with the same commit: [run 37833750321](https://github.com/BrandonRobare/bean-brigade-customer-management-platform/actions/runs/37833750321) failed `backend`, skipped `image` (no `crm-api-jar` artifact)
and the PR showed `BLOCKED`. `gh run download 37833750321 -n test-reports` gave the same
`Status expected:<200> but was:<503>` in `TEST-com.northstar.crm.api.HealthApiIT.xml`. After the fix and the SAST
step below, [run 37834456992](https://github.com/BrandonRobare/bean-brigade-customer-management-platform/actions/runs/37834456992) was green on all seven jobs.

Rerun steps: download the report with `gh run download <run> -n test-reports`, open the failing class's XML or
TXT, fix, push. Never `-DskipTests`, never re-run hoping it goes green.

## L51-2 and L44-1, L44-4: nothing rebuilds, only the manifest's digests deploy

- `capstone-cd.yml` contains no Maven or npm build. `promote` finds the green `main` CI run for the tagged commit and
  fails if there isn't one ("No successful main CI run for <sha>. Tag a commit that main has built.").
- `scripts/release.sh deploy` takes the API and UI from that run's `artifact-manifest.json`. Six tests in
  `.github/scripts/tests/test_release_script.py` cover it and ran green locally: a manifest without both images, a
  manifest from another commit, a tag instead of a digest and an image outside the registry are all refused; a valid
  manifest deploys; a failed API rollout fails the release and leaves the UI alone.
- CI pushes only `sha-<commit>` tags, never `latest`, and CD rejects anything that isn't `@sha256:`.

## L51-3: approval can't be skipped

- The `production` environment requires a reviewer (Brandon) and only accepts `main` and `v*` tags. `promote`,
  `rollback` and `infra` all run in it.
- Every CD run so far waited for and got that approval: 37694325503 (v0.1.0), 37799296200 (v0.1.1), 37809907764
  (rollback) and 37831406587 (v0.1.2), all "BrandonRobare approved production".
- Known gap: the release owner and the only reviewer are the same person, and self-review is allowed. Adding a second
  reviewer would make it a two-person release.

## L44-5: smoke can't be skipped

The `Smoke` step has no condition or input to turn it off. `Record result` marks the release `succeeded` only when
smoke passed; otherwise it stays `failed` and doesn't become the rollback target.

## SAST: SQL injection reaches the gate

Throwaway commit: a `@GetMapping` that concatenates a `@RequestParam` into `JdbcTemplate.queryForList`, the classic
user-input-to-query flow. Never pushed.

CodeQL CLI 2.27.1 (the bundle the pinned `codeql-action` v4.38.2 uses, checksum verified), Java database traced from
the same `./mvnw -DskipTests compile` as CI, `java-code-scanning` suite, then CI's own
`.github/scripts/check-codeql-sarif.py` on the SARIF:

| Run | Results | Gate |
| --- | --- | --- |
| Baseline (`main` code) | 2 × `java/spring-disabled-csrf-protection`, both excepted as cq-001 | exit 0 |
| With the endpoint | the same 2, plus `java/sql-injection` (security-severity 8.8, high) at `CustomerLookupDrill.java:20` | exit 1, "CodeQL gate failed: high/critical findings" |

On GitHub, the same commit on PR #126: [run 37834042916](https://github.com/BrandonRobare/bean-brigade-customer-management-platform/actions/runs/37834042916) passed `backend` but failed `sast` at the CodeQL gate, skipped `image`,
and the PR showed `BLOCKED`. The Security tab raised alert #4 (`java/sql-injection`, high,
`CustomerLookupDrill.java:20`) on the PR only; it cleared when the next commit removed the endpoint ([run 37834456992](https://github.com/BrandonRobare/bean-brigade-customer-management-platform/actions/runs/37834456992), green), and
`main` never had it.

## Skipped

L44-2 (NO-GO tabletop) and L44-9 (peer dry-run of the rollback runbook) are Lab 44 practice steps, not capstone
requirements, and were skipped on 2026-10-08. The automatic NO-GO rules are in the
[release checklist](../docs/release-checklist.md), and rollback was rehearsed live (L44-3).
