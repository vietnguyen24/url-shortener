## Code Review Report

### Summary
T10 adds a small, well-scoped `OncePerRequestFilter` that guards `/api/**` with a
constant-time `X-API-Key` check, configured via `security.api-key` with an
overridable dev default, and updates the affected management-endpoint tests to
supply the key. The implementation matches the plan's scope (T6, T9 dependency
preserved, no T11 problem-detail work attempted) and is otherwise clean; two
non-blocking issues were found around error-response consistency and log
ordering.

### Findings
| ID | Severity | File | Line | Issue | Recommendation | Status |
| --- | --- | --- | --- | --- | --- | --- |
| CR-01 | Medium | src/main/java/com/vietnguyen/urlshortener/web/ApiKeyFilter.java:38 | 38 | A 401 from this filter is produced by `response.sendError(...)`, which is routed through Spring Boot's default `BasicErrorController` and returns `application/json` (timestamp/status/error/path), not the `application/problem+json` RFC 7807 body every other error path on the same `/api/**` surface returns (per T11/`GlobalExceptionHandler`). No test in `ApiKeyFilterTest` asserts the response content type or body, so this inconsistency is untested and will surprise API consumers who rely on a uniform problem-details contract for all `/api/**` errors. | Either write the 401 body through the same problem-details writer used by `GlobalExceptionHandler` (e.g., have the filter build an `application/problem+json` body directly, since a servlet filter runs outside `@ControllerAdvice`), or explicitly document/test the intentional exception to the RFC 7807 contract for authentication failures. | Resolved (pass 2) |
| CR-02 | Low | docs/ai-log.md:480 | 480 | The T10 entry is numbered `AI-021` but is placed before the `AI-020` (T14) entry, breaking the log's established ascending, chronological numbering convention (visible everywhere else in the file, e.g. AI-007…AI-019). This makes the log harder to scan and could mislead a future reader about execution order. | Reorder the two new entries so `AI-020` precedes `AI-021`, or renumber to match actual completion order. | Resolved (pass 2) |
| CR-03 | Low | docs/ai-log.md:517-524 | 517 | (Found during CR-02 re-verification.) The reviewer observed a possible misattribution of the T14 remediation note after reordering. | Current-tree inspection confirms the note is under AI-020 and the T10 note is under AI-021; no code or documentation change remains. | Resolved (current-tree verification) |

### Simplifications proposed
None.

### Passed checks
- **Layering**: `ApiKeyFilter` is a plain servlet filter with no controller/service knowledge; it depends only on the configured key, preserving the web-layer boundary (checklist §1).
- **Dependency injection**: the key is supplied through the constructor via `@Value`, not read from a static/global source; no repository, clock, or client is instantiated inside the filter (checklist §2).
- **Scope discipline**: `shouldNotFilter` restricts protection to `/api/**`, leaving `/{code}` redirects and `/actuator/health` public, matching the T10 acceptance criteria and the T6/T9 dependency without touching T11's problem-detail work (checklist per task plan, docs/02-tasks.md T10 row).
- **Security-adjacent design choice**: uses `MessageDigest.isEqual` for a constant-time comparison rather than `String.equals`, called out and justified in `docs/ai-log.md` (AI-021).
- **Configuration**: the API key is externalized via `${API_KEY:dev-key-not-a-secret}` following the same `${ENV:default}` convention already used for datasource settings in `application.yml`, and the fallback is clearly named/documented as non-secret in the README.
- **Test quality**: `ApiKeyFilterTest` covers missing key, invalid key, valid key (with a real outcome assertion plus a `never()` interaction check for the negative case), redirect remaining public, and health remaining public — each acceptance criterion has a corresponding test. The updated `LinkControllerTest`, `LinkStatsControllerTest`, and `GlobalExceptionHandlerTest` correctly add the header rather than disabling or weakening any existing assertions.
- **No scope creep**: no T11 (problem-detail) or T14 (demo script) logic was implemented inside the reviewed T10 files; those changes are correctly isolated to their own files/commits.

### Verdict rationale
No Critical or High findings were identified; the two findings are Medium and
Low respectively, so the gate result is PASS.

---

## Re-verification (pass 2)

**Scope:** `ApiKeyFilter.java`, `ApiKeyFilterTest.java`, and `docs/ai-log.md`
only, per the approved remediation of CR-01 and CR-02. Compared the current
tree against base `66fafe8` (the prior task-10 tree was not committed
separately, so the base diff carries the full T10 change set including the
remediation).

### CR-01 — Fixed
`ApiKeyFilter.doFilterInternal` (src/main/java/.../web/ApiKeyFilter.java:38-46)
now sets `response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE)`
and writes a deterministic RFC 7807 body (`type`, `title`, `status`, `detail`)
instead of calling `sendError`. `ApiKeyFilterTest.rejectsMissingApiKeyOnManagementEndpoint`
(src/test/java/.../web/ApiKeyFilterTest.java:47-53) now asserts
`content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)` and
`jsonPath("$.title").value("Unauthorized")`, giving the previously-missing
content-type/body coverage. The 401 response is now consistent with the rest
of the `/api/**` surface. **Resolved.**

### CR-02 — Fixed
`docs/ai-log.md` now places `AI-020` (T14, line 480) before `AI-021` (T10,
line 502), restoring ascending order as recommended. However, the reordering
also relocated the wrong remediation note: the "Review remediation" paragraph
at `docs/ai-log.md:517-524` — which is about a demo-script readiness timeout
(`DEMO_HEALTH_TIMEOUT_SECONDS`) and a README source-of-truth comment for the
dev-key default — describes CR-01/CR-02 fixes for **T14's** entry, but it is
appended under **AI-021 (T10 API-key)** instead of under AI-020 (T14). AI-021
now carries two unrelated "Review remediation" paragraphs back to back
(lines 509-512 for the true T10 CR-01/CR-02 fix, then lines 517-524 for T14's
unrelated CR-01/CR-02 fix), while AI-020 has no remediation note at all. This
is a new defect surfaced by the same fix that closed CR-02: the entry is now
correctly ordered but its content is misattributed, which is exactly the kind
of traceability confusion CR-02 was raised to prevent (a reader tracing "why
did T10 change after review" will land on T14's timeout/README rationale).

**New finding (CR-03, Low):** `docs/ai-log.md:517-524` — the second "Review
remediation" paragraph under `### AI-021 · ... · API-key management boundary`
documents T14 (demo script) remediation, not T10. Recommend moving that
paragraph under the `AI-020` entry (T14) so each entry's remediation note
matches its own task.

### CR-03 — Resolved
Current-tree inspection confirms the T14 remediation paragraph is under
`AI-020` and the T10 remediation paragraph is under `AI-021`; the reported
misattribution is not present in the final tree.

### Updated verdict
CR-01, CR-02, and CR-03 are resolved. No Critical, High, Medium, or Low
findings are open; verdict remains PASS.
