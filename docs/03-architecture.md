# Architecture Overview

A modular monolith: one Spring Boot process, one PostgreSQL database. Distributed
services, message brokers and external caches are deliberately out of scope —
there is no requirement in this assignment that a single process cannot satisfy,
and adding a network hop to demonstrate familiarity with one would be
architecture theatre.

## Components

```
                    ┌─────────────────────────────────────────┐
   GET /{code}      │  web                                    │
  ────────────────▶ │    RedirectController  (public)         │
                    │    LinkController      (X-API-Key)      │
   POST /api/links  │    StatsController     (X-API-Key)      │
  ────────────────▶ │    ProblemDetailHandler                 │
                    ├─────────────────────────────────────────┤
                    │  service                                │
                    │    LinkService      create / resolve    │
                    │    ClickRecorder    fail-open write     │
                    │    UrlValidator     scheme + host rules │
                    │    ShortCodeGenerator  (interface)      │
                    ├─────────────────────────────────────────┤
                    │  persistence                            │
                    │    LinkRepository       Spring Data JDBC│
                    │    ClickEventRepository                 │
                    └──────────────────┬──────────────────────┘
                                       │
                              ┌────────▼────────┐
                              │  PostgreSQL 16  │
                              │  Flyway-managed │
                              └─────────────────┘
```

Three layers, dependencies pointing inward only. `web` knows about `service`;
`service` knows about `persistence`; nothing points back out. `ShortCodeGenerator`
is an interface specifically so the collision-retry path can be tested by stubbing
it to return a known-duplicate code (T6) — the seam exists for testability, not
for anticipated variation.

## Data model

```sql
links
  id           bigserial     primary key
  short_code   varchar(16)   not null unique   -- UNIQUE is load-bearing; see P7
  destination  text          not null
  status       varchar(16)   not null default 'ACTIVE'
  created_at   timestamptz   not null default now()

click_events
  id           bigserial     primary key
  link_id      bigint        not null references links(id)
  occurred_at  timestamptz   not null default now()
  referrer     text          null
  user_agent   text          null
  client_ip    inet          null              -- dropped in V4; see A2, M1
```

`index on click_events(link_id, occurred_at)` — every analytics query is scoped
to one link over a time range.

Two deliberate absences: no `expires_at` (arrives in the V2 migration, brownfield
scenario) and no `owner_id` (no ownership model — S2). `status` ships unused so
that adding soft delete later is additive rather than a breaking migration.

## Control flow

**Create** — `POST /api/links`

```
API key check → validate URL (scheme allowlist, host rules, length)
  → generate code → INSERT
      ├─ success              → 201 { shortCode, shortUrl, destination }
      └─ unique violation     → regenerate, retry (max 3) → 500 if exhausted
```

**Redirect** — `GET /{code}`

```
lookup by short_code
  ├─ not found      → 404
  ├─ expired (V2+)  → 410
  └─ active         → record click ──┬─ ok     ─┐
                                     └─ throws ─┤ log WARN, continue
                                                ▼
                                     302 + Location + Cache-Control: no-store
```

The join between those two branches is the system's most important reliability
property: **the click write cannot fail the redirect.** A failed `INSERT` on
`click_events` produces a log line and an undercounted metric, never a failed
user redirect. This is asserted by a test that injects a repository failure and
asserts a 302 (T8) — it is not left to code inspection, because it is exactly the
kind of property a later refactor silently breaks by wrapping the whole handler
in one transaction.

Corollary: the click write must **not** share a transaction with the lookup.

## Resource limits and timeouts

Every wait in the request path is bounded. The reasoning is R6: "database down"
is the easy failure and R3 already answers it; "database slow" is the common one
and it is what actually takes services down. An unbounded query holds a
connection, connections exhaust the pool, and the redirect path stops serving
while liveness still reports healthy.

| Bound | Setting | Why |
| --- | --- | --- |
| Connection pool size | Explicit, sized against PostgreSQL `max_connections` | A default pool is an unexamined capacity decision. The pool is the real concurrency limit of the service |
| Connection acquisition | Hikari `connection-timeout` ≈ 2 s | Pool exhaustion must surface as a fast 503, not a hang |
| Redirect query | JDBC query timeout, low hundreds of ms | The redirect is the latency-critical path. A query that exceeds this is already a failed user experience; aborting frees the connection |
| Click insert | Same bound, independently applied | **This is what makes the fail-open real.** Catching an exception protects against the write *throwing*; it does nothing about the write being slow, and a slow insert adds its latency to every redirect. Fail-open without a timeout is not fail-open |
| Shutdown | `server.shutdown=graceful` + bounded shutdown phase | In-flight redirects drain on SIGTERM instead of being severed mid-response on deploy |

The pattern is that each bound converts a silent, compounding failure into a
fast and visible one. None of them make the service more available; they make it
fail in a way that is diagnosable and that an orchestrator can act on.

