# Pending Template Updates — Take-home (Senior Software Developer III)

The user-level working agreement (`~/.claude/CLAUDE.md`) still applies. This file adds the project
context and overrides the parts that do not fit a Java project. Always respond in English.

## Source of truth

- The brief is @docs/brief/BRIEF.md. If this file, a plan or the code conflicts with the brief,
  the brief wins: point out the conflict instead of resolving it silently.
- When a decision depends on the brief, quote the sentence it relies on.
- Never bring in requirements, numbers or constraints that are not in the brief (for example firm
  counts, budgets, "real time within seconds", version branches). If a number is needed, state it
  as an explicit assumption.

## What we are building

Caseware engagement files are created from a product template at a given version. Templates are
updated about once a week per product. Users must see, at a glance, which of their engagements
have a pending template update, and read a human-readable summary of the inbound changes so they
can apply or decline it. Several updates can accumulate before the user decides. Actually applying
template content is out of scope.

## Deliverables and limits (from the brief)

1. `docs/DESIGN.md`: 1–3 pages, exactly these sections: Target Architecture, Implementation Plan,
   Testing Strategy, Evaluation & Observability, Failure Modes & Tradeoffs.
2. `pending-updates-core/`: one small slice of the system. Focus on interfaces, contracts and
   correctness, with tests. "This is not a full application build."
3. Diagrams in `docs/diagrams/` (two at most: architecture and publish → visible indicator).
4. `docs/AI_USAGE_NOTES.md`: where AI helped, where I corrected or ignored it, how I would guide
   other engineers, and where AI should not be trusted in this domain.

The brief expects 2–3 hours and says "do not over-optimize" and "optimize for clarity and tradeoffs
over completeness". The level is evaluated on judgment, not on volume. Push back if a step makes the
deliverable bigger without making it clearer.

## Hard constraints from the brief

- Reading an engagement's template info requires loading it: about 1 minute per file, a hard
  constraint. Engagement storage is not queryable.
- Opening a firm's engagement list, and learning about a new publish, must never wait on a load.
- The template store is shared by all firms and knows nothing about engagements. Engagements live
  in customer-specific databases.
- Hooks or events can be added to any action of the template store or the engagement system
  (create, load, apply/decline decision, publish).
- A quick, reliable JSON diff between two versions of a template exists; users are non-technical
  and need a human-readable summary.
- Scale: hundreds of engagements per firm, many products, firms of very different sizes. One
  publish can affect every firm using that product at once.
- Observability must answer, without waiting for a user report: (1) how many engagements a new
  publish affects, (2) whether the pending check is running for every firm, (3) a firm's history
  of apply/decline decisions.
- Security: firm isolation must be a structural property, not an application-layer filter. Only
  users authorized for a firm can see or act on its pending updates. Data residency of the firm's
  engagement data must be respected.
- AWS shop.

## Current design decisions (change them only through /plan)

- **Pending is derived on read, never stored per engagement.** A publish updates one record per
  template (`latestPublishedVersion`), not one row per engagement.
- **Latest version per template** is template data, not firm data: one logical regional table
  (e.g. a DynamoDB global table, replicated to each region), fed by publish events. Writes are
  monotonic (max version), so duplicates and out-of-order events are harmless. A periodic resync
  from the template store repairs missed events, with a freshness metric per region. Status is
  computed in the app: firm bindings (isolated) + regional latest table; no cross-region call.
- **Per-engagement binding** (read model): `firmId`, `engagementId`, `templateId`,
  `appliedVersion`, `lastDeclinedVersion`, `revision`. It is captured from events the engagement
  system emits when it already has the engagement loaded (create, open, apply, decline).
- **Binding events are full snapshots** (`applied`, `lastDeclined`, `revision`, cause), not deltas:
  the highest revision wins. Deltas break when an older apply arrives after a newer decline. Same
  revision with different content is a producer bug: it throws in the slice; in production it goes
  to a DLQ with an alarm, not a consumer crash. A `lastDeclined <= applied` is normalized to "no
  decline" (the producer may not have cleared an old decline); a metric counts normalizations.
- **Versions** are positive integers, monotonic per `templateId` (linear, no branches).
- **Rule:** `pending = latest > max(applied, lastDeclined)`.
- **Accumulated updates produce ONE cumulative offer**, `applied → latest`. It is flagged
  "includes versions you declined" when `lastDeclined > applied`.
- **Apply** moves `applied` to the offered version and clears the decline. **Decline** records
  `lastDeclined = offered`. A newer version is offered again.
- **Idempotent, order-independent writes:** `applyIfNewer(revision)`. Duplicates and late events
  never move a binding backwards.
- **UI tri-state:** UPDATE_AVAILABLE, UP_TO_DATE, CHECKING (binding unknown or suspect). Nothing is
  shown as up to date without evidence.
- **Backfill** of existing engagements is the only path that pays the 1-minute load. It is
  asynchronous, capped by concurrency and resumable.
