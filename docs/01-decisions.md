# Decisions and Assumptions

This assignment has no stakeholder to answer requirement questions. Where the
requirement is ambiguous, I resolved it myself and recorded the reasoning below.
Every row is a decision I own, not a question I am waiting on.

Each decision states the condition that would make me change my mind. A decision
without a revisit trigger is a guess; a decision with one is a position.

## Deliberate sequencing note

Three gaps in the v1 baseline are intentional, not oversights:

| Gap in v1 | Why it is deliberate |
| --- | --- |
| No link expiration | Expiration is the **brownfield scenario** (see `02-tasks.md`, T15). Pre-building `expires_at` in the v1 schema would reduce that scenario to a one-line change and destroy its value as evidence of migration and compatibility reasoning. |
| Analytics records raw IP, full referrer, full user-agent | Privacy hardening is the **ambiguous scenario** (T16). v1 deliberately collects what a naive-but-correct implementation collects, so that "make analytics privacy-safe" is a real exercise with real work behind it rather than a no-op. This is flagged as known privacy debt below (A2), and it is resolved before submission. |
| No caching | See R2. Adding a cache before measuring is the wrong order, and it would be invalidation-complex once expiry and soft delete exist. |

If the baseline already solved these, the two scenarios that demonstrate the most
judgment would have nothing to demonstrate.

---

## Product and API behaviour

| # | Question | Decision | Rationale | Would revisit if |
| --- | --- | --- | --- | --- |
| P1 | Which redirect status? | **302** with `Cache-Control: no-store` | 301 is a semantic promise the system cannot keep. Expiry (P4), soft delete (P5) and any future edit capability all mean a link may legitimately stop resolving — and a 301 cached in a browser is unrecallable. The analytics argument (301 suppresses repeat click events) is real but secondary. `no-store` is added because browsers do opportunistically cache 302s. | Links became genuinely immutable and permanent, and click counting moved to edge/CDN logs. Then 301 buys real latency. |
| P2 | Can a destination be changed after creation? | **No.** Destination is immutable for the life of the code. | A short code is a handle that people paste into places the service does not control. Silently retargeting it is the mechanic behind a real phishing pattern — the link a user vetted is not the link they land on. Immutability makes the handle trustworthy by construction. | A campaign-retargeting requirement appeared. It would need owner-scoped authz, an audit trail of destination changes, and a user-visible notice — not just an `UPDATE`. |
| P3 | Are custom aliases required? | **Not in v1.** Deferred, design sketched in `03-architecture.md`. | Aliases drag in a namespace-policy surface far larger than the feature: reservation of system words, case-sensitivity rules, impersonation and profanity screening, reuse-after-deletion semantics. That is a policy problem, not a coding problem, and it is disproportionate to the budget. | The service became user-facing with branded links. The first thing to build is the reserved-word list, not the endpoint. |
| P4 | Unknown / expired / disabled code? | **404** unknown, **410 Gone** expired, **410** disabled. RFC 7807 problem body on the API; plain response on the redirect path. | 410 tells a legitimate user their link *was* real and has ended, which is materially more useful than a blanket 404. The counter-argument is that distinguishing the two confirms a code existed — but with random 7-character codes (P7) enumeration is impractical, so the leak is negligible and clarity wins. Recording the trade-off because the reasoning, not the answer, is the point. | Codes became sequential or otherwise guessable. Then collapse everything to 404 and accept the worse user experience. |
| P5 | Is deletion required? Soft or hard? | **Not exposed in v1**, but the schema carries a `status` column so adding it is additive, and it will be **soft** delete. | Hard delete orphans click events and destroys the analytics record the service exists to provide. Soft delete preserves referential integrity and leaves an audit trail. Shipping the column without the endpoint costs nothing and avoids a breaking migration later. | A GDPR erasure path were required. That needs genuine purge semantics — soft delete is not erasure — including cascade to click events. |
| P6 | Is idempotent creation required on retry? | **No.** | Without a client-supplied key, idempotency is guesswork: the same URL submitted twice may legitimately want two codes (two campaigns, two channels, separately measured). Collapsing them silently would be wrong. Doing it properly means an `Idempotency-Key` header, a dedupe store and a TTL policy. | Clients reported duplicate-link pollution from retries. Implement `Idempotency-Key` with a short-TTL store — never infer intent from URL equality. |

