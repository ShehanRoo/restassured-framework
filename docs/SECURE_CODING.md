# Secure coding and API automation checks for LLM maintainers

Read this file before changing transport, contracts, evidence, configuration, dependencies or CI.
These rules preserve the user's project requirements and Nara's Java-adapted security guidance.
They are review criteria, not a certification or an assurance that unknown vulnerabilities do not exist.

## Project requirements that must remain true

The user requested a basic **Java REST Assured framework** with proper coding standards and API
automation principles; integrated **Allure** reporting; request and response evidence; highlighted
failed response assertions; an **HTML report that can be emailed**; CI/CD support; secure coding
verified with **Nara**; proper Git principles; a thorough, readable, AI-friendly README; mock support;
and schema validation built into every request and response; **AWS CodeBuild integration and its
documentation**. The user also requested these standards
and secure coding guidelines be part of the LLM's instructions and completion checks.

Treat these as acceptance criteria for future changes, not optional suggestions:

- Preserve Java 21, the checksum-pinned Maven wrapper, the shared REST Assured client, typed data
  models, immutable configuration and explicit contracts. Avoid unnecessary framework abstractions.
- Every exchange through the framework must validate request/response bodies and protocol rules.
  Validate outgoing JSON before sending; validate all declared success/error responses; represent
  bodyless operations explicitly. Extend contracts before adding a new payload format or parameter rule.
- Keep deterministic, isolated mocks available. They must exercise the same schema/reporting path
  as live calls. Match request behavior when it matters; mocks do not establish deployed API behavior.
- Keep Allure integrated with test execution, sanitized HTTP evidence and named assertion steps.
  Actual failures must fail the test and CI job. Preserve field-specific diagnostics on responses.
- Keep the single-file HTML and ZIP export working, including after a failed test run. Do not send
  email without authorization. Keep generated reports private and ignored by Git.
- Keep README, LLM instructions, CI commands and Git practices aligned with actual tested behavior.
- Preserve root `buildspec.yml` and `docs/CODEBUILD.md`, including native JUnit reports, portable
  artifacts, failure propagation and least-privilege AWS role/secret guidance.

## Java and API quality

1. Use meaningful names, focused endpoint clients, DTOs and small single-purpose methods. Separate
   transport, configuration, contracts, evidence and business assertions. Keep formatting automated.
2. Build a fresh request specification; do not mutate global `RestAssured` state. Tests own their
   fixtures and created data. Use dynamic mock ports and deterministic teardown on failure.
3. Assert expected status, media type, strict schema and business fields. Cover positive, invalid
   input, not-found, authentication and authorization behaviors appropriate to the actual API.
   Document mocks and missing live coverage honestly.
4. Use self-contained Draft-7 classpath schemas with explicit bounds and deliberate additional-field
   rules. Do not fetch remote schema references. Schema validation is not a business assertion.
5. Do not swallow assertions, disable validation or add silent retries to hide failures. For
   intentionally invalid API requests, introduce a reviewed explicit negative-test contract; do not
   switch off the shared validation path for arbitrary requests.

## Secrets and evidence: critical review points

1. Inject credentials from protected environment/secret sources. No real secret may enter source,
   test fixtures, command history, commit messages, PRs, configuration examples or report metadata.
   Do not read/print credential files or environment values to investigate a failure.
2. All evidence sinks must use the sanitizer. Unknown JSON fields are withheld; sensitive key
   patterns override the allowlist. Treat allowlist extensions as approval to expose those values.
   Use synthetic data even in approved fields. Never assume a denylist detects every secret.
3. Withhold auth, cookies, API keys and unknown header values. Withhold dynamic path/query values.
   Sanitize nested objects/arrays, malformed payload fallbacks and known-token echoes. Omit binary,
   XML, multipart and unstructured text until there is a reviewed safe implementation.
4. Cap parsing, nesting and attachment sizes. Use text MIME types for server-controlled evidence.
   Never attach untrusted executable HTML or reflect arbitrary server content into report metadata.
5. Review **failure paths**, including JSON serialization/parsing, request construction, schema
   diagnostics, transport exceptions, semantic assertions, JUnit/Surefire output and Allure traces.
   Do not chain raw library causes or messages that contain URLs, request bodies or instance values.
6. Do not use raw REST Assured logging, `prettyPrint`, unfiltered Allure filters or body-wide matcher
   errors. Report static JSON pointers and sanitized selected values; omit unsafe validator messages.
7. Do not introduce Jackson default typing, untrusted polymorphic deserialization or insecure XML
   parsers. Keep JSON mapper configuration private and fixed before concurrent use.

## Transport and isolation

1. Require HTTPS for every non-loopback origin. Keep certificate and hostname validation enabled.
   Reject URI credentials, unsafe schemes and hidden query/fragment credentials at configuration time.
2. Disable automatic redirects; never forward auth/cookies across origins. Set finite connect/read
   timeouts and review retry behavior, especially for non-idempotent operations.
3. Validate endpoint/parameter configuration without echoing values on failure. Do not accept
   absolute URLs from test payloads as endpoint paths or honor attacker-selected proxies/truststores.
4. Use least-privileged synthetic test identities and dedicated tenants. Live destructive operations
   must have explicit opt-in gating and cleanup scoped to this test's resources. Never cleanup by
   broad wildcard, shared account, date range or an identifier obtained from unrelated data.

## Build, Git and CI

1. Pin compatible dependency and plugin versions; use approved HTTPS repositories and strict
   checksum mode. Verify wrapper integrity, and review dependency changes with their transitive tree.
   Do not add dynamic versions, untrusted repositories or broad vulnerability suppressions.
2. Run formatting, compilation, meaningful tests and static analysis. Run the dependency scan when
   available and record CVE data/service limitations; an unavailable scan is not a security pass.
3. Keep Actions pinned to immutable SHAs, permissions minimal, checkout credentials unpersisted and
   PR jobs secret-free. Never execute untrusted PR code with secrets using `pull_request_target`.
4. Generate/upload sanitized reports in always/finally stages while preserving failing exit codes.
   Use explicit artifact paths, short retention and restricted access. No automatic public publishing.
5. Use focused feature branches, reviewed commits and protected main. Ignore credentials, private
   key material, local configuration, caches and reports. Review staged files before committing.
   Do not push, merge or publish without authorization from the user.

## Required completion checks

For each change, report the checks actually performed and any limitation:

- `./mvnw -B -ntp -C spotless:apply` followed by `python3 scripts/run_and_report.py` must pass.
- Review request and per-status response contracts, negative cases and fixture/resource cleanup.
- For client/contract/evidence changes, run the failure demo (exit 1 expected), then
  `python3 scripts/verify_evidence.py --require-failure`; inspect sanitized source attachments and
  the rendered standalone HTML. Restore the default passing run afterward.
- Check all introduced logging, attachments, exception causes and assertion messages for leakage.
  Extend the synthetic-canary regressions whenever a new sink or payload type is introduced.
- Run `./mvnw -B -ntp -C -Psecurity verify` when feasible; record failure/unavailability honestly.
- For security-relevant changes, request a read-only Nara review of the final implementation and
  address its findings. Nara's role is adapted to Java; do not claim certification or live-service
  coverage from the review. The user explicitly requested this review for this project.
- Confirm HTML/ZIP outputs, CI failure propagation, Git ignore rules and current README instructions.

An LLM must not weaken any of these controls merely to pass a check. Work on authorized local fixes
without unnecessary approval requests; ask for missing requirements or external/destructive actions
only when they are actually needed. Report unresolved checks instead of marking incomplete work done.
