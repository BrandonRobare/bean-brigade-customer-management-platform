# Kafka dashboard

Prometheus runs in the namespace and scrapes each API pod's management port (8081, `/actuator/prometheus`).
Grafana runs on a laptop and reads Prometheus through `kubectl port-forward`. The Ingress never routes 8081.

## Open it

Against the cluster:

```bash
kubectl --context <your-context> -n <namespace> port-forward --address 127.0.0.1 svc/crm-prometheus 9090:9090
docker compose --profile monitoring up -d grafana
```

Against a local API (no port-forward running, PostgreSQL and Kafka up as in the README):

```bash
cd backend
MANAGEMENT_SERVER_PORT=8081 MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,prometheus ./mvnw spring-boot:run
```

```bash
docker compose --profile monitoring up -d grafana prometheus
```

Open <http://localhost:3000/d/crm-kafka>. Prometheus targets are at <http://localhost:9090/targets>.
Grafana is anonymous read-only and bound to `127.0.0.1`. Stop it with
`docker compose --profile monitoring rm -sfv grafana prometheus`.

## Panels

| Panel | PromQL |
| --- | --- |
| Consumer lag | `max by (instance, client_id) (kafka_consumer_fetch_manager_records_lag_max{job="crm-api"})` |
| Dead letters in range | `sum by (topic) (increase(crm_kafka_messages_total{job="crm-api",outcome="dead_lettered"}[$__range]))` |
| Kafka failures/s | `sum by (topic) (rate(crm_kafka_messages_total{job="crm-api",outcome="failed"}[5m]))` |
| HTTP 5xx/s | `sum(rate(http_server_requests_seconds_count{job="crm-api",status=~"5.."}[5m])) or vector(0)` |

`crm_kafka_messages_total` has only `topic` and `outcome` tags (`processed`, `failed`, `retried`,
`dead_lettered`); see [API runtime](api-runtime.md) for what each outcome counts. With one partition and two API
pods, only one pod consumes, so one lag series is normal. Rates need two scrapes (30 s) before they show.

## During the drills

- **Poison message:** dead letters go up by one, failures blip, and the next valid event is still processed.
  A message that can't be deserialized goes straight to the DLT without retries.
- **Broker kill:** POSTs keep returning 201 and events published while the broker is down are lost
  ([drill report](../reports/kafka-failure-drills-2026-10-12.md)). Neither DLT nor 5xx moves, because those
  events never reached Kafka; the API log's `Failed to publish interaction event` is the signal.
- **Replay** ([runbook](dlt-replay-runbook.md)): `processed` goes up; replaying the same `eventId` again is a
  no-op that still counts as `processed`.

## Quota

Prometheus requests 50m / 192Mi (limits 100m / 256Mi) and uses `Recreate`, so it never surges. Kafka's memory
request drops from 768Mi to 512Mi (it uses about 381Mi) to make room. Worst case during a release, with one API
surge pod and the backup Job running:

| | CPU req | Mem req | CPU limit | Mem limit | Pods |
| --- | ---: | ---: | ---: | ---: | ---: |
| Quota | 2000m | 3072Mi | 4000m | 6144Mi | 15 |
| Steady, with Prometheus | 1400m | 2112Mi | 2850m | 4608Mi | 6 |
| + API surge + backup Job | 1750m | 2752Mi | 3600m | 5888Mi | 8 |

## Network

Terraform adds two policies: API ingress on TCP 8081 only from `crm-prometheus`, and Prometheus egress only to
`crm-api` on 8081 (DNS comes from the existing policy). Port-forward goes through the Kubernetes API and kubelet,
not pod networking, so it needs `pods/portforward` permission rather than a policy.

## Limits

History lives in an `emptyDir` (24 h / 256 MB retention) and is lost when the pod is replaced. No Alertmanager:
nobody is on call, so the dashboard is the alert. Prometheus doesn't reload a changed ConfigMap; run
`kubectl rollout restart deployment/crm-prometheus` after editing it.
