# AI Traceability Log

Record of material AI assistance: what was asked, what came back, what I did
with it, and why.

## How this log stays credible

A traceability log written in one sitting at the end of a project reads like one
written at the end of a project — uniform in tone, no dead ends, no rejections,
no entries where the engineer was wrong. Three rules keep this one honest:

1. **Entries are committed alongside the code they describe.** The `git log`
   timestamps are the evidence that this was contemporaneous. A log added in a
   single final commit proves nothing.
2. **Rejections and reversals are recorded, including unflattering ones.** An
   entry where I accepted something that turned out to be wrong is worth more
   than three entries where the AI was helpful.
3. **Rationale is technical and specific.** "Didn't match style" is not a
   rejection reason. "H2 diverges from PostgreSQL on unique-constraint violation
   semantics, which is the behaviour under test" is.

## Secure AI usage policy

Binding for the duration of this project:

- **No secrets, credentials or API keys in prompts.** This repository contains
  none; a fake key may be used only by the development profile, and
  non-development startup requires an environment-supplied value.
- **No proprietary or employer-owned code in prompts.** All code here is written
  for this assignment.
- **Every AI-suggested dependency is verified before it is added:** that the
  package exists under exactly that coordinate, that it is actively maintained,
  and that it has no unresolved critical CVEs. AI-hallucinated package names are
  a live supply-chain attack vector — an attacker who registers a plausible
  hallucinated name gets code execution in any build that trusts the suggestion.
  Dependabot alerts and pull-request dependency review are the automated
  backstop, not the primary control.
- **Security-sensitive logic is read line by line, never accepted on test-pass
  alone.** Specifically: URL validation, the API-key filter, and the
  collision-retry path. A passing test proves the cases I thought of.
- **High-impact changes require explicit sign-off** recorded in this log:
  authentication, privacy/data handling, destination validation, schema
  migrations, and public API compatibility.

## Disposition key

| Value | Meaning |
| --- | --- |
| `adopted` | Used essentially as produced |
| `edited` | Used after material change — the change is described |
| `rejected` | Not used — the reason is technical and recorded |

---

## Entries

### AI-001 · 2026-09-19 · Planning · Initial requirement analysis

**Task:** Interpret the assignment PDF and produce an initial plan.
**Intent given:** Analyse the assignment, identify scope, propose a technical
direction and an execution plan.
**Output:** The first `README.md` — scope, proposed stack, five-phase plan,
guardrails, and 20 open questions.
**Disposition:** `edited` — substantially restructured, see AI-003.
**Rationale:** The scope discipline was sound (modular monolith, explicit
exclusions) and survived into the final docs. The structural flaw was that it
ended in 20 unanswered questions with no stakeholder to answer them, which meant
the plan could not actually start. Requirement understanding means normalising
ambiguity into decisions, not cataloguing it.
**Validation:** None applicable — planning artifact.

### AI-002 · 2026-09-19 · Planning · Adversarial review of the plan

**Task:** Review the plan against the assignment's evaluation criteria.
**Intent given:** Critique the plan as a reviewer would, against §6 and §7.
**Output:** Eight findings — open questions not converted to decisions,
traceability described but not instrumented, phases rather than a task graph,
a deferred persistence choice, scenario overlap between brownfield and
ambiguous, missing short-code strategy, unnamed quality-gate tools, deliverables
not mapped to paths.
**Disposition:** `adopted` for seven of eight; one carried a factual error.
**Rationale:** The findings were accurate against the actual document, verified
line by line rather than taken on trust. One overstated count — "~25 open
questions" where the document had 20 — which is the kind of detail worth
checking before acting on a review.
**Validation:** Each finding checked against the specific lines it referenced.

### AI-003 · 2026-09-19 · Planning · Second-opinion review

