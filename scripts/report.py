#!/usr/bin/env python3
"""Build a self-contained Allure HTML report and ZIP for email, even after failed tests."""
import argparse
import os
from pathlib import Path
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def safe_paths(results: Path, output: Path) -> tuple:
    target = ROOT / "target"
    for path in [target, results, output, output.parent / (output.name + ".zip")]:
        # Reject symlink paths before resolving; Allure --clean must never leave target/.
        if path.is_symlink() or any(parent.is_symlink() for parent in path.parents if parent != ROOT):
            raise ValueError("Report paths must not use symbolic links")
    results, output = results.resolve(), output.resolve()
    if not results.is_relative_to(target) or not output.is_relative_to(target) or output == target:
        raise ValueError("Results and report output must be beneath the repository target directory")
    if results.is_relative_to(output) or output.is_relative_to(results):
        raise ValueError("Report output and test results must not overlap")
    return results, output


def generate(results: Path, output: Path) -> int:
    results, output = safe_paths(results, output)
    if not list(results.glob("*-result.json")):
        print("No Allure test results found. Run tests before generating a report.", file=sys.stderr)
        return 2
    results.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(ROOT / "config/allure-categories.json", results / "categories.json")
    properties = ET.parse(ROOT / "pom.xml").getroot().find("{*}properties")
    version = properties.find("{*}allure.cli.version").text
    tools = ROOT / "target/tools"
    if tools.is_symlink():
        raise ValueError("Allure tools must not use a symbolic link")
    tools.mkdir(parents=True, exist_ok=True)
    wrapper = str(ROOT / ("mvnw.cmd" if os.name == "nt" else "mvnw"))
    command = [wrapper, "-B", "-ntp", "-C"]
    # An optional public cache location avoids credential-bearing Maven settings.
    if os.environ.get("FRAMEWORK_MAVEN_CACHE"):
        command.append("-Dmaven.repo.local=" + os.environ["FRAMEWORK_MAVEN_CACHE"])
    command.extend(["org.apache.maven.plugins:maven-dependency-plugin:3.11.0:copy",
                    f"-Dartifact=io.qameta.allure:allure-commandline:{version}:zip",
                    "-DoutputDirectory=" + str(tools)])
    copied = subprocess.run(command, cwd=ROOT, check=False)
    if copied.returncode:
        return copied.returncode
    archive = tools / f"allure-commandline-{version}.zip"
    with zipfile.ZipFile(archive) as distribution:
        # Defense in depth against archive path traversal.
        for member in distribution.infolist():
            if not (tools / member.filename).resolve().is_relative_to(tools.resolve()):
                raise ValueError("Invalid Allure distribution path")
        distribution.extractall(tools)
    binary = tools / f"allure-{version}/bin" / ("allure.bat" if os.name == "nt" else "allure")
    if os.name != "nt":
        binary.chmod(0o755)
    generated = subprocess.run([str(binary), "generate", str(results), "--single-file",
                                "--clean", "--output", str(output)], cwd=ROOT, check=False)
    if generated.returncode:
        return generated.returncode
    if not (output / "index.html").is_file():
        print("Allure did not create index.html", file=sys.stderr)
        return 2
    with zipfile.ZipFile(output.parent / (output.name + ".zip"), "w", zipfile.ZIP_DEFLATED) as delivery:
        delivery.write(output / "index.html", "index.html")
    print("HTML report:", output / "index.html")
    print("Email attachment:", output.parent / (output.name + ".zip"))
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--results", type=Path, default=ROOT / "target/allure-results")
    parser.add_argument("--output", type=Path, default=ROOT / "target/allure-report")
    args = parser.parse_args()
    try:
        return generate(args.results, args.output)
    except ValueError:
        print("Unsafe report path: use separate, non-symlink directories beneath target/", file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
