# Pending Template Updates — Design

Pending is derived on read, never stored per engagement. A publish writes one record per template,
its latest version, and the engagement list computes each status from that record and the firm's
own bindings: `pending = latest > max(applied, lastDeclined)`. Bindings come from events the
engagement system emits when it already has an engagement loaded, so no user-facing path pays the
1-minute load; only the backfill of existing engagements and a small audit sample do.

## 1 Target Architecture

Diagrams: [diagrams.md](diagrams.md).

| Component | Status | Role |
|---|---|---|
| Template store | existing | New publish hook emits `TemplatePublished`. |
| Latest-version table | new | One row per template; global table, replicated per region; keeps the max version. |
| Summary service | new | Renders each `(template, from, to)` diff once, for all firms. |
| Engagement system | existing | New hooks emit a binding snapshot on create, open, apply and decline. |
| Binding consumer | new | Per firm; keeps the highest revision. |
| Bindings, decision log | new tables, existing firm database | This feature's firm state. |
| Engagement list API | extended | Derives each status on read. |
| Affected-count consumer | new | Per firm, on every publish; counts only. |

| Data | Source of truth | Lives in | Confidential |
|---|---|---|---|
| Template content, summaries | Template store | Global template plane | No |
| Latest version per template | Template store | Regional replica | No |
| Binding, decision log | Engagement system | Firm database, firm region | Yes |
| Affected counts | Binding projection | Metrics, firm region | Counts only, no IDs |

Status is UPDATE_AVAILABLE, UP_TO_DATE or CHECKING. CHECKING means the binding is unknown or the
latest table is behind: nothing is shown as up to date without evidence.

**Accumulated updates.** When "multiple template updates accumulate", the user gets one cumulative
offer, `applied → latest`. Apply clears any decline; decline records `lastDeclined`, and a newer
version is offered again. An offer that skips a declined version says "includes versions you
declined". The primary view is the net diff, because that is what the user decides on; what each
version brought is a collapsed secondary list.

**Summaries.** "The users are non-technical." A deterministic change list rendered from the JSON
diff is the authority. An optional LLM narrative may only restate changes by citing their IDs; a
validation gate rejects any uncited sentence. If the gate fails or the model is down, the user
sees the list only. Summaries hold no firm data.

**Security.** Isolation must be "a structural property (not just an application-layer filter)".
Preferred: bindings and the decision log live in the firm's existing customer-specific database,
inheriting its isolation and residency (to confirm with the owning team). Fallback: a shared
database per residency region with Postgres RLS (`FORCE ROW LEVEL SECURITY`, a role without
`BYPASSRLS`, `firm_id` set with `SET LOCAL`). Every repository call takes a `FirmScope` built
from the token, never from the request body. Viewing and deciding are separate permissions.
Firm data never leaves its region; only template data is global.

## 2 Implementation Plan

Each phase is additive and has its own flag. Turning a flag off reverts that phase; no existing
behaviour changes before phase 4.

| Phase | What turns on | Exit check |
|---|---|---|
| 1 Hooks dark | Publish and snapshot hooks emit; consumers fill bindings and the latest table. | Events arrive for every enabled firm. |
| 2 Backfill | Each existing engagement is loaded once to emit its snapshot. | Coverage gauge is complete. |
| 3 Shadow | Status and affected counts are computed, not shown, and checked against audit loads. | Audit sample matches. |
| 4 Enable per region | Status and summaries are shown, one region at a time. | Silent-firm and freshness alarms stay quiet. |

**Backfill** is the only bulk path that pays the 1-minute load. It is asynchronous, capped by a
fixed concurrency limit (an assumed value, set with the engagement-system team), pausable, and
resumable per firm. It walks the firm's engagement IDs (A4) and skips those that already have a
binding.

**`lastDeclined` (A2)** is not stored on engagements today. The decline hook adds it in phase 1.
Earlier declines carry no version, so they backfill as "no decline" and are offered once more.

## 3 Testing Strategy