**Task:** Independent re-analysis, with a different model, of both the plan and
the first review.
**Intent given:** Analyse again from scratch; disagree where warranted.
**Output:** Agreement on most of AI-002, plus: reframing the core problem as
over-planning against a fixed clock rather than a formatting issue; a missing
time budget and cut list; the Phase 1 threat model being inverted (threat-modelling
decisions that did not yet exist); no demonstration path for a reviewer; and
retrospective-log detectability.
**Disposition:** `adopted`.
**Rationale:** The time-budget point was the one I had missed entirely and it
changed the shape of the plan — the honest estimate is ~24 h against a 2–3 day
budget, which forces a cut list and forces protecting the two scenario tasks.
`make demo` (T14) came from this review and is high-return: deliverable #1 is
"runnable end-to-end", and a reviewer who runs one command sees that directly.
**Validation:** Estimates assigned per task and totalled; the overrun is stated
in `02-tasks.md` rather than hidden.

### AI-004 · 2026-09-19 · Design · Testcontainers fallback — REJECTED

**Task:** De-risk integration-test setup cost within the budget.
**Intent given:** Testcontainers is the most expensive item in the toolchain;
propose a mitigation.
**Output (AI-002):** Time-box the setup and fall back to H2 for integration
tests if it overruns.
**Disposition:** `rejected`.
**Rationale:** H2 diverges from PostgreSQL on precisely the behaviour these
tests exist to verify — unique-constraint violation semantics (the P7 retry
path), upsert, and PostgreSQL-specific migration DDL. Running Flyway migrations
against a database that is not the deployment target makes the migration tests
theatre: they would pass while proving nothing about the real schema change in
T15. Docker is already a hard dependency because of Docker Compose (R4), so
Testcontainers introduces no new environmental requirement — only startup
latency, which a single static container on a shared abstract base class pays
once rather than per test class.
**Adopted instead:** Single reusable container via `@ServiceConnection`,
budgeted inside T5. If Docker were genuinely unavailable, the honest fallback is
*fewer* integration tests, not the same tests against a different database.
**Validation:** To be confirmed at T5 — if container startup exceeds the 1.5 h
budget, this decision gets revisited in a follow-up entry rather than quietly
abandoned.

### AI-005 · 2026-09-19 · Design · Short-code generation strategy

**Task:** Decide the short-code generation approach.
**Intent given:** Compare random, counter+base62 and hash-of-URL for a URL
shortener where links may expire and should not be enumerable. Constraints:
PostgreSQL, single instance, concurrent creation possible.
**Output:** Random base62 recommended over sequential, on non-enumerability.
**Disposition:** `edited` — the recommendation was right, the reasoning was
incomplete on the point that actually matters.
**Rationale:** The comparison covered collision probability but not the
concurrency question, which is where the real bug lives. The natural
implementation — `SELECT WHERE short_code = ?` then `INSERT` — is a check-then-act
race: two concurrent creations can both observe the code as free. The `UNIQUE`
constraint is the only thing that serialises this, and catching the violation is
the correct handling. I added that to the decision (P7) and made it the reason
the constraint exists rather than an incidental schema detail.
**Sign-off:** Engineer — high-impact (public API surface and data integrity).
**Validation:** Planned in T5 — a forced-collision test with a stubbed generator
asserting the retry path, plus a concurrent-creation test asserting all codes
are distinct.

### AI-006 · 2026-09-19 · Design · Redirect status code

**Task:** Choose the redirect status.
**Output:** 302 recommended, justified on 301 being aggressively browser-cached
and suppressing repeat click events.
**Disposition:** `edited` — kept the answer, replaced the primary reasoning.
**Rationale:** The analytics argument is true but secondary, and it would not
survive a reviewer asking "so if you moved analytics to edge logs, would you
switch to 301?" The durable reason is that the link lifecycle is mutable:
expiration (T15) and soft delete (P5) both mean a link may legitimately stop
resolving, and a 301 cached in a browser cannot be recalled. 301 is a promise
this system cannot keep. Added `Cache-Control: no-store` because browsers do
opportunistically cache 302s.
**Validation:** Asserted in T7's controller test — status and both headers.

### AI-007 · 2026-09-19 · Planning · Raising the bar to production-grade

