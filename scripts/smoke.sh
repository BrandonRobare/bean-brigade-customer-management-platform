#!/usr/bin/env bash
set -uo pipefail

base="${SMOKE_URL:?SMOKE_URL is required, for example https://crm.example.edu}"
: "${SMOKE_AGENT_PASSWORD:?}" "${SMOKE_ADMIN_PASSWORD:?}"
host="${base#https://}"
body=$(mktemp)
trap 'rm -f "$body"' EXIT
curl_args=(-sS --max-time 15 -o "$body" -w '%{http_code}')
if [ -n "${SMOKE_CACERT:-}" ]; then curl_args+=(--cacert "$SMOKE_CACERT"); fi
failures=0

request() {
  curl "${curl_args[@]}" "$@" 2>/dev/null || true
}

check() {
  local name="$1" expected="$2" got="$3" body_test="${4:-}"
  if [ "$got" = "$expected" ] && { [ -z "$body_test" ] || jq -e "$body_test" "$body" > /dev/null 2>&1; }; then
    echo "PASS $name"
  else
    echo "FAIL $name (HTTP $got, expected $expected)"
    failures=$((failures + 1))
  fi
}

login() {
  request -X POST "$base/api/v1/auth/login" -H 'Content-Type: application/json' \
    --data "$(jq -n --arg u "$1" --arg p "$2" '{username: $u, password: $p}')"
}

token_for() {
  [ "$(login "$1" "$2")" = 200 ] && jq -r '.accessToken // empty' "$body"
}

check "readiness over trusted TLS" 200 "$(request "$base/actuator/health/readiness")" '.status == "UP"'
check "UI served through the Ingress" 200 "$(request "$base/")"
grep -q '<app-root' "$body" || { echo "FAIL UI body is not the Angular app"; failures=$((failures + 1)); }
check "HTTP redirects to HTTPS" 308 "$(curl -sS --max-time 15 -o /dev/null -w '%{http_code}' "http://$host/" 2>/dev/null || true)"
check "anonymous API call is 401" 401 "$(request "$base/api/v1/interactions?customerId=CUS-1001")"
check "wrong password is 401" 401 "$(login agent1 not-the-password)"

agent=$(token_for agent1 "$SMOKE_AGENT_PASSWORD")
admin=$(token_for admin1 "$SMOKE_ADMIN_PASSWORD")
if [ -z "$agent" ] || [ -z "$admin" ]; then
  echo "FAIL real login for agent1/admin1"
  echo "Smoke failed: login is required for the remaining checks."
  exit 1
fi
echo "PASS real login for agent1 and admin1"

check "AGENT metrics is 403" 403 "$(request -H "Authorization: Bearer $agent" "$base/actuator/metrics")"
check "ADMIN metrics is 200" 200 "$(request -H "Authorization: Bearer $admin" "$base/actuator/metrics")" '.names | length > 0'
for id in CUS-1001 CUS-1002; do
  check "read $id" 200 "$(request -H "Authorization: Bearer $agent" "$base/api/v1/customers/$id")" ".publicId == \"$id\""
done
check "record an interaction with lab-request-001" 201 "$(request -X POST "$base/api/v1/interactions" \
  -H "Authorization: Bearer $agent" -H 'Content-Type: application/json' -H 'X-Correlation-ID: lab-request-001' \
  --data '{"customerId":"CUS-1001","interactionType":"NOTE","summary":"release smoke check"}')" '.correlationId == "lab-request-001"'
check "unknown customer is 404" 404 "$(request -X POST "$base/api/v1/interactions" \
  -H "Authorization: Bearer $agent" -H 'Content-Type: application/json' \
  --data '{"customerId":"CUS-9999","interactionType":"NOTE","summary":"release smoke check"}')"

if [ "$failures" -ne 0 ]; then
  echo "Smoke failed: $failures check(s)."
  exit 1
fi
echo "Smoke passed."
