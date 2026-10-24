# AI usage log (raw notes for AI_USAGE_NOTES.md)

One line per event: what the AI proposed, and what I did (accepted / corrected / rejected) and why.

- Reused my Staff take-home solution as a reference, then **rejected** most of it for this brief: AIMD limiter, permit quarantine, fairness scheduler, version DAG, cost model and the numbers that are not in this brief. Reason: "do not over-optimize"; the brief is calibrated for this level.
- **Corrected** an AI suggestion to place the binding store in a shared DB, offering "residency" only to some firms. The brief makes residency mandatory, and it already has customer-specific databases.
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