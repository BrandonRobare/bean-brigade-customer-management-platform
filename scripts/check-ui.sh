#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
check_dir=$(mktemp -d)
container=""
cleanup() {
  if [ -n "$container" ]; then docker rm -f "$container" >/dev/null 2>&1 || true; fi
  rm -rf "$check_dir"
}
trap cleanup EXIT
docker build -t crm-ui:check frontend
container=$(docker run -d --read-only --tmpfs /tmp:rw,noexec,nosuid,size=32m \
  --user 101:101 --cap-drop ALL --security-opt no-new-privileges \
  -p 127.0.0.1::8080 crm-ui:check)
base="http://$(docker port "$container" 8080/tcp)"
curl --fail --silent --show-error --retry 15 --retry-connrefused --retry-delay 1 \
  "$base/healthz" > "$check_dir/health"
grep -q '^ok$' "$check_dir/health"
curl --fail --silent --show-error "$base/" > "$check_dir/index"
grep -q '<app-root>' "$check_dir/index"
curl --fail --silent --show-error "$base/interactions" > "$check_dir/deep-link"
cmp "$check_dir/index" "$check_dir/deep-link"
for path in /api/v1/interactions /actuator/health /missing.js; do
  [ "$(curl --silent --show-error -o /dev/null -w '%{http_code}' "$base$path")" = 404 ]
done
curl --silent --show-error -D "$check_dir/headers" -o /dev/null \
  -H 'Host: demo.example.invalid' -H 'X-Forwarded-Proto: http' \
  "$base/api/v1/interactions?customerId=CUS-1001"
grep -q ' 308 ' "$check_dir/headers"
grep -qi '^Location: https://demo.example.invalid/api/v1/interactions?customerId=CUS-1001' "$check_dir/headers"
printf 'PASS: non-root/read-only UI, health, SPA deep links, API isolation and HTTPS redirect\n'
