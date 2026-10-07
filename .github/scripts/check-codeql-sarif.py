import json
from pathlib import Path
import sys


def severity(score):
    if score >= 9:
        return "critical"
    if score >= 7:
        return "high"
    if score >= 4:
        return "medium"
    return "low"


def findings(report):
    runs = json.loads(Path(report).read_text())["runs"]
    if not isinstance(runs, list) or not runs:
        raise ValueError("no runs")
    counts = {}
    for run in runs:
        if not isinstance(run["results"], list):
            raise ValueError("no results")
        if any(invocation.get("executionSuccessful") is False for invocation in run.get("invocations", [])):
            raise ValueError("analysis failed")
        tool = run.get("tool", {})
        rules = tool.get("driver", {}).get("rules", [])
        rules += [rule for extension in tool.get("extensions", []) for rule in extension.get("rules", [])]
        scores = {rule["id"]: rule.get("properties", {}).get("security-severity") for rule in rules}
        for result in run["results"]:
            score = scores.get(result.get("ruleId"))
            label = f"level {result.get('level', 'warning')}" if score is None else severity(float(score))
            counts[label] = counts.get(label, 0) + 1
    return counts


def main(reports):
    failed = not reports
    print("### CodeQL\n")
    print("| Report | Severity | Findings |")
    print("|---|---|---|")
    for report in reports:
        name = Path(report).stem
        try:
            counts = findings(report)
        except (OSError, ValueError, KeyError, TypeError, AttributeError):
            print(f"| {name} | missing, invalid or failed analysis | - |")
            failed = True
            continue
        for label, count in sorted(counts.items()) or [("none", 0)]:
            print(f"| {name} | {label} | {count} |")
        failed |= bool(counts.keys() & {"high", "critical"})
    print("\nFails at high/critical (security-severity 7.0+) or a missing/failed analysis. Alerts are in the Security tab;")
    print("triage in `docs/security-findings.csv`. Full SARIF in the `codeql-report` artifact.")
    if failed:
        print("::error::CodeQL gate failed: high/critical findings or a missing/failed analysis. "
              "See the job summary and the Security tab.", file=sys.stderr)
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
