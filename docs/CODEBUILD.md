# AWS CodeBuild integration

The root `buildspec.yml` runs the same verification/report scripts as local builds and GitHub Actions.
It needs no AWS SDK dependency in the Java framework. Default tests use loopback mocks and no API
credentials. This repository provides configuration; it does not create AWS resources automatically.

## Project settings

Create a CodeBuild project or a CodePipeline build action using this repository as the source:

| Setting | Value |
| --- | --- |
| Buildspec | Use the root `buildspec.yml` |
| Environment | Managed Linux EC2 image `aws/codebuild/standard:7.0` (Ubuntu 22.04, x86_64) |
| Runtime selection | Corretto 21 and Python 3.12, selected in the buildspec |
| Privileged mode | Disabled; no Docker daemon is needed |
| Compute | Start with general-purpose small; adjust for suite size |
| Timeout | Allow 30 minutes for a cold dependency cache; reduce after measurement |
| Artifacts | Private S3 ZIP artifacts, or CodePipeline-managed artifacts |
| Cache | Optional private S3 cache for public Maven dependencies only |
| Logs | Private CloudWatch log group with your retention policy |

The chosen image supports both runtimes in [AWS's available-runtime table](https://docs.aws.amazon.com/codebuild/latest/userguide/available-runtimes.html).
Review image/runtime updates before changing them. The build always uses the checked-in Maven wrapper,
even if the image includes another Maven version. Network access to Maven Central is needed for a cold
cache. A VPC build needs appropriate egress to dependency sources and, for live suites, the test API.

Connect the source through your organization's approved connection; scope the service role to this
project, source connection, log group, artifact/cache bucket prefixes and report group. No AWS access
key belongs in this repository or a buildspec variable. Disable secrets for untrusted PR builds.

## Build and reporting behavior

`python3 scripts/run_and_report.py` runs `clean verify`, attempts HTML generation regardless of the
test exit code, and returns the original failure. The build phase's `finally` block verifies evidence
even when the command fails and removes default report/result artifacts if verification fails, preventing
CodeBuild's unconditional artifact phase from exporting those files. AWS documents `finally` failure semantics in the
[buildspec reference](https://docs.aws.amazon.com/codebuild/latest/userguide/build-spec-ref.html).

The `api-tests` report group imports `target/surefire-reports/TEST-*.xml` as JUnit XML. CodeBuild
creates `<project-name>-api-tests` when a report-group name is used. These are native test summaries;
the Allure HTML is delivered as an artifact. [AWS test reports](https://docs.aws.amazon.com/codebuild/latest/userguide/test-reporting.html)
document supported formats and report retention.

Artifacts retain these relative paths:

- `target/allure-report/index.html`: standalone Allure report.
- `target/allure-report.zip`: extract and open locally, or attach using an approved email process.
- `target/allure-results/`: sanitized Allure source evidence.
- `target/surefire-reports/TEST-*.xml`: CI test results.

The artifact-upload phase is attempted even when the build fails, as documented in
[AWS build-phase transitions](https://docs.aws.amazon.com/codebuild/latest/userguide/view-build-details-phases.html).
An early compilation/install failure may have no test results or report to upload. Download the outer
CodeBuild artifact ZIP, then open the contained HTML or extract the inner report ZIP. Keep S3 public
access blocked, enable encryption, restrict download access and set artifact/cache lifecycle policies.
Do not publish Allure output to a public S3 website.

## Native test-report permissions

Add the following **incremental** permissions to the CodeBuild service role, replacing all placeholders
with the actual region/account/project. This is not a complete source/log/artifact policy. If you
pre-create the group, remove `CreateReportGroup` and use the group's ARN in the buildspec.
Review [AWS test-report permissions](https://docs.aws.amazon.com/codebuild/latest/userguide/test-permissions.html)
when configuring the role.

```json
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Action": [
      "codebuild:CreateReportGroup",
      "codebuild:CreateReport",
      "codebuild:UpdateReport",
      "codebuild:BatchPutTestCases"
    ],
    "Resource": "arn:aws:codebuild:REGION:ACCOUNT_ID:report-group/PROJECT_NAME-api-tests"
  }]
}
```

Use separate least-privilege source, CloudWatch Logs and S3 permissions. If your buckets/secrets use
customer-managed encryption keys, scope the required KMS permissions to those keys and services.
Do not replace resource scoping with `AdministratorAccess` to resolve a reporting error.

## Optional live API suites and dependency scans

Keep the default project mock-only. For trusted live tests, create a separate protected project or
pipeline stage, set the public `API_BASE_URL`, and inject `API_TOKEN` with a **Secrets Manager** or
**SSM Parameter Store** environment-variable type at project level. Allow only the exact secret/parameter
in the service role. Use a dedicated synthetic test tenant. A live project can run:

```sh
python3 scripts/run_and_report.py -Pexternal -Dtest=ExternalUserApiTest
```

The example requires the documented Users contract. Adapt it to your API before enabling it.
Never echo variables or rely solely on CodeBuild's log masking; the framework's sanitizer protects
Allure and assertion evidence as well. Do not attach production data or run destructive live scenarios
without explicit gating and scoped cleanup.

For a trusted dependency-security project, inject `NVD_API_KEY` through the same protected mechanism
and use `python3 scripts/run_and_report.py -Psecurity`. Add `target/dependency-check-report.*` to that
project's artifact list if scanner output is required. First-run CVE data downloads need extra time;
the absence of scan data is not a security pass.

## Verification and troubleshooting

Locally run `python3 scripts/run_and_report.py` and `python3 scripts/verify_evidence.py`. The Java21
suite and script behavior are locally verifiable; a successful managed CodeBuild run also depends on
your source connection, IAM, network and artifact configuration. An AWS run has not been performed
as part of constructing this starter.

For a reporting-regression project, run the documented `failure-demo`, require its exit code to be 1,
then run `verify_evidence.py --require-failure`. A verifier success confirms it was an actual failed
test with request/response attachments rather than an unrelated compilation failure. Keep this
expected-failure verification separate from the normal API quality gate.

| Failure | Check |
| --- | --- |
| Unsupported runtime | Use the documented managed image and runtime names |
| Dependency download fails | VPC/NAT/proxy egress and approved Maven repositories |
| Native test report access denied | Exact report-group ARN and incremental service-role permissions |
| Missing artifacts | Whether tests were discovered, report generation completed, and S3 permissions are scoped correctly |
| Secret access denied | Exact secret/parameter ARN and any required KMS permission; never print the secret |
| Build is red although HTML exists | Expected: report creation must not erase a test/analysis failure |

For LLM changes to this integration, read `AGENTS.md` and `docs/SECURE_CODING.md`, preserve failure exit
codes, report paths and secret-free PR behavior, and update this file together with `buildspec.yml`.
