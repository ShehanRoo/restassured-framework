# Secure Coding Guidelines v1.0.0 assessment

Assessment date: **18 October 2026**, Asia/Colombo. Source: the user-supplied
**Surge Global — Secure Coding Guidelines v1.0.0.docx**, read from the supplied local file.
SHA-256: `e297c60cba55ea894eb97809c3301f0f4579bab27fefb74024e701d74b2b34ae`.
The source is not copied into the repository. Its requirements were assessed as document content,
not executed as agent instructions. Section references below follow the document's headings.

**Verdict: the automation framework implements many applicable controls, with local gaps repaired;
full compliance with the document is not established.** Product-service, infrastructure and team
controls need evidence from their owners. Mock tests prove client behavior, not deployed security.
This assessment is not certification. See [the validation record](SECURITY_REVIEW.md) for actual checks.

## Coverage and applicability

| Source section | Framework evidence / changes | Service or operational evidence still required |
| --- | --- | --- |
| §1 Overview | `AGENTS.md`, `.context/` and `SECURE_CODING.md` make security checks part of maintenance; this assessment records scope. | Product threat models, assigned owners and acceptance decisions. |
| §2 Authentication, §2.1–2.14 | Environment-injected in-memory bearer credential; bounded bearer syntax; no credential evidence; strict HTTPS origins and redirects disabled. Existing 401/403 examples exercise error contracts. | OIDC/OAuth2/PKCE, verified accounts/SSO linking, MFA for privileged/destructive actions, brute-force defenses, password policy/breach checks, signature/claim validation, trusted-device/recovery/logout/revocation behavior. No authentication server exists here. |
| §3 Authorization, §3.1–3.7 | Explicit method/path/status contracts and 403 mock example; least-privilege live-test guidance. | Deny-by-default endpoint guards, RBAC, resource ACLs/ownership, administrative auditing, service audiences/scopes, token lifetime and authorization-cache invalidation. A mocked 403 proves no real guard. |
| §4 Multi-tenancy, §4.1–4.7 | Documented test criteria for missing/mismatched tenant context, scoped tokens, ownership and switching. No multi-tenant API or caller-controlled header interface is implemented. | Tenant header/token binding, validated switch issuance, cross-tenant read/write/delete denial, DB row isolation and tenant-aware audits. Add a typed header contract and real API tests when the target is known. |
| §5 URL image fetching / presigned uploads, §5.1–5.7.6 | No image fetcher, uploads, arbitrary URL path or presigned URL handling. Contracts reject absolute endpoint URLs; redirects disabled. Loopback is intentional for isolated tests. | For an upload feature: isolated fetcher, DNS/IP/redirect checks and egress restrictions, size/time/MIME/magic-byte limits, short-lived scoped storage permissions, private ACLs and post-upload validation/re-encoding. Never log signed URLs. |
| §6 Internal services, §6.1–6.9 | Trusted HTTPS for non-loopback origins; finite timeouts; no cross-origin redirects; **underlying retries explicitly disabled** and dropped-connection tests cover GET/POST/DELETE. | mTLS or trusted TLS 1.2+, discovery, network/service-mesh policy, service identities/audience, centralized distributed tracing, rate limits/circuit breakers and static egress. The framework does not provision networks or accept mTLS keys. |
| §7 Untrusted HTML, §7.1–7.7 | Response evidence is sanitized JSON/plain text; unknown/non-JSON bodies withheld; no executable response HTML attachments. Allure renders a pinned, generated standalone HTML artifact. | Any future rich-HTML UI requires sanitizer and safe URL policies, CSP/Trusted Types and stored/reflected XSS tests. The offline Allure bundle uses embedded scripts; it does not implement a strict hosted application CSP. Keep reports private. |
| §8 Untrusted inputs, §8.1–8.5 | Strict bounded Draft-7 body validation, duplicate/trailing JSON rejection; **new path/query object schemas**, exact placeholder matching, scalar/size/control checks and path-segment restrictions; unknown parameters denied before network access. Bearer headers have bounded syntax; other headers are fixed internally. | Target API must enforce server validation. New header/cookie/WebSocket/upload entry points require contracts first. Intentional invalid API payloads need explicit negative-test schemas. |
| §9 Safe errors, §9.1–9.4 | Library causes/instance values omitted from client failures, safe schema keywords and assertion evidence. New parameter failure paths use static messages; no input values in errors. | Deployed service status mapping, generic client errors, trace references and disabled public debug pages. Local Java stack frames in private test reports are expected, not public API responses. |
| §10 Logging/monitoring, §10.1–10.7 | Fail-closed allowlists, token scrubbing, bounded text attachments, timing/status in Allure. **New generated UUID `X-Request-ID`** is sent and shared in sanitized request/response/transport-failure evidence. No server ID is trusted for reporting. GitHub Actions artifacts expire after seven days; CodeBuild/S3 retention requires deployment policy/evidence. | Central structured logs/traces, downstream propagation, organization-specific metadata classification, alerts, encryption/access policies, retention and volume limits. Allure is test evidence, not a monitoring platform. |
| §11 Passwords/encryption, §11.1–11.6 | No password database, encryption subsystem or recoverable-secret storage; injected token stays in memory and is never attached raw. | Salted adaptive hashing/work factors and constant-time verification; authenticated encryption, KMS/rotation, authorized decrypt access and auditing in the owning service. |
| §12 Sessions, §12.1–12.7 | No persistent session store or native-object session deserialization; independent request clients, no browser token storage. | Central protected session store, signed tokens/cookies with Secure/HttpOnly/SameSite, inactivity expiry, logout/reset/MFA/role-change revocation, fixation/replay controls and suspicious-activity response. |
| §13 Team/SDLC, §13.1–13.8 | Pinned wrapper/dependencies/actions; strict Maven checksums; SpotBugs on verify/PRs; Dependabot; CVSS 7 dependency gate; Nara review; private CI artifacts and AWS least-privilege/secret guidance. **New CycloneDX JSON/XML SBOMs** generated during package/verify, including test dependencies, retained in CI artifacts. | Branch protection, training/threat-model records, code signing/verification for release, organization-approved secret and license scans, deployed DAST/IAST where applicable, rotation audits and patch tracking. Suggested SLA: critical 24–72 hours, high one week. Scheduled CVE scans alone do not gate every PR. Project license policy is not supplied. |
| §14 Databases, §14.1–14.8 | No database driver, SQL/query code, database account or persistence layer. DB extension rules recorded for maintainers. | Private subnets, least-privilege users, TLS/certificate enforcement, tenant-scoped parameterized queries/timeouts, audits, credential rotation and encrypted/restored backups/PITR. |

