# Task Decomposition

Tasks with explicit IDs, dependencies and estimates. Phases are a reading aid;
the dependency column is what actually governs sequencing.

## Budget reality

The estimates below total **~26.5 hours against a 2–3 day budget.** That is a real
squeeze, not a comfortable fit, and pretending otherwise would be the first
engineering-judgment failure of the project. Two consequences, both deliberate:

1. There is a **cut list** (below) with a fixed order. Work is shed from the
   bottom of that list, never improvised under pressure at 11pm.
2. **T15 and T16 are protected.** They are the brownfield and ambiguous
   scenarios — graded deliverables in their own right (assignment §5). If the
   baseline runs long, quality gates and polish are sacrificed to protect them,
   not the reverse. A thin but production-quality baseline with two
   well-reasoned scenarios scores better than a polished baseline with no
   scenarios.

**Re-planned after the standard was raised.** Committing to a production-grade
bar (see the README's engineering standard) added T21 (CI), T22 (timeouts, pool
sizing, graceful shutdown) and a two-step `V3`/`V4` in T16 — about 1.5 h — and moved
T10 out of the cut list. Raising a quality bar without re-costing the plan is
the same judgment failure this section exists to prevent, so the estimate and
the slack below both moved with it.

## Execution status

| Task | Status | Evidence |
| --- | --- | --- |
| T1 | Complete | Java 25 selected through `.java-version`; Spring Boot 4.1.1 application starts against healthy PostgreSQL 16 Compose service |
| T2 | Complete | Spotless, Checkstyle, and SpotBugs run in `mvn verify`; Dependabot and PR dependency review provide the hosted vulnerability gate |
| T3 | Complete | V1 migration applied to PostgreSQL 16 and verified by `SchemaMigrationTest` after an observed behavioral RED |
| T4 | Complete | Base62 generator implemented through RED–GREEN; contract verified across 1,000 generated codes |
| T5 | Complete | Spring Data JDBC mappings and repositories verified against PostgreSQL 16 Testcontainers; duplicate short codes are rejected by the database unique constraint |
| T21 | Configured, remote run pending | `.github/workflows/ci.yml` runs `mvn verify` with Temurin Java 25 |

## Critical path

```
T1 → T3 → T5 → T7 → T8 → T9 → T15 → T16 → T19
```

≈ 14 h. T6 branches from T5 and must join before T15 because expiration changes
the creation API. T15 (brownfield) cannot start until both creation and the
redirect/analytics path are real, and T16 (ambiguous, privacy) depends on click
recording existing to make privacy-safe. Slipping T5, T7, or T9 compresses both
scenarios directly.

## Phase A — Foundation

| ID | Task | Depends on | Est | Done when |
| --- | --- | --- | --- | --- |
| **T1** | Bootstrap: Maven, Java 25, Spring Boot 4.1.1, package layout, `compose.yaml` with PostgreSQL 16 | — | 1.5 h | `mvn verify` passes and the app starts against Compose PostgreSQL |
| **T2** | Quality gates: Spotless, Checkstyle, and SpotBugs in Maven; Dependabot alerts/updates and PR dependency review in GitHub | T1 | 1.0 h | `mvn verify` fails on formatting, style, or static-analysis violations; dependency review rejects newly introduced high-severity vulnerabilities |
| **T3** | Flyway migration `V1__initial_schema.sql`: `links`, `click_events` | T1 | 0.75 h | Migration applies cleanly to an empty database; schema matches `03-architecture.md` |
| **T4** | Short-code generator (base62 / `SecureRandom`) + unit tests | T1 | 1.5 h | Alphabet and length asserted; generator is an interface so it can be stubbed in T6's collision test |
| **T21** | CI pipeline (GitHub Actions) running `mvn verify` on push | T2 | 0.5 h | Pipeline green on `main`; a deliberately introduced lint or test failure turns it red |

## Phase B — Greenfield baseline

| ID | Task | Depends on | Est | Done when |
| --- | --- | --- | --- | --- |
| **T5** | Persistence: Spring Data JDBC entities and repositories with Testcontainers integration tests, including database-enforced short-code uniqueness | T3, T4 | 1.5 h | Repository mappings work against PostgreSQL; a duplicate code is rejected by the real unique constraint |
| **T6** | Link-creation service and `POST /api/links` — collision retry in a fresh transaction, URL validation, scheme allowlist, private-address rejection, length cap | T5 | 2.0 h | Valid URL returns 201 + code; a forced collision retries successfully; each rejection class returns 400 with a problem body |
| **T7** | `GET /{code}` — resolve and redirect, 302 + `Cache-Control: no-store` | T5 | 1.0 h | Known code redirects; unknown returns 404 |
| **T8** | Click recording on the redirect path, **fail-open** | T7 | 1.0 h | Click row written on redirect; an injected write failure still produces a 302 (asserted by test, not by inspection) |
| **T9** | `GET /api/links/{code}/stats` — total and per-day counts, referrer, user-agent | T8 | 1.0 h | Counts match seeded events |
| **T10** | `X-API-Key` filter on `/api/**`; redirect and health left public | T6, T9 | 0.75 h | Management endpoints 401 without a key; redirect unaffected |
| **T11** | RFC 7807 problem details + global exception handling | T6, T7 | 0.75 h | Every error path returns a typed problem body; no stack traces escape |
| **T12** | OpenAPI contract via springdoc | T6, T7, T9 | 0.5 h | `/v3/api-docs` complete; spec committed to `docs/openapi.json` |
| **T13** | Actuator health/readiness, structured JSON logging with correlation id, Micrometer counters | T7 | 1.0 h | Readiness reflects database state; logs carry a correlation id end to end |
| **T14** | `make demo` — boot Compose, wait for health, create a link, follow the redirect, print stats | T6, T7, T9 | 0.75 h | One command demonstrates the full loop from a clean checkout |
| **T22** | Resource limits: Hikari pool size and `connection-timeout`, JDBC query timeout on the redirect path, graceful shutdown | T7, T8 | 0.75 h | Pool exhaustion returns 503 rather than hanging; a stalled query aborts at the timeout; in-flight requests drain on SIGTERM |

