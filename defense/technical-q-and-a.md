# Technical Q&A

Practice answers for the panel, as of `v1.1.0` (`9bf13d2`). Each one is claim, evidence, trade-off, next step, and
should take about a minute out loud. If the honest answer is "we didn't build that", say so and say what we'd do.
The links are for us. Don't read run or PR numbers to the panel.

## 1. Where is the JWT checked?

**Claim:** In the API, on every `/api/**` request, before any controller runs. Angular only attaches the token. The
guard hides screens, but anyone can call the API with curl, so the API is the lock.

**Evidence:** [`SecurityConfiguration`](../backend/src/main/java/com/northstar/crm/security/SecurityConfiguration.java)
runs Spring Security's resource server with RS256 keys. [`ProductionSecurityIT`](../backend/src/test/java/com/northstar/crm/api/ProductionSecurityIT.java)
rejects no token, a bad signature, an expired token, the wrong issuer and the old fixed demo token. Every release's
smoke test checks the anonymous 401 and the wrong-password 401. Local output: [no token 401, agent delete 403](fallback/local-run-2026-10-12.md#deny).

**Trade-off:** We issue our own tokens ([ADR 0006](../docs/adrs/0006-use-self-issued-jwts-for-auth.md)) with two
in-memory users. No refresh tokens, no lockout, no way to kill a token early. A token lives 30 minutes.

**Next step:** Move issuing to a real identity provider (Keycloak, Auth0). Validation is already the standard
resource server, so it's mostly config.

## 2. Can you show us a token?

**Claim:** Not a live one. A token is a 30-minute key to the API, and anything on the projector ends up in someone's
photo. We can show what's inside one.

**Evidence:** [Login output](fallback/local-run-2026-10-12.md#sign-in) with the token redacted. Claims are issuer
`bean-brigade-crm`, subject, `roles`, 30-minute expiry ([`docs/api-runtime.md`](../docs/api-runtime.md)). Gitleaks
scans the full history on every PR, and the demo shell never `echo`s a token.

**Trade-off:** It's a little less convincing than pasting one into jwt.io.

**Next step:** If they really want it, decode a token from the local stack off screen and show only the claims.

## 3. What's the difference between your 401 and 403?

**Claim:** 401 means the API doesn't know who you are. 403 means it knows and the answer is no. Both come back as
Problem Details with a correlation ID, including the ones from the security filter.

**Evidence:** [Local output](fallback/local-run-2026-10-12.md#deny): no token gives 401; `agent1` deleting an
interaction or changing a status gives 403. Admin-only routes are customer writes and interaction delete
([`docs/api-runtime.md`](../docs/api-runtime.md)).

**Trade-off:** Two roles is coarse. There's no "only your own customers" rule.

**Next step:** Finer permissions if a real team needed them. The role checks live in one class, so it's an extension,
not a rewrite.

## 4. What fails closed?

**Claim:** Most things. Without its JWT keys the API won't start. No token is a 401, an unknown customer is a 404 with
no row and no event, a bad body is a 400. If Postgres goes away the API marks itself not ready and drops out of
traffic instead of serving errors. In CD, no backup means no release, Terraform drift means no release, and a release
that fails smoke never becomes the rollback target.

**Evidence:** [`SecurityConfiguration`](../backend/src/main/java/com/northstar/crm/security/SecurityConfiguration.java)
asserts the keys at startup; [400, 404, 409 output](fallback/local-run-2026-10-12.md#errors);
readiness probe on `/readyz` in [`k8s/application.yaml`](../k8s/application.yaml);
[`scripts/release.sh`](../scripts/release.sh) for the backup and known-good rules.

**Trade-off:** Kafka is the exception, on purpose. A save doesn't wait for Kafka (question 7).

**Next step:** Same list, plus the outbox so Kafka stops being the exception.

## 5. Why PostgreSQL, and who owns the schema?

**Claim:** It's the system of record, it's on the taught stack, and Flyway owns the schema. Hibernate only checks it
(`ddl-auto: validate`), so nothing changes the database except a reviewed migration.

**Evidence:** [`V1` to `V5` migrations](../backend/src/main/resources/db/migration/). Integration tests run against a
real PostgreSQL 16 in CI, not H2, so the SQL we test is the SQL we ship. Every release takes a `pg_dump` first and stops
if the backup fails ([`k8s/jobs/crm-db-backup.yaml`](../k8s/jobs/crm-db-backup.yaml)).

**Trade-off:** One Postgres pod on one volume, no failover. The backup sits on the same node, so it covers a bad
release or bad data, not a lost server. The app still connects as the owner `crm`.

**Next step:** Switch the app to the `crm_app` role that `V3` already creates, and copy backups off the node.

## 6. How do two people editing the same record not overwrite each other?

**Claim:** Optimistic locking. Every interaction and customer has a `version`, every write after create sends the
version it read, and a stale write gets a 409 and changes nothing.

**Evidence:** [`V4`](../backend/src/main/resources/db/migration/V4__interaction_version.sql) and
[`V5`](../backend/src/main/resources/db/migration/V5__customer_version.sql);
[`InteractionMutationApiIT`](../backend/src/test/java/com/northstar/crm/api/InteractionMutationApiIT.java) and
[`CustomerMutationApiIT`](../backend/src/test/java/com/northstar/crm/api/CustomerMutationApiIT.java). Selenium covers
the two-tab edit. [Local output](fallback/local-run-2026-10-12.md#edit-conflict): tab 2 saves and goes to version 1,
tab 1 sends version 0 and gets `Interaction version is stale`.

**Trade-off:** The second person has to reload and redo their edit. No merge.

**Next step:** Fine at this size. Show what changed in the conflict message if agents complained.

## 7. Why publish to Kafka after the commit, and what if Kafka is down?

**Claim:** The database is the truth and the event follows it. We never announce a save that rolled back
([ADR 0005](../docs/adrs/0005-publish-events-after-commit.md)). If Kafka is down the save still works and the user gets
a 201; the event is lost once the producer gives up, and each loss is logged.

**Evidence:** [`InteractionEventPublisher`](../backend/src/main/java/com/northstar/crm/messaging/InteractionEventPublisher.java)
listens `AFTER_COMMIT`. [`reports/kafka-failure-drills-2026-10-12.md`](../reports/kafka-failure-drills-2026-10-12.md):
broker down 140.7 s, 30 of 30 saves returned 201 with no slowdown, 18 events never reached Kafka (one batch timed out
at 2 minutes) and nothing resent them.

**Trade-off:** A 201 means saved, not delivered. One transaction can't cover both: Kafka transactions only cover
Kafka.

**Next step:** Transactional outbox, first on the list. Every event already goes through one publisher, so it's a
contained change.

## 8. How do you avoid handling the same event twice, and what about bad messages?

**Claim:** The consumer records each `eventId` in `processed_event` in the same transaction as its work, so a repeat is
skipped. A transient failure gets two retries a second apart, then goes to the dead-letter topic. Bad JSON or an
unknown event version goes straight to the DLT. The consumer keeps going either way.

**Evidence:** [`InteractionEventConsumerIT`](../backend/src/test/java/com/northstar/crm/messaging/InteractionEventConsumerIT.java);
drills 2 and 3 in the [Kafka report](../reports/kafka-failure-drills-2026-10-12.md); replay steps in
[`docs/dlt-replay-runbook.md`](../docs/dlt-replay-runbook.md). [Local output](fallback/local-run-2026-10-12.md#break-the-consumer):
a version-999 event went to the DLT with no retries, then the fixed event replayed twice logged `Handled`, then
`Skipping duplicate`.

**Trade-off:** This covers duplicate events, not duplicate requests. A client that retries a POST can still create a
second row because there's no `Idempotency-Key` on the API.

**Next step:** `Idempotency-Key` on `POST /interactions`.

## 9. How would you know the consumer was broken?

**Claim:** Every message is counted by outcome (processed, failed, retried, dead_lettered), and consumer lag is
exposed. Prometheus scrapes the API on an internal port and Grafana charts it, so a broken consumer shows up as a jump
in dead letters, not a log line nobody reads. One correlation ID follows a request through the API log, the row, the
event and the consumer log.

**Evidence:** [`docs/kafka-dashboard.md`](../docs/kafka-dashboard.md), [`k8s/monitoring.yaml`](../k8s/monitoring.yaml),
[`CorrelationIdFilter`](../backend/src/main/java/com/northstar/crm/api/CorrelationIdFilter.java).
[Local output](fallback/local-run-2026-10-12.md#save-row-event-consumer): one save, same ID in all four places;
processed 1, lag 0.

**Trade-off:** Prometheus keeps 24 hours on an `emptyDir`, so history goes on restart, and nothing pages anyone.
Grafana runs on a laptop, not in the cluster.

**Next step:** Alerts on dead letters and lag, and a volume for Prometheus.

## 10. How do you prove the image in production is the one CI tested?

**Claim:** CI builds the JAR once. The image job checks the JAR's SHA-256 before `docker build`, and the Dockerfile only
copies it in. Each `main` build writes `artifact-manifest.json` with the commit, the JAR hash and both image digests.
CD reads the manifest from that exact commit's CI run and deploys by `@sha256:` digest.

**Evidence:** `v1.1.0`: `main` CI [run 38308415270](https://github.com/BrandonRobare/bean-brigade-customer-management-platform/actions/runs/38308415270)
on `9bf13d2`; the CD log says `Deployed 9bf13d2…: crm-api@sha256:402405ae… and crm-ui@sha256:e5f35588…`
([run 38308570206](https://github.com/BrandonRobare/bean-brigade-customer-management-platform/actions/runs/38308570206),
attempt 5), and `kubectl get deploy` shows the same digests. CD refuses `latest`, any tag, another commit's build and
any registry but ours ([`test_release_script.py`](../.github/scripts/tests/test_release_script.py)).

**Trade-off:** Copying a pre-built JAR instead of a multi-stage build means the image depends on CI's artifact
storage. That's the point, though: a multi-stage build compiles a JAR the tests never saw.

**Next step:** Sign the images and check the signature at deploy.

## 11. What stops a bad change from reaching production?

**Claim:** Pull requests only, with eight required checks: Angular tests and build, backend tests on real Postgres,
Selenium, dependency scan, CodeQL, gitleaks and the IaC scan, then the image job, which waits for the other seven and
scans the image and runs DAST before anything is pushed. A release needs a tag on a green `main` commit and a reviewer's
approval. CD checks for Terraform drift and takes a backup before it deploys, and smoke can't be skipped.

**Evidence:** [`capstone-ci.yml`](../.github/workflows/capstone-ci.yml), [`capstone-cd.yml`](../.github/workflows/capstone-cd.yml),
[`reports/failure-experiments-2026-10-08.md`](../reports/failure-experiments-2026-10-08.md): a failing test and a
planted SQL injection each blocked the merge. The pipeline also refused `v1.1.0` itself: once because we tagged it
before `main` had built it, then three times because two new network policies weren't applied. We ran infra-plan,
reviewed it, applied that exact plan, and the next attempt passed smoke 13/13.

**Trade-off:** Zero required human approvals on PRs. Four people moving fast for a week; the human gate is the
production approval instead.

**Next step:** Require one PR review.

## 12. How do you roll back, and how fast?

**Claim:** The rollback job redeploys the last API + UI digest pair that passed smoke, then runs smoke again. It never
touches the database. Migrations only add, so the old app runs on the new schema.

**Evidence:** [`docs/rollback-runbook.md`](../docs/rollback-runbook.md). Live rehearsal 2026-10-08 took 1 min 24 s,
51 s of that waiting for approval, smoke 12/12 after
([`reports/release-v0.1.1-2026-10-08.md`](../reports/release-v0.1.1-2026-10-08.md)).

**Trade-off:** A person decides; nothing rolls back on its own. The job also can't go from `v1.1.0` back to `v1.0.0`,
whose image doesn't serve `/livez` and `/readyz`. That would mean redeploying the old commit.

**Next step:** Roll back automatically when smoke fails.

## 13. Why no staging, and why rolling instead of blue-green?

**Claim:** We have one namespace with a 2 CPU, 3 GiB quota. Staging beside production would share its quota and its
token, and blue-green needs a second copy of everything; we fit one extra pod during a rollout. So CI is the test stage
(real Postgres, DAST on the exact images), and a local k3d cluster with the same quota rehearsed CD.

**Evidence:** [`docs/environment-strategy.md`](../docs/environment-strategy.md). On k3d, 13 of 5,000 requests failed
during a rollout before we added a 5-second preStop drain, and 0 of 6,910 after. Deleting one API pod under 3,552 reads:
all 200 ([`k8s/application.yaml`](../k8s/application.yaml)).

**Trade-off:** Those numbers are from the rehearsal cluster, not production. And two API pods on one node isn't high
availability for the server.

**Next step:** Staging in its own namespace with its own token, and repeat the rollout test in production.

## 14. Which requirement don't you meet?

**Claim:** Performance. We set p95 under 2 seconds and haven't measured it. Accessibility and browser checks are
still pending too.

**Evidence:** [`docs/nfrs.md`](../docs/nfrs.md), the Performance and Accessibility rows, with how we'd measure them.

**Trade-off:** We spent that time on release safety and Kafka failure handling.

**Next step:** Time search, profile and save against the seeded data, `EXPLAIN` the search query, and do a
keyboard-only pass through the screens.

## If we don't know

"We don't know. Here's how we'd check." Then name the file, log or command. Don't guess a number.

## Gaps to say before someone finds them

- Events are published after commit with no outbox, and we measured the loss while Kafka was down.
- No `Idempotency-Key` on POST.
- One Kafka broker, one Postgres, one node, and backups on that node.
- Restore drill and pod-delete test done on k3d, not in production.
- Performance and accessibility not measured.
- The app connects to Postgres as the owner.
- Zero required PR approvals.
- Long-lived deploy token, and Terraform state in a namespace Secret ([`docs/risk-register.md`](../docs/risk-register.md)
  R-11, R-14).
