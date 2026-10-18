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

## Supplied v1.0.0 guidelines reassessment — 18 October 2026

This section supersedes the earlier executed-check counts for the current working tree. The earlier
14 October review remains historical evidence. The supplied DOCX and all 14 section groups are mapped
in [GUIDELINES_ASSESSMENT.md](GUIDELINES_ASSESSMENT.md), with its source digest and applicability limits.

### Findings and count

Seven pre-existing gaps were identified and repaired, counted by root cause: **3 medium** (path/query
validation, implicit transport retries, scalar report-text spoofing) and **4 low** (bearer-header
validation bounds, generated request correlation, SBOM inventory, incomplete agent guideline coverage).
These are contextual review severities, not CVSS scores or confirmed CVEs; no high/critical finding
was identified in this scope. Missing service/deployment evidence is not counted as a proven defect.

The read-only Nara reviewer identified the scalar-evidence issue and four implementation refinements:
strict parameter-schema shape and bounded Unicode escaping (**2 medium**), correct GitHub-versus-S3
retention wording and a non-object-schema regression that actually proves its guard (**2 low**).
All were addressed. Across baseline gaps and implementation refinements: **11 tracked items,
5 medium and 6 low**. See the assessment's issue registry for the seven original gaps.

### Executed verification

- Used Java **21.0.12.1**, the checked-in Maven wrapper and an isolated public Maven dependency cache
  under OS temporary storage. Sandbox socket binding failed initially; the authorized rerun permitted
  loopback WireMock. No external API tests or destructive live operations ran.
- `./mvnw -B -ntp -C spotless:apply`: passed (temporary tool/cache environment paths supplied).
- Final `python3 scripts/run_and_report.py`: **51 Java tests**, zero failures/errors/skips;
  convergence/formatting/build gates passed, **zero high-priority SpotBugs findings**, **6 Python
  safety tests**, and sanitized source/embedded HTML/ZIP verification passed. Exit **0**.
- The final failure demo exited **1** as expected, with one actual failed `/id` assertion;
  `python3 scripts/verify_evidence.py --require-failure` passed. Inspected safe expected `999` versus
  actual `7`, redacted name/cookie, withheld parameter values and shared generated UUID in request
  and response attachments. The final default passing run was restored afterward.
- New failure-path regression exercises CR/LF, tab, ANSI, bidi and Unicode separator escaping through
  an actual response-field assertion. The artifact verifier also rejects raw display controls in
  text attachments. A 999,000-character formatting-string regression verifies bounded/truncated
  output. Parameter negative cases assert zero requests; dropped GET/POST/DELETE fault tests assert
  one request each; unsafe parameter schemas fail before transport.
- CycloneDX **1.6** JSON/XML inventories generated and parsed: **39 project dependency components**,
  with REST Assured, JUnit and WireMock present. Verified the reusable deny-all parameter schema is
  packaged in the framework JAR. SBOM is project inventory, not a complete build-tool/CLI inventory
  or a license approval.
- Updated GitHub workflows and CodeBuild YAML parsed; generated reports/SBOMs remain Git-ignored
  beneath `target/`; `git diff --check` passed. No credentials/reports committed, no push or publish.

### Current limits

- `./mvnw -B -ntp -C -Psecurity verify` was executed with a **180-second** local bound. Tests,
  SBOM generation and SpotBugs completed; OWASP Dependency-Check began its initial NVD update and
  warned that no API key was provided. Download reached **20,000 of 409,375 records (5%)** before
  timeout; the process group was stopped, exit **124**. No completed scanner report or CVE verdict
  was produced. Configure protected NVD access and finish the trusted scan before claiming a clean
  dependency result; no vulnerability gate/suppression was weakened.
- Browser policy rejected the local `file:` HTML URL. No workaround or alternative browser surface
  was used. Current source attachments, embedded report data and ZIP were inspected/verified;
  **current rendered-browser visual inspection was not completed**.
- Managed GitHub Actions/CodeBuild, live authentication/RBAC/tenant boundaries, infrastructure,
  signing, organization secret/license scans, DAST/IAST and team policy evidence remain unverified.
  Do not claim full guideline compliance, certification or deployed-service security from this run.

Final 18 October read-only **Nara delta-review: Pass**, with **no unresolved actionable finding**
in the reviewed working tree. Nara confirmed the seven baseline gaps and four addressed refinements,
the severity tally, and the scanner/browser/service/deployment limits above. This conclusion is
limited to the local source/test/evidence review and is not security certification.