## Phase C — Scenarios (protected)

| ID | Task | Depends on | Est | Done when |
| --- | --- | --- | --- | --- |
| **T15** | **Brownfield: link expiration.** `V2` migration adding nullable `expires_at`; expiry check on resolve returning 410; `POST /api/links` accepts optional TTL; tests; impact analysis written to `docs/scenario-brownfield.md` | T6, T9 | 2.5 h | Existing rows unaffected (backward-compatible migration proven by test); expired link returns 410; impact analysis covers API, service, schema, tests, docs |
| **T16** | **Ambiguous: "make analytics privacy-safe."** Interpretation, alternatives and chosen policy; `V3` backfill plus application change that stops writing `client_ip`; verified release checkpoint; later `V4` column drop; referrer reduced to host, user-agent bucketed; retention policy; written to `docs/scenario-ambiguous.md` | T8, T15 | 2.75 h | A2/A3 debt in `01-decisions.md` is discharged; Git history preserves a verified checkpoint between stopping writes and adding `V4`; the reasoning from ambiguity to policy is legible to a reviewer |

The greenfield scenario is not a separate task — Phase B **is** the greenfield
scenario, written up in `docs/scenario-greenfield.md` as part of T19.

## Phase D — Validation and close-out

| ID | Task | Depends on | Est | Done when |
| --- | --- | --- | --- | --- |
| **T17** | Failure-mode tests: malformed/hostile URLs, expired link, database unavailable, analytics write failure, concurrent creation | T15 | 1.5 h | Each failure mode has a named test asserting the documented behaviour |
| **T18** | Performance check: `hey` against the redirect path, p95 recorded | T14 | 1.0 h | p95 recorded in the summary and **labelled indicative, not a benchmark** |
| **T19** | Final engineering summary: plan, decisions, artifacts, validation, risks, trade-offs, limitations, next steps | T1–T18, T21, T22; T20 current | 1.5 h | Assignment §5 deliverables each map to a committed path |
| **T20** | AI traceability log — **continuous**, updated alongside the code it describes | — | ~1.0 h aggregate | Entries exist for every task that used AI, including rejections |

**T20 is not a close-out task.** Writing the log at the end produces something
that reads as written at the end. See `ai-log.md`.

## Cut list

Shed in this order. Anything cut is recorded as a stated limitation in T19,
never silently dropped:

1. **T18** (performance check) — state "not measured" as a limitation. A missing
   number is honest; a fabricated one is not.
2. **T13** metrics — keep health/readiness, drop Micrometer counters.
3. **T12** OpenAPI — the endpoint list in the README carries the contract.
4. **T17** — reduce to the two highest-value cases: hostile URL rejection and
   analytics fail-open.

That is **~2.25 h of genuine slack against a ~26.5 h estimate.** Thin, and stated
plainly, because the alternative is discovering it at midnight on day three. If
the spine overruns by more than half a day the correct response is to narrow
scope deliberately — reduce T9 to a total-only click count, or T10 to a single
hardcoded key comparison — not to compress T15 or T16.

**Never cut:**

- **T15, T16, T19** — graded deliverables (assignment §5).
- **T8's fail-open assertion** — the reliability decision the whole design rests
  on, and precisely the property a later refactor breaks silently by wrapping
  the handler in one transaction.
- **T10** — public redirects are the product, but management and analytics
  endpoints must never be reachable unauthenticated.
- **T21** — a quality gate that runs only on the engineer's machine is not a
  gate; CI must run `mvn verify` and dependency review must protect dependency
  changes.

## Risks

| Risk | Mitigation |
| --- | --- |
| Testcontainers startup cost compounds across test classes | Shared Spring test context and service-connected PostgreSQL container. Budgeted in T5. Rejected an H2 fallback — see technology decision T6 in `01-decisions.md` |
| Toolchain setup (T1+T2 = 2.5 h) eats day one before any feature lands | T2 is deferrable past T7 if T1 overruns; gates matter less than having something to gate |
| Scenarios compressed into the final hours, producing thin write-ups | Protected status above; the critical path is arranged so T15 can start once T7 and T9 land, not after all of Phase B |
| Documentation written retrospectively reads as retrospective | Scenario docs written during their task, not after; T20 committed alongside code |
| T16's column drop is destructive and Flyway Community has no undo | Expand/contract across release checkpoints: `V3` backfills, the application stops the write, and only a subsequent release adds `V4`. Forward-only policy stated in `03-architecture.md`; the drop is irreversible by design |