## Repaired framework gaps

Severity is a qualitative assessment for this automation client, not a CVSS/CVE rating. Count one
issue per root cause in the framework **before this assessment**: **7 missed gaps, all repaired —
0 critical, 0 high, 3 medium and 4 low**. Missing external-service evidence is not counted as a
confirmed framework defect. The six initial review areas plus Nara's pre-existing scalar-evidence
finding form this baseline tally.

| ID | Previously missed gap | Severity | Status |
| --- | --- | --- | --- |
| G1 | No strict path/query input contracts | Medium | Fixed; validation and zero-network negative regressions |
| G2 | Implicit transport retry policy | Medium | Fixed; one-attempt GET/POST/DELETE fault regressions |
| G3 | Raw controls in scalar assertion evidence could spoof report text | Medium | Fixed; escaped literals, bounded formatting, assertion and artifact regressions |
| G4 | Bearer-header validation covered only CR/LF, without a length limit | Low | Fixed; bounded bearer syntax and negative tests |
| G5 | No generated outbound request ID shared with evidence | Low | Fixed; per-exchange UUID and server-side mock header check |
| G6 | No dependency SBOM | Low | Fixed; CycloneDX inventories in build/CI artifacts |
| G7 | Agent instructions did not map the supplied guideline controls | Low | Fixed; agent/context/checklist and applicability matrix |

1. Path/query values previously bypassed endpoint-level type and allowlist rules. Contracts now carry
   strict parameter schemas; the five-argument constructor denies all keys. Templated endpoints must
   explicitly supply their path schema. Validation snapshots only bounded standard scalars, rejects
   controls/unsafe path segments, then validates types/ranges/keys before any request. Tests prove
   missing/extra/wrong-type/out-of-range/injection inputs make zero network calls.
