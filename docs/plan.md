# Implementation plan — Part 2 slice (approved)

Decisions live in `CLAUDE.md` ("Current design decisions"). This file holds the step list and the
test list. Steps 2–6 are test-first; pause for "continue" after each one, and verify each with
`mvn -f pending-updates-core/pom.xml test -Dtest=<Class>`.

## What the slice proves

1. The brief's "multiple template updates accumulate" and "up-to-date manner" hold when binding
   events arrive duplicated or out of order.
2. Obs 1/2 (affected count, with zero as a heartbeat) and firm isolation work as code contracts.

## Answers to the open questions

- **A. Event shape:** full snapshot per revision; the highest revision wins. Delta events break
  when an older `Applied` arrives after a newer `Declined`.
- **B. Same revision, different content:** throw `ConflictingRevision` (a producer bug). In
  production it goes to a DLQ with an alarm.
- **C. git:** initialized, so `/review` and `code-reviewer` have a diff.
- **D. View/decide permissions:** design doc only, not in the slice.
- **Adjustment 1:** a snapshot with `lastDeclined <= applied` is normalized to "no decline", and a
  test covers it.
- **Adjustment 2:** the decision log (Obs 3) is design doc only.

## Steps

0. **CLAUDE.md** — "Current design decisions" section updated. *(done)*
1. **Scaffold**
    - `pom.xml` and `src/` are under `pending-updates-core/`.
    - The sample `Main.java` is removed.
    - The settings file is named `.claude/settings.json`, lowercase.
    - Check: `mvn -f pending-updates-core/pom.xml -q compile` passes.
2. **Value types** — `domain/{Version, FirmId, EngagementId, TemplateId, FirmScope}`, plus
   `ValuesTest`.
3. **Pending rule** — `domain/{PendingStatus, PendingRule}`, plus a table-driven `PendingRuleTest`.
4. **Binding updates** — `domain/{BindingSnapshot, EngagementBinding, BindingUpdate}`, plus
   `EngagementBindingTest`.
5. **Repository**
    - `port/BindingRepository`
    - `adapter/inmemory/InMemoryBindingRepository`
    - An abstract `BindingRepositoryContract` test, reusable by a future Postgres adapter, plus
      `InMemoryBindingRepositoryTest`.
6. **Affected count**
    - `port/{EngagementDirectory, AffectedCountSink}`
    - `application/AffectedCountOnPublish`
    - `adapter/inmemory/InMemoryEngagementDirectory`
    - Plus `AffectedCountOnPublishTest`.
7. **Full run**
    - `mvn -f pending-updates-core/pom.xml test`
    - Size check: about 750 lines is the target; stay in the 600–900 range.

## Design of the slice

### Domain (pure, no I/O)

- **Value types.** `Version` is a positive, comparable integer. `FirmId`, `EngagementId` and
  `TemplateId` are ID records. `FirmScope` holds a `FirmId`.
- **`BindingSnapshot`** is the event. It carries firm, engagement, template, `applied`,
  `lastDeclined`, `revision` and the cause.
- **`EngagementBinding.applyIfNewer(Optional<current>, snapshot)`**, a pure function (adapters
  only persist its result atomically), returns a sealed `BindingUpdate`:
    - `Created(binding)` or `Changed(binding)`
    - `Ignored(DUPLICATE | STALE)`
    - It throws a `RejectedSnapshot` (conflicting revision or template mismatch): DLQ in production.
- **`PendingRule.evaluate(Optional<binding>, Optional<latest>)`** returns a sealed `PendingStatus`:
    - `UpdateAvailable(from, to, includesDeclinedVersions)`
    - `UpToDate`
    - `Checking(NO_BINDING | LATEST_UNKNOWN | LATEST_BEHIND)`

  `LATEST_BEHIND` means `max(applied, lastDeclined) > latest`: the latest-version table is lagging, so the rule
  refuses to show "up to date" without evidence.

### Ports (contracts in Javadoc)

- **`BindingRepository`**
    - Operations: `find(scope, engagementId)`, `upsertIfNewer(scope, snapshot)`,
      `findByTemplate(scope, templateId)`.
    - The compare-and-write is atomic.
    - Rows are keyed by (firm, engagement).
    - A snapshot whose firm differs from the scope's firm is rejected.
    - Results are bounded per firm.
- **`EngagementDirectory`** lists a firm's engagement IDs without loading the engagements
  (assumption A4).
- **`AffectedCountSink.report(AffectedCountReport)`**. The report holds counts only: firm,
  template, version, publishId, affected, checking.

### Application

`AffectedCountOnPublish.handle(scope, TemplatePublished)`:

- "Affected" reuses `PendingRule` with `latest = newVersion`, which matches the definition
  `max(applied, lastDeclined) < newVersion`.
- Engagements with no binding count as `checking`.
- It always reports, including 0/0, so the report doubles as the silent-firm heartbeat.

### Rejected alternative

A pending flag stored on each engagement and written on every publish. Reads become trivial, but
every publish fans out to every engagement in every firm. That contradicts "a publish updates one
record per template".

## Tests

### PendingRuleTest (table-driven)

- No binding, or latest unknown → CHECKING
- `applied > latest` → CHECKING (LATEST_BEHIND)
- `applied = latest` → up to date
- `applied 2`, `latest 5` → offer 2→5
- `applied 2`, `declined 3`, `latest 5` → offer 2→5, flagged "includes versions you declined"
- `declined 5`, `latest 5` → up to date
- `declinedUpdateIsOfferedAgainWhenANewerVersionIsPublished` (declined 5, latest 6)

### EngagementBindingTest

- The first snapshot creates the binding.
- An identical duplicate is ignored as DUPLICATE.
- An older revision is ignored as STALE, and the state is unchanged.
- The same revision with different content throws `ConflictingRevision`.
- An apply arriving after a newer decline leaves the state correct (the bug behind question A).
- Delivering {create, apply, decline} in any order ends in the same state.
- `lastDeclined <= applied` is normalized to "no decline".
- A template mismatch throws.

### BindingRepositoryContract

- A stale upsert is ignored.
- `firmAScopeNeverReadsFirmBRows`
- The same engagement ID in two firms gives two independent rows.
- A firm-A snapshot written under firm B's scope is rejected, and nothing is stored.
- `findByTemplate` returns only that firm's engagements on that template.

### AffectedCountOnPublishTest

- Mixed firm: affected, up to date, declined, other template, no binding (counted as checking).
- An empty firm reports 0/0.
- A redelivered publish reports the same counts.
- The report type carries no engagement IDs.

## Risks to state in the design doc

- **Contracts:** `BindingSnapshot` is a new event schema between systems, and the revision
  assumption (A1) must be confirmed with the engagement-system team.
- **Security:**
    - The isolation test proves the port contract, not database isolation. Database isolation is
      proven by an integration test against Postgres RLS (Testcontainers).
    - `FirmScope` is a plain record here; deriving it from the token happens at the edge.
- **Checking counts:** an engagement with no binding counts as checking on every publish, because
  its template is unknown. This overstates checking until the backfill finishes, which is
  acceptable but needs one line in the design doc.
- **Performance:** `findByTemplate` returns a list, which is fine for hundreds of engagements per
  firm.