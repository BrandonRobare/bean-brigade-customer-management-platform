import datetime
import json
import os
from pathlib import Path
import sys


EXCEPTIONS = Path(os.environ.get("NPM_AUDIT_EXCEPTIONS", Path(__file__).resolve().parents[1] / "npm-audit-exceptions.json"))
FIELDS = {"id", "advisory", "until", "owner", "reason"}
BLOCKING = {"high", "critical"}


def load_exceptions():
    if not EXCEPTIONS.exists():
        return {}
    entries = json.loads(EXCEPTIONS.read_text())
    if not isinstance(entries, list) or not all(isinstance(e, dict) and FIELDS <= e.keys() for e in entries):
        raise ValueError(f"every entry needs {sorted(FIELDS)}")
    today = datetime.date.today()
    return {e["advisory"]: e["id"] for e in entries if datetime.date.fromisoformat(e["until"]) >= today}


def advisories(report):
    audit = json.loads(Path(report).read_text())
    if "error" in audit or not isinstance(audit.get("vulnerabilities"), dict):
        raise ValueError("npm audit did not finish")
    found = {}
    for vulnerability in audit["vulnerabilities"].values():
        for via in vulnerability["via"]:
            if isinstance(via, dict) and via.get("severity") in BLOCKING:
                advisory = via["url"].rstrip("/").rsplit("/", 1)[-1]
                found.setdefault((advisory, via["severity"]), set()).add(via["name"])
    return audit["metadata"]["vulnerabilities"], found


def main(report):
    try:
        exceptions = load_exceptions()
    except (OSError, ValueError, TypeError, KeyError) as error:
        print(f"::error::npm audit gate failed: invalid exceptions file {EXCEPTIONS}: {error}", file=sys.stderr)
        return 1
    print("### npm audit (production dependencies)\n")
    try:
        counts, found = advisories(report)
    except (OSError, ValueError, KeyError, TypeError, AttributeError):
        print("No report: npm audit didn't finish. Check the npm audit step log.")
        print("::error::npm audit gate failed: missing or invalid report.", file=sys.stderr)
        return 1
    print("| Severity | Packages |")
    print("|---|---|")
    for severity, count in counts.items():
        if severity != "total" and count:
            print(f"| {severity} | {count} |")
    if not counts.get("total"):
        print("| none | 0 |")
    blocked = False
    if found:
        print("\n| Advisory | Severity | Packages | Status |")
        print("|---|---|---|---|")
        for (advisory, severity), packages in sorted(found.items()):
            excepted = exceptions.get(advisory)
            blocked |= not excepted
            print(f"| {advisory} | {severity} | {', '.join(sorted(packages))} | {f'excepted ({excepted})' if excepted else 'blocking'} |")
    print("\nFails at high/critical unless the advisory is excepted in `.github/npm-audit-exceptions.json`.")
    print("Triage in `docs/security-findings.csv`. Full report in the `npm-audit-report` artifact.")
    if blocked:
        print("::error::npm audit gate failed: high/critical advisories without an exception. "
              "See the job summary.", file=sys.stderr)
    return 1 if blocked else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1]))