**Task:** I rewrote the framing from "prototype" to production-grade, added an
engineering standard to the README, and hardened R1, R4 and S4 (fail-closed
startup without a configured key; explicit ineligibility for public exposure
without abuse controls). Then asked for a review against the raised bar.
**Intent given:** The standard is now production-grade. Review again against it.
**Output:** Nine findings. Four material: no migration policy despite `V3`
dropping a column; the failure analysis covering "database down" but not
"database slow"; no CI behind the four named quality gates; and a contradiction
I introduced myself — T10 listed inside a cut list while marked "never cut".
**Disposition:** `adopted`.
**Rationale:** Two of these are the kind of gap that only appears once the bar
moves. The migration one is the sharpest: `V3` dropped `client_ip` in a single
step, which fails outright if running code still writes the column, and Flyway
Community has no undo — so the plan claimed migration safety while containing
the one migration pattern most likely to cause an incident. Split into
expand/contract (`V3` stops the write, `V4` drops) and written up as M1.

The "slow vs down" finding changed a decision I thought was already settled:
A4's fail-open catches the click write *throwing*, but does nothing about it
being slow, and a slow insert adds its latency to every redirect. **Fail-open
without a timeout is not fail-open.** Added as R6 with explicit bounds on pool
acquisition, query execution and shutdown.
**Recorded against myself:** the limitations list in the README stated that the
delivered service stores raw client IP. T16 removes it before submission, so
that statement would have been false at hand-in — a limitations list that
overstates its own flaws is still an inaccurate limitations list. It came from
carrying A2's sequencing note into a section describing the delivered artifact.
Corrected.
**Re-planned:** the estimate moved from ~24 h to ~26 h and the cut list lost
T10, leaving ~2.75 h of slack. Raising a quality bar without re-costing the
plan is the same judgment failure `02-tasks.md` exists to prevent.
**Sign-off:** Engineer — high-impact (migration safety, data handling).

### AI-008 · 2026-09-19 · Phase A · Foundation implementation

**Task:** Bootstrap the project, initial PostgreSQL schema, short-code generator,
quality gates, and CI workflow.
**Intent given:** Start the first execution phase, use `jenv`, and select JDK 25.
**Output:** Java 25/Spring Boot 4.1.1 Maven service, PostgreSQL 16 Compose
environment, Flyway V1 migration, base62 `SecureRandom` generator, build gates,
and GitHub Actions workflow.
**Disposition:** `edited` — two generated/default choices were rejected after
execution evidence.
**Rationale:** Spring Initializr advertised parent version `4.1.1.RELEASE`, but
that artifact does not exist in Maven Central. The published stable coordinate
is `4.1.1`, so the suffix was removed instead of adding a non-central repository.
The generated Testcontainers image used `postgres:latest`; it was pinned to
`postgres:16-alpine` to match the documented database contract. Local port 5432
was already owned by another PostgreSQL process, so Compose and the application
default were moved together to 5433 rather than stopping unrelated work.

**TDD evidence:**

- T3 RED: `SchemaMigrationTest` ran against PostgreSQL 16 and found neither
  required table. GREEN: V1 created both tables, the short-code unique index,
  and the analytics index; the same test passed.
- T4 RED: `SecureRandomShortCodeGeneratorTest` expected seven base62 characters
  and received the compiling stub's empty string. GREEN: the minimal generator
  implementation passed 1,000 contract checks.

**Validation:** Application startup applied V1 to the Compose database and
readiness returned `{"status":"UP"}` on an isolated port. Spotless, Checkstyle,
and SpotBugs passed.

### AI-009 · 2026-09-20 · Phase A · Dependency security gate

