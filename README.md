# URL Shortener — AI-Assisted Engineering Assignment

A production-grade URL-shortening service delivered through engineer-led,
AI-accelerated execution. The service is the artifact; the decisions,
decomposition, validation, and traceability around it are the substance.

> **Status: Phase A foundation implemented locally.**
> The Java 25/Spring Boot application, PostgreSQL environment, initial schema,
> short-code generator, quality gates, and CI workflow are implemented locally.
> `mvn verify` passes; the CI run remains pending until this change set is
> committed and pushed. The feature API has not started, so `make demo` remains
> a target until T14. Per-task status is in
> [`docs/02-tasks.md`](docs/02-tasks.md).

## Engineering standard

“Production-grade” is the quality bar for every implemented capability, not a
claim that this local single-instance deployment has production capacity or an
operations team. Every shipped path must have defined failure behavior, secure
defaults, migration safety, observability, automated validation, and explicit
ownership. Features deliberately deferred from the implementation must be
documented with their deployment consequence; they are never silently treated
as production-ready.

The standard applies to the **delivered state**. Intermediate commits are staged
deliberately — the baseline is built, then hardened by the two scenarios — and
one of those stages carries a known privacy debt on purpose, discharged before
submission. That sequencing is recorded in `01-decisions.md` rather than left
for a reviewer to find in the history.

## Quick start

Requires Docker and JDK 25. This repository includes `.java-version`; with
`jenv`, select it using `jenv local 25`.

```bash
make demo
```

Boots PostgreSQL via Compose, waits for health, creates a link, follows the
redirect, and prints its analytics — the whole loop in one command.

Manually:

```bash
docker compose up -d      # PostgreSQL 16
mvn verify                # build, lint, static analysis, unit + integration tests
mvn spring-boot:run       # http://localhost:8080
```

## API

| Method | Path | Auth | Purpose |
| --- | --- | --- | --- |
| `POST` | `/api/links` | `X-API-Key` | Create a short link |
| `GET` | `/{code}` | public | Resolve and redirect (302) |
| `GET` | `/api/links/{code}/stats` | `X-API-Key` | Click analytics |
| `GET` | `/actuator/health` | public | Liveness / readiness |

```bash
curl -X POST localhost:8080/api/links \
  -H 'X-API-Key: dev-key-not-a-secret' \
  -H 'Content-Type: application/json' \
  -d '{"destination":"https://example.com/some/long/path"}'
# 201 → {"shortCode":"aB3xK9z","shortUrl":"http://localhost:8080/aB3xK9z", ...}
```

## Deliverables map

Assignment §5, mapped to paths:

| Deliverable | Where |
| --- | --- |
| Working prototype (runnable end-to-end) | This repository · `make demo` |
| Architecture overview | [`docs/03-architecture.md`](docs/03-architecture.md) |
| Greenfield scenario | `docs/scenario-greenfield.md` *(T19)* |
| Brownfield scenario | `docs/scenario-brownfield.md` *(T15)* |
| Ambiguous scenario | `docs/scenario-ambiguous.md` *(T16)* |
| Setup instructions | Quick start, above |
| Testing approach, limitations, trade-offs | Below, and [`docs/01-decisions.md`](docs/01-decisions.md) |
| Decisions and assumptions | [`docs/01-decisions.md`](docs/01-decisions.md) |
| Task decomposition | [`docs/02-tasks.md`](docs/02-tasks.md) |
| AI traceability | [`docs/ai-log.md`](docs/ai-log.md) |
| Final engineering summary | `docs/99-summary.md` *(T19)* |

## Design in one page

Modular monolith — Java 25, Spring Boot 4.1.1, PostgreSQL 16, Flyway, Spring Data
JDBC. Three layers with inward-pointing dependencies: `web → service →
persistence`. No message broker, no cache, no second service; nothing in the
requirements needs one.

The decisions most worth arguing about:

- **302, not 301.** The link lifecycle is mutable — expiry and soft delete both
  mean a link may stop resolving, and a 301 cached in a browser is unrecallable.
  301 is a promise this system cannot keep.
- **Random 7-char base62 codes, not sequential.** Sequential codes make every
  destination in the system enumerable and leak creation order and volume.
  Uniqueness is enforced by a database constraint with a bounded retry, because
  `SELECT`-then-`INSERT` is a check-then-act race.
