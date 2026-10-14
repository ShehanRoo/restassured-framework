# Security review and validation record

Review date: **14 October 2026** (Asia/Colombo). Reviewer: **Nara subagent**, adapting its security
and tester personas to Java REST Assured/JUnit rather than Playwright. Review scope covers source,
tests, schema enforcement, configuration, Allure evidence, build dependencies and CI definitions.
This is not a security certification, penetration test or deployed-service authorization assessment.

## Findings and remediation

| Initial finding | Implemented remediation |
| --- | --- |
| Artifact uploads ran even after evidence verification failed | GitHub uploads require the evidence step's successful outcome; verifier can purge unsafe default evidence, including before CodeBuild's unconditional artifact phase |
| Server-controlled media-type parameters could reach evidence | Only normalized JSON media types remain; parameters and unknown header names are omitted; a canary regression covers both |
| Allure `--clean` could target arbitrary directories | Results/output must be separate paths beneath repository `target/`; symlinks are rejected; Python tests cover path/quarantine safety |
| Create-request negative coverage omitted several constraints | Eight data-driven cases cover missing required keys, empty/overlong names, wrong types and unexpected fields; each asserts zero network requests |
| Dependency convergence was not enforced | Maven Enforcer now requires dependency convergence; the resolved tree passes |
| Custom numeric expectations could create raw exception diagnostics | Expected scalar types are constrained to standard JSON/JDK classes and expectation conversion/comparison has a sanitized failure path |
| Review/context documentation was missing | This record, `.context/project-context.md`, `.context/README.md` and the secure-coding checklist are checked in |

Additional hardening during implementation: mapper configuration is private; duplicate/trailing JSON
and excessive nesting/numeric/string tokens are rejected; request-spec/parameter construction and
transport errors are sanitized, including checked exceptions propagated through Groovy; schema
direction metadata is constrained; unknown JSON field names are withheld; the dependency-scan
workflow accepts only the default branch; and action revisions/wrapper checksum are pinned.

## Executed checks

- Checksum-verified temporary Temurin **21.0.12.1** and official checksum-pinned Maven **3.9.11**.
- `python3 scripts/run_and_report.py`: **39 Java tests pass**, formatting/convergence/build checks
  pass, SpotBugs reports **zero high-priority findings**, **5 Python reporting-safety tests pass**,
  and standalone HTML/ZIP evidence verification passes.
- Deliberate failure demo: **one actual failed test**, process exits **1**, Allure marks `/id` as
  failed, and attachments show expected `999` / actual `7` while retaining sanitized HTTP evidence.
- `python3 scripts/verify_evidence.py --require-failure`: passed on the demo. The synthetic canary
  is absent from raw Allure results, Surefire output, HTML text and base64-embedded Allure data.
  The delivery ZIP contains exactly the matching `index.html`.
- Browser inspection of generated HTML: confirmed failed-test status/category, red field step,
  expected/actual evidence, redacted response name/cookie and available HTTP request/response attachments.
- Root CodeBuild buildspec, GitHub workflows and Dependabot YAML parse successfully.
- Git ignore checks exclude `.env`, results, reports and delivery ZIP, while retaining `.env.example`.

The default passing suite/report was restored after the failure demo. All outputs remain ignored
under `target/`. The generated demo report can be reproduced with the README command.

## Limits and deployment checks

- OWASP Dependency-Check is configured with a CVSS 7 threshold and a trusted-branch workflow; a full
  current CVE-data scan has **not been completed locally**. Configure NVD access and execute the
  security profile; do not interpret the source review or SpotBugs as a clean dependency-CVE result.
- GitHub Actions and managed AWS CodeBuild have not been executed in a connected remote repository
  or AWS account. YAML, paths and local command behavior were validated. Cloud IAM/source/network/
  artifact settings must be verified in the user's project using `docs/CODEBUILD.md`.
- Mocks verify framework behavior; live endpoint compatibility, RBAC and tenant isolation require
  the user's API and protected synthetic identities. External tests remain opt-in.
- The evidence allowlist is not general unknown-secret detection. Approved fields must contain
  synthetic report-safe values; future evidence/transport changes require another Nara review and
  updated canary regressions. No report is emailed or publicly published by this framework.

Final Nara delta-review result: **Pass**. All initial findings were remediated; residual limits above remain.
