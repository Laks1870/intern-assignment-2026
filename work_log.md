# Work Log

## Day 0 — 08/09 (night)
Received the assignment late at night. Read through README.md and DATA_SCHEMA.md to understand
the task before touching any data.

## Day 1 — 09/09
Started exploring the dataset structure — sessions, chunks, events.jsonl, gt.jsonl, gt_manifest.json.
Downloaded one sample chunk and its ground truth to work with directly instead of reasoning about
the spec alone. Began comparing raw events against ground truth for one process execution by hand,
before writing any real code — wanted to build intuition first rather than guess at a segmentation
rule and find out later it was wrong.

## Day 2 — 10/09
Continued the by-hand analysis. Found that raw `app_switch` events are unreliable — discovered a
burst of 20+ app_switch events firing within under a second, all pointing to the same window
(same process_id, same title), which turned out to be recorder noise from repeated clicking, not
real transitions. Tested an early hypothesis that a "new segment" should start after a long idle
gap — this was wrong; found a real process transition with only a 0.39s gap between it and the
previous process, which meant idle-gap alone would miss most real boundaries. Also tested
clipboard events as a boundary signal and found they lag the real transition by up to 17 seconds,
so they aren't reliable as a primary signal either.

## Day 3 — 11/09
This was the heaviest analysis day. Landed on the actual approach: track specific named business
systems (e.g. specific Chrome tabs/portals) as "anchors," and treat a new segment as starting only
when a genuinely different anchor system appears and the worker stays there — not just any app
switch, since one real process can span multiple apps (e.g. Chrome → Excel → Notepad as one task).
Tested this and found it worked well when a distinctive system was involved, but failed when two
different processes both only touched shared/generic tools (like a common reference spreadsheet)
with no distinctive anchor in between. Worked through the remaining Step 1 sub-problems in the
same evidence-first way: noise blocks (no reliable signal exists even in ground truth, so not
solvable from this data), suspended/resumed processes (the only linking field, split_id, only
exists in gt.jsonl, not in the raw events I'd actually have on Dataset B — decided not to attempt
merging), and variant/exception detection (found real evidence that "exception" executions have
noticeably higher keystroke/action density than standard ones).

## Day 4 — 12/09
Turned the validated approach into actual code: noise filtering, anchor-based segmentation with a
clipboard-based secondary refinement step for long/merged segments, cross-chunk stitching, English
labeling, and variant tagging. Ran the full pipeline across all 63 sessions in Dataset A and scored
against ground truth using per-segment IoU — got an average of 0.644, higher than my earlier
single-session test (0.560), which was a good sign the method wasn't just overfitting to one
session. Applied the same frozen method to Dataset B (had to first discover Dataset B's own
distinctive systems, since it's a different department with different apps — found Word documents
tied to specific named procedures worked even better as labels than Dataset A's shared systems).
Generated segments.jsonl (868 segments across 15 sessions) and pushed Step 1 code + deliverable to
GitHub.
  - Commit 3ef24a7: "Full Step 1 pipeline: noise filtering, anchor+clipboard segmentation,
    labeling, variant tagging. Avg IoU 0.644 across 63 sessions in Dataset A"
  - Commit 223939a: "Add Step 1 deliverable (segments.jsonl) and Step 2 process analysis +
    automation priority ranking"

## Day 5 — 13/09
Built the Step 2 analysis: grouped segments by label, computed frequency/duration/exception-rate,
and found that some of my labels (the shared-system ones) were actually mixing several unrelated
tasks together — excluded those from the automation ranking since they don't represent one real
automatable process. Built a priority ranking using total time cost discounted by exception rate,
and picked monthly_fixed_transaction_list for Step 3 based on its 0% observed exception rate
despite not having the single highest score.

For Step 3, went back into the raw events for real examples of this process and found the actual
business rule written directly in the interface text: match a received invoice's ID/amount/company
against a reference list, flag mismatches for human review. Searched across all of Dataset B and
found 26 real examples of this confirmation happening, which gave me real input/output pairs to
design against. Discovered the actual reference master list itself isn't captured anywhere in the
logs — a real, documented limitation, not something I could code around — so built the prototype
against a small mock reference table, deliberately matching the same ~42%/58% discrepancy split
found in the real data.

Built a Spring Boot backend with MySQL (had a couple of setup issues along the way — MySQL driver
dependency missing from pom.xml initially, and a public-key-retrieval/database-name issue in
DBeaver — both resolved), implementing the comparison logic and comment generation matching the
real Japanese phrasing found in the data. Added a simple static HTML/JS frontend calling the API
directly, tested both discrepancy and clean cases successfully. Pushed Step 3 code and Step 2
notebook to GitHub.
  - Commit abb95ee: "Add Step 2 process analysis notebook"
  - Commit 24073cb: "Add Step 3 exploration notebook: discovered actual business rule from raw
    event text, identified reference-list data limitation, generated mock seed data for
    automation testing"
  - Commit b8074b2: "Step 3: Spring Boot + MySQL invoice reconciliation automation prototype with
    frontend demo"

## Day 6 — 15/09
Used the available time to write the final report (report.md) and this work log, pulling together
the reasoning from the previous days into a coherent writeup, and made sure the Git history
reflects how the work actually progressed rather than a single end-of-week commit.

## AI usage note
Used Claude throughout this project — primarily to stress-test my own hypotheses against the real
data rather than to generate the approach outright. Most segmentation ideas (clipboard-only boundaries, single-app-change boundaries) were things I proposed myself
first, which were then checked against real timestamps and either confirmed or disproven before I
committed to an approach. Also used it for Java/Spring Boot debugging (dependency errors, DBeaver
connection issues) and to help structure this report and work log from my own draft notes.