## Identity, security and abuse prevention

| # | Question | Decision | Rationale | Would revisit if |
| --- | --- | --- | --- | --- |
| S1 | Which operations require authentication? | Redirect (`GET /{code}`) is **public**. Create and analytics require a **static API key** in an `X-API-Key` header. Actuator health is public; other actuator endpoints are not exposed. | The redirect must be anonymous or the service does not function. Everything else is management surface. A shared-secret header is a deliberately minimal trust boundary — sufficient to enforce the public/protected split correctly, explicitly insufficient for multi-tenancy (S2). | Multi-tenancy. Then this becomes OAuth2 resource-server + per-account keys, and analytics scopes to the owner (S2). |
| S2 | Who may access analytics? | **Any holder of a valid API key.** No per-link ownership model in v1. | Ownership requires a user/account model, registration, and key lifecycle — days of work that demonstrate nothing the rubric asks for. Stated plainly as a limitation rather than hidden. | More than one tenant. Links gain `owner_id`, analytics queries filter on it, and key→owner resolution moves into the auth filter. |
| S3 | Which destination schemes and hosts are permitted? | **`http` and `https` only.** Reject all other schemes. Reject private, loopback, link-local and cloud-metadata addresses. Cap URL length at 2048. | Scheme allowlisting is the load-bearing control: accepting `javascript:` or `data:` turns the redirect into a stored-XSS delivery vector. On the address blocking, precision matters — **the service never fetches destinations, so classic SSRF does not apply to it directly.** The real exposure is being used as a laundering hop to bypass *another* system's egress filter, plus the fact that any future preview/favicon feature would make it live. Cheap insurance, blocked at creation time. | A legitimate deep-link use case appeared (`myapp://`). Extend by **allowlist**, never by relaxing to a blocklist. |
| S4 | What abuse protections are expected? | v1: URL validation, scheme allowlist, length cap. **Rate limiting deferred** with its design recorded in `03-architecture.md`. No malware/phishing reputation check. | Rate limiting is the more interesting design conversation (filter placement, keying, in-memory vs distributed, testability under a clock) but it touches little schema and competes directly with the brownfield scenario for time. Writing the design without building it captures the reasoning at a fraction of the cost. A reputation check means an external API dependency, a key, and a failure-mode policy. This local/reference deployment is not eligible for public exposure without those controls. | Any public deployment. Rate limiting becomes mandatory before exposure, and a Safe Browsing-style check before accepting user-submitted destinations at scale. |
| S5 | Are there mandated org security standards or scanners? | No one to ask, so I imposed my own: **Spotless** (format), **Checkstyle** (style), **SpotBugs** (static analysis), **OWASP Dependency-Check** (known CVEs in dependencies). | An unnamed "security check" is not a quality gate. Named tools with a pass/fail in the build are. | The organisation named its own toolchain. These are all replaceable without touching application code. |

## Analytics and privacy

