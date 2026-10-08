#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

: "${API_IMAGE:?API_IMAGE is required}" "${UI_IMAGE:?UI_IMAGE is required}"
out="${DAST_OUT:-dast-report}"
zap_image=ghcr.io/zaproxy/zaproxy:stable@sha256:7aaa659b0d43078febd82e29bad112285c370727e86ab8340444220e17d9f0d2
edge_image=nginxinc/nginx-unprivileged:stable-alpine@sha256:15c994d10d6d78658721c3bcafff14cb281fba2a4bdf9d5ba92c416a472516e3
run="crm-dast-$$"
work=$(mktemp -d)
cleanup() {
  docker rm -f "$run-edge" "$run-ui" "$run-api" "$run-pg" > /dev/null 2>&1 || true
  docker network rm "$run" > /dev/null 2>&1 || true
  rm -rf "$work"
}
trap cleanup EXIT
mkdir -p "$out"
chmod 777 "$out"
out=$(cd "$out" && pwd)

openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$work/private.pem" 2> /dev/null
openssl pkey -in "$work/private.pem" -pubout -out "$work/public.pem"
db_password=$(openssl rand -hex 16)
agent_password=$(openssl rand -hex 16)
admin_password=$(openssl rand -hex 16)

docker network create "$run" > /dev/null
docker run -d --name "$run-pg" --network "$run" \
  -e POSTGRES_USER=crm -e POSTGRES_PASSWORD="$db_password" -e POSTGRES_DB=crm postgres:16 > /dev/null
until docker exec "$run-pg" pg_isready -U crm -d crm > /dev/null 2>&1; do sleep 1; done
docker run -d --name "$run-api" --network "$run" --read-only --tmpfs /tmp --cap-drop ALL --security-opt no-new-privileges \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e SPRING_DATASOURCE_URL="jdbc:postgresql://$run-pg:5432/crm" -e SPRING_DATASOURCE_USERNAME=crm \
  -e SPRING_DATASOURCE_PASSWORD="$db_password" \
  -e JWT_PRIVATE_KEY="$(cat "$work/private.pem")" -e JWT_PUBLIC_KEY="$(cat "$work/public.pem")" \
  -e DEMO_AGENT_PASSWORD="$agent_password" -e DEMO_ADMIN_PASSWORD="$admin_password" \
  "$API_IMAGE" > /dev/null
docker run -d --name "$run-ui" --network "$run" --read-only --tmpfs /tmp --user 101:101 --cap-drop ALL \
  --security-opt no-new-privileges "$UI_IMAGE" > /dev/null

# stands in for the Ingress: TLS ends here in production, so forward https and pass the apps' own headers through
cat > "$work/edge.conf" <<EOF
server {
    listen 8080;
    server_tokens off;
    proxy_pass_header Server;
    proxy_set_header Host \$host;
    proxy_set_header X-Forwarded-Proto https;
    location ~ ^/(api|actuator)(/|\$) {
        proxy_pass http://$run-api:8080;
    }
    location / {
        proxy_pass http://$run-ui:8080;
    }
}
EOF
chmod 644 "$work/edge.conf"
docker run -d --name "$run-edge" --network "$run" -p 127.0.0.1::8080 \
  -v "$work/edge.conf:/etc/nginx/conf.d/default.conf:ro" "$edge_image" > /dev/null
base="http://$(docker port "$run-edge" 8080/tcp | head -1)"

for _ in $(seq 90); do
  [ "$(curl -s -o /dev/null -w '%{http_code}' "$base/actuator/health/readiness")" = 200 ] && break
  sleep 2
done

body="$work/body"
request() {
  curl -sS --max-time 15 -o "$body" -w '%{http_code}' "$@" 2> /dev/null || true
}
check() {
  if [ "$3" = "$2" ]; then
    echo "PASS $1"
  else
    echo "FAIL $1 (HTTP $3, expected $2)"
  fi
}
login() {
  request -X POST "$base/api/v1/auth/login" -H 'Content-Type: application/json' \
    --data "$(jq -n --arg u "$1" --arg p "$2" '{username: $u, password: $p}')"
}

{
  check "readiness" 200 "$(request "$base/actuator/health/readiness")"
  check "anonymous customer read is 401" 401 "$(request "$base/api/v1/customers/CUS-1001")"
  check "forged bearer token is 401" 401 "$(request -H 'Authorization: Bearer not.a.jwt' "$base/api/v1/customers/CUS-1001")"
  check "anonymous metrics is 401" 401 "$(request "$base/actuator/metrics")"
  for endpoint in env beans configprops heapdump loggers mappings; do
    got=$(request "$base/actuator/$endpoint")
    if [ "$got" = 200 ]; then echo "FAIL actuator/$endpoint is exposed (HTTP 200)"; else echo "PASS actuator/$endpoint is not exposed"; fi
  done
  got=$(request -X POST "$base/api/v1/auth/login" -H 'Content-Type: application/json' --data '{"username":')
  if grep -q -E 'Exception|\bat [a-z]+\.[a-z]+\.' "$body"; then
    echo "FAIL malformed JSON leaks a stack trace (HTTP $got)"
  else
    echo "PASS malformed JSON gets no stack trace (HTTP $got)"
  fi
  headers=$(curl -sS -o /dev/null -D - -X OPTIONS "$base/api/v1/customers/CUS-1001" \
    -H 'Origin: https://attacker.example' -H 'Access-Control-Request-Method: GET' 2> /dev/null || true)
  if grep -qi '^access-control-allow-origin:' <<< "$headers"; then
    echo "FAIL cross-origin request from another site is allowed"
  else
    echo "PASS cross-origin request from another site is refused"
  fi
  agent=""
  [ "$(login agent1 "$agent_password")" = 200 ] && agent=$(jq -r '.accessToken // empty' "$body")
  if [ -n "$agent" ]; then echo "PASS AGENT login"; else echo "FAIL AGENT login"; fi
  check "AGENT customer read is 200" 200 "$(request -H "Authorization: Bearer $agent" "$base/api/v1/customers/CUS-1001")"
  check "AGENT metrics is 403" 403 "$(request -H "Authorization: Bearer $agent" "$base/actuator/metrics")"
} | tee "$out/probes.txt"
failures=$(grep -c '^FAIL' "$out/probes.txt" || true)

if [ "$failures" -ne 0 ]; then
  docker logs "$run-api" 2>&1 | tail -40 > "$out/api.log"
fi

# passive baseline: spider the app through the edge, report every alert; check-zap-report.py decides pass/fail
status=0
docker run --rm --network "$run" -v "$out:/zap/wrk:rw" "$zap_image" \
  zap-baseline.py -t "http://$run-edge:8080" -J zap.json -r zap.html -I > "$out/zap.log" 2>&1 || status=$?
[ "$status" -eq 0 ] || { echo "ZAP baseline did not finish (exit $status), see $out/zap.log"; exit 1; }

[ "$failures" -eq 0 ] || { echo "DAST probes failed: $failures"; exit 1; }
echo "DAST probes passed. ZAP report in $out/zap.json"
