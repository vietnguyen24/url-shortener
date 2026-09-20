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
  none; the dev API key is supplied via environment variable with an
  obviously-fake default.
- **No proprietary or employer-owned code in prompts.** All code here is written
  for this assignment.
- **Every AI-suggested dependency is verified before it is added:** that the
  package exists under exactly that coordinate, that it is actively maintained,
  and that it has no unresolved critical CVEs. AI-hallucinated package names are
  a live supply-chain attack vector — an attacker who registers a plausible
  hallucinated name gets code execution in any build that trusts the suggestion.
  OWASP Dependency-Check (T2) is the automated backstop, not the primary control.
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

---

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