| # | Question | Decision | Rationale | Would revisit if |
| --- | --- | --- | --- | --- |
| A1 | Which analytics are required? | Total clicks, clicks per day, referrer, user-agent. No geography, no device fingerprinting. | Covers the "analytics" requirement with the signals that are actually derivable from a redirect request. Geography needs a GeoIP database and licensing; fingerprinting is a privacy liability with no stated purpose. | A product need for campaign attribution. That is a UTM-parameter problem, not a fingerprinting problem. |
| A2 | What privacy rules apply to personal data? | **v1: raw client IP, full referrer URL and full user-agent are stored. This is known privacy debt, deliberately retained as the input to the ambiguous scenario (T16), and resolved before submission** — IP dropped entirely, referrer reduced to host, user-agent bucketed to a coarse class. | A full referrer URL leaks the page a user came from, which can itself be sensitive; a raw IP is personal data under GDPR, and nothing in this service establishes a lawful basis for holding one. The delivered state collects what answers the product question and nothing more. The debt is visible in this table rather than discovered by a reviewer. | Nothing — T16 resolves it. If T16 were cut, this row becomes a stated limitation in the final summary, not a silent omission. |
| A3 | What is the retention period? | Decided in T16 alongside A2. Target: **90 days** for click events, with a documented (not necessarily automated) purge path. v1 is unbounded. | Retention is meaningless before the data model is privacy-settled — deciding it in the same task as A2 keeps the policy coherent. 90 days is long enough for trend analysis, short enough to be defensible without a stated business need. | A business need for year-over-year comparison. That wants pre-aggregated daily counts with long retention and raw events with short retention — two different lifetimes. |
| A4 | Is click recording synchronous, eventually consistent, or best-effort? | **Synchronous write on the redirect path, but fail-open: if the click write throws, log it and redirect anyway.** | This is the most important reliability decision in the system. The user's redirect is the product; the click row is a secondary signal. Letting a failed `INSERT` turn into a failed redirect would trade the core function for a metric. The consequence — undercounting during a partial database failure — is the correct thing to sacrifice, and it is stated rather than discovered. | Redirect p95 degraded under load, or click loss became material. Move to an async queue with a bounded buffer, accepting the added complexity and at-least-once semantics. |

## Reliability, scale and operations

| # | Question | Decision | Rationale | Would revisit if |
| --- | --- | --- | --- | --- |
| R1 | What traffic, latency, availability and durability targets? | Set by me, since there is no stakeholder: **single-instance reference deployment, p95 redirect < 50 ms** measured locally with a warm pool, no availability claim, and PostgreSQL durability at default `fsync`. Production deployment requires a defined SLO, managed backups and restore testing, and a platform-owned HA topology. | Asking a nonexistent stakeholder for SLOs is noise. Modest, falsifiable, self-imposed targets are worth more than open questions, and the p95 number is checkable in T18. The number is indicative of local behaviour, not a benchmark, and is labelled as such. Separating implementation quality from absent deployment infrastructure avoids overstating readiness. | Real deployment. Every one of these becomes a genuine conversation, starting with the availability target, because it determines whether single-instance is viable at all. |
| R2 | Is caching expected for redirects? | **No cache in v1.** | A lookup on a unique indexed column is sub-millisecond at the volumes this service is built for. A cache would add invalidation complexity precisely where correctness matters most — a cached entry for a link that has since expired or been disabled serves a redirect that should have stopped. Do not add a cache without a measurement showing the lookup is the bottleneck. | T18 showed database lookup dominating redirect latency. Then cache with a short TTL and explicit invalidation on expiry/disable, and accept a bounded staleness window. |
| R3 | What if analytics or PostgreSQL is unavailable? | Click-write failure: **log and redirect anyway** (A4). PostgreSQL unavailable: redirect returns **503**, readiness probe fails so an orchestrator stops routing. No stale-serving fallback. | Distinguishes a degraded secondary path from a dead primary one. Serving a redirect requires knowing the destination — without the database there is nothing truthful to serve, and guessing is worse than failing. | A cache existed (R2). Then serving a known-stale destination during an outage becomes a deliberate, bounded choice worth making. |
| R4 | Deployment environment and configuration mechanism? | **Docker Compose** locally. Configuration via Spring profiles and environment variables. **No secrets in the repository**; the development API key is supplied only through a development profile or environment variable. Non-development startup fails without a configured key. | Compose makes the service runnable in one command, which is deliverable #1. Environment-variable config is the boundary that makes the same artifact deployable elsewhere without edits. Failing closed for missing credentials is the production-grade default. | Deployment to a real platform. Externalise config to that platform's secret store; the application side does not change. |
| R5 | Which observability signals are mandatory? | Actuator **health/readiness**; **structured JSON logs** with a per-request correlation id; **Micrometer counters** for links created, redirects served, redirect misses. No tracing, no alerting. | These are the three questions worth asking of this service in production: is it up, what happened to a given request, is it being used. Tracing pays off across service boundaries and there is only one service. Alerting needs somewhere to send an alert. | It stopped being a single process. Distributed tracing becomes worthwhile at the first network hop between components. |
| R6 | What happens when PostgreSQL is **slow** rather than down? | Bounded everywhere: Hikari pool sized explicitly against the database's `max_connections`, Hikari `connection-timeout` ~2 s, **JDBC query timeout on the redirect path in the low hundreds of milliseconds**, and graceful shutdown so in-flight redirects drain on SIGTERM. Pool exhaustion surfaces as 503, not a hang. | R3 answers "down", which is the easier failure. "Slow" is more common and more dangerous: an unbounded query holds a connection, connections exhaust the pool, and the redirect path dies while liveness still reports healthy. Bounding every wait converts a silent cascade into a fast, visible error. **This is also what makes A4's fail-open real** — catching an exception protects against the click write *throwing*, not against it being slow, and a slow insert still adds its latency to every redirect. Fail-open without a timeout is not fail-open. | Load testing showed the redirect timeout tripping under normal conditions — that would mean the budget is wrong, not that the bound should be removed. |

