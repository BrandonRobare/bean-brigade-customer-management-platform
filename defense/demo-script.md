# Demo script: Northstar CRM final defense

The live product demo for the defense: how to set it up, what to say and show, and what to do when it
breaks. Written for release `v1.1.0`. Runs on the demo laptop's local stack with a wiped database, so the row, the
consumer log and the dashboard can be shown live and Ravi's one-way status change can be repeated. Production shows
up only in the release beat, through the Actions run and the image digests, never the address bar.

- **10 minutes live**, then Q&A.
- Fixtures: `CUS-1001` Amina Khan (ACTIVE), `CUS-1002` Ravi Singh (PROSPECT), users `agent1` (AGENT) and `admin1`
  (ADMIN) with passwords from `.env`.

## What the demo proves

| Requirement | Beat |
| --- | --- |
| Search `CUS-1001`, open the profile, record an interaction | 0:20 to 1:45 |
| UI to PostgreSQL proven, not just a toast | 2:05 row |
| Versioned event, a consumer processes it, duplicates handled | 2:35 log, 7:40 replay twice |
| Logs and metrics keyed by a correlation ID | 2:35, 3:10 dashboard |
| Lifecycle journey: Ravi PROSPECT to ACTIVE | 4:20 |
| Break it on purpose, the platform notices, recovery | 5:00 invalid move 409, 7:00 broken consumer on the dashboard |
| The API is the security boundary, not Angular | 6:00 401 / 403 |
| Image digest deployed, the pipeline is the only way in | 8:30 release beat |

## Part 1: Run the demo locally

