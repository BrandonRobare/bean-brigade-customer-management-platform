# DLT replay runbook

Inspect one dead-lettered record, fix the cause, replay it once, check it applied once, then move to the next.
Tested locally on 2026-10-12 (Kafka 3.9.1); see the [failure drills](../reports/kafka-failure-drills-2026-10-12.md).
The cluster form below is the same commands through `kubectl exec`; it was not rehearsed in the cluster.

## Set up

Local Compose, from the repository root:

```bash
kafka() { docker exec -i crm-kafka /opt/kafka/bin/"$@"; }
db() { docker exec -i crm-postgres psql -U crm -d crm "$@"; }
```

Cluster, with the placeholders filled in:

```bash
kafka() { kubectl -n <namespace> exec -i <kafka-pod> -- /opt/kafka/bin/"$@"; }
db() { kubectl -n <namespace> exec -i <postgres-pod> -- psql -U crm -d crm "$@"; }
```

Then, for both:

```bash
B=localhost:9092
MAIN=crm.customer.interactions.v1
DLT=$MAIN.DLT
TYPE='__TypeId__:com.northstar.crm.messaging.CustomerInteractionRecordedV1'
```

## 1. Inspect

```bash
kafka kafka-get-offsets.sh --bootstrap-server $B --topic $DLT
OFFSET=0
kafka kafka-console-consumer.sh --bootstrap-server $B --topic $DLT --partition 0 --offset $OFFSET \
  --max-messages 1 --timeout-ms 10000 --property print.headers=true --property print.key=true
```

The offsets line is `topic:partition:end`; records sit at offsets below `end`. The headers say why the record failed
(`kafka_dlt-exception-message`, `kafka_dlt-exception-cause-fqcn`) and where it came from
(`kafka_dlt-original-topic`, `kafka_dlt-original-offset`). Note the payload's `eventId`.

## 2. Decide and fix

- **Fix and replay:** a consumer bug fixed by a release, a database outage that is over, or a payload the producer
  got wrong in a way you can correct (for example an unsupported `eventVersion`).
- **Drop:** bytes that are not JSON, or an event that should never apply. Write down the offset, the reason and who
  decided. Leave the record; the DLT keeps records for seven days.

Malformed JSON and an invalid event go to the DLT on the first failure. Only other errors get the two retries,
one second apart.

Check whether the event was already applied:

```bash
EVENT_ID=<eventId>
db -c "select count(*), max(processed_at) from processed_event where event_id = '$EVENT_ID'"
```

## 3. Replay one record

Save the key and payload (one line, tab separated), correct the payload if needed, then send it back to the main
topic with the type header the consumer needs. Keep the same `eventId`; never make a new one to get past dedupe.

```bash
kafka kafka-console-consumer.sh --bootstrap-server $B --topic $DLT --partition 0 --offset $OFFSET \
  --max-messages 1 --timeout-ms 10000 --property print.key=true > dlt-record.txt
sed 's/"eventVersion":"999"/"eventVersion":"1"/' dlt-record.txt > fixed-record.txt
printf '%s\t' "$TYPE" | cat - fixed-record.txt | kafka kafka-console-producer.sh --bootstrap-server $B \
  --topic $MAIN --property parse.headers=true --property parse.key=true
```

The `sed` line is the local test's fix; replace it with the correction your record needs, or copy the file as is
when the cause was outside the payload.

## 4. Check it applied once

```bash
db -c "select count(*), max(processed_at) from processed_event where event_id = '$EVENT_ID'"
kafka kafka-get-offsets.sh --bootstrap-server $B --topic $DLT
```

Expect one row, and the API log to show `Handled interaction event eventId=<eventId>` (or
`Skipping duplicate interaction event` if it was already applied). Locally the log is wherever the API's output
goes; in the cluster use `kubectl -n <namespace> logs <api-pod> --since=10m | grep <eventId>`.

If the DLT end offset went up, the replay failed again: stop, read the new record's headers and fix what remains.
Only one replay at a time; check each one before sending the next.

## Tested

On 2026-10-12 a record with `eventVersion` `999` and a new `eventId` went to the DLT at offset 0. Steps 1 to 4
replayed it with the version corrected to `1`: the log showed `Handled`, `processed_event` had one row. Replaying the
same file again logged `Skipping duplicate`; still one row, same `processed_at`, DLT end offset unchanged at 1.
