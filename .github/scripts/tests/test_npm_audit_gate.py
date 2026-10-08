import datetime
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


GATE = Path(__file__).resolve().parents[1] / "check-npm-audit.py"
REPO_EXCEPTIONS = Path(__file__).resolve().parents[2] / "npm-audit-exceptions.json"


def audit(*advisories):
    vulnerabilities = {}
    for name, ghsa, severity in advisories:
        entry = vulnerabilities.setdefault(name, {"name": name, "severity": severity, "via": []})
        entry["via"].append({"name": name, "severity": severity, "url": f"https://github.com/advisories/{ghsa}"})
    counts = {s: sum(v["severity"] == s for v in vulnerabilities.values())
              for s in ["info", "low", "moderate", "high", "critical"]}
    vulnerabilities["@angular/forms"] = {"name": "@angular/forms", "severity": "moderate", "via": ["@angular/core"]}
    return {"vulnerabilities": vulnerabilities, "metadata": {"vulnerabilities": {**counts, "total": sum(counts.values())}}}


def exception(advisory, until="2099-01-01"):
    return {"id": "npm-001", "advisory": advisory, "until": until, "owner": "brandon", "reason": "test"}


class NpmAuditGateTest(unittest.TestCase):
    def run_gate(self, report, exceptions=None):
        with tempfile.TemporaryDirectory() as directory:
            env = {**os.environ, "NPM_AUDIT_EXCEPTIONS": str(Path(directory) / "exceptions.json")}
            if exceptions is not None:
                Path(env["NPM_AUDIT_EXCEPTIONS"]).write_text(exceptions if isinstance(exceptions, str) else json.dumps(exceptions))
            path = Path(directory) / "npm-audit.json"
            if report is not None:
                path.write_text(report if isinstance(report, str) else json.dumps(report))
            return subprocess.run([sys.executable, str(GATE), str(path)], capture_output=True, text=True, env=env)

    def test_clean_audit_passes(self):
        result = self.run_gate(audit())
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("| none | 0 |", result.stdout)

    def test_moderate_passes(self):
        result = self.run_gate(audit(("@angular/core", "GHSA-hh8m-fm6v-7cvg", "moderate")))
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("| moderate | 1 |", result.stdout)

    def test_high_and_critical_block(self):
        for severity in ["high", "critical"]:
            with self.subTest(severity=severity):
                result = self.run_gate(audit(("lodash", "GHSA-aaaa-bbbb-cccc", severity)))
                self.assertEqual(result.returncode, 1, result.stdout)
                self.assertIn(f"| GHSA-aaaa-bbbb-cccc | {severity} | lodash | blocking |", result.stdout)
                self.assertIn("::error::", result.stderr)

    def test_excepted_advisory_passes_and_is_listed(self):
        report = audit(("@angular/core", "GHSA-jj27-h5hq-8x99", "high"), ("@angular/compiler", "GHSA-jj27-h5hq-8x99", "high"))
        result = self.run_gate(report, [exception("GHSA-jj27-h5hq-8x99")])
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("| GHSA-jj27-h5hq-8x99 | high | @angular/compiler, @angular/core | excepted (npm-001) |", result.stdout)

    def test_new_advisory_blocks_next_to_excepted_one(self):
        report = audit(("@angular/core", "GHSA-jj27-h5hq-8x99", "high"), ("axios", "GHSA-aaaa-bbbb-cccc", "high"))
        result = self.run_gate(report, [exception("GHSA-jj27-h5hq-8x99")])
        self.assertEqual(result.returncode, 1, result.stdout)
        self.assertIn("| GHSA-aaaa-bbbb-cccc | high | axios | blocking |", result.stdout)

    def test_expired_exception_blocks(self):
        yesterday = (datetime.date.today() - datetime.timedelta(days=1)).isoformat()
        result = self.run_gate(audit(("@angular/core", "GHSA-rgjc-h3x7-9mwg", "high")), [exception("GHSA-rgjc-h3x7-9mwg", yesterday)])
        self.assertEqual(result.returncode, 1, result.stdout)

    def test_malformed_exceptions_block(self):
        for exceptions in ["not json", [{"advisory": "GHSA-rgjc-h3x7-9mwg"}], [exception("GHSA-x", until="soon")]]:
            with self.subTest(exceptions=exceptions):
                result = self.run_gate(audit(), exceptions)
                self.assertEqual(result.returncode, 1, result.stdout)
                self.assertIn("invalid exceptions file", result.stderr)

    def test_missing_or_failed_report_blocks(self):
        for report in [None, "", "not json", {"error": {"code": "ENOTFOUND"}}]:
            with self.subTest(report=report):
                result = self.run_gate(report)
                self.assertEqual(result.returncode, 1, result.stdout)
                self.assertIn("didn't finish", result.stdout)

    def test_repo_exceptions_file_is_valid(self):
        entries = json.loads(REPO_EXCEPTIONS.read_text())
        self.assertTrue(entries)
        for entry in entries:
            self.assertLessEqual({"id", "advisory", "until", "owner", "reason"}, entry.keys())
            datetime.date.fromisoformat(entry["until"])


if __name__ == "__main__":
    unittest.main()
