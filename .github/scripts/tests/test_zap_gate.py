import datetime
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


GATE = Path(__file__).resolve().parents[1] / "check-zap-report.py"


def report(*alerts):
    return {"site": [{"@name": "http://crm-edge:8080", "alerts": [
        {"pluginid": plugin, "alert": f"alert {plugin}", "riskcode": risk, "count": "2", "otherinfo": info}
        for plugin, risk, info in alerts]}]}


class ZapGateTest(unittest.TestCase):
    def run_gate(self, zap_report, exceptions=None):
        with tempfile.TemporaryDirectory() as directory:
            env = {**os.environ, "ZAP_EXCEPTIONS": str(Path(directory) / "exceptions.json")}
            if exceptions is not None:
                Path(env["ZAP_EXCEPTIONS"]).write_text(exceptions if isinstance(exceptions, str) else json.dumps(exceptions))
            path = Path(directory) / "zap.json"
            if zap_report is not None:
                path.write_text(zap_report if isinstance(zap_report, str) else json.dumps(zap_report))
            return subprocess.run([sys.executable, str(GATE), str(path)], capture_output=True, text=True, env=env)

    def exception(self, **overrides):
        until = (datetime.date.today() + datetime.timedelta(days=30)).isoformat()
        return [{"id": "zap-001", "plugin": "10003", "match": "@angular/core, version 19.2.25", "until": until,
                 "owner": "brandon", "reason": "same advisories as npm-001", **overrides}]

    def test_clean_report_passes(self):
        result = self.run_gate(report())
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("| none | - | 0 |", result.stdout)

    def test_medium_and_low_pass_and_are_listed(self):
        result = self.run_gate(report(("10038", "2", ""), ("10036", "1", "")))
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("| alert 10038 | medium | 2 |", result.stdout)
        self.assertIn("| alert 10036 | low | 2 |", result.stdout)

    def test_high_blocks(self):
        result = self.run_gate(report(("10003", "3", "lodash 4.17.0")))
        self.assertEqual(result.returncode, 1, result.stdout)
        self.assertIn("| alert 10003 | high | 2 |", result.stdout)
        self.assertIn("::error::", result.stderr)

    def test_triaged_exception_reports_without_blocking(self):
        result = self.run_gate(report(("10003", "3", "The identified library @angular/core, version 19.2.25 is vulnerable.")),
                               exceptions=self.exception())
        self.assertEqual(result.returncode, 0, result.stdout)
        self.assertIn("| alert 10003 | high, excepted (zap-001) | 2 |", result.stdout)

    def test_exception_only_covers_its_plugin_and_match(self):
        for plugin, info in [("10003", "lodash 4.17.0"), ("40012", "@angular/core, version 19.2.25")]:
            with self.subTest(plugin=plugin, info=info):
                result = self.run_gate(report((plugin, "3", info)), exceptions=self.exception())
                self.assertEqual(result.returncode, 1, result.stdout)

    def test_expired_exception_blocks(self):
        result = self.run_gate(report(("10003", "3", "@angular/core, version 19.2.25")),
                               exceptions=self.exception(until="2020-01-01"))
        self.assertEqual(result.returncode, 1, result.stdout)

    def test_missing_or_invalid_report_blocks(self):
        for bad in [None, "not json", {}, {"site": []}]:
            with self.subTest(bad=bad):
                result = self.run_gate(bad)
                self.assertEqual(result.returncode, 1, result.stdout)
                self.assertIn("missing or invalid", result.stdout)

    def test_malformed_exceptions_file_blocks(self):
        for bad in ["not json", [{"plugin": "10003"}], {"plugin": "10003"}]:
            with self.subTest(bad=bad):
                result = self.run_gate(report(), exceptions=bad)
                self.assertEqual(result.returncode, 1, result.stdout)
                self.assertIn("exceptions", result.stderr)


if __name__ == "__main__":
    unittest.main()