Needs Java 21, Node 22, Docker Desktop, OpenSSL, `jq` and `uuidgen` (macOS has it; on Linux it's in `uuid-runtime`).
Run every command below from **bash**, not zsh or PowerShell, in a terminal opened at the repo root. The API and UI
terminals `cd` into `backend` and `frontend` and stay there.

### Night before (once)

```bash
git fetch --tags
git switch --detach v1.1.0
bash scripts/setup-local.sh
(cd frontend && npm ci)
(cd backend && ./mvnw -q -DskipTests package)
docker compose --profile monitoring pull
```

`setup-local.sh` checks Java, Node, Docker and OpenSSL, then creates `.env` and the JWT keys in `backend/.keys/` if
they're missing (both gitignored). The `package` run and the `pull` warm the Maven, npm and image caches so nothing
downloads on the venue's network.

### T-30 minutes: fresh stack

Wipe the database so Amina and Ravi come back with empty timelines and Ravi is PROSPECT again:

```bash
docker compose down -v
docker compose up -d
docker compose ps -a
```

Wait until `crm-postgres` and `crm-kafka` are `healthy` and `kafka-topics` shows `Exited (0)` (it creates
`crm.customer.interactions.v1` and its `.DLT`).

**Terminal 1, API**, with the metrics port on. Large font, leave it visible:

```bash
cd backend
MANAGEMENT_SERVER_PORT=8081 MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,prometheus \
  ./mvnw spring-boot:run 2>&1 | tee /tmp/crm-api.log
```

Ready when the log shows `Started CrmApplication`. Flyway applies the migrations on the empty database first.

**Dashboard**, once the API is up:

```bash
docker compose --profile monitoring up -d grafana prometheus
```

Grafana is at <http://localhost:3000/d/crm-kafka>. <http://localhost:9090/targets> should show the API `UP`. Rates
need two scrapes (about 30 s) before they draw. Panels are explained in [the Kafka dashboard doc](../docs/kafka-dashboard.md).

**Terminal 2, UI:**

```bash
cd frontend
npx ng serve
```

Ready at <http://localhost:4200>. The dev server forwards `/api` to the API on 8080.

### T-5 minutes: demo shell and browser

Tokens last **30 minutes**, so this goes last.

**Terminal 3, demo shell.** Paste once:

```bash
cd "$(git rev-parse --show-toplevel)"
set -a; source .env; set +a
login() { curl -s localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' \
  -d "$(jq -cn --arg u "$1" --arg p "$2" '{username: $u, password: $p}')" | jq -r .accessToken; }
AGENT=$(login agent1 "$DEMO_AGENT_PASSWORD")
ADMIN=$(login admin1 "$DEMO_ADMIN_PASSWORD")
sql() { docker exec crm-postgres psql -U crm -d crm -c "$1"; }
kafka() { docker exec -i crm-kafka /opt/kafka/bin/"$@"; }
B=localhost:9092 MAIN=crm.customer.interactions.v1
TYPE='__TypeId__:com.northstar.crm.messaging.CustomerInteractionRecordedV1'
clear
```

Never `echo` the tokens. If a token expires mid-demo, rerun the two `login` lines.

**Browser** (Chrome, one window, tabs in this order):

1. <http://localhost:4200/login>, not signed in (signing in is the first beat)
2. <http://localhost:4200/login>, signed in as `agent1` at T-1, for the edit conflict
3. Grafana dashboard, time range "Last 15 minutes", auto-refresh 10 s
4. GitHub Actions: the `v1.1.0` CD run, Summary page
5. The backup recording, paused at 0:00

### Pre-seed checklist

Any fail: fix it, or plan to start on the fallback.

- [ ] `sql "select public_id, full_name, status from customer"`: Amina `CUS-1001` ACTIVE, Ravi `CUS-1002` PROSPECT, nothing else
- [ ] `sql "select count(*) from customer_interaction"` is `0`
- [ ] `curl -s -o /dev/null -w '%{http_code}\n' -H "Authorization: Bearer $AGENT" localhost:8080/api/v1/customers/CUS-1001` prints `200`
- [ ] `kafka kafka-topics.sh --bootstrap-server $B --list` shows the topic and `.DLT`
- [ ] Grafana loads, Prometheus target `UP`
- [ ] Production deployments ready (off screen): `kubectl --context <your-context> -n <namespace> get deploy crm-api crm-ui` shows 2/2 and 1/1
- [ ] Recording plays

## Part 2: Script, 10:00

### 1. Find the customer (0:00 to 1:15)

Signing in sends the username and password to the API, which checks them and hands back a signed token that lasts
30 minutes. The token carries the user's role, AGENT or ADMIN, and the browser attaches it to every call after that.
Search matches any part of a name or the exact customer ID, ignoring case; the name match uses a trigram index, so it
doesn't scan the whole table. The empty-summary check happens in the browser so nothing gets sent, but the API checks
the same thing and answers 400 if someone skips the screen.

**0:00** Tab 1. Pass: login page

> A service agent gets a call. They need to find the customer fast, see what's happened before, and log this call so the next person sees it. We built that end to end, and we'll break it on purpose along the way.

**0:20** Sign in as `agent1`. Pass: **Customers**, Amina and Ravi

> Agents sign in with their own account. Their role comes back in the token, and the API checks it on every call.

**0:40** Type `amina`, **Search**. Pass: one result `Amina Khan CUS-1001 · ACTIVE`

> Search matches part of a name or the exact customer ID, either case. The search stays in the address, so back and forward work.

**1:00** Click Amina. Pass: **No interactions yet.**

> Her page: who she is, her status, and everything we've done with her, newest first.

**1:15** **Record interaction** with the summary empty. Pass: **Summary is required.**

> An empty note never leaves the browser.

### 2. Record a call and prove it landed (1:30 to 3:10)

This is the main path. The API saves the interaction to PostgreSQL in one transaction, with the request's
correlation ID on the row. Only after that commit does it publish an event to Kafka, keyed by customer so one
customer's events stay in order. The consumer reads the event, writes its event ID to `processed_event` so a repeat gets
skipped, and bumps the `processed` counter the dashboard draws. The point is showing all three: the row, the log line
with the same ID, and the counter.

**1:30** **Call**, `Asked when her statement closes`, **Record interaction**. Pass: new row on top

> Now the real one.

**2:05** Terminal 3, the saved row. Pass: `CALL`, version `0`, a UUID

```bash
sql "select interaction_type, summary, version, correlation_id from customer_interaction order by created_at desc limit 1"
```

> The screen says it saved. Here's the database, with the request's tracking ID on the row.

**2:35** Terminal 3, the consumer log. Pass: same `correlationId` as the row

```bash
grep "Handled interaction event" /tmp/crm-api.log | tail -1
```

> After the save commits, it's announced on Kafka. Our consumer picks it up and records the event ID, so a repeat delivery is skipped, not applied twice.

**3:10** Tab 3. Pass: processed +1, lag 0

> And the platform counts it: every message by what happened to it. That bump is the call we just logged.

### 3. Protecting the data (3:30 to 5:30)

Every interaction and customer has a version number. A save sends the version it read; if someone else saved in
between, the numbers don't match and the API returns 409 without changing anything. Status changes follow fixed rules:
a prospect can become active or closed, active can only become closed, and closed is final. The screen only offers
legal moves, but the API enforces the rules on its own, which the curl at 5:00 shows. Creating customers, changing
status and deleting are admin only, which is why we switch to `admin1` here.

**3:30** **Edit** the row in tab 2 and tab 1; save tab 2, then save tab 1. Pass: tab 1: **This interaction changed. Reload interactions before trying again.**

> Two agents open the same note. The first save wins; the second is told the record changed instead of silently overwriting it.

**4:00** **Reload** in tab 1. Pass: timeline shows tab 2's text

> Every row carries a version. A stale write gets a conflict and changes nothing.

**4:20** Reload (signs out), sign in as `admin1`, open Ravi, **ACTIVE**, **Change status**. Pass: Ravi `ACTIVE`

> Ravi's been a prospect and just signed. Only an admin can change a status.

**5:00** Terminal 3, move Ravi back to PROSPECT. Pass: `409`, Problem Details, Ravi still ACTIVE

```bash
curl -s -X PATCH localhost:8080/api/v1/customers/CUS-1002/status \
  -H "Authorization: Bearer $ADMIN" -H 'Content-Type: application/json' \
  -d '{"status":"PROSPECT","version":1}' -w '\n%{http_code}\n' | jq -R 'fromjson? // .'
```

> The screen only offers legal moves. Going back to prospect isn't one, and the API refuses it even if you skip the screen.

**5:30** On search, create `Jordan Lee`; on Amina, **Delete** the call and confirm. Pass: `CUS-10001 · PROSPECT`; row gone

> Admins also create customers, and only admins see Delete, which asks first.

### 4. The API is the lock (6:00)

Hiding a button isn't security, because anyone can call the API directly. So this section skips the screen. No token
gets 401: the API doesn't know who you are. An agent's token on an admin-only delete gets 403: it knows who you are and
says no. The role check runs before the API looks anything up, so a made-up interaction ID gets the same 403.

**6:00** Terminal 3, no token, then an agent deleting history. Pass: `401`, then `403`

```bash
curl -s -o /dev/null -w '%{http_code}\n' "localhost:8080/api/v1/interactions?customerId=CUS-1001"
curl -s -o /dev/null -w '%{http_code}\n' -X DELETE -H "Authorization: Bearer $AGENT" \
  "localhost:8080/api/v1/interactions/$(uuidgen | tr A-Z a-z)?version=0"
```

The role check runs before any lookup, so any well-formed ID gives the agent 403 (5:30 may have deleted the only row).

> The screen hides what you can't do, but it isn't the lock. No token: the API doesn't know you. An agent deleting history: it knows you, and says no.

### 5. Break it on purpose (7:00 to 7:40)

We put an event on the topic with a version the consumer doesn't support. The consumer doesn't retry something that
will never work: it sends it straight to the dead-letter topic and keeps reading, so the dashboard shows a failure and
a dead letter while the app keeps serving. Then we fix the version and send the same event twice. The first is
handled; the second has an event ID the consumer has already seen, so it's skipped. That's the duplicate protection
from section 2 actually happening.

**7:00** Terminal 3, copy the first event with an unsupported version and a new event ID, send it; then tab 3. Pass: dead letters +1, failures blip; the app keeps serving

```bash
kafka kafka-console-consumer.sh --bootstrap-server $B --topic $MAIN --partition 0 --offset 0 \
  --max-messages 1 --timeout-ms 10000 --property print.key=true > /tmp/ev.txt
sed -E 's/"eventVersion":"1"/"eventVersion":"999"/; s/"eventId":"[^"]+"/"eventId":"'"$(uuidgen | tr A-Z a-z)"'"/' /tmp/ev.txt > /tmp/bad.txt
printf '%s\t' "$TYPE" | cat - /tmp/bad.txt | kafka kafka-console-producer.sh --bootstrap-server $B \
  --topic $MAIN --property parse.headers=true --property parse.key=true
```

> Now we break the consumer. I'll send it an event version it doesn't understand. Watch the dashboard, not us.

**7:40** Terminal 3, fix it and replay twice. Pass: `Handled` then `Skipping duplicate`; processed +2 (the skipped duplicate still counts as processed)

```bash
sed 's/"eventVersion":"999"/"eventVersion":"1"/' /tmp/bad.txt > /tmp/fixed.txt
printf '%s\t' "$TYPE" | cat - /tmp/fixed.txt | kafka kafka-console-producer.sh --bootstrap-server $B \
  --topic $MAIN --property parse.headers=true --property parse.key=true
printf '%s\t' "$TYPE" | cat - /tmp/fixed.txt | kafka kafka-console-producer.sh --bootstrap-server $B \
  --topic $MAIN --property parse.headers=true --property parse.key=true
grep -E "Handled interaction event|Skipping duplicate" /tmp/crm-api.log | tail -2
```

The full DLT procedure is in [the DLT replay runbook](../docs/dlt-replay-runbook.md).

> It didn't crash or block the queue: the bad message went to a dead-letter topic. We fix it and replay it once. And replay it again: the consumer recognizes the event and skips it.

### 6. Production (8:30 to 9:30)

Everything so far ran on the laptop; this shows the same build is what's in production. CI builds each image once
and records its digest, a SHA-256 of the image. CD deploys by that digest, never a tag, after an approval, a drift check
and a backup, and the smoke tests decide whether it counts. The v1.1.0 run was refused before it passed: once because
we tagged before main had built it, then because two network rules weren't applied yet. We fixed that through the
pipeline, not by hand. Show the GitHub run and the digests, never the server's address.

**8:30** Tab 4: the refused attempts, then the passing attempt's Smoke step. Then Terminal 3, the production digests (the output has no host or namespace). Pass: Smoke all PASS; images are `@sha256:`

```bash
kubectl --context <your-context> -n <namespace> get deploy crm-api crm-ui \
  -o custom-columns='NAME:.metadata.name,READY:.status.readyReplicas,IMAGE:.spec.template.spec.containers[0].image'
```

> Everything you saw is the build in production right now, deployed by its exact image fingerprint. And the pipeline refused this very release today: first because we tagged it before main had built it, then because the new monitoring network rules weren't applied. We applied them through the pipeline, and the next attempt passed.

**9:30** Back to tab 1. Pass: hand off

> Find the customer, see the history, log the call, prove it landed, break it, and watch the platform catch it. Questions on any layer.

**Reloads:** the session lives in memory, so a reload or typing in the address bar signs you out. The 4:20 reload is
on purpose (switch to admin); anywhere else use the app's own links.

**Cut order if running long:** 5:30 create/delete, then the second replay at 7:40, then 4:00 (merge into 3:30).

Dry run 2026-10-12 on a local `v1.1.0` API with exactly these commands, under bash: `dead_lettered` 0 to 1 and
`failed` 0 to 1 within 4 s, DLT end offset 2 to 3, `retried` stayed 0 (bad versions skip retries); first replay
`Handled`, second `Skipping duplicate` for the same `eventId`; `processed` +2.

## Part 3: The failure story in two halves

One beat can't show an injected failure, a platform signal and recovery through the pipeline in ten minutes, so the
demo shows two:

- **Live, at the application:** the 7:00 broken consumer. The dashboard notices, the DLT contains it, the runbook
  recovers it, the second replay proves idempotency. The 5:00 409 and 6:00 401/403 are smaller live failures.
- **Told with evidence, at the pipeline (8:30):** the `v1.1.0` CD run. The first attempt was refused at "Find the
  main CI run" because the tag was pushed while main's CI was still building. The next attempts were refused at
  "Infrastructure matches this commit": the two Prometheus network policies weren't applied. The fix went through
  the pipeline (infra-plan, review, infra-apply of that exact plan), then the last attempt deployed and passed
  smoke 13/13. Nobody touched production by hand. Say "refused", not the number of retries.

## Part 4: When it breaks

Move down one rung, say one sentence, keep going. No troubleshooting past **20 seconds**.

| What broke | Switch to | Say |
| --- | --- | --- |
| One UI step | do it with `curl` in Terminal 3 (see [the backend demo](../docs/backend-demo.md)) | "Same call, straight to the API." |
| Grafana empty or slow | Prometheus's own graph at <http://localhost:9090> for `crm_kafka_messages_total`, or skip to the log lines | "Here's the raw counter." |
| Local API or database down | restart if under 20 s; otherwise the recording | "We'll show it from our rehearsal." |
| Kafka down locally | keep going: saves still work; skip 2:35 and 7:00 | "Saving doesn't wait on Kafka; that's on purpose." |
| Laptop, network or projector | the recording | "The live environment isn't cooperating, so here's the recording from our rehearsal. It shows the same journey; it doesn't prove today's build." |

Production is not a live fallback for the journey: its address shows the server, and Ravi's status change can only
happen once there. Production demo passwords stay in the password manager.

## Reset between rehearsals

```bash
docker compose down -v
docker compose up -d
docker compose --profile monitoring up -d grafana prometheus
```

Then stop and restart terminal 1 (Flyway runs again on the empty database) and redo T-5. The wipe also resets Ravi to
PROSPECT and the next new customer to `CUS-10001`.

Stop everything after the defense:

```bash
docker compose --profile monitoring down -v
```

## Close

- Residual risks we own: events are published after commit, not through an outbox, so an event can be lost if Kafka
  stays down past the producer timeout; demo accounts are in memory with no lockout or refresh tokens; production
  only, no staging.
- What we would do with one more week: _____
