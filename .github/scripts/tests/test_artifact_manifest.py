import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest


SCRIPT = Path(__file__).resolve().parents[1] / "write-artifact-manifest.sh"
API_DIGEST = "sha256:" + "a" * 64
UI_DIGEST = "sha256:" + "b" * 64
JAR_SHA = "c" * 64


class ArtifactManifestTest(unittest.TestCase):
    def run_script(self, **overrides):
        with tempfile.TemporaryDirectory() as directory:
            jar_sums = Path(directory) / "jar.sums"
            jar_sums.write_text(f"{JAR_SHA}  app.jar\ncommit=abc123\nrun=7\nversion=0.0.1-SNAPSHOT\n")
            dist_sums = Path(directory) / "dist.sums"
            dist_sums.write_text(f"{'d' * 64}  lab50-crm-ui/browser/index.html\n")
            env = {
                "PATH": os.environ["PATH"],
                "API_REPO": "ghcr.io/owner/crm-api",
                "API_DIGEST": API_DIGEST,
                "UI_REPO": "ghcr.io/owner/crm-ui",
                "UI_DIGEST": UI_DIGEST,
                "GITHUB_SHA": "abc123",
                "GITHUB_RUN_ID": "42",
                "GITHUB_SERVER_URL": "https://github.com",
                "GITHUB_REPOSITORY": "owner/repo",
                **overrides,
            }
            env = {key: value for key, value in env.items() if value is not None}
            result = subprocess.run(
                ["bash", str(SCRIPT), str(jar_sums), str(dist_sums)],
                capture_output=True, text=True, env=env,
            )
            return result, hashlib.sha256(dist_sums.read_bytes()).hexdigest()

    def test_records_the_release_pair_and_keeps_root_fields(self):
        result, dist_sha = self.run_script()
        self.assertEqual(result.returncode, 0, result.stderr)
        manifest = json.loads(result.stdout)
        self.assertEqual(manifest["application"], "crm-api")
        self.assertEqual(manifest["version"], "0.0.1-SNAPSHOT")
        self.assertEqual(manifest["gitCommit"], "abc123")
        self.assertEqual(manifest["runId"], "42")
        self.assertEqual(manifest["jarSha256"], JAR_SHA)
        self.assertEqual(manifest["imageRepository"], "ghcr.io/owner/crm-api")
        self.assertEqual(manifest["imageDigest"], API_DIGEST)
        self.assertEqual(manifest["builtBy"], "capstone-ci")
        self.assertEqual(manifest["runUrl"], "https://github.com/owner/repo/actions/runs/42")
        self.assertRegex(manifest["builtAt"], r"^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\dZ$")
        self.assertEqual(manifest["images"], {
            "api": {"repository": "ghcr.io/owner/crm-api", "digest": API_DIGEST},
            "ui": {"repository": "ghcr.io/owner/crm-ui", "digest": UI_DIGEST, "distSha256": dist_sha},
        })

    def test_refuses_missing_or_malformed_digests(self):
        for name in ["API_DIGEST", "UI_DIGEST"]:
            for value in ["", "latest", "sha256:" + "A" * 64, "sha256:" + "a" * 63, API_DIGEST + "\nx"]:
                with self.subTest(name=name, value=value):
                    result, _ = self.run_script(**{name: value})
                    self.assertNotEqual(result.returncode, 0)
                    self.assertEqual(result.stdout, "")

    def test_refuses_unset_identity(self):
        for name in ["GITHUB_SHA", "GITHUB_RUN_ID", "API_REPO", "UI_REPO", "UI_DIGEST"]:
            with self.subTest(name=name):
                result, _ = self.run_script(**{name: None})
                self.assertNotEqual(result.returncode, 0)
                self.assertEqual(result.stdout, "")


if __name__ == "__main__":
    unittest.main()