**Task:** Replace the local OWASP dependency scan with a security gate suitable
for a personal MVP repository.
**Intent given:** Use GitHub Dependabot because there is no organization or NVD
API key.
**Output:** Weekly Maven and GitHub Actions update checks, Dependabot alerts, and
pull-request dependency review failing on newly introduced high-severity
vulnerabilities.
**Disposition:** `edited` — the original plan bound OWASP Dependency-Check to
every `mvn verify`.
**Rationale:** Dependency-Check 13.0.0 failed without an NVD API key. Version
12.2.2 supported anonymous access but its first synchronization had 395,560 CVE
records and stalled after 10,000 under NVD rate limits. That made the local
feedback loop and a 20-minute CI job operationally unreliable. Dependabot uses
GitHub-hosted vulnerability data, requires no organization or personal secret,
and separates dependency intelligence from deterministic local compilation and
analysis.
**Validation:** Maven verification remains the local and CI code-quality gate;
the Dependabot configuration covers Maven and GitHub Actions, and the dependency
review workflow is limited to pull requests. The resulting `mvn verify` completed
successfully with all three tests and all local quality gates passing.

### AI-010 · 2026-09-20 · Phase A · Checkstyle gate review

**Task:** Review the complete Phase A change set for high-confidence build and
configuration defects.
**Output:** Checkstyle loaded Google's rules but treated their warning severity
as below Maven's default error-only violation threshold, so the build printed
warnings and still reported zero violations.
**Disposition:** `adopted`.
**Rationale:** A quality gate that reports findings without affecting the build
does not meet T2's acceptance criterion. The plugin now sets
`violationSeverity=warning`; required public API documentation was added and the
redundant generated test launcher was removed.
**Validation:** `mvn verify` completed with zero Checkstyle findings after the
warning threshold became build-enforcing.

### AI-011 · 2026-09-20 · Phase A · GitHub dependency-security readiness

**Task:** Re-review the final Phase A configuration.
**Output:** The dependency-review workflow was valid, but GitHub's dependency
graph and Dependabot security features were disabled, so the hosted gate could
not run.
**Disposition:** `adopted`.
**Rationale:** Committing a workflow that is guaranteed to fail is not a quality
gate. The repository owner enabled the dependency graph, Dependabot alerts, and
security updates rather than weakening or removing the PR check.
**Validation:** GitHub's repository SBOM endpoint now returns the project SBOM,
confirming that the dependency graph is active. The first workflow execution
remains pending this change set being pushed.

### AI-012 · 2026-09-20 · Tooling · Independent clean-code review agent

**Task:** Adapt an existing Maestro code-review agent for Copilot CLI.
**Intent given:** Preserve independent diff review, design and test-quality
checks, report receipts, and incremental re-verification.
**Output:** Personal local Copilot agent, intentionally stored outside the
repository, using Claude Sonnet 5 with Copilot's read, search, edit, and execute
tool aliases.
**Disposition:** `edited`.
**Rationale:** Maestro-specific runners, hooks, report paths, and external
severity definitions do not apply to Copilot. The local version uses bounded
Git diff commands, includes untracked files explicitly, defines its own severity
contract, and preserves stable finding IDs and JSON receipts. Copilot
frontmatter cannot restrict `edit` to one path or `execute` to an executable
allowlist, so confinement is an explicit agent instruction rather than a hard
hook boundary. Keeping the agent outside the repository prevents personal
workflow configuration from becoming part of the public deliverable.
**Validation:** The YAML frontmatter parses successfully and uses only official
Copilot custom-agent fields and tool aliases.

### AI-013 · 2026-09-20 · Tooling · Review orchestration policy

**Task:** Make independent clean-code review automatic after implementation
slices while retaining engineer control over remediation and final review.
**Output:** Clone-local Copilot instructions, excluded from Git, that invoke the
personal `clean-code-review` agent after meaningful, verified implementation
slices; require explicit engineer approval before fixes; allow one incremental
re-verification; and reserve the whole-project review for manual invocation
before submission.
**Disposition:** `adopted`.
**Rationale:** Agent descriptions permit model invocation but do not guarantee a
review at a lifecycle boundary. Clone-local instructions make the trigger and
handoff explicit without publishing personal workflow configuration, allowing
the reviewer to fix its own findings, or allowing the implementation agent to
remediate without human approval.
**Validation:** The policy defines trigger exclusions, required review context,
task-specific report paths, blocking severity, re-verification limits, and the
manual final-review boundary.

