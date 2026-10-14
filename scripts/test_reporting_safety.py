#!/usr/bin/env python3
"""Safety regressions for destructive report-path handling and unsafe artifact quarantine."""
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import report
import verify_evidence


class ReportingSafetyTest(unittest.TestCase):
    def test_output_outside_target_is_rejected_before_modification(self):
        with tempfile.TemporaryDirectory() as directory:
            sentinel = Path(directory) / "keep.txt"
            sentinel.write_text("keep")
            with self.assertRaises(ValueError):
                report.safe_paths(report.ROOT / "target/allure-results", Path(directory))
            self.assertEqual(sentinel.read_text(), "keep")

    def test_target_root_and_overlapping_paths_are_rejected(self):
        target = report.ROOT / "target"
        for output in [target, target / "allure-results", target / "allure-results/nested"]:
            with self.assertRaises(ValueError):
                report.safe_paths(target / "allure-results", output)

    def test_valid_paths_are_accepted(self):
        target = report.ROOT / "target"
        result, output = report.safe_paths(target / "allure-results", target / "allure-report")
        self.assertEqual(result, target / "allure-results")
        self.assertEqual(output, target / "allure-report")

    def test_quarantine_removes_only_generated_evidence(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for name in ["allure-results", "allure-report", "surefire-reports", "classes"]:
                (root / "target" / name).mkdir(parents=True)
                (root / "target" / name / "sentinel.txt").write_text("synthetic")
            (root / "target/allure-report.zip").write_text("synthetic")
            with patch.object(verify_evidence, "ROOT", root):
                verify_evidence.purge_default_artifacts()
            self.assertTrue((root / "target/classes/sentinel.txt").exists())
            self.assertFalse((root / "target/allure-results").exists())
            self.assertFalse((root / "target/allure-report.zip").exists())

    def test_symlink_quarantine_does_not_delete_destination(self):
        with tempfile.TemporaryDirectory() as directory, tempfile.TemporaryDirectory() as destination:
            root = Path(directory)
            sentinel = Path(destination) / "keep.txt"
            sentinel.write_text("keep")
            try:
                (root / "target").symlink_to(destination, target_is_directory=True)
            except OSError:
                self.skipTest("Symlink creation unavailable on this platform")
            with patch.object(verify_evidence, "ROOT", root):
                verify_evidence.purge_default_artifacts()
            self.assertEqual(sentinel.read_text(), "keep")
            self.assertFalse((root / "target").exists())


if __name__ == "__main__":
    unittest.main()
