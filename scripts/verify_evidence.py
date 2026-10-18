#!/usr/bin/env python3
"""Verify Allure failure-demo integration and scan generated results/reports for synthetic leaks."""
import argparse
import json
from pathlib import Path
import sys
import shutil
import base64
import re
import zipfile
import unicodedata

ROOT = Path(__file__).resolve().parents[1]
CANARY = "synthetic-security-canary-42"


def steps(items):
    for item in items:
        yield item
        yield from steps(item.get("steps", []))


def verify_attachment_text(content: str) -> None:
    # Newlines delimit trusted labels; untrusted scalar newlines must be JSON-escaped.
    if any(char != "\n" and unicodedata.category(char) in {"Cc", "Cf", "Zl", "Zp"}
           for char in content):
        raise AssertionError("Unsafe display control in text evidence")


def purge_default_artifacts() -> None:
    target = ROOT / "target"
    if target.is_symlink():
        target.unlink()
        return
    for name in ["allure-results", "allure-report", "surefire-reports"]:
        path = target / name
        if path.is_symlink():
            path.unlink()
        elif path.is_dir():
            shutil.rmtree(path)
    (target / "allure-report.zip").unlink(missing_ok=True)


def verify(require_failure: bool) -> None:
    results = ROOT / "target/allure-results"
    records = [json.loads(path.read_text()) for path in results.glob("*-result.json")]
    if not records:
        raise AssertionError("No Allure result files")
    attached_names = set()
    for record in records:
        for item in [record, *steps(record.get("steps", []))]:
            attached_names.update(attachment["name"] for attachment in item.get("attachments", []))
    assert {"HTTP request", "HTTP response"}.issubset(attached_names), "HTTP evidence missing"
    for path in results.glob("*-attachment.txt"):
        # Preserve CR bytes; universal-newline decoding would turn spoofed CR into allowed LF.
        verify_attachment_text(path.read_bytes().decode("utf-8"))
    for directory in [results, ROOT / "target/surefire-reports", ROOT / "target/allure-report"]:
        for path in directory.rglob("*"):
            if path.is_file() and CANARY.encode() in path.read_bytes():
                raise AssertionError("Synthetic secret leaked in generated artifact: " + str(path.relative_to(ROOT)))
    html_path = ROOT / "target/allure-report/index.html"
    html = html_path.read_text()
    embedded = re.findall(r"d\('([^']+)','([A-Za-z0-9+/=]+)'\)", html)
    assert any(name.startswith("data/test-cases/") for name, _ in embedded), "Standalone report data missing"
    assert any(name.startswith("data/attachments/") for name, _ in embedded), "Standalone report evidence missing"
    for name, encoded in embedded:
        if CANARY.encode() in base64.b64decode(encoded, validate=True):
            raise AssertionError("Synthetic secret leaked in embedded HTML data")
    with zipfile.ZipFile(ROOT / "target/allure-report.zip") as delivery:
        assert delivery.namelist() == ["index.html"], "Unexpected delivery ZIP content"
        assert delivery.read("index.html") == html_path.read_bytes(), "Delivery ZIP differs from standalone report"
    if require_failure:
        demos = [record for record in records if "ReportingFailureDemoTest" in record.get("fullName", "")]
        assert len(demos) == 1 and demos[0]["status"] == "failed", "Failure demo must be a real failed test"
        demo_steps = list(steps(demos[0].get("steps", [])))
        assert any(step["name"] == "Assert response field /id" and step["status"] == "failed"
                   for step in demo_steps), "Failed field step missing"
        names = {item["name"] for step in demo_steps for item in step.get("attachments", [])}
        assert {"HTTP request", "HTTP response", "Failed response assertion"}.issubset(names)
    print("Verified sanitized HTTP evidence" + (", failed field step and expected/actual diagnostics" if require_failure else ""))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--require-failure", action="store_true")
    parser.add_argument("--purge-on-failure", action="store_true",
                        help="Remove default generated evidence if verification fails, for CI artifact safety")
    args = parser.parse_args()
    try:
        verify(args.require_failure)
    except Exception:
        if args.purge_on_failure:
            purge_default_artifacts()
        print("Evidence verification failed; do not publish generated reports."
              + (" Default evidence artifacts removed." if args.purge_on_failure else ""), file=sys.stderr)
        sys.exit(1)
