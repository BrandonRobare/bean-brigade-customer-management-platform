#!/usr/bin/env bash
set -euo pipefail

jar_sums=${1:-backend/target/SHA256SUMS}
dist_sums=${2:-frontend/dist/SHA256SUMS}
jar_sha=$(awk '/\.jar$/ {print $1}' "$jar_sums")
dist_sha=$(sha256sum "$dist_sums" | cut -d' ' -f1)

for digest in "$API_DIGEST" "$UI_DIGEST" "sha256:$jar_sha" "sha256:$dist_sha"; do
  if [[ ! "$digest" =~ ^sha256:[0-9a-f]{64}$ ]]; then
    echo "::error::Manifest refused: '$digest' is not sha256: followed by 64 lowercase hexadecimal digits." >&2
    exit 1
  fi
done

jq -n \
  --arg version "$(sed -n 's/^version=//p' "$jar_sums")" \
  --arg commit "$GITHUB_SHA" \
  --arg run "$GITHUB_RUN_ID" \
  --arg runUrl "$GITHUB_SERVER_URL/$GITHUB_REPOSITORY/actions/runs/$GITHUB_RUN_ID" \
  --arg builtAt "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
  --arg jar "$jar_sha" \
  --arg dist "$dist_sha" \
  --arg apiRepo "$API_REPO" \
  --arg apiDigest "$API_DIGEST" \
  --arg uiRepo "$UI_REPO" \
  --arg uiDigest "$UI_DIGEST" \
  '{application: "crm-api", version: $version, gitCommit: $commit, runId: $run, jarSha256: $jar,
    imageRepository: $apiRepo, imageDigest: $apiDigest, builtBy: "capstone-ci",
    builtAt: $builtAt, runUrl: $runUrl,
    images: {api: {repository: $apiRepo, digest: $apiDigest},
             ui: {repository: $uiRepo, digest: $uiDigest, distSha256: $dist}}}'