---

### AI-014 · 2026-09-20 · Phase B · Persistence implementation

**Task:** Implement T5: Spring Data JDBC entities and repositories with
PostgreSQL integration coverage, including database-enforced short-code
uniqueness.
**Intent given:** Follow the task graph and prove repository behavior against
the real PostgreSQL Testcontainer rather than H2 or mocked exceptions.
**Output:** `Link` and `ClickEvent` mappings, repositories, PostgreSQL `INET`
conversion support, and integration tests covering save/find, click persistence,
and duplicate short-code rejection.
**Disposition:** `edited` — the initial `InetAddress` mapping was rejected after
SpotBugs reported exposed mutable state and PostgreSQL could not infer its JDBC
type. A typed `ClientIp` value plus explicit PostgreSQL `PGobject` conversion was
used instead; a generic `String` converter was also rejected after it captured
unrelated string columns.
**TDD evidence:** RED was first observed as missing persistence types, then as
the unimplemented mapping surface. GREEN passed the focused Testcontainers
tests. Refactoring addressed SpotBugs, PostgreSQL `INET` binding, formatting,
and retained Spring Data's built-in conversions.
**Validation:** `./mvnw -Dtest=LinkRepositoryTest test` and `./mvnw verify`
passed. The duplicate test asserts `DataIntegrityViolationException` from the
real PostgreSQL unique constraint.

**Review remediation:** The clean-code review's CR-01 and CR-02 findings were
accepted for fixing. Both persistence-test timestamps are truncated to
PostgreSQL's microsecond precision before equality assertions, and link status
is represented by `LinkStatus` with explicit JDBC converters. Focused tests and
the full Maven verification gate passed after remediation; re-verification
resolved CR-02 and the remaining CR-01 call site was then corrected.

### AI-015 · 2026-09-20 · Phase B · Link creation implementation

**Task:** Implement T6: link creation service and `POST /api/links` with
collision retry, destination validation, scheme allowlist, private-address
rejection, and a 2048-character length cap.
**Intent given:** Follow TDD and prove collision retry through PostgreSQL rather
than a mocked constraint alone.
**Output:** `UrlValidator`, `LinkService`, `LinkController`, Spring bean wiring
for the short-code generator, web/service tests, and a Testcontainers
integration test that forces a real unique-constraint collision before a
successful retry.
**Disposition:** `edited` — Spring Boot 4's relocated `WebMvcTest` and
`MockitoBean` packages were used after the initial test imports failed to
compile; unresolved hosts are rejected rather than treated as public, and
URL credentials are rejected to avoid accepting ambiguous destinations.
**TDD evidence:** RED first showed the missing service, validator, and
controller types. GREEN covered accepted/rejected URLs, collision retry,
201 creation, and 400 problem responses. The integration test exercises the
real PostgreSQL unique constraint and verifies the retry succeeds in a fresh
repository transaction.
**Validation:** `./mvnw -Dtest=UrlValidatorTest,LinkServiceTest,LinkServiceIntegrationTest,LinkControllerTest test`
and `./mvnw verify` passed.

**Review remediation:** The clean-code review's CR-01 through CR-05 findings
were accepted for fixing because they were directly coupled to T6's validation
and error contract. IPv6 unique-local addresses are now rejected, DNS
resolution is bounded and injectable in tests, domain-specific validation and
creation-failure exceptions prevent generic or persistence exceptions from
being misclassified, and validation details are derived from binding errors.
The full Maven verification gate passed after these changes.

### AI-016 · 2026-09-20 · Phase B · Redirect implementation

**Task:** Implement T7: resolve a short code and redirect publicly with the
documented cache policy, returning 404 for unknown codes.
**Intent given:** Keep redirect behavior in a separate web controller, leave
click recording for T8, and use the existing repository lookup through
`LinkService`.
**Output:** `LinkService.resolve`, `LinkNotFoundException`, and
`RedirectController` mapped to `GET /{code}`.
**Disposition:** `adopted` — the implementation follows the documented
controller/service/persistence layering and keeps resolution side-effect-free.
**TDD evidence:** RED was observed with the new service and web tests failing
to compile because the production types and method were absent. GREEN passed
after adding the minimal resolution and redirect behavior.
**Validation:** `./mvnw -Dtest=RedirectControllerTest,LinkResolutionTest test`
and `./mvnw verify` passed.

