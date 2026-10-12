# Kafka failure drills, 2026-10-12

Issue #131, run locally at `18689f8` with no application changes: Kafka 3.9.1, PostgreSQL 16, the API from
`./mvnw spring-boot:run`. The drills used their own `drill-kafka` and `drill-postgres` containers on ports 19092 and
15432 and the API on 18080, so the Compose stack was not touched. This is one broker process going away, not a
Kubernetes reschedule or a lost volume.

## Setup

```bash
docker run -d --name drill-postgres -p 15432:5432 \
  -e POSTGRES_USER=crm -e POSTGRES_PASSWORD=change-me -e POSTGRES_DB=crm postgres:16
docker run -d --name drill-kafka -p 19092:19092 \
  -e KAFKA_NODE_ID=1 -e KAFKA_PROCESS_ROLES=broker,controller \
  -e KAFKA_LISTENERS=PLAINTEXT://:19092,CONTROLLER://:9093 \
  -e KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://localhost:19092 \
  -e KAFKA_LISTENER_SECURITY_PROTOCOL_MAP=PLAINTEXT:PLAINTEXT,CONTROLLER:PLAINTEXT \
  -e KAFKA_CONTROLLER_LISTENER_NAMES=CONTROLLER -e KAFKA_CONTROLLER_QUORUM_VOTERS=1@localhost:9093 \
  -e KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1 -e KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR=1 \
  -e KAFKA_TRANSACTION_STATE_LOG_MIN_ISR=1 -e KAFKA_AUTO_CREATE_TOPICS_ENABLE=false apache/kafka:3.9.1
for t in crm.customer.interactions.v1 crm.customer.interactions.v1.DLT; do
  docker exec drill-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:19092 \
    --create --topic $t --partitions 1 --replication-factor 1
done
set -a; source .env; set +a
cd backend && SERVER_PORT=18080 SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:15432/crm \
  SPRING_KAFKA_BOOTSTRAP_SERVERS=localhost:19092 ./mvnw spring-boot:run
```

## Drill 1: broker down while posting

Signed in as `agent1` and posted 30 NOTE interactions for `CUS-1001`, each with `X-Correlation-ID: drill-NN`, timed
with curl's `%{http_code} %{time_total}`. After request 05, `docker kill drill-kafka`; requests 06 to 25 went out
every seven seconds; after request 25, `docker start drill-kafka`. Then each correlation ID was matched across
`customer_interaction`, the main topic, the publisher/consumer log lines and `processed_event`.

| Phase | POSTs | 201s | Latency min / median / max (ms) | Events that reached the consumer |
| --- | --- | --- | --- | --- |
| Before, 01 to 05 | 5 | 5 | 14 / 15 / 86 | 5 |
| Broker down, 06 to 25 | 20 | 20 | 5 / 12 / 20 | 2 (24 and 25, after restart) |
| After restart, 26 to 30 | 5 | 5 | 9 / 12 / 14 | 5 |

- Broker down 140.7 s (13:32:10 to 13:34:30 UTC). The consumer was back 14.5 s after the start and handled every
  buffered event within 10 ms.
- **POSTs never slowed and never failed.** All 30 interaction rows committed.
- **18 events were lost: requests 06 to 23.** The producer had queued them in one batch; at 13:34:10 that batch hit
  `delivery.timeout.ms=120000` and the publisher logged one failure per event:

  ```text
  Failed to publish interaction event eventId=c143742f-… correlationId=drill-06
  TimeoutException: Expiring 18 record(s) for crm.customer.interactions.v1-0:120002 ms has passed since batch creation
  ```

  Request 23 was only about a second old but expired with the batch. Requests 24 and 25 were in a later batch and
  survived. Nothing resent the 18 after Kafka came back.

## Drill 2: poison message

Produced `{bad-json` to the main topic with the console producer, key `CUS-1001` and the `__TypeId__` header.

- It went to `crm.customer.interactions.v1.DLT` with the original bytes and `DeserializationException` in the
  `kafka_dlt-exception-*` headers.
- **No retries:** Spring treats deserialization errors as not retryable, so it went straight to the DLT. A debug-log
  rerun showed one receive, `Successful dead-letter publication`, then the offset committed.
- The consumer kept going: the next good POST returned 201 in 7 ms and was handled.

## DLT replay (#132)

The [DLT replay runbook](../docs/dlt-replay-runbook.md) was run as written. A record with `eventVersion` `999` and
a new `eventId` went to the DLT. Replaying it with the version corrected logged `Handled` and wrote one
`processed_event` row. Replaying the same record again logged `Skipping duplicate`; the row count and
`processed_at` did not change and nothing new went to the DLT.

## What this means

- A 201 means the interaction is saved, not that its event was delivered. The event is sent after commit
  ([ADR 0005](../docs/adrs/0005-publish-events-after-commit.md)) with nothing that resends it, so a broker outage
  longer than two minutes loses events. The DLT can't help here: it catches records the consumer rejects, and these
  never reached Kafka.
- Bad messages are contained: they go to the DLT and do not block the partition.
- Replay is safe to repeat because the consumer dedupes on `eventId`.

## Follow-ups

- A transactional outbox, if every saved interaction must produce an event.
- Alerts on `Failed to publish interaction event` and on DLT growth (#129).
- Not tested: the cluster, retryable consumer failures, a cold producer or a full send buffer.

Cleanup: `docker rm -f drill-kafka drill-postgres`.
