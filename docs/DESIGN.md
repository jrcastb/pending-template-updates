# Pending Template Updates — Design

Pending is derived on read, never stored per engagement: `pending = latest > max(applied,
lastDeclined)`. A publish writes one latest-version record per template; each firm's bindings come
from events the engagement system emits while an engagement is already loaded. No user-facing
path pays the 1-minute load; only the backfill and an audit sample do.

## 1 Target Architecture

| Component | Status | Role |
|---|---|---|
| Template store | existing | Publish hook with outbox emits `TemplatePublished`. |
| Summary service | new | Renders each `(template, from, to)` diff once, for all firms. |
| Engagement system | existing | Hooks with outbox emit snapshots (create, open, apply, decline) and archive. |
| Regional queue | new | SQS with DLQ, for publish and engagement events. |
| Tracking service | new | Event consumer, affected-count job, Pending-updates API. |
| Tracking DB | new | Aurora Postgres, one schema per firm: bindings, engagement registry, decision log. |
| Latest-version table | new | Max version per template; firm roles read, a template-writer role writes. |
| Engagement list API | extended | Forwards the user token; the Pending-updates API validates it and checks permission. |

| Data | Source of truth | Lives in | Confidential |
|---|---|---|---|
| Template content, summaries | Template store | Global template plane | No |
| Latest version per template | Template store | Regional table | No |
| Bindings, registry, decision log | Engagement system | Firm's schema, firm region | Yes |
| Affected counts | Bindings | Metrics, firm region | Counts only, no IDs |

The consumer keeps the highest revision; a snapshot above any tombstone registers an unknown
engagement. Archive removes the registry row and binding, leaving a tombstone at that revision:
older snapshots are ignored.

Status is UPDATE_AVAILABLE, UP_TO_DATE or CHECKING. CHECKING means the binding is unknown or the
latest table is behind: nothing is shown as up to date without evidence.

**Accumulated updates.** When "multiple template updates accumulate", the user gets one cumulative
offer, `applied → latest`. Apply clears any decline; decline records `lastDeclined`; a newer
version is offered again, flagged "includes versions you declined". The net diff, what the user
decides on, is primary; each version's changes are a collapsed list.

**Summaries.** "The users are non-technical." A deterministic change list from the JSON diff is
the authority. An optional LLM narrative may only restate changes by citing their IDs; a gate
rejects uncited sentences; on failure, only the list is shown. The summary request carries
template IDs and versions only; the list never waits on it.

**Security.** Isolation must be "a structural property (not just an application-layer filter)".
Each firm's schema is owned by a per-firm role; the service role has no direct grants and can only
assume firm roles. Every transaction runs `SET LOCAL ROLE` for one firm: it reaches only that
firm's schema, no table mixes firms, and a missing role switch fails closed. Choosing the role,
from the token or the verified producer, is the one app-layer step, tested and audited. Rejected:
RLS, where one missed policy exposes every firm. The consumer accepts engagement events only from
the engagement system's role, which sets `firmId` from its own context. Rejected: a global
`engagementId → firmId` check, a cross-firm structure. `FirmScope` on every repository call guards
against code bugs. Viewing and deciding are separate permissions; firm data never leaves its region.

## 2 Implementation Plan

Each phase is additive behind its own flag. No existing behaviour changes before phase 4.

| Phase | What turns on | Exit check |
|---|---|---|
| 1 Hooks dark | Hooks emit; the consumer fills bindings, registry and latest table. | Events arrive for every enabled firm. |
| 2 Backfill | Each existing engagement is loaded once to emit its snapshot. | Coverage gauge is complete. |
| 3 Shadow | Status and counts are computed, not shown, and checked against audit loads. | Audit sample matches. |
| 4 Enable per region | Status and summaries shown, region by region. | Silent-firm and freshness alarms stay quiet. |

**Backfill** is the only bulk path that pays the 1-minute load: asynchronous, capped by a fixed
concurrency limit (assumed; set with the engagement-system team) and resumable per firm. It lists
the engagement IDs once (A4) to seed the registry, skipping tombstones and those with a binding.

**`lastDeclined` (A2)** is not stored today; the decline hook adds it in phase 1. Earlier declines
carry no version, so they are offered once more.

## 3 Testing Strategy

| Level | What it proves |
|---|---|
| Domain unit (slice) | The pending rule, table-driven; snapshots in any order, duplicates, conflicting revisions, decline normalization. |
| Port contract (slice) | Atomic read-decide-write and firm isolation at the port; one suite for every adapter. |
| Per-firm role isolation (Testcontainers) | A transaction as one firm's role cannot touch another firm's schema. |
| Event contract (engagement-system team) | Snapshot schema and a monotonic revision per engagement (A1). |
| Shadow mode | The projection matches loaded engagements on real data. |
| Summary golden set | Known diffs render to reviewed summaries; gates reject bad narratives. |

The slice proves ordering, idempotency, the cumulative offer and port isolation; only the role
isolation test proves database isolation.

## 4 Evaluation & Observability

The brief asks for three answers "without waiting for a user to report it".

**How many engagements a publish affects.** On every publish, the affected-count job reports
per enabled firm, with the publish ID, `affected` (bindings on that template with
`max(applied, lastDeclined) < newVersion`) and `checking` (registered, no binding yet). No
engagement IDs leave the firm.

**Whether the check runs for every firm.** That report is also the heartbeat: every enabled firm
reports, even 0/0; a missing report raises a silent-firm alarm. Lag is each firm's newest outbox
event against the newest applied, plus queue oldest-message age.
Backfill coverage is a per-firm gauge. An audit sample loads engagements to check the
projection. Rising decline normalizations mean the producer is not clearing declines.

**A firm's decision history.** An append-only log in the firm's schema: engagement, offer,
apply or decline, user, time, summary ID and a hash of the exact text the user saw.

**Summary quality.** A golden set of reviewed summaries runs on every renderer, prompt or model
change. Production narratives are citation-checked (failure rate is a metric) and sampled for
human review. Model and prompt versions are pinned per summary.

**Freshness.** A per-region metric compares the latest-version table with the template store; the
periodic resync repairs missed events, and a persistent gap alarms.

## 5 Failure Modes & Tradeoffs

| Failure | Detection | User sees | Recovery |
|---|---|---|---|
| Lost, duplicate or out-of-order event | Lag, audit sample | A loss leaves a stale status | Highest revision wins; resync or next snapshot |
| Conflicting revision | DLQ alarm | Last good state | Fix producer, replay DLQ |
| Latest table lagging | Freshness metric | Late offer; CHECKING if binding is ahead | Resync |
| Projection lost | Coverage gauge | CHECKING | Re-run backfill |
| LLM down or failing gates | Gate failure rate | Change list only | None needed |
| Backfill overloads engagement system | Its load metrics | Nothing | Pause or lower the cap |
| Silent firm | Heartbeat alarm | Stale status | Fix that firm's schema or role |
| Cross-firm access (code bug) | `FirmMismatch` or role-denial alarm; tests, audit | Nothing, or another firm's data if the role is wrong | DLQ; fix the bug |

**Tradeoffs.**

- **Derived on read**, not a pending flag per engagement: a flag makes every publish write to
  every engagement in every firm.
- **Snapshot events**, not deltas: deltas break when an older apply arrives after a newer decline.
  Cost: larger events.
- **One cumulative offer**, not one per version: one decision instead of many, but no partial take.
- **A tracking service with a schema per firm**, not the firm's existing database: engagement
  storage is not directly queryable and belongs to another team. Cost: a new store of
  client-derived data, needing per-firm roles and a trusted producer.

| Assumption | If false |
|---|---|
| A1 Monotonic revision per engagement | Snapshots cannot be ordered; another sequence is needed. |
| A2 Engagement stores `lastDeclined` | Declines live only in the projection; losing it re-offers them. |
| A3 `templateId` never changes | A change needs a new binding; today it is rejected to the DLQ. |
| A4 A firm's engagement IDs can be listed once, for the backfill, without loading them | Backfill cannot seed the registry or measure coverage. |

**Cut first:** the LLM narrative, then the per-version breakdown; the status and change list
stay.

**Out of scope:** applying template content; adaptive throttling. Version branches or withdrawn
versions: a future extension, comparing within a branch.
