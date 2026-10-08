# Failure experiments, 2026-10-08

Lab 51 failure experiments 1-3, Lab 44 failure experiments 1-5 and Lab 44 Step 9, done on purpose to show the
pipeline stops what it should. Local runs used a throwaway branch that was never pushed.

| # | Experiment | How | What stopped it | Status |
| --- | --- | --- | --- | --- |
| L51-1 | Break a unit test | Backend assertion flipped locally (no Angular specs yet) | `mvn verify` fails, no JAR, so no image | done locally; GitHub run pending |
| L51-2 | Deploy rebuilds the JAR | Design + tests | CD has no Maven; it deploys only the digests in the tagged commit's `main` manifest | done |
| L51-3 | Skip environment approval | Approval records + environment rules | `production` needs Brandon's approval, and only `main` and `v*` can deploy | done |
| L44-1 | Promote a wrong digest | Release script tests | `release.sh deploy` refuses another commit's manifest, a tag instead of a digest, and images outside GHCR | done |
| L44-2 | Tabletop NO-GO | Talk-through with Bryan | Release checklist automatic NO-GO | pending (needs Bryan) |
| L44-3 | Roll back to the prior digest | Live on `student08` | 1 min 24 s to smoke 12/12 | done, see [release v0.1.1 report](release-v0.1.1-2026-10-08.md) |
| L44-4 | Use `:latest` once | Release script tests | Refused: not a digest; CI never pushes `latest` | done |
| L44-5 | Skip smoke | Workflow review | Smoke has no skip input; a pair only becomes known-good when it passes | done |
| SAST | SQL injection reaches the gate | CodeQL CLI locally, same version and suite as CI | `java/sql-injection` high (8.8), CodeQL gate exits 1 | done locally; GitHub run pending |
| L44-9 | Peer dry-run of the rollback runbook | Teammate reads it cold | - | pending (needs Bryan or Chad) |

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

In CI the same failure makes `backend` red, which skips `image` and blocks the merge (`backend` is a required check).
The GitHub run itself isn't done yet; PR #103's runs 37682029423 and 37683574215 show the same chain for the CI script
tests in `sast`.

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

In CI that exit fails `sast`, a required check, so the merge is blocked and `image` never runs. The GitHub run, and
the alert in the Security tab, are still to do.

## L44-2: NO-GO tabletop (to do with Bryan)

Scenario: v0.1.3 includes a Flyway migration that renames `customer.full_name`, which v0.1.2 still reads. Walk the
[release checklist](../docs/release-checklist.md) and record:

- Check 4 (Migrations): FAIL, the previous image can't run on the new schema. This is an automatic NO-GO.
- Decision: NO-GO. Owner: Brandon (release), Bryan (data). Timestamp:
- What makes it GO: expand then contract. Add the new column and write both, release, then drop the old one in a
  later release once nothing reads it.

## L44-9: peer dry-run (to do with Bryan or Chad)

They read [`docs/rollback-runbook.md`](../docs/rollback-runbook.md) cold and talk through each step without running
it. Note every question they had to ask (those are gaps to fix), the time taken, and add a row to the runbook's
Rehearsals table.