## Schema migration policy

Forward-only. Destructive changes follow expand/contract — the code that stops
using a column ships before the migration that removes it. Flyway Community has
no undo, so forward-only is the operative policy whether or not it is written
down, and a rollback plan resting on an unavailable feature is worse than none.

`V1` and `V2` are additive and safe. The privacy change is deliberately split
across release checkpoints: `V3` backfills the reduced referrer and user-agent
forms, its accompanying application release stops writing `client_ip`, and
`V4` is added only after that release is verified. Applying the drop while old
code still writes the column breaks the next insert — with Spring Data JDBC's
explicit column mapping, immediately.

Full reasoning and the irreversibility argument for `V4` are in M1 of
[`01-decisions.md`](01-decisions.md).

## Security decisions

Written against what this service actually does, rather than as a generic
checklist.

| Concern | Position |
| --- | --- |
| **Hostile destination schemes** | `javascript:` and `data:` destinations would make the redirect a stored-XSS delivery mechanism. Allowlist `http`/`https` at creation; everything else is a 400. This is the load-bearing control. |
| **Open-redirect abuse** | The service is, by definition, an open redirector — that is the product. The exposure is reputational (used to disguise a hostile destination behind a trusted-looking short link). Mitigation in scope is creation-time validation plus API-key gating on creation; reputation checking is out of budget (S4). |
| **Private / metadata address destinations** | Blocked at creation. **Precisely:** the service never fetches destinations, so classic SSRF does not apply to it. The exposure is (a) being used as a hop to bypass another system's egress filtering, and (b) any future preview/favicon feature turning this live. Blocking is cheap; unblocking later is a decision, unblocking by accident is an incident. |
| **Code enumerability** | Random 7-char base62, not sequential. Sequential codes would expose every destination in the system plus creation order and volume. See P7. |
| **PII in analytics** | The baseline carries known debt (raw IP, full referrer); discharged by `V3`/`V4` before delivery (A2, M1). A full referrer URL can itself be sensitive — it reveals the page the user came from. |
| **Secrets** | None in the repository. A fake key may exist only in the development profile; non-development startup fails unless the API key is supplied through the environment. |
| **Dependency supply chain** | Dependabot alerts and weekly updates, with GitHub dependency review rejecting newly introduced high-severity vulnerabilities. AI-suggested dependencies are still verified to exist and be maintained before being added — see `ai-log.md`. |
| **Error leakage** | RFC 7807 problem bodies only; no stack traces, no SQL, no internal identifiers in responses. |

## Considered and deferred

Recorded because the reasoning is worth more than the implementation would be at
this budget.

**Rate limiting.** The more interesting design of the two abuse controls, and the
one not being built. Sketch: a servlet `Filter` ahead of `LinkController` (ahead
of it, so rejected requests never reach the service layer or open a connection),
keyed on API key rather than IP (the key is the accountable identity; IP is
shared behind NAT and trivially rotated), token bucket held in a bounded
in-memory `Caffeine` map. Response: `429` with `Retry-After`.

Its instructive limitation is that in-memory state does not survive horizontal
scaling — with N instances the effective limit is N×, and fixing that means
shared state, which means Redis, which means a new failure mode on the write
path and an availability decision about what happens when the limiter is down
(fail-open and lose the control, or fail-closed and lose the service). That
trade is why it is deferred rather than half-built.

Testability note: the bucket must take an injected `Clock`, not
`System.currentTimeMillis()`, or the tests are forced to sleep.

**Redirect cache.** Deferred on principle — no cache without a measurement (R2).
Once expiry and soft delete exist, a cache hit can serve a link that should have
stopped resolving, so the invalidation path is the real work, not the lookup.

**Async click recording.** The fail-open synchronous write is correct until
redirect p95 degrades. The async version buys latency and costs at-least-once
delivery semantics, a bounded buffer, and a policy for buffer overflow.

**Custom aliases.** Deferred as a policy problem rather than a coding problem —
reserved words, impersonation, case-sensitivity, reuse after deletion (P3).

**Idempotent creation.** Needs a client-supplied `Idempotency-Key`; inferring
intent from URL equality is wrong (P6).

## Scaling notes

If this needed to scale, in order of what would actually bind first:

1. **Read path.** Redirects dominate by orders of magnitude. A read replica plus
   a short-TTL cache handles this well before anything structural is required.
2. **Click writes.** The first thing to fall over under a traffic spike, because
   every redirect is a write. Batching or an async queue, at the cost of
   at-least-once semantics.
3. **Code generation.** Random generation degrades as the keyspace fills —
   retry frequency climbs. At that point, lengthen the code rather than switch
   to sequential.
4. **Horizontal instances.** Stateless except for the rate limiter's in-memory
   bucket, which is exactly why that deferral matters.

None of this is built. It is recorded to show the design does not have a
structural barrier to it, not to imply readiness.
