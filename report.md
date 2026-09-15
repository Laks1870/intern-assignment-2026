############################# REPORT ###################################
1. # What i understand from Problem statement? #
Basically, a company has been recording everything employees do on their computers — every click, every keystroke, which app is open, when they copy-paste something — for a while now. But nobody's actually looked at this data to understand what it means. The logs just say "user clicked X" or "switched to Chrome," nothing about what task the person was actually doing.
My job is to figure out, just from this raw activity, where one task starts and ends, and label repeated instances of the same task consistently. I get Dataset A to practice on, which comes with an answer key (gt.jsonl and gt_manifest.json) telling me the real start/end times for each task — so I can check how close my method gets. Dataset B doesn't have this answer key at all, and it's from a completely different department using different apps, so my method actually has to generalize, not just memorize patterns from A.
Once I have these segments, I need to figure out which tasks eat up the most time across the department, decide which ones are worth automating, and actually build something that automates one of them — being honest about what I'm assuming, what a human would still need to do afterward, and what could realistically go wrong if this got deployed for real.

2. # Understanding the Dataset #
The data comes in two parts. Dataset A has 63 sessions with about 162,000 events total, and comes with the answer key. Dataset B has 15 sessions, around 20,000 events, no answer key, and is from a different department doing different work with different apps — this is the "real" data I actually need to produce results for.
Each session represents one continuous recording of a worker's PC activity, but it's split into multiple "chunks" — this split is just how the recording tool happens to save files, it has nothing to do with when a task actually starts or stops. A single business process can easily start in one chunk and finish in the next one, so before doing anything else, I had to stitch a session's chunks back together in the right time order.
Inside each chunk, events.jsonl has the raw log — one line per action, things like app_switch, keystroke, mouse_click, clipboard_change. Every event has a timestamp, which app was active, and some extra metadata depending on the event type. About 4% of events also carry extracted_text — actual text read off the screen at that moment, which turned out to be one of the most useful signals I had, since it sometimes captured the literal instructions or data a worker was looking at.
For Dataset A only, gt.jsonl records the real business process boundaries — when a process started, when it got switched out, when it resumed after being paused, and so on. gt_manifest.json is a cleaner, summarized version of the same information, grouped by process type and execution.
One thing I noticed early on: the ground truth data isn't perfectly clean either. The schema documentation itself warns that some things don't line up consistently (e.g., a process switch doesn't always have a matching start event), and I ran into this myself later when trying to track a paused-and-resumed process — the case ID before and after the pause didn't even match.
Since everything is from a Japanese company, process names, window titles, and any on-screen text are in Japanese. I didn't translate everything, just the pieces I actually needed for labeling and building the Step 3 automation.

3. # How I approached this, and dividing it into three steps #
Before writing any code, I spent time just reading through one real session's raw events side by side with its ground truth, trying to spot patterns by hand rather than jumping straight to an algorithm. This mattered a lot — a few ideas that looked reasonable on paper (like using clipboard events as the main signal for a new task starting) turned out to be wrong once I checked them against real timestamps, and I would have wasted time building on a bad assumption otherwise.
Once I had a feel for the data, I split the work the same way the assignment is structured:

Step 1 — Segmentation Turn the raw, unlabeled event stream into a list of segments, each one representing a single execution of a business process. I built this entirely on Dataset A, since it's the only place I could check my accuracy against a real answer key, then applied the same method, unchanged, to Dataset B.

Step 2 — Analysis. Once I had segments for Dataset B, I looked at which processes actually happen, how often, how long each one takes, and how many different people are doing them. From that, I built a ranked list of which processes are worth automating first, along with my reasoning for the ranking.

Step 3 — Automation. I picked the process that ranked well and also looked genuinely safe to automate (very low observed exception rate), and built a small working prototype — a Spring Boot backend with a MySQL database and a simple frontend — that replicates the actual comparison logic a worker does when reconciling an invoice.

Within Step 1 specifically, I broke the problem down further into a set of smaller, individually testable questions — how do I filter out recorder noise, how do I decide where one task ends and another begins, how do I handle a session split across multiple files, how do I label two different executions of the same process consistently, and so on. I'll go through each of these one at a time in the next section, since each one involved a real decision backed by evidence from the data, not just a guess.

4. # Step 1 in detail — the factors I worked through, with real examples #
# Factor 1 — Filtering out noise 
The very first thing I noticed when I looked at raw app_switch events was that they're not reliable on their own. In one 10-minute chunk, there were 365 app_switch events, but a lot of them were obviously not real transitions. For example, at one point I saw over 20 app_switch events fire within less than a second, all pointing to the exact same window — same process_id (3924), same window title (財務会計システム - Google Chrome), and every single one tagged with trigger: window_click. That told me the recorder was logging an app_switch event every time the worker clicked somewhere inside an already-focused window, not because they actually switched anywhere.

# Definition of Noise
a repeated app_switch event that doesn't represent an actual change of window — same process_id and same window_title as the last real one I kept. My fix was simple: go through the app_switch events in order, and only keep one if its (process_id, window_title) pair is different from the last one I kept. That took the count down from 365 to 55 real transitions in that one chunk, which felt like a believable number for a 10-minute session.

# Factor 2 - Deciding where one task ends and another begins
This was the hardest part, and my first instinct was wrong. I initially assumed a new task would start after some kind of pause — a long gap since the last action. But when I checked this against the ground truth, I found a case where one process (bank reconciliation) ended and the very next process (a childcare leave request) started with a gap of only 0.39 seconds — basically no pause at all. If I only used idle-time gaps to find boundaries, I would have completely missed this transition.

I also tried using clipboard events as the signal for a new task starting, since copying a new case's data felt like a natural "this is a new task" marker. That didn't work either — I found the clipboard copy for the new task didn't happen until 17 seconds after the real switch had already occurred. So clipboard timing lags too much to be a boundary signal by itself; I ended up only using it as a secondary, confirming signal, not the primary one.

What actually worked was tracking specific named business systems — the exact browser tab or portal being used (e.g. 財務会計システム for the finance system, HR人事給与システム for HR). A new segment starts when a genuinely different named system appears and the worker stays on it, not just any app switch. One process can still use multiple apps in sequence (I saw one process go Chrome → Excel → Notepad → back to Chrome, all as one task), so the boundary isn't "any app change," it's specifically "a different distinctive system now in play."

Even this wasn't perfect on its own — some processes only ever touch shared, generic tools (like a common reference spreadsheet everyone uses), so there was no distinctive system to detect a boundary at all. For those cases, I added a second pass: inside any segment that ran unusually long, I checked for clipboard events and used them to split the segment into smaller pieces, since a long single segment with several clipboard actions inside it was often actually several different tasks that my main method had merged together by mistake.

# Factor 3 — Handling a session split across multiple files
A session isn't always saved in one file — it gets split into "chunks," but this split is purely a storage decision the recording tool makes, it has nothing to do with when a real task starts or stops. I actually caught this happening for real: one process started 9 seconds before a chunk file ended, and its actual finish was sitting in the next chunk file entirely. If I'd only processed one chunk at a time, I'd have thought that process just vanished halfway through.

The fix was mechanical rather than a judgment call: before doing any segmentation, I load every chunk belonging to one session, combine all their events into a single list, and sort that list by the real timestamp (not by which file it came from, since file names aren't guaranteed to be in the right order). Only after that do I run my noise-filtering and boundary-detection logic, treating the whole session as one continuous timeline.

# Factor 4 — Making sure the same task gets the same label every time
Once I had segment boundaries, each one needed a name, and the same real task had to get the same name every time it happened, even though the actual data inside it (names, case numbers, amounts) is different each time. My first idea was to use whatever tool/file the segment spent the most time in as its label. That backfired — I found a shared reference Excel file that got used by two completely unrelated processes, so labeling by "which file was open" would have wrongly lumped them together as the same task.

What worked better was using the specific named business system or specific named document as the label — things like the actual Chrome tab title (財務会計システム) or a specifically-named Word document (shinkuitorihikisaki_touroku_tetsuzuki, the new-supplier-registration form). These are tied to one real kind of work, unlike a generic shared tool that anyone might open for any reason.

# Factor 5 — Noise blocks (activity that isn't part of any real process)
The session metadata mentioned there were a couple of "noise blocks" in the data — stretches of activity that don't belong to any real business process at all. I went looking for these directly in gt.jsonl, expecting to find some kind of gap or a specifically labeled "noise" event. I didn't find one. Every second of the session was attributed to some real process, back-to-back, with no separate marker for noise anywhere in the ground truth itself.

That told me noise isn't sitting in an obvious empty gap — it's hidden inside the duration of some process executions. I noticed this concretely later, while working on Step 3: one segment I pulled up for closer inspection turned out to contain the literal terminal output from the script that generated this test dataset (seed numbers, setup logs, orchestrator output) — clearly not real work, just something that got captured mid-session and lumped into whatever segment was active at the time.

Given this, I didn't try to build a separate noise-detector — there wasn't a reliable signal to build one against, even in the answer key. I accept that some noise gets silently absorbed into real segments, and I've noted this as a #  known limitation # rather than pretending my segmentation perfectly isolates it.

# Factor 6 — Processes that get paused and resumed later
I found a real case of this: one process started, then got suspended about a minute in while the worker switched to something else, and only resumed nearly five minutes later. In reality, this is one continuous task with a long interruption in the middle, not two separate unrelated executions.

I looked into whether I could detect and merge these back together. The ground truth actually has a field for this (split_id) that links the paused and resumed halves — but that field only exists in gt.jsonl, not in the raw events.jsonl I'd actually have access to on Dataset B. Worse, even within the ground truth itself, the case ID logged at the pause point didn't match the case ID logged when it resumed, which made me trust this mechanism even less.

Given there's no reliable signal available in the raw data to detect this reliably, I chose not to attempt merging suspended and resumed segments. My method treats them as two separate entries with the same label, and I'm documenting this explicitly as a limitation rather than building something fragile.

# Factor 7 — Handling "exception" cases within the same process
Some processes have a "standard" way of being handled and an "exception" way, when something unusual comes up in that specific case. I tested whether this shows up in observable behavior, not just in the hidden ground truth label. Looking at one known exception case, it had 82 keystrokes and a much higher rate of actions-per-second (2.37) compared to three standard executions of the same process, which ranged between 0.43 and 1.22 actions per second.

So I built a simple rule: for each process type, calculate the typical (median) activity density across all its executions, and flag any individual execution whose density is well above that median (I used 1.8x) as a possible exception. I excluded very short segments from this check, since density becomes an unreliable number when there's barely any time to measure it over. This isn't perfect — it's based on limited evidence — but it gave me a genuinely useful signal later for the Step 2 automation-risk analysis (a process with a high exception rate is riskier to fully automate than one that's always handled the same way).

# How I defined activity density

Activity density measures how much "work" was happening per second within a segment — essentially, how busy the worker was during that stretch of time, based on how many hands-on actions they performed.

activity_density = (keystrokes + mouse_clicks + shortcuts + mouse_scrolls) / duration_in_seconds

# Factor 8 — How I measured my own accuracy
I couldn't just check whether my predicted start/end timestamps matched the ground truth exactly — they never will, down to the millisecond. Instead I used an overlap-based score: for each real process execution, I calculate how much my predicted segment's time range overlaps with the true time range, as a ratio between 0 and 1 (intersection over union — the same idea used for scoring bounding boxes in computer vision, just applied to time instead of space).I score this per individual segment and then average across all of them, rather than treating a whole session as one big block

# Results — how well this actually worked
I first tested my full method (noise filtering, boundary detection, cross-chunk stitching, labeling, variant tagging) on a single session by hand, checking every step against its ground truth as I built it. Once I was confident it was reasoning correctly, I wrapped it into a single reusable pipeline and ran it automatically across all 63 sessions in Dataset A, scoring each one against its own gt_manifest.json.

# Overall result: an average IoU of 0.644 across all 63 sessions.
This is a real, honest number — not tuned to look good on one session and then hoped to generalize. In fact, running the full pipeline across all 63 sessions gave a higher average (0.644) than my earlier single-session test (0.560), which was encouraging — it suggested the method wasn't overfitting to the one session I built it on.

I also looked at where it did worst, rather than just accepting the average. The worst-performing session scored 0.202, and the next few worst were all in the 0.46–0.53 range. I traced this back to the same failure pattern I found during development: my boundary detection relies on spotting a distinctive named business system, and it struggles specifically when two different real processes happen back-to-back but both only touch shared, generic tools rather than a distinctive system — in those cases, my method sometimes merges them into one segment instead of two.

# Applying the same method to Dataset B
Once I was satisfied with this result on Dataset A, I froze the method — no more tuning — and applied it, unchanged, to Dataset B. Since Dataset B is a different department with different applications, my original list of "distinctive systems" (built entirely from Dataset A's finance/HR/inventory tabs) didn't automatically apply. I had to first explore Dataset B's own window titles the same way I did for A, and found that in this case labeling was actually easier: instead of one shared generic tool, Dataset B's Word documents were each tied to one specific, clearly named procedure (e.g. a "new supplier registration" form), which turned out to be a stronger, less ambiguous labeling signal than what I had in Dataset A.

Running the frozen pipeline across all 15 sessions in Dataset B produced 868 segments in total, with no errors.

# Output format from Dataset B
Each segment gets written to segments.jsonl as one JSON object per line, matching exactly the format the assignment specifies:
{"session_id": "ses_...", "start": "2026-07-01T18:32:32Z", "end": "2026-07-01T18:35:41Z", "label": "new_supplier_registration"}

# . Step 2 — Analysis and automation priority ranking
Once I had segments for Dataset B, I grouped them by label and calculated, for each one: how often it happens, how long it typically takes, the total cumulative time it costs across the whole department, how many different sessions (roughly, different workers) touch it, and — using my exception-tagging from Step 1 — what fraction of its executions look unusual.

Before ranking anything, I had to deal with an issue in my own labeling. Three of my labels (hr_payroll, financial_accounting, order_inventory) came from shared browser systems that many different, unrelated tasks all pass through — the same way a shared Excel reference file got reused across different processes in Dataset A. These labels had by far the highest total time numbers, but they don't represent one real, automatable task — they're a mix of several different things that happened to touch the same system. I excluded these from my automation ranking for that reason, and only ranked the labels tied to one specific, named procedure (a distinct Word document), since those are the ones I can be confident actually represent a single repeatable task.

* priority_score = total_time_cost × (1 − exception_rate)
The reasoning: total time cost tells me how much time automating this process could realistically save across the department, and the exception rate discounts that score for processes that vary a lot case-to-case, since those are inherently riskier and harder to automate reliably.

# Final ranking:

Process	                            Total time (s)    Exception rate	Priority score
new_supplier_contract	                     385	           12.0%	    338.8
new_supplier_registration			         350               15.4%        296.2
monthly_fixed_transaction_list	277	0%	                                    277.0
new_hire_checklist	286	7.1%	                                            265.6
outsourcing_expense_regulations	249	13.9%	                                214.4
outsourcing_acceptance	168	0%	                                            168.0
contract_termination	100	0%	                                            100.0
admin_authority_application	57	0%	                                         57.0
childcare_leave_regulations	43	0%	                                         43.0

new_supplier_contract ranks highest by raw score, but monthly_fixed_transaction_list stood out to me for a different reason: it's the single most frequent process (55 occurrences) and had a 0% observed exception rate — meaning every single execution I could check followed the exact same pattern. That combination — high volume, zero observed variance — is close to the ideal profile for automation: I can be far more confident it'll behave predictably in production than a process with a 12-15% exception rate, even if that process scores slightly higher on raw time saved. This is why I chose monthly_fixed_transaction_list for Step 3, prioritizing reliability over the highest possible score.

# Step 3 — Building the automation
# Why this process, and why this scope
I picked monthly_fixed_transaction_list — specifically the invoice reconciliation task within it — because it had the best combination of volume and predictability: 55 occurrences with a 0% observed exception rate. Rather than trying to build something that covers multiple different processes, I scoped this narrowly to just this one task, done properly, rather than attempting a broader framework that covers several processes shallowly. Given the time I had, I judged that a single, fully working, well-tested piece of automation was worth more than a half-built general-purpose tool that couldn't actually demonstrate anything end-to-end.

# What the process actually involves
Before designing anything, I went back into the raw event data for real examples of this process and reconstructed what a worker actually does. Each execution follows the same shape: a worker opens an invoice from a queue in the finance system, briefly checks a reference document, then writes a confirmation comment. I got lucky and found the exact business rule written out in the interface text itself, captured directly in one of the raw events: "Match the received invoice's INV number, amount, and company name against this list. If there's a discrepancy, hold it as needing confirmation." This meant I wasn't guessing at the logic — I had the actual rule, word for word, from the system a human worker sees.

# Why I chose a deterministic backend service over other implementation forms
I considered three options: a simple script, an AI/LLM-based agent, and a small backend service. I ruled out an AI agent specifically because the underlying task doesn't need judgment — it's a straightforward field-by-field comparison with a fixed rule, and I found zero observed exceptions across every example of this process I could check. Using an LLM for a task like this would add cost, latency, and a small amount of unpredictability, without adding any real value, since there's no ambiguity for it to resolve. I chose a Spring Boot backend with a MySQL database over a bare script mainly because it mirrors how this would actually integrate with a real company system (as a callable service, not a one-off script someone has to run manually), and because it let me demonstrate a complete, realistic API rather than just a proof-of-concept snippet.

# Input and output design
# The input represents one incoming invoice:
{
  "invoiceId": "INV-2026-7345",
  "amount": 455128,
  "companyName": "北陸部品工業株式会社"
}

# The output is the reconciliation result:
{
  "invoiceId": "INV-2026-7345",
  "discrepancyFound": true,
  "generatedComment": "請求書照合完了。INV-2026-7345　金額：455,128円。差異あり要確認。",
  "requiresHumanReview": true
}

# An honest limitation I ran into
The actual master reference list — the real "source of truth" a worker checks an invoice against — is never fully visible in the captured logs. The instruction to check against "this list" got captured, but the list's actual contents weren't visible at that exact moment in any session I checked. Since this data genuinely isn't available from the logs, I built the prototype against a small mock reference table that I populated myself, deliberately mixing matching and mismatching records so the ratio (about 42% discrepancy, 58% clean) matches what I actually observed in the real data. I've been explicit in my code and here that this table is simulated, not real production data.

# What manual work remains after this is deployed
This prototype only handles the comparison and generates the correct comment text — it doesn't actually write that comment back into the real finance system, since I have no access to that system. A human would still need to: connect this service to the real reference database (not my mock table), wire the output into the actual finance portal so the comment gets posted automatically, and manually review every case flagged requiresHumanReview: true, since my system deliberately doesn't try to resolve discrepancies on its own — it escalates them, as the actual business rule instructs.

#