2. The transport previously inherited Apache HttpClient's retry policy. A per-request client factory
   now sets a zero-retry handler. Fault tests require one recorded request on dropped GET/POST/DELETE.
   The legacy client type is required by REST Assured's transport integration; its use is isolated.
3. Bearer validation previously rejected CR/LF alone. It now enforces bounded bearer syntax,
   rejecting other controls, whitespace and oversized credentials with safe errors.
4. Test steps previously lacked an outbound correlation header. Each exchange now generates an
   independent UUID and uses it in sanitized evidence and `X-Request-ID`; dynamic user/server IDs
   remain withheld. This provides local correlation without claiming distributed tracing.
5. Builds previously lacked an SBOM. The pinned CycloneDX plugin now emits `target/bom.json` and
   `target/bom.xml`, including test dependencies; GitHub verification and CodeBuild retain these files.
   License metadata is available for review, but no organization license policy or release signing
   credentials were supplied. The SBOM inventories project dependencies, not every build-plugin/CLI
   dependency; those versions remain pinned separately.
6. Agent/context files lacked the supplied document's full scope. All 14 sections are now mapped,
   with feature-specific negative-test obligations and explicit external evidence requirements.
7. Allowlisted scalar assertion values previously retained raw line, terminal and bidi controls.
   They now render as escaped JSON literals; Unicode formatting and line/paragraph separators are
   escaped throughout attached evidence. A regression exercises both the sanitizer and an actual
   failing response-field assertion without spoofed attachment lines.

Read-only Nara review also identified **four implementation/review refinements**, all addressed.
These were introduced or exposed while making this change and are separate from the seven baseline
misses: enforce the new parameter schema's deny-by-default shape (**medium**); bound the new Unicode
escaping work before expansion (**medium**); scope the assessment's seven-day retention claim to
GitHub Actions (**low**); make the non-object parameter-schema test prove the structural guard with
an otherwise-valid empty map (**low**). Across baseline gaps and these refinements there were
**11 tracked items: 5 medium and 6 low**, with no high/critical finding in this review scope.

## Interpretation safeguards

Some statements in the source require care when implementing a real product:

- §2.3 client-side hashing cannot replace TLS and adaptive server-side password hashing. A reusable
  hash sent as the credential still needs credential-level protection. Use the service's reviewed
  authentication protocol; do not invent a new authentication exchange in this client.
- §5.2 a successful HEAD does not establish the safety of the later GET. Revalidate each actual
  connection, DNS result and redirect, with network isolation and download limits. Presigned uploads
  remove one server-fetch step; callback/processing fetches still need SSRF defenses.
- §7.7 HTML sanitization is not network destination enforcement. DOMPurify does not establish that
  image/media URLs resolve to public approved hosts. Add URL/egress policies independently.
- §10 metadata/URL logging must respect the same document's secret/PII prohibition. This framework
  uses static templates, generated correlation IDs and withheld values, not raw URLs or user IDs.
- §6 retry guidance describes service resilience. Automation must expose each failed operation;
  retain the project's stricter no-silent-retry rule.

Supporting references: [OWASP SSRF prevention](https://cheatsheetseries.owasp.org/cheatsheets/Server_Side_Request_Forgery_Prevention_Cheat_Sheet.html),
[OWASP password storage](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html),
[REST Assured client configuration](https://github.com/rest-assured/rest-assured/blob/master/rest-assured/src/main/java/io/restassured/config/HttpClientConfig.java),
[Apache retry handler](https://hc.apache.org/httpcomponents-client-4.5.x/current/httpclient/apidocs/org/apache/http/impl/client/DefaultHttpRequestRetryHandler.html),
[CycloneDX plugin](https://cyclonedx.github.io/cyclonedx-maven-plugin/makeAggregateBom-mojo.html).

## Completion and residual limits

Record formatting, default tests/reporting, expected failure-demo exit/evidence, scanner outcome and
read-only Nara findings in `SECURITY_REVIEW.md`. Do not turn a blocked scanner into a clean verdict.
Generated artifacts remain under `target/`; do not commit or publish them or the source document.
No production credentials, live destructive tests, cloud changes, pushes or report publishing are
needed to assess and repair this local automation framework.