### AI-017 · 2026-09-20 · Phase B · Click recording implementation

**Task:** Implement T8: record redirect click metadata synchronously while
keeping click persistence fail-open.
**Intent given:** Reuse the existing `ClickEvent` persistence mapping, capture
request referrer, user-agent, and remote address, and ensure repository
failures are logged without changing the redirect response.
**Output:** `ClickRecorder` service and redirect-controller integration with
focused metadata and failure-path tests.
**Disposition:** `adopted` — the recorder owns the fail-open boundary so the
redirect controller remains responsible only for resolution and HTTP response
construction.
**TDD evidence:** RED was observed when the new tests could not compile because
`ClickRecorder` was absent. GREEN passed after adding the recorder and wiring it
into the redirect path.
**Validation:** `./mvnw -Dtest=ClickRecorderTest,RedirectControllerTest,LinkResolutionTest,LinkRepositoryTest test`
and `./mvnw verify` passed.

### AI-018 · 2026-09-20 · Phase B · Observability implementation

**Task:** Implement T13: Actuator health/readiness reflecting database state,
structured JSON logging with a per-request correlation id, and Micrometer
counters for links created, redirects served, and redirect misses (R5).
**Intent given:** Keep the scope to the three signals R5 names — no tracing,
no alerting, no timeout/pool tuning (that is T22) — and prove each behavior
with a test rather than configuration inspection.
**Output:**
- `management.endpoint.health.group.readiness.include=readinessState,db` so
  `/actuator/health/readiness` reflects live PostgreSQL reachability, proven by
  stopping the Testcontainers PostgreSQL instance mid-test and observing 503.
- `logging.structured.format.console=ecs` for structured JSON console logging.
- `CorrelationIdFilter`, a highest-precedence servlet filter that reuses an
  incoming `X-Correlation-Id` header when it is safe (bounded, alphanumeric/
  hyphen only) or generates a UUID otherwise, publishes it to SLF4J's MDC for
  the request's duration, and echoes it on the response header.
- `links.created`, `redirects.served`, and `redirects.missed` Micrometer
  counters incremented in `LinkController` and `RedirectController`
  respectively.
**Disposition:** `adopted` — readiness, logging, and metrics are wired at the
web/controller layer, consistent with the existing controller/service/
persistence layering; no changes were made to timeouts, pooling, tracing, or
alerting.
**TDD evidence:** RED was observed for `CorrelationIdFilterTest` (missing
type), `ReadinessHealthIntegrationTest#readinessGoesDownWhenDatabaseIsUnreachable`
(200 instead of 503 before the readiness group included `db`),
`StructuredLoggingIntegrationTest` (confirmed to fail without the `ecs`
structured-logging configuration), and the `links.created`/`redirects.served`/
`redirects.missed` counter tests (`MeterNotFoundException` before the
counters existed). GREEN passed after each corresponding minimal
implementation.
**Security note:** SpotBugs flagged `HRS_REQUEST_PARAMETER_TO_HTTP_HEADER` for
echoing the incoming correlation-id header verbatim — a header/log injection
risk. Fixed by validating the incoming id against a bounded
alphanumeric/hyphen pattern before reuse, with a regression test asserting a
CRLF-bearing header value is replaced by a generated id. Re-verified clean.
**Validation:**
`./mvnw -Dtest=CorrelationIdFilterTest,ReadinessHealthIntegrationTest,StructuredLoggingIntegrationTest,LinkControllerTest,RedirectControllerTest test`
and `./mvnw verify` (32 tests; Spotless, Checkstyle, SpotBugs clean) passed.

### AI-019 · 2026-09-20 · Phase B · RFC 7807 exception handling

