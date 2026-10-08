import datetime
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


GATE = Path(__file__).resolve().parents[1] / "check-codeql-sarif.py"


def sarif(*scores, level="warning", rules_in="extensions", executed=True, path="src/App.java"):
    rules = [{"id": f"rule/{i}", "properties": {"security-severity": score}}
             for i, score in enumerate(scores) if score is not None]
    tool = {"driver": {"name": "CodeQL", "rules": []}}
    if rules_in == "extensions":
        tool["extensions"] = [{"name": "codeql/java-queries", "rules": rules}]
    else:
        tool["driver"]["rules"] = rules
    return {
        "version": "2.1.0",
        "runs": [{
            "tool": tool,
            "invocations": [{"executionSuccessful": executed}],
            "results": [{"ruleId": f"rule/{i}", "level": level, "locations": [{"physicalLocation": {"artifactLocation": {"uri": path}}}]}
                        for i in range(len(scores))],
        }],
    }


class CodeqlGateTest(unittest.TestCase):
    def run_gate(self, *reports, exceptions=None):
        with tempfile.TemporaryDirectory() as directory:
            env = {**os.environ, "CODEQL_EXCEPTIONS": str(Path(directory) / "exceptions.json")}
            if exceptions is not None:
                Path(env["CODEQL_EXCEPTIONS"]).write_text(exceptions if isinstance(exceptions, str) else json.dumps(exceptions))
            paths = []
            for i, report in enumerate(reports):
                path = Path(directory) / f"lang{i}.sarif"
                if report is not None:
                    path.write_text(report if isinstance(report, str) else json.dumps(report))
                paths.append(path)
            before = [p.read_text() for p in paths if p.exists()]
            result = subprocess.run([sys.executable, str(GATE), *map(str, paths)], capture_output=True, text=True, env=env)
            self.assertEqual([p.read_text() for p in paths if p.exists()], before, "Gate changed a report")
            return result

    def test_clean_analysis_passes(self):
        result = self.run_gate(sarif())
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("| lang0 | none | 0 |", result.stdout)

    def test_low_and_medium_pass_and_are_listed(self):
        result = self.run_gate(sarif("2.0", "6.9"))
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("| lang0 | low | 1 |", result.stdout)
        self.assertIn("| lang0 | medium | 1 |", result.stdout)

    def test_high_and_critical_block(self):
        for score, label in [("7.0", "high"), ("8.8", "high"), ("9.8", "critical")]:
            for rules_in in ["extensions", "driver"]:
                with self.subTest(score=score, rules_in=rules_in):
                    result = self.run_gate(sarif(score, rules_in=rules_in))
                    self.assertEqual(result.returncode, 1, result.stdout)
                    self.assertIn(f"| lang0 | {label} | 1 |", result.stdout)
                    self.assertIn("::error::", result.stderr)

    def test_non_security_result_is_reported_without_blocking(self):
        result = self.run_gate(sarif(None, level="note"))
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("| lang0 | level note | 1 |", result.stdout)

    def test_any_blocking_report_fails_the_gate(self):
        result = self.run_gate(sarif(), sarif("9.1"))
        self.assertEqual(result.returncode, 1, result.stdout)
        self.assertIn("| lang0 | none | 0 |", result.stdout)

    def test_failed_analysis_blocks(self):
        result = self.run_gate(sarif(executed=False))
        self.assertEqual(result.returncode, 1, result.stdout)

    def test_missing_invalid_or_incomplete_reports_block(self):
        for report in [None, "not json", {}, {"runs": []}, {"runs": [{"tool": {}}]}]:
            with self.subTest(report=report):
                result = self.run_gate(report)
                self.assertEqual(result.returncode, 1, result.stdout)
                self.assertIn("missing, invalid or failed analysis", result.stdout)

    def test_no_reports_blocks(self):
        result = self.run_gate()
        self.assertEqual(result.returncode, 1, result.stdout)


    def exception(self, **overrides):
        until = (datetime.date.today() + datetime.timedelta(days=30)).isoformat()
        return [{"id": "cq-001", "rule": "rule/0", "path": "src/App.java", "until": until,
                 "owner": "brandon", "reason": "stateless bearer tokens", **overrides}]

    def test_triaged_exception_reports_without_blocking(self):
        result = self.run_gate(sarif("8.8"), exceptions=self.exception())
        self.assertEqual(result.returncode, 0, result.stdout)
        self.assertIn("| lang0 | excepted (cq-001) | 1 |", result.stdout)

    def test_expired_exception_blocks(self):
        result = self.run_gate(sarif("8.8"), exceptions=self.exception(until="2020-01-01"))
        self.assertEqual(result.returncode, 1, result.stdout)
        self.assertIn("| lang0 | high | 1 |", result.stdout)

    def test_exception_only_covers_its_rule_and_file(self):
        for overrides in [{"rule": "rule/9"}, {"path": "src/Other.java"}]:
            with self.subTest(overrides=overrides):
                result = self.run_gate(sarif("8.8"), exceptions=self.exception(**overrides))
                self.assertEqual(result.returncode, 1, result.stdout)

    def test_malformed_exceptions_file_blocks(self):
        for bad in ["not json", [{"rule": "rule/0"}], {"rule": "rule/0"}]:
            with self.subTest(bad=bad):
                result = self.run_gate(sarif(), exceptions=bad)
                self.assertEqual(result.returncode, 1, result.stdout)
                self.assertIn("exceptions", result.stderr)

if __name__ == "__main__":
    unittest.main()
