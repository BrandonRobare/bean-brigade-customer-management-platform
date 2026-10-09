import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest


REPO = Path(__file__).resolve().parents[3]
COMMIT = "a" * 40
API = {"repository": "ghcr.io/owner/crm-api", "digest": "sha256:" + "b" * 64}
UI = {"repository": "ghcr.io/owner/crm-ui", "digest": "sha256:" + "c" * 64}


FAILING_KUBECTL = """#!/bin/sh
echo "$@" >> "{calls}"
exit 1
"""

API_ROLLOUT_FAILS = """#!/bin/sh
echo "$@" >> "{calls}"
case "$*" in *"-f -"*) cat > /dev/null ;; esac
case "$*" in
  *"rollout status deployment/crm-api"*) exit 1 ;;
  *"get configmap"*|*"get deployment"*) exit 1 ;;
  *"get job crm-db-backup"*) echo Complete ;;
  *"logs -l job-name=crm-db-backup"*) echo "BACKUP /backup/crm-20261008T230000Z.dump 4096" ;;
esac
exit 0
"""

BACKUP_FAILS = """#!/bin/sh
echo "$@" >> "{calls}"
case "$*" in *"-f -"*) cat > /dev/null ;; esac
case "$*" in
  *"get configmap"*|*"get deployment"*) exit 1 ;;
  *"get job crm-db-backup"*) echo Failed ;;
  *"logs -l job-name=crm-db-backup"*) echo "pg_dump: error: connection refused" ;;
esac
exit 0
"""


class ReleaseScriptTest(unittest.TestCase):
    def deploy(self, manifest, kubectl=FAILING_KUBECTL):
        with tempfile.TemporaryDirectory() as directory:
            calls = Path(directory) / "kubectl-calls"
            fake = Path(directory) / "kubectl"
            fake.write_text(kubectl.format(calls=calls))
            fake.chmod(0o755)
            path = Path(directory) / "manifest.json"
            path.write_text(json.dumps(manifest))
            env = {**os.environ, "PATH": f"{directory}:{os.environ['PATH']}",
                   "EXPECTED_COMMIT": COMMIT, "EXPECTED_REGISTRY": "ghcr.io/owner/"}
            env.update({"PLATFORM_HOSTNAME": "crm.example", "INGRESS_CLASS": "traefik",
                        "STORAGE_CLASS": "local-path", "INGRESS_NAMESPACE": "kube-system"})
            result = subprocess.run(["bash", "scripts/release.sh", "deploy", str(path)],
                                    cwd=REPO, capture_output=True, text=True, env=env)
            return result, calls.read_text() if calls.exists() else ""

    def assert_refused(self, manifest, reason):
        result, calls = self.deploy(manifest)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn(reason, result.stderr)
        self.assertEqual(calls, "", "kubectl ran before the manifest was accepted")

    def test_manifest_without_the_pair_is_refused(self):
        self.assert_refused({"gitCommit": COMMIT, "imageDigest": API["digest"]}, "no images.api / images.ui pair")

    def test_manifest_from_another_commit_is_refused(self):
        self.assert_refused({"gitCommit": "d" * 40, "images": {"api": API, "ui": UI}}, "is not the release commit")

    def test_tag_instead_of_digest_is_refused(self):
        for tag in ["latest", "sha256:" + "B" * 64, "sha256:" + "b" * 63]:
            with self.subTest(tag=tag):
                ui = {**UI, "digest": tag}
                self.assert_refused({"gitCommit": COMMIT, "images": {"api": API, "ui": ui}}, "not pinned to a sha256 digest")

    def test_image_outside_the_registry_is_refused(self):
        api = {**API, "repository": "docker.io/someone/crm-api"}
        self.assert_refused({"gitCommit": COMMIT, "images": {"api": api, "ui": UI}}, "is not in ghcr.io/owner/")

    def test_valid_manifest_reaches_the_cluster(self):
        result, calls = self.deploy({"gitCommit": COMMIT, "images": {"api": API, "ui": UI}})
        self.assertNotEqual(calls, "", result.stderr)

    def test_failed_api_rollout_fails_the_release_and_leaves_the_ui_alone(self):
        result, calls = self.deploy({"gitCommit": COMMIT, "images": {"api": API, "ui": UI}}, API_ROLLOUT_FAILS)
        self.assertNotEqual(result.returncode, 0, result.stdout)
        self.assertIn("Rollout failed", result.stderr)
        self.assertIn("set image deployment/crm-api", calls)
        self.assertNotIn("deployment/crm-ui", calls)

    def test_backup_runs_before_anything_is_applied(self):
        result, calls = self.deploy({"gitCommit": COMMIT, "images": {"api": API, "ui": UI}}, API_ROLLOUT_FAILS)
        self.assertIn("Backed up crm to /backup/crm-20261008T230000Z.dump", result.stdout)
        self.assertLess(calls.index("apply -f k8s/jobs/crm-db-backup.yaml"), calls.index("apply -f -"))

    def test_backup_job_manifests_exist(self):
        for job in ["crm-db-backup", "crm-db-restore-drill"]:
            self.assertTrue((REPO / "k8s" / "jobs" / f"{job}.yaml").is_file(), job)

    def test_failed_backup_refuses_the_release(self):
        result, calls = self.deploy({"gitCommit": COMMIT, "images": {"api": API, "ui": UI}}, BACKUP_FAILS)
        self.assertNotEqual(result.returncode, 0, result.stdout)
        self.assertIn("pre-release backup failed", result.stderr)
        self.assertNotIn("apply -f -", calls)
        self.assertNotIn("set image", calls)


if __name__ == "__main__":
    unittest.main()
