import datetime
import json
import os
from pathlib import Path
import sys


EXCEPTIONS = Path(os.environ.get("ZAP_EXCEPTIONS", Path(__file__).resolve().parents[1] / "zap-exceptions.json"))
FIELDS = {"id", "plugin", "match", "until", "owner", "reason"}
RISKS = {"3": "high", "2": "medium", "1": "low", "0": "informational"}


def load_exceptions():
    if not EXCEPTIONS.exists():
        return []
    entries = json.loads(EXCEPTIONS.read_text())
    if not isinstance(entries, list) or not all(isinstance(e, dict) and FIELDS <= e.keys() for e in entries):
        raise ValueError(f"every entry needs {sorted(FIELDS)}")
    today = datetime.date.today()
    return [e for e in entries if datetime.date.fromisoformat(e["until"]) >= today]


def alerts(report, exceptions):
    sites = json.loads(Path(report).read_text())["site"]
    if not isinstance(sites, list) or not sites:
        raise ValueError("no sites scanned")
    rows = []
    for site in sites:
        for alert in site["alerts"]:
            risk = RISKS[str(alert["riskcode"])]
            excepted = next((e["id"] for e in exceptions if str(e["plugin"]) == str(alert["pluginid"])
                             and e["match"] in alert.get("otherinfo", "")), None)
            rows.append((alert["alert"], f"{risk}, excepted ({excepted})" if excepted else risk, int(alert.get("count", 1))))
    return rows


def main(report):
    try:
        exceptions = load_exceptions()
    except (OSError, ValueError, TypeError, KeyError) as error:
        print(f"::error::ZAP gate failed: invalid exceptions file {EXCEPTIONS}: {error}", file=sys.stderr)
        return 1
    print("### ZAP baseline\n")
    print("| Alert | Risk | Instances |")
    print("|---|---|---|")
    try:
        rows = alerts(report, exceptions)
    except (OSError, ValueError, KeyError, TypeError, AttributeError):
        print("| report missing or invalid | - | - |")
        rows, failed = [], True
    else:
        failed = any(risk == "high" for _, risk, _ in rows)
    for name, risk, count in rows or ([] if failed else [("none", "-", 0)]):
        print(f"| {name} | {risk} | {count} |")
    print("\nFails at high risk not excepted in `.github/zap-exceptions.json`, or a missing report. Triage in")
    print("`docs/security-findings.csv`. Full report in the `dast-report` artifact.")
    if failed:
        print("::error::ZAP gate failed: high-risk alerts or a missing report. See the job summary.", file=sys.stderr)
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else "dast-report/zap.json"))
