# AI Usage Notes

I used Claude Code as a pair, plus a second model to cross-check design advice. The raw,
per-event record is [AI_log.md](AI_log.md); this page is the summary.

## How I worked with it

- **Source of truth first.** `CLAUDE.md` holds the brief's constraints, the design decisions and
  the out-of-scope list. The rule: if the AI, a plan or the code conflicts with the brief, the
  brief wins, and the AI must say so instead of resolving it silently.
- **Plan, approve, then code.** Every step was planned, approved by me, then built test-first in a
  small diff, and stopped for my "continue".
- **Every accept, correction and rejection was logged** as it happened, not reconstructed at the
  end.

## Where AI helped

- **Finding a real ordering bug.** With delta events, an older `Applied` arriving after a newer
  `Declined` corrupts the binding. That moved the design to full snapshot events where the highest
  revision wins.
- **Edge cases in the pending rule.** It proposed treating a decline ahead of the latest-version
  table as lag (`CHECKING`), so the UI never shows "up to date" without evidence.
- **Plan consistency.** It caught that Step 3 needed a type planned for Step 4, and asked instead of
  reordering the plan.
- **Test-first scaffolding.** Table-driven tests, a test that replays events in all six delivery
  orders, and a reusable repository contract suite.
- **Editing to a limit.** It kept the design doc inside the brief's 3 pages, and found a
  contradiction in my own summary during the final pass (the audit sample also pays the 1-minute
  load).

## Where I corrected or ignored it

- **Over-engineering.** I started from a solution to a harder exercise and rejected most of it:
  adaptive limiter, permit quarantine, fairness scheduler, version DAG, cost model, and every number
  not in this brief. The brief says "do not over-optimize".
- **Residency.** It suggested residency for only some firms. The brief makes residency mandatory
  for every firm. (The shared regional database with a schema per firm came later, as my own
  decision; see the AI log.)
- **Observability.** An "event-lag watermark" makes a quiet firm look broken. Lag now compares each
  firm's newest outbox event with the newest one applied, and every firm reports even 0/0.
- **Product judgment.** It defaulted to a summary grouped by version. The primary view is the net
  diff, because that is what the user decides on.
- **Data rules.** It proposed rejecting a snapshot with `lastDeclined <= applied`. That can be a
  valid state, so it is normalized to "no decline" instead.
- **Architecture.** It put "the first snapshot creates the row" in the persistence adapter. I moved
  it into one pure domain function: business rules do not belong in SQL or in an adapter.
- **Accuracy.** It said tests were about 60% of the code; the real split was 46%. A diagram marked
  "Metrics" as existing when only the service exists and the metrics and alarms are new.
- **Cross-check.** A second model found four gaps in the first one's advice, including that an
  isolation test at the port proves the contract, not the database.

## How I would guide other engineers

1. Give the AI the brief and a decisions file, and make it quote the sentence a decision relies on.
2. Ask for a plan with open questions before any code. Answer the questions yourself.
3. Work test-first in small steps, and ask of every test: "what does this actually prove?"
4. Never let the AI choose numbers (scale, limits, budgets). Unsourced numbers are assumptions and
   must be labelled.
5. Review undocumented choices. Ask the AI to list the decisions it made that the plan did not
   specify, then accept or reject each one.
6. Keep a log of accepts and corrections. It shows where the AI is reliable and where it is not.

## Where AI should not be trusted in this domain

- **Summaries that drive an audit decision.** Users apply or decline template changes based on the
  summary. An invented or missing change can push an engagement away from the firm's methodology.
  So the deterministic change list from the JSON diff is the authority. An LLM narrative may only
  restate changes it cites by ID, it fails closed to the list, and it is checked against a golden
  set with pinned model and prompt versions.
- **Security and isolation claims.** AI will call a design "isolated" from code that only filters
  in the application. Firm isolation must be structural, and only a database-level test (per-firm
  roles) proves it.
- **Facts about other teams' systems.** The revision ordering, `lastDeclined` storage and
  engagement listing (A1–A4) are assumptions that the AI cannot verify. They must be confirmed
  with the owning teams.
- **Confident numbers and summaries.** It was wrong about code size and about what a diagram
  showed. Measure; do not take the AI's word for it.