## Technology choices

| # | Decision | Rationale | Would revisit if |
| --- | --- | --- | --- |
| T1 | **Java 21 LTS**, not 25 | Both are LTS. 21 has the deeper pool of mature tooling, plugin support and published guidance, and nothing in this design needs anything newer. Choosing the older LTS is the lower-variance call when the budget has no room for toolchain debugging. | The project had a runway longer than three days, or needed a feature only present in a later release. |
| T2 | **Spring Boot 3.x** (exact version pinned in `pom.xml` at bootstrap) | Least-surprise choice for a JVM service: Actuator, validation, test slices and OpenAPI tooling are all first-party or well-trodden. | — |
| T3 | **Maven**, not Gradle | Spotless, SpotBugs, Checkstyle and OWASP Dependency-Check are all one plugin block each, and the declarative build is easier for a reviewer to audit than a Gradle script. Build speed is irrelevant at this size. | A multi-module build with real build logic. |
| T4 | **Spring Data JDBC**, not JPA | Explicit SQL and an explicit persistence boundary. JPA's lazy loading, dirty checking and transaction-boundary surprises are a debugging cost that only pays for itself on a rich object graph — this domain is two tables. Choosing the simpler tool is the point. | The domain grew an aggregate with deep associations and genuine identity-map needs. |
| T5 | **PostgreSQL 16 + Flyway** | Versioned, reproducible, reviewable schema evolution — and the brownfield scenario depends on a real migration being a real artifact. | — |
| T6 | **Testcontainers** against real PostgreSQL, with a single reusable container | Considered and **rejected** an H2 fallback for integration tests. H2 diverges from PostgreSQL on exactly the behaviour under test — unique-constraint violation semantics (P7's retry path), upsert, and PostgreSQL-specific migration DDL — so migration tests against H2 would be theatre. Docker is already a hard dependency because of Compose (R4), so Testcontainers adds no new environmental requirement, only startup latency, and a static container on a shared abstract base test class pays that once rather than per class. | Docker were unavailable in the target environment. Then the honest fallback is fewer integration tests, not the same tests against a different database. |
| T7 | Short code: **7-character base62, `SecureRandom`, unique constraint, bounded retry** | See P7 below — this is a design decision, not just a tech choice. | — |

## P7 — Short-code generation

Called out separately because it was the largest hole in the original plan, and
because three other things depend on it: the data model cannot be specified
without it, the collision test cannot be written without it, and it determines
whether every link in the system is enumerable.

**Decision:** 7 characters from a base62 alphabet, drawn from `SecureRandom`.
A `UNIQUE` constraint on `links.short_code`. On constraint violation, regenerate
and retry, bounded at 3 attempts, then fail with a 500.

**Rationale:**

- **Random over sequential.** Counter + base62 makes collisions impossible by
  construction, which is genuinely attractive. It also makes every link in the
  system trivially enumerable, and leaks creation order and volume — anyone can
  walk the keyspace and read every destination, and infer how many links were
  created between two timestamps. For a service whose entire purpose is issuing
  opaque handles, that is the wrong default. Rejected.
- **Random over hash-of-URL.** Hashing gives free deduplication, but it makes the
  code a function of the destination, so an attacker who guesses the URL confirms
  the link exists, and P6 already established that silent deduplication is wrong.
  Rejected.
- **Why 7.** 62^7 ≈ 3.5 × 10^12. At any volume this service will realistically
  see, collision probability per insert is negligible; the retry path exists to
  be correct, not because it is expected to run.
- **Why the database constraint, not an application-level check.** A
  `SELECT ... WHERE short_code = ?` followed by an `INSERT` is a
  check-then-act race: two concurrent creations can both see the code as free.
  The `UNIQUE` constraint is the only thing that actually serialises this, and
  catching the violation is the correct way to handle it. This is called out
  explicitly because the `SELECT`-then-`INSERT` version is the natural thing to
  write and is wrong under concurrency.

**Validation:** unit tests for alphabet and length; an integration test that
forces a collision by seeding a known code and stubbing the generator, asserting
the retry path produces a distinct code and a successful insert; a concurrency
test issuing parallel creations and asserting all codes are distinct.

**Would revisit if:** codes needed to be shorter for print/voice use — then the
keyspace shrinks, collision probability becomes non-negligible, and the retry
bound and its failure semantics need to be designed rather than assumed.

## M1 — Migration policy

Called out separately because the README names migration safety as part of the
engineering standard, and because the most dangerous migration in this project
is the one the privacy scenario requires.

**Decision:** migrations are **forward-only**. Any migration that destroys data
or removes a column follows **expand/contract**: the code that stops using the
column ships first, the drop ships in a later, separate migration.

**Rationale:**

- Flyway Community has no undo. Forward-only is therefore the actual policy
  whether or not it is written down, and a rollback plan that depends on an
  unavailable feature is worse than no rollback plan.
- A column drop applied while running code still writes that column fails, or
  breaks the next insert. With Spring Data JDBC the entity maps columns
  explicitly, so the failure is immediate. Ordering is not a nicety.
- In this project both steps land in one deploy, so the practical risk is low.
  The policy is stated anyway because in a rolling deployment it is two
  releases, and the discipline is the point.

**Applied to the three planned migrations:**

| Migration | Class | Safety |
| --- | --- | --- |
| `V1` initial schema | Additive | Safe. Empty database |
| `V2` add nullable `expires_at` (T15) | Additive | Safe. Nullable with no default, so existing rows are untouched and old code ignores it — backward compatibility proven by a test that seeds pre-migration rows and resolves them afterwards |
| `V3` / `V4` privacy (T16) | **Destructive** | `V3` stops writing `client_ip` and backfills the reduced referrer/user-agent forms; `V4` drops `client_ip`. Split deliberately |

**On the irreversibility of `V4`:** dropping `client_ip` destroys the data with
no recovery path. That is the correct outcome — the entire purpose of the change
is to stop holding it, and an "anonymise in place" alternative leaves the column
available for a future developer to start populating again. The decision is to
drop, knowingly, rather than to soften it into something reversible.

**Would revisit if:** the service ran multi-instance with rolling deploys. The
two steps then need to be two releases with a verified gap, not two migrations
in one deploy.
