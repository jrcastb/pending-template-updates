# Pending Template Updates

Take-home design exercise (Senior Software Developer III). Users see which of their engagement
files have a pending product-template update, read a summary of the changes, and apply or decline
it, without any user-facing wait on the ~1-minute engagement load.

**Central decision:** pending status is derived on read, never stored per engagement:
`pending = latest > max(applied, lastDeclined)`.
- A publish writes one record per template: its latest version.
- Each firm keeps a small read model of its engagements' template bindings, in its own schema of
  a regional tracking service. It is fed by snapshot events the engagement system emits while an
  engagement is already loaded.
- Only the one-time backfill of existing engagements pays the load.

## Deliverables

| File | What it is |
|---|---|
| [docs/DESIGN.md](docs/DESIGN.md) | Design document. It covers Target Architecture, Implementation Plan, Testing Strategy, Evaluation & Observability, and Failure Modes & Tradeoffs. |
| [docs/diagrams.md](docs/diagrams.md) | Two Mermaid diagrams: the architecture with its data boundaries, and the flow from publish to visible status. |
| [pending-updates-core/](pending-updates-core/) | Part 2: the slice of the system that holds its riskiest contracts. |
| [docs/AI_USAGE_NOTES.md](docs/AI_USAGE_NOTES.md) | How AI was used: where it helped, where I corrected it, and where it should not be trusted. |
| [docs/AI_log.md](docs/AI_log.md) | Raw log of each AI suggestion I accepted, corrected or rejected. |
| [docs/plan.md](docs/plan.md) | The approved implementation plan for the slice. |
| [CLAUDE.md](CLAUDE.md) | The project context and decisions given to the AI assistant. |
| [docs/brief/BRIEF.md](docs/brief/BRIEF.md) | The exercise brief. |

## Run the tests

Requires JDK 21+ and Maven 3.9+. From the repository root:

```
mvn -f pending-updates-core/pom.xml test
```

This runs 35 tests. There are no runtime dependencies; tests use JUnit 5 and AssertJ. Compilation
runs with `-Xlint:all -Werror`.

## The slice

Pure Java, hexagonal layout, under `com.example.pendingupdates`:

| Package | Contents |
|---|---|
| `domain` | The rules, with no I/O. `PendingRule` returns a sealed `PendingStatus`: `UpdateAvailable`, `UpToDate` or `Checking`. `EngagementBinding.applyIfNewer` is one pure function that decides how a snapshot changes a binding: created, changed, duplicate, stale or rejected. |
| `port` | Contracts in Javadoc. `BindingRepository` (every call takes a `FirmScope`), `EngagementDirectory` and `AffectedCountSink`. |
| `application` | `AffectedCountOnPublish`: on every publish, it reports one firm's affected and checking counts; the job calls it once per enabled firm. It reports even 0/0, so the report also works as a heartbeat. |
| `adapter.inmemory` | Reference adapters used by the tests. |

What the tests prove:
- **Pending rule.** One cumulative offer for accumulated updates. A declined update is offered again
  when a newer version is published. A status is never "up to date" without evidence.
- **Event handling.** Duplicate and out-of-order snapshot events, including all six delivery orders
  of three events, end in the same state. A conflicting revision or a template change is rejected.
- **Firm isolation at the port.** A shared contract suite that every repository adapter must pass.
- **Affected counts.** Counts only, no engagement IDs, and a report even at 0/0.

What the tests do not prove:
- Database-level isolation. That needs a per-firm role isolation test against Postgres (see
  Testing Strategy in the design).
- Real AWS adapters, the summary pipeline, and the decision log. These are covered in the design
  only.