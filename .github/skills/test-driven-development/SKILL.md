---
name: test-driven-development
description: >
  Apply strict Red-Green-Refactor discipline when implementing features,
  changing behavior, or fixing bugs. Write and run a failing behavioral test
  before production code, implement the minimum change, then refactor while
  tests remain green. Use whenever production code is about to be written.
---

# Test-Driven Development

Never write production behavior before observing a test fail for the expected
reason.

```text
RED      -> a failing test that describes the required behavior
GREEN    -> the minimum production code that makes that test pass
REFACTOR -> improve the implementation without changing behavior
```

## Establish the Test Interface

Before the first cycle:

1. Read the repository's build files and neighboring tests.
2. Reuse the existing test framework, naming, fixtures, and assertion style.
3. Prefer the repository's wrapper (`./mvnw` or `./gradlew`) when present.
4. Identify the smallest command that runs one test and the command that runs
   the complete relevant suite.
5. Do not install a new test runner when the repository already has one.

For this project, use Maven commands once `pom.xml` exists:

```bash
# One JUnit class
mvn -Dtest=LinkServiceTest test

# One JUnit method
mvn -Dtest='LinkServiceTest#whenCodeCollides_retriesWithNewCode' test

# Full quality gate
mvn verify
```

These are defaults, not overrides. If the Maven configuration defines separate
unit and integration-test phases, use its actual Surefire and Failsafe
selectors.

## Choose Test Scope Deliberately

| Phase | Scope | Question being answered |
| --- | --- | --- |
| RED | One test | Is the required behavior currently missing? |
| GREEN | The same test | Did the minimum implementation satisfy it? |
| REFACTOR | The same test plus directly affected tests | Did cleanup preserve behavior? |
| Slice checkpoint | Relevant package or module | Did the completed slice regress nearby behavior? |
| Final gate | `mvn verify` | Does the complete project meet its build and quality gates? |

Run a broader suite earlier when changing a shared contract, database schema,
public API, test fixture, or helper used by unrelated tests.

## RED: Prove the Behavior Is Missing

1. Translate one acceptance criterion into one behavioral test.
2. Name the test so the scenario and expected result are clear.
3. Run that test before implementing the behavior.
4. Inspect the failure. Proceed only when it fails because the behavior is
   missing.

A valid RED is normally an assertion failure that demonstrates the expected
behavior differs from the actual behavior.

These are not valid RED states:

- compilation failure caused by a malformed test or missing import;
- test discovery or configuration failure;
- unavailable database, Docker daemon, or other required fixture;
- an unexpected exception from broken setup;
- a mocked failure that cannot occur through the production path;
- a nonzero command exit with no understood test failure.

Fix the test or environment first, then run it again.

### When the Production Type Does Not Exist

A test may initially fail to compile because the class or method under design
does not exist. Create only the smallest signature needed to compile, returning
a neutral value or throwing `UnsupportedOperationException`. Then rerun the
test and observe an assertion failure before implementing behavior.

The compiling stub is part of RED. Business logic is not.

## GREEN: Implement Only What the Test Demands

Write the smallest production change that honestly makes the RED test pass.
Rerun the same test.

Do not add speculative:

- overloads or extension points;
- caching;
- logging unrelated to the behavior;
- generalized abstractions;
- error handling for cases not yet represented by a test;
- dependencies that the behavior does not require.

Do not silently modify a failing test to make it pass. If the expectation is
wrong, explain why and correct it as a deliberate test change before resuming
the cycle.

If GREEN requires an unplanned schema migration, dependency, API change, or
security decision, stop implementation and update the relevant decision and
task documentation first.

## REFACTOR: Improve Without Changing Behavior

With the target test green:

1. Remove duplication.
2. Improve names and make intent explicit.
3. Match the project's package boundaries and constructor-injection style.
4. Simplify control flow without weakening validation or error handling.
5. Rerun the target test after each material refactor.
6. Run the relevant package or module suite when the slice is complete.

Refactoring is complete only while the tests remain green. A changed assertion
means behavior changed and requires a new RED cycle.

## Add Edge Cases as Separate Cycles

After the primary happy-path cycle, add one cycle for each relevant boundary:

- null, blank, malformed, and oversized input;
- missing, expired, disabled, and conflicting resources;
- collision and concurrency behavior;
- timeout and dependency failure;
- authorization failure;
- migration compatibility;
- privacy-sensitive input;
- exact HTTP status, headers, and response body.

Do not combine unrelated behavior into one test merely to reduce test count.

## Bug Fixes: Reproduce Before Diagnosis

A bug fix begins with a regression test:

1. Convert the reported symptom into a focused test.
2. Run it and verify that it fails because the bug exists.
3. If it does not fail, do not guess at a fix. Reassess the reproduction,
   fixture, environment, and interpretation of the report.
4. Diagnose only after reproducing the symptom.
5. Implement the minimum correction.
6. Run the regression test, then the affected suite.
7. Keep the test permanently.

A compile error or mocked exception is not a reproduction of a runtime bug.

## Integration Tests Must Prove the Real Mechanism

Use a real dependency when the behavior depends on that dependency's semantics.
For this project, PostgreSQL-specific behavior must be tested through
Testcontainers rather than H2.

Examples:

- Prove short-code uniqueness with the PostgreSQL `UNIQUE` constraint.
- Force a collision through a deterministic `ShortCodeGenerator`, then exercise
  the real service and repository path.
- Verify that retry occurs in a fresh transaction, because PostgreSQL aborts a
  transaction after a constraint violation.
- Apply Flyway migrations to PostgreSQL and test compatibility with existing
  rows.
- Do not mock the constraint exception and claim that database collision
  handling has been proven.

Mocks are appropriate for testing orchestration after the underlying failure
mechanism has separate integration coverage.

## Anti-Patterns

| Anti-pattern | Why it hurts | Use instead |
| --- | --- | --- |
| Writing test and implementation together | RED is never observed, so the test may be incapable of detecting the defect | Write, run, and inspect the test first |
| Treating any failed command as RED | Configuration failures provide no evidence about behavior | Require an understood behavioral failure |
| Modifying the test until it passes | Converts a real failure into a false green | Fix production code or explicitly correct a faulty requirement |
| Testing private methods | Couples tests to implementation structure | Test through the public behavior |
| Instantiating dependencies inside methods | Prevents controlled substitution and focused testing | Use constructor injection |
| Testing only the happy path | Leaves production failure modes unverified | Add focused boundary and failure cycles |
| Shared mutable fixtures | Produces order-dependent tests | Create fresh state for each test |
| One test asserting unrelated behavior | The first failure hides later failures | Keep one behavior per test |
| Sleeping in concurrency or time tests | Produces slow, flaky tests | Inject a `Clock` and use synchronization primitives |
| Mocking the mechanism being proved | Demonstrates that the mock framework works, not production behavior | Exercise the real constraint, transaction, migration, or timeout |

## Evidence and Traceability

For material work in this assignment, record the cycle in
`docs/ai-log.md` or the relevant scenario document:

- acceptance criterion and test name;
- RED command and the reason the failure was valid;
- minimal GREEN implementation;
- refactoring performed;
- targeted and full-gate results;
- AI output adopted, edited, or rejected, with rationale.

Do not claim that a cycle occurred unless the command was actually run. Keep
full command output in the terminal or CI logs; documentation should contain a
concise, reviewable result rather than copied noise.

## Per-Feature Checklist

```text
RED
  [ ] One acceptance criterion selected
  [ ] Behavioral test written before production behavior
  [ ] Target test executed
  [ ] Failure observed and confirmed as behavior-related

GREEN
  [ ] Minimum implementation written
  [ ] Same target test passes
  [ ] Test was not weakened to obtain GREEN

REFACTOR
  [ ] Project conventions applied
  [ ] Target and directly affected tests remain green
  [ ] Edge cases added through separate cycles

SLICE
  [ ] Relevant package or module suite passes
  [ ] Schema/API/security decisions are documented

FINAL
  [ ] Every acceptance criterion maps to a named test
  [ ] `mvn verify` passes
  [ ] Relevant TDD and AI-assistance evidence is recorded
```

Coverage percentage is not the objective. Prefer tests with meaningful
assertions over tests that merely execute lines.
