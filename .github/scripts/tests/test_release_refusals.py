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


class ReleaseRefusalTest(unittest.TestCase):
    def deploy(self, manifest):
        with tempfile.TemporaryDirectory() as directory:
            calls = Path(directory) / "kubectl-calls"
            fake = Path(directory) / "kubectl"
            fake.write_text(f'#!/bin/sh\necho "$@" >> "{calls}"\nexit 1\n')
            fake.chmod(0o755)
            path = Path(directory) / "manifest.json"
            path.write_text(json.dumps(manifest))
            env = {**os.environ, "PATH": f"{directory}:{os.environ['PATH']}",
                   "EXPECTED_COMMIT": COMMIT, "EXPECTED_REGISTRY": "ghcr.io/owner/"}
            result = subprocess.run(["bash", "scripts/release.sh", "deploy", str(path)],
                                    cwd=REPO, capture_output=True, text=True, env=env)
            return result, calls.exists()

    def assert_refused(self, manifest, reason):
        result, touched_cluster = self.deploy(manifest)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn(reason, result.stderr)
        self.assertFalse(touched_cluster, "kubectl ran before the manifest was accepted")

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
        result, touched_cluster = self.deploy({"gitCommit": COMMIT, "images": {"api": API, "ui": UI}})
        self.assertTrue(touched_cluster, result.stderr)


if __name__ == "__main__":
    unittest.main()
