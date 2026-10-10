#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

problems=()

java_bin=java
if [ -n "${JAVA_HOME:-}" ]; then
  java_home=$JAVA_HOME
  command -v cygpath >/dev/null && java_home=$(cygpath -u "$JAVA_HOME")
  java_bin="$java_home/bin/java"
fi
if ! command -v "$java_bin" >/dev/null; then
  problems+=("Java not found ($java_bin). Install JDK 21 and open a new terminal.")
else
  java_major=$("$java_bin" -version 2>&1 | sed -n 's/.*version "\([0-9]*\).*/\1/p' | head -1)
  [ "${java_major:-0}" -ge 21 ] || problems+=("Java ${java_major:-?} found at $java_bin; need 21+. Fix JAVA_HOME or PATH, then open a new terminal.")
fi

if ! command -v node >/dev/null; then
  problems+=("Node not found. Install Node 22 and open a new terminal.")
else
  node_major=$(node -v | sed 's/^v\([0-9]*\).*/\1/')
  [ "$node_major" -ge 22 ] || problems+=("Node $node_major found; need 22+.")
fi

if ! command -v docker >/dev/null; then
  problems+=("Docker not found. Install Docker Desktop.")
elif ! docker info >/dev/null 2>&1; then
  problems+=("Docker is installed but not responding. Start Docker Desktop and wait for it to finish starting. On Linux, also check you're in the docker group (docker info shows the error).")
fi

command -v openssl >/dev/null || problems+=("openssl not found. On Windows, run this script from Git Bash, not PowerShell.")

if [ ${#problems[@]} -gt 0 ]; then
  printf 'Setup stopped. Fix these, then rerun:\n' >&2
  printf '  - %s\n' "${problems[@]}" >&2
  exit 1
fi

[ -f .env ] || cp .env.example .env

mkdir -p backend/.keys
cd backend/.keys
[ -f jwt-private.pem ] || openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out jwt-private.pem
openssl pkey -in jwt-private.pem -pubout -out jwt-public.pem

echo "Setup done. Next: docker compose up -d"
