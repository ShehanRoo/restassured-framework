#!/usr/bin/env python3
"""Preserve the test failure exit code while always attempting to generate HTML."""
import os
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
wrapper = ROOT / ("mvnw.cmd" if os.name == "nt" else "mvnw")
command = [str(wrapper), "-B", "-ntp", "-C"]
if os.environ.get("FRAMEWORK_MAVEN_CACHE"):
    command.append("-Dmaven.repo.local=" + os.environ["FRAMEWORK_MAVEN_CACHE"])
tests = subprocess.run(command + ["clean", "verify"] + sys.argv[1:], cwd=ROOT, check=False)
report = subprocess.run([sys.executable, str(ROOT / "scripts/report.py")], cwd=ROOT, check=False)
safety = subprocess.run([sys.executable, str(ROOT / "scripts/test_reporting_safety.py")], cwd=ROOT, check=False)
evidence = subprocess.run([sys.executable, str(ROOT / "scripts/verify_evidence.py"), "--purge-on-failure"],
                          cwd=ROOT, check=False)
sys.exit(tests.returncode or report.returncode or safety.returncode or evidence.returncode)