- **Click recording fails open — and is timed out.** If the analytics write
  throws, the redirect still happens. The user's redirect is the product; the
  click row is a secondary signal. Catching the exception is only half of it:
  a *slow* write still adds its latency to every redirect, so the write is
  bounded by a query timeout. Fail-open without a timeout is not fail-open.
- **Every wait in the request path is bounded.** Pool size, connection
  acquisition, query timeout, graceful shutdown. "Database down" is the easy
  failure; "database slow" is the one that exhausts the pool while liveness
  still reports healthy.
- **No cache.** Not without a measurement showing one is needed — and once
  expiry exists, a stale cache entry serves a link that should have stopped.

Full reasoning, including what would change each decision, in
[`docs/01-decisions.md`](docs/01-decisions.md).

## Testing approach

| Layer | Tooling | Covers |
| --- | --- | --- |
| Unit | JUnit 5, Mockito | Code generation, URL validation, service logic |
| Integration | Testcontainers + **real PostgreSQL** | Repositories, migrations, collision retry, concurrent creation |
| Web | `@WebMvcTest` | Status codes, headers, problem bodies, auth |
| Failure modes | JUnit + injected failures | Hostile URLs, expired links, database down, analytics write failure |

Integration tests run against real PostgreSQL rather than H2. H2 diverges from
PostgreSQL on exactly the behaviour under test — unique-constraint violation
semantics and migration DDL — so migration tests against it would pass while
proving nothing. Reasoning in [`docs/ai-log.md`](docs/ai-log.md) (AI-004).

Quality gates in `mvn verify`: **Spotless**, **Checkstyle**, and **SpotBugs**.
GitHub provides the dependency-security layer: **Dependabot alerts and weekly
updates**, plus dependency review that rejects new high-severity vulnerabilities
in pull requests. This avoids putting a rate-limited NVD database download in
the inner development loop while keeping dependency risk visible and actionable.
The repository dependency graph, Dependabot alerts, and security updates must
remain enabled in GitHub settings.

## Trade-offs and limitations

Known and deliberate. Stated here rather than left for a reviewer to find:

- **Single-instance local deployment, not an HA topology.** The application is
  designed to be deployable behind a platform-managed PostgreSQL service with
  backups, health-based routing, and operational ownership; those infrastructure
  controls are not represented by local Docker Compose.
- **Static API key**, not real authentication. No accounts, so analytics is
  visible to any key holder — no per-link ownership. A production multi-tenant
  deployment requires identity, authorization scopes, and key lifecycle
  management before exposure.
- **No rate limiting or destination reputation check.** The service must not be
  publicly exposed until these abuse controls are implemented. The rate-limit
  design and its deferral rationale are in
  [`docs/03-architecture.md`](docs/03-architecture.md).
- **No custom aliases, no idempotent creation, no link editing.** Each was
  decided against with reasoning, not overlooked.
- **Performance figures are indicative**, measured locally with `hey` on one
  machine. Not a benchmark.
- **Analytics retention has no automated purge.** The 90-day policy is defined
  and documented; enforcing it is a scheduled job that is not built.
- **No rollback path for the privacy migration.** Dropping `client_ip` is
  irreversible by design — see M1 in
  [`docs/01-decisions.md`](docs/01-decisions.md).

The delivered service does **not** store raw client IP, and reduces referrer to
host and user-agent to a coarse class. An earlier commit deliberately did store
them, as the starting point for the ambiguous scenario; that sequencing is
described in `docs/scenario-ambiguous.md` and A2, and is not a property of what
is handed in.

## AI usage

AI has been used across analysis, design review, Phase A implementation, test
generation, and documentation. Every material use is logged in
[`docs/ai-log.md`](docs/ai-log.md) with disposition — adopted, edited or
rejected — and technical rationale, including the rejections and one case where
the recommendation was right but its reasoning was not.

Independent implementation slices are reviewed by a local Copilot subagent
that is intentionally kept outside the repository. It reviews only the supplied
diff, never remediates its own findings, and holds remediation for engineer
approval. The whole-project review remains a manual final gate.

The engineer owns every decision in this repository. Nothing here was accepted
on the basis that it passed a test.