**Task:** Implement T11: centralize current controller error handling as RFC
7807 problem details without exposing stack traces or persistence causes.
**Intent given:** Preserve existing 400/404 status and title compatibility,
cover malformed requests and unexpected failures, and avoid changing T10
authentication or unrelated endpoints.
**Output:** `GlobalExceptionHandler` returning Spring `ProblemDetail` with
`application/problem+json`, controller-local handlers removed, and focused
web tests for validation, malformed JSON, not-found, creation failure, and
unexpected exceptions.
**Disposition:** `edited` — the initial controller-local response records were
replaced by one advice boundary; known exception messages remain useful client
details, while generic failures use a fixed detail and never serialize causes.
**TDD evidence:** RED was observed with missing advice behavior: existing
responses were `application/json` or empty for 404 and malformed JSON. GREEN
passed after the advice mapped every current error path to typed problem
details. Refactoring removed duplicate controller handlers without changing
the existing statuses or titles.
**Validation:** `./mvnw -Dtest=GlobalExceptionHandlerTest,LinkControllerTest,RedirectControllerTest test`
passed. An initial full gate was blocked by unrelated parallel T9 formatting;
after that work was formatted, the final `./mvnw verify` passed with all tests,
Spotless, Checkstyle, and SpotBugs green.

### AI-020 · 2026-09-20 · Phase B · Link analytics implementation

**Task:** Implement T9: `GET /api/links/{code}/stats` with total clicks,
UTC per-day counts, referrer counts, and user-agent counts matching persisted
`ClickEvent` rows.
**Intent given:** Keep management analytics separate from the public redirect
controller, preserve the existing 404 behavior for unknown codes, and prove
aggregation against real PostgreSQL rows rather than only mocked events.
**Output:** `LinkStats`, `LinkStatsService`, `LinkStatsController`, focused
unit and MVC tests, and a Testcontainers integration test that seeds three
click events and verifies all analytics dimensions.
**Disposition:** `edited` — the initial integration assertion used unordered
`Map.of` values with an order-sensitive AssertJ assertion; it was corrected to
assert each expected entry while retaining sorted production output.
**TDD evidence:** RED was observed when the new tests failed to compile because
the stats service, response model, and controller were absent. GREEN passed
after the minimal aggregation and endpoint implementation. Refactoring kept
aggregation in the service, sorted output deterministically, and excluded
null referrer/user-agent values from analytics maps.
**Validation:** `./mvnw -Dtest=LinkStatsServiceTest,LinkStatsControllerTest,LinkStatsIntegrationTest test`
and `./mvnw verify` passed.

**Review remediation:** The clean-code review reported CR-01 and CR-02.
Following the current management-endpoint convention, the local empty-body
404 handler was removed so `GlobalExceptionHandler` owns not-found responses,
and a web-layer `LinkStatsResponse` now keeps the JSON contract separate from
the service record. The focused tests and full Maven verification gate passed
after both changes; the findings were directly coupled to T9's endpoint
behavior and were accepted for fixing under autopilot.
Re-verification also identified CR-03 in the slice test: a test-local advice
could mask the shared problem response. That advice was removed; the test now
imports the real global handler and asserts the RFC 7807 content type, title,
and status. The focused test and a final `./mvnw verify` passed afterward.

### AI-021 · 2026-09-20 · Phase B · OpenAPI contract implementation

