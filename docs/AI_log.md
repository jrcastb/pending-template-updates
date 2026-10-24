# AI usage log (raw notes for AI_USAGE_NOTES.md)

One line per event: what the AI proposed, and what I did (accepted / corrected / rejected) and why.

- Reused my Staff take-home solution as a reference, then **rejected** most of it for this brief: AIMD limiter, permit quarantine, fairness scheduler, version DAG, cost model and the numbers that are not in this brief. Reason: "do not over-optimize"; the brief is calibrated for this level.
- **Corrected** an AI suggestion that offered residency for only some firms. The brief makes residency mandatory for every firm. (The shared regional DB with a schema per firm came later, as my decision: see the "My decision" entry below.)
- **Accepted, with refinements:** per-firm affected counts (Obs 1). Refinements: counts only, CHECKING counted separately, and every firm reports 0 so the report doubles as the silent-firm heartbeat.
- **Corrected** the "event-lag watermark" idea. A quiet firm looks like a broken one, so lag is measured as producer outbox position vs. consumer position.
- **Corrected** the default "summary grouped by version". The primary view is the net diff (what the user decides on); the per-version breakdown is secondary.
- Cross-checked one AI's design advice with another AI. It found 4 real gaps:
    - the silent-firm alarm needed an expected-firm list;
    - the "bindings in the firm DB" assumption conflicted with the brief's "not directly queryable" wording;
    - the isolation test proves the contract, not the database;
    - the net diff can hide reverted declined changes, so the flag now reads "includes versions you declined".

  Lesson: check AI advice against the brief's exact wording, and ask what a test actually proves.
- **Accepted** the switch to snapshot events. The AI found an ordering bug with delta events: an older Applied arriving after a newer Declined.
- **Corrected** the AI's "reject snapshot when declined <= applied". It is now normalized to "no decline", because it can be a valid state if the producer didn't clear an old decline.
- **Accepted** the AI's fix for a plan-order conflict: Step 3's `PendingRule.evaluate(Optional<binding>, …)` needed `EngagementBinding`, planned for Step 4. Chose a data-only binding record in Step 3, with behaviour in Step 4, over swapping the steps or changing the rule's signature. Reason: keeps the approved signature and step boundaries.
- **Accepted** the AI's widening of `LATEST_BEHIND` to `max(applied, lastDeclined) > latest` (the plan said `applied > latest`): a decline ahead of the latest table is the same lag, and "up to date" would be shown without evidence. plan.md updated.
- **Accepted** the AI's DESIGN.md plan and its 9 recommended answers to open questions, among them: diagrams in `docs/diagrams.md`; rebuild a lost projection by re-running backfill; the silent-firm expected list = firms enabled in the region; pre-feature declines without a version are offered once more (A2 consequence); no new decisions in the doc, gaps listed as open questions.
- **Accepted, partly,** the AI's size trims: hard-coded the 6 delivery orders instead of a permutation helper, and cut `ValuesTest` to one ID test (the three ID records share the same validation). **Accepted** ending at ~1,000 lines, above the 600–900 target, rather than drop the reusable `BindingRepositoryContract`. The AI first claimed tests were ~60% of the code; the real split is 535 main / 462 test.
- **Accepted** the AI's A3 "if false" line, and **made it a rule**: a `templateId` mismatch is a producer bug, handled like a conflicting revision (throws in the slice, DLQ plus alarm in production). **Accepted** the one-line branch extension.
- **Corrected** the AI's diagram, which marked "Metrics" as simply existing. It now reads "CloudWatch (existing) — new metrics & alarms": the service exists, but the metrics and alarms are new.
- Reviewed the AI's undocumented code choices from Steps 4–5:
    - **Accepted:** a snapshot for another firm or engagement throws; `cause` is not stored on the binding; `FirmMismatch` is a distinct security exception on the port.
    - **Adjusted:** the nested snapshot exceptions now share a sealed base type, `RejectedSnapshot`, so the "goes to the DLQ" contract is visible in code.
    - **Adjusted:** decline normalizations are not measured in the slice. DESIGN.md says they are counted (a rising count means the producer is not clearing declines), and CLAUDE.md marks it design doc only.
    - **Corrected:** the AI had put "the first snapshot creates the row" in the in-memory adapter. Business rules belong in the domain, not in the persistence adapter. One pure function, `applyIfNewer(Optional<current>, snapshot)`, now decides created / changed / duplicate / stale / rejected. The adapter only does the atomic read-decide-write, and the contract test covers atomicity and isolation only. Done test-first.
- **My decision:** Moved bindings from the firm's existing database to a regional tracking service with a schema per firm. Reason: the brief says engagement storage is not directly queryable, and that database belongs to another team. Cost: a new store of client-derived data, which needs per-firm roles to be structural, plus producer verification.
- AI flagged that a per-schema registry cannot detect a cross-firm event; trust moved to the producer's IAM identity.
- AI found that archiving needs a tombstone, or a late snapshot recreates the binding.
- Reviewer caught an overclaim ('the database denies cross-firm access') and a race between tombstones and backfill seeding.