- **Summaries** are computed once per `(templateId, from, to)` and shared across firms, because
  template content is not firm data. The primary view is the net diff `applied → latest` (what
  the user is deciding on); a collapsed "what each version brought" breakdown is secondary.
  Tradeoff: an intermediate version cannot be taken without the later ones. A deterministic
  change list rendered from the JSON diff is the authority. An optional LLM narrative (design doc
  only, not in the slice) may only restate cited change ids; if validation fails, only the list
  is shown.
- **Firm isolation:** preferred: bindings and the decision log live in the firm's existing
  customer-specific database, inheriting structural isolation and residency with no new store of
  client data (to confirm with the owning team). Fallback: one shared database per residency
  region with Postgres RLS (`FORCE ROW LEVEL SECURITY`, a non-owner role without `BYPASSRLS`,
  `firm_id` from the token set via `SET LOCAL` per transaction). Residency is mandatory for every
  firm through regional placement; a dedicated database is for very large firms, for performance
  only. Every repository call requires a `FirmScope` derived from the authenticated token, never
  from the request body. Viewing and deciding are separate permissions (design doc only, not in
  the slice).
- **Observability:**
    - Obs 1: on every publish, each firm consumer reports counts only (no engagement ids):
      `affected` = bindings on that template with `max(applied, lastDeclined) < newVersion`, and
      `checking` (unknown bindings) separately. It reports even when both are zero.
    - Obs 2: that per-publish report is the heartbeat; a firm that does not report raises a
      silent-firm alarm. Lag is producer outbox position vs. consumer position per firm, so a quiet
      firm is not a false alarm. Backfill coverage is a gauge. A small daily audit-load sample
      (projection vs. loaded engagement) is a correctness signal (design doc only).
- **Decision log (Obs 3):** append-only, per firm. Each record stores the summary id and the hash
  of the text the user saw. Design doc only, not in the slice.
- **Assumptions (state them in the design doc):** A1 the engagement system emits a monotonic
  revision per engagement; A2 the engagement stores `lastDeclined` beside template id and version
  (added via hooks); A3 an engagement's `templateId` never changes; A4 a firm's engagement ids can
  be listed without loading engagements.

## Out of scope — do not build

Applying template content. Version branches, DAGs or withdrawn versions (one line in the design
as a future extension). Adaptive (AIMD) limiter, per-firm fairness scheduler, permit quarantine.
Spring or any framework. Real AWS adapters. Demo applications. Custom mutation or test harnesses.
If a change needs one of these, stop and ask.

## Code (overrides the TypeScript-specific rules)

- **Stack:** Java, compiled with `maven.compiler.release` 21 (developed on JDK 27; must build on
  JDK 21+). Maven. JUnit 5 and AssertJ for tests. No runtime dependencies.
- **Layout:** module `pending-updates-core/`, base package `com.example.pendingupdates`:
    - `domain` — pure rules, no I/O, no clock.
    - `application` — use cases and event handlers.
    - `port` — interfaces, with their contracts in Javadoc.
    - `adapter.inmemory` — reference implementations used by the tests.
- **Conventions:**
    - Use records for values and sealed interfaces for outcomes, handled with an exhaustive `switch`.
    - No `null` in the domain: validate in compact constructors and use `Optional` for absence.
    - Inject `Clock` and other dependencies; there is no static state.
    - Errors are domain exceptions or sealed results; never swallow them.
- **Tests:**
    - Name tests after the business behaviour, e.g.
      `declinedUpdateIsOfferedAgainWhenANewerVersionIsPublished`.
    - Write table-driven tests for the pending rule and given/when/then structure elsewhere.
    - Tests must be deterministic: no sleeps and no real clock.
- **Size target:** roughly 600–900 lines including tests. Flag it if we drift beyond that.

### Commands (Windows-friendly; run from the repo root)

| Purpose | Command |
|---|---|
| Compile, typecheck and lint (`-Xlint:all -Werror`) | `mvn -f pending-updates-core/pom.xml -q compile` |
| All tests | `mvn -f pending-updates-core/pom.xml test` |
| One test class | `mvn -f pending-updates-core/pom.xml test -Dtest=ClassName` |
| One test method | `mvn -f pending-updates-core/pom.xml test -Dtest=ClassName#method` |

There is no separate lint or typecheck step: compilation with `-Xlint:all -Werror` is that step.

## How the slash commands apply here

- `/explore` — also check the code and docs against BRIEF.md and report any requirement that is
  not yet covered.
- `/plan` — tie every step to a brief requirement or a decision above. In this project, "tenancy"
  means firm isolation. Include the size impact of the plan.
- `/implement` — test first. Work in one small step at a time and wait for my "continue".
- `/review`, `code-reviewer` — also flag over-engineering against the brief: anything not needed
  by the brief or the decisions above.
- `/verify` — use the Maven commands above, not npm.
- `/summary` — write it so I can say it aloud in the live design review.

## Learning mode

I will defend every line of this project live, in English, in a design review. Therefore:

- Explain the *why* of each decision and the alternative you rejected, in two or three sentences.
- Never generate more than the step asked for.
- When something is a judgment call, say so. Tell me which interview question it is likely to
  trigger.
- Keep a running list of where I accepted, corrected or rejected your suggestions, so I can write
  `docs/AI_USAGE_NOTES.md` honestly.