| Level | What it proves |
|---|---|
| Domain unit (slice) | The pending rule, table-driven; snapshots in any order, duplicates, conflicting revisions, decline normalization. |
| Port contract (slice) | Atomic read-decide-write and firm isolation at the port. Every adapter runs the same suite. |
| RLS integration (Testcontainers) | Database isolation: a session scoped to one firm cannot read or write another firm's rows. |
| Event contract, with the engagement-system team | The snapshot schema and a monotonic revision per engagement (A1). |
| Shadow mode | The projection matches loaded engagements on real data. |
| Summary golden set | Known diffs render to reviewed summaries; narrative gates reject bad output. |

The slice in `pending-updates-core/` proves the contracts: ordering, idempotency, the cumulative
offer and isolation at the port. Database isolation is a separate claim, proven only by the RLS
integration test.

## 4 Evaluation & Observability

The brief asks for three answers "without waiting for a user to report it".

**How many engagements a publish affects.** On every publish, each firm's consumer reports two
counts with the publish ID: `affected` (bindings on that template with
`max(applied, lastDeclined) < newVersion`) and `checking` (no binding yet). No engagement IDs
leave the firm.

**Whether the check runs for every firm.** That report is also the heartbeat: every firm reports,
even 0/0. An enabled firm that does not report raises a silent-firm alarm. Lag is the producer's
outbox position against each firm consumer's position, so a quiet firm is not a false alarm.
Backfill coverage is a gauge per firm. A small scheduled audit sample loads engagements and
compares them with the projection. Decline normalizations are counted: a rising count means the
producer is not clearing declines.

**A firm's decision history.** An append-only log in the firm's database: engagement, offer,
apply or decline, user, time, summary ID and a hash of the text the user saw. The hash ties
the decision to the exact text shown.

**Summary quality.** A golden set of real diffs with reviewed summaries runs on every renderer,
prompt or model change. Production narratives are citation-checked; the failure rate is a metric.
A human review sample covers shown narratives. Model and prompt versions are pinned and stored
with each summary.

**Freshness.** The latest-version table has a freshness metric per region, against the template
store. The periodic resync repairs missed events; a gap that persists raises an alarm.

## 5 Failure Modes & Tradeoffs

| Failure | Detection | User sees | Recovery |
|---|---|---|---|
| Lost, duplicate or out-of-order event | Lag, audit sample | Nothing; a loss leaves a stale status | Highest revision wins; resync or next snapshot |
| Conflicting revision | DLQ alarm | Last good state | Fix producer, replay DLQ |
| Latest table lagging | Freshness metric | Late offer; CHECKING if binding is ahead | Resync |
| Projection lost | Coverage gauge | CHECKING | Re-run backfill |
| LLM down or failing gates | Gate failure rate | Change list only | None needed |
| Backfill overloads engagement system | Its load metrics | Nothing; backfill is async | Pause or lower the cap |
| Silent firm | Heartbeat alarm | Stale status in that firm | Restart consumer; lag shows backlog |

**Tradeoffs.**

- **Derived on read**, not a pending flag per engagement: a flag makes every publish write to
  every engagement in every firm.
- **Snapshot events**, not deltas: deltas break when an older apply arrives after a newer decline.
  Cost: larger events.
- **One cumulative offer**, not one per version: one decision instead of many, but no partial take.
- **The firm's own database**, not a new shared store: isolation and residency are inherited, but
  it needs the owning team's agreement and allows no cross-firm queries.

| Assumption | If false |
|---|---|
| A1 Monotonic revision per engagement | Snapshots cannot be ordered; another per-engagement sequence is needed. |
| A2 Engagement stores `lastDeclined` | Declines live only in the projection; losing it re-offers them. |
| A3 `templateId` never changes | A change needs a new binding; today it is rejected to the DLQ. |
| A4 Engagement IDs listable without a load | Backfill cannot find engagements, and coverage cannot be measured. |

**Cut first:** the LLM narrative, then the per-version breakdown. The status and the change list
stay.

**Out of scope:** applying template content; adaptive throttling. Version branches or withdrawn
versions are a future extension: the rule would compare within a branch.
