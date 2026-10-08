#!/usr/bin/env bash
set -euo pipefail

state=crm-release
digest_re='^sha256:[0-9a-f]{64}$'

fail() { echo "::error::$*" >&2; exit 1; }
k() { kubectl --namespace "${NAMESPACE:-student08}" "$@"; }

state_get() {
  { k get configmap "$state" -o json 2>/dev/null || echo '{}'; } | jq -r --arg key "$1" '.data[$key] // empty'
}

state_set() {
  local data
  data=$({ k get configmap "$state" -o json 2>/dev/null || echo '{}'; } | jq '.data // {}')
  for pair in "$@"; do
    data=$(jq --arg k "${pair%%=*}" --arg v "${pair#*=}" '.[$k] = $v' <<< "$data")
  done
  jq -n --arg name "$state" --argjson data "$data" \
    '{apiVersion: "v1", kind: "ConfigMap", metadata: {name: $name}, data: $data}' | k apply -f - > /dev/null
}

image_of() {
  k get deployment "$1" -o jsonpath='{.spec.template.spec.containers[0].image}' 2>/dev/null || true
}

roll() {
  k set image deployment/crm-api "api=$1" > /dev/null &&
    k rollout status deployment/crm-api --timeout=300s &&
    k set image deployment/crm-ui "ui=$2" > /dev/null &&
    k rollout status deployment/crm-ui --timeout=180s
}

render() {
  local dir
  dir=$(mktemp -d)
  cp -R k8s "$dir/base"
  rm -f "$dir/base/README.md"
  sed -i.bak \
    -e "s|^  hostname: .*|  hostname: ${PLATFORM_HOSTNAME:?}|" \
    -e "s|^  ingress-class: .*|  ingress-class: ${INGRESS_CLASS:?}|" \
    -e "s|^  storage-class: .*|  storage-class: ${STORAGE_CLASS:?}|" \
    -e "s|^  ingress-namespace: .*|  ingress-namespace: ${INGRESS_NAMESPACE:?}|" \
    "$dir/base/configuration.yaml"
  cat > "$dir/kustomization.yaml" <<EOF
resources: [base]
images:
  - {name: ghcr.io/brandonrobare/crm-api, newName: ${1%@*}, digest: ${1#*@}}
  - {name: ghcr.io/brandonrobare/crm-ui, newName: ${2%@*}, digest: ${2#*@}}
EOF
  kubectl kustomize "$dir"
  rm -rf "$dir"
}

deploy() {
  local manifest="${1:?usage: release.sh deploy artifact-manifest.json}" api ui commit current_api current_ui
  commit=$(jq -r '.gitCommit // empty' "$manifest")
  api="$(jq -r '.images.api.repository // empty' "$manifest")@$(jq -r '.images.api.digest // empty' "$manifest")"
  ui="$(jq -r '.images.ui.repository // empty' "$manifest")@$(jq -r '.images.ui.digest // empty' "$manifest")"
  jq -e '.images.api.repository and .images.api.digest and .images.ui.repository and .images.ui.digest' "$manifest" > /dev/null \
    || fail "Refused: the manifest has no images.api / images.ui pair. Release a commit whose CI built both images."
  [ "$commit" = "${EXPECTED_COMMIT:?}" ] || fail "Refused: manifest commit '$commit' is not the release commit '$EXPECTED_COMMIT'."
  for image in "$api" "$ui"; do
    [[ "${image#*@}" =~ $digest_re ]] || fail "Refused: '$image' is not pinned to a sha256 digest."
    [[ "$image" == "${EXPECTED_REGISTRY:?}"* ]] || fail "Refused: '$image' is not in $EXPECTED_REGISTRY."
  done

  current_api=$(image_of crm-api)
  current_ui=$(image_of crm-ui)
  k delete job crm-kafka-topics --ignore-not-found > /dev/null
  if [[ "$current_api" == *@sha256:* && "$current_ui" == *@sha256:* ]]; then
    render "$current_api" "$current_ui" | k apply -f - > /dev/null
  else
    render "$api" "$ui" | k apply -f - > /dev/null
  fi
  state_set "release-commit=$commit" "release-api=$api" "release-ui=$ui" "status=deploying"
  k rollout status statefulset/crm-postgres --timeout=300s
  k rollout status statefulset/crm-kafka --timeout=300s
  if k get job crm-kafka-topics > /dev/null 2>&1; then
    k wait --for=condition=complete job/crm-kafka-topics --timeout=240s
  fi
  if ! roll "$api" "$ui"; then
    state_set "status=failed"
    fail "Rollout failed. The previous pods keep serving; run the rollback job to restore the known-good pair."
  fi
  state_set "status=deployed"
  echo "Deployed $commit: $api and $ui. Smoke decides whether it becomes known-good."
}

mark() {
  case "${1:-}" in
    succeeded)
      state_set "status=succeeded" "known-good-commit=$(state_get release-commit)" \
        "known-good-api=$(state_get release-api)" "known-good-ui=$(state_get release-ui)"
      ;;
    failed) state_set "status=failed" ;;
    *) fail "usage: release.sh mark succeeded|failed" ;;
  esac
  echo "Release $(state_get release-commit) marked $1."
}

rollback() {
  local api ui commit
  api=$(state_get known-good-api)
  ui=$(state_get known-good-ui)
  commit=$(state_get known-good-commit)
  [ -n "$api" ] && [ -n "$ui" ] || fail "Refused: no known-good release recorded yet."
  if [ "$(image_of crm-api)" = "$api" ] && [ "$(image_of crm-ui)" = "$ui" ]; then
    fail "Refused: the known-good release $commit is already running."
  fi
  state_set "release-commit=$commit" "release-api=$api" "release-ui=$ui" "status=rolling-back"
  roll "$api" "$ui" || { state_set "status=failed"; fail "Rollback rollout failed."; }
  state_set "status=deployed"
  echo "Rolled back to $commit: $api and $ui. Run smoke next."
}

secrets() {
  local dir
  dir=$(mktemp -d)
  trap 'rm -rf "$dir"' RETURN
  ( umask 077
    printf '%s' "${CRM_DB_PASSWORD:?}" > "$dir/password"
    printf '%s' "${CRM_AGENT_PASSWORD:?}" > "$dir/agent-password"
    printf '%s' "${CRM_ADMIN_PASSWORD:?}" > "$dir/admin-password"
    printf '%s\n' "${CRM_JWT_PRIVATE_KEY:?}" > "$dir/jwt-private.pem"
    printf '%s\n' "${CRM_JWT_PUBLIC_KEY:?}" > "$dir/jwt-public.pem" )
  upsert() { k create "$@" --dry-run=client -o yaml | k apply -f - > /dev/null; }
  upsert secret generic crm-db --from-file="$dir/password"
  upsert secret generic crm-auth --from-file="$dir/agent-password" --from-file="$dir/admin-password"
  upsert secret generic crm-jwt --from-file="$dir/jwt-private.pem" --from-file="$dir/jwt-public.pem"
  if [ -n "${CRM_TLS_CERT:-}" ]; then
    ( umask 077; printf '%s\n' "$CRM_TLS_CERT" > "$dir/tls.crt"; printf '%s\n' "${CRM_TLS_KEY:?}" > "$dir/tls.key" )
    upsert secret tls crm-tls --cert="$dir/tls.crt" --key="$dir/tls.key"
  fi
  echo "Secrets crm-db, crm-auth and crm-jwt are up to date${CRM_TLS_CERT:+, and crm-tls}."
}

case "${1:-}" in
  deploy) deploy "${2:-}" ;;
  mark) mark "${2:-}" ;;
  rollback) rollback ;;
  secrets) secrets ;;
  *) fail "usage: release.sh deploy <manifest> | mark succeeded|failed | rollback | secrets" ;;
esac
