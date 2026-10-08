#!/usr/bin/env bash
set -euo pipefail

tf_version=1.13.5
tf_sha256=0dbe3fcc268eb670801af6a6456799d1ae26e72e73797f6c6167e18aafd1fd9a
tools="${RUNNER_TEMP:?}/iac-tools"

mkdir -p "$tools"
curl -sSfL -o "$tools/terraform.zip" \
  "https://releases.hashicorp.com/terraform/$tf_version/terraform_${tf_version}_linux_amd64.zip"
echo "$tf_sha256  $tools/terraform.zip" | sha256sum -c -
unzip -oq "$tools/terraform.zip" terraform -d "$tools"

python3 -m venv "$tools/venv"
"$tools/venv/bin/pip" install -q ansible-core==2.21.4 ansible-lint==26.9.0 kubernetes==36.0.3
"$tools/venv/bin/ansible-galaxy" collection install -r infra/ansible/requirements.yml

echo "$tools" >> "$GITHUB_PATH"
echo "$tools/venv/bin" >> "$GITHUB_PATH"