**Task:** Implement T12: expose an springdoc-generated OpenAPI contract for
the current create, redirect, stats, and error endpoint surface, and commit
the spec to `docs/openapi.json`.
**Intent given:** Add only the springdoc dependency and per-endpoint
annotations needed to document the existing behavior accurately (including
the RFC 7807 error responses from T11), commit a real generated snapshot, and
avoid pulling in T10 auth or T13 observability work.
**Output:** `org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.1` added to
`pom.xml` (the first release line compatible with the Spring Boot 4.1.1 /
Spring Framework 7 parent already in use — verified against the artifact's own
published POM before adding it); `@ApiResponses` annotations on
`LinkController`, `RedirectController`, and `LinkStatsController` describing
their real success and error codes; a minimal `OpenApiConfig` info bean;
`OpenApiContractTest` asserting both the live `/v3/api-docs` surface and that
the committed `docs/openapi.json` matches it; and the generated
`docs/openapi.json` snapshot itself.
**Disposition:** `edited` — the initial contract test used
`TestRestTemplate`/`com.fasterxml.jackson.databind`, which do not exist under
Spring Boot 4.1.1's Jackson 3 / `spring-boot-resttestclient` restructuring;
switched to `MockMvc` (matching the project's existing test convention) and
`tools.jackson.databind`.
**TDD evidence:** RED was observed as a genuine 404 from `/v3/api-docs` before
springdoc was on the classpath. GREEN passed once the dependency and
`@ApiResponses` annotations were added and `docs/openapi.json` was generated
from the live contract. A second RED/GREEN cycle covered the review
remediation below.
**Validation:** `./mvnw -Dtest=OpenApiContractTest test` and `./mvnw verify`
passed (35 tests; Spotless, Checkstyle, SpotBugs green).

**Review remediation:** The independent clean-code review reported CR-01
(Medium): `GlobalExceptionHandler`'s catch-all handler can return a 500
`application/problem+json` body from any controller, but only `LinkController`
documented a 500 response, understating the redirect and stats error surface.
Accepted for fixing under autopilot as a low-risk accuracy correction directly
in scope for the task's own "error endpoint surface" requirement. Fix: added a
documented 500 response to `RedirectController` and `LinkStatsController`,
extended `OpenApiContractTest` with a RED/GREEN cycle proving the new
assertions fail without the annotation and pass with it, and regenerated
`docs/openapi.json`. Re-verification review confirmed CR-01 resolved with no
new findings; a final `./mvnw verify` passed.

### AI-022 · 2026-09-20 · Phase B · API-key management boundary

**Task:** Implement T10: require `X-API-Key` for `/api/**` while leaving public
redirects and actuator health unprotected.
**Intent given:** Add the smallest filter-based boundary, return 401 for
missing or invalid keys, preserve the existing exception advice scope, and do
not implement T11 behavior.
**Output:** `ApiKeyFilter` as a `OncePerRequestFilter`, configured through
`security.api-key` with the development fallback already documented in the
README, plus focused endpoint tests for missing, invalid, and valid keys and
public redirect/health paths.
**Disposition:** `edited` — constant-time byte comparison was used rather than
plain string comparison; the existing management endpoint tests were updated
to supply the configured test key, while redirect and exception handling were
left otherwise unchanged. Authentication failures now write a small RFC 7807
problem body at the filter boundary because controller advice cannot intercept
servlet-filter responses.
**TDD evidence:** RED was observed with `ApiKeyFilterTest` failing to compile
because the production filter did not exist. GREEN passed after adding only
the filter and wiring it as a Spring component. A follow-up test run exposed
the expected contract change in existing management tests (401 without a key);
those tests were updated to represent authenticated management calls.
**Validation:** `./mvnw -Dtest=ApiKeyFilterTest,LinkControllerTest,LinkStatsControllerTest,RedirectControllerTest,GlobalExceptionHandlerTest test`
passed, followed by `./mvnw verify`.

**Review remediation:** CR-01 added a deterministic RFC 7807 body for filter
401 responses and a focused content-type/title assertion without changing the
existing controller advice scope. CR-02 was resolved by placing this entry
after AI-020 in ascending traceability order.

## Pending sign-offs

High-impact items requiring explicit engineer review before merge:

- [ ] URL validation and scheme allowlist (T6) — S3
- [ ] API-key filter and the public/protected endpoint split (T10) — S1
- [ ] `V1` schema migration (T3)
- [ ] `V2` expiration migration and backward compatibility (T15)
- [ ] `V3`/`V4` privacy migration, expand/contract ordering, retention policy
      (T16) — A2, A3, M1
- [ ] Collision-retry path under concurrency (T5) — P7
- [ ] Pool sizing and request-path timeouts (T22) — R6
