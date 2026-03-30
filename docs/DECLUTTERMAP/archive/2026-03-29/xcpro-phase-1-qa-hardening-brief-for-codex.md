# XCPro Phase 1 — QA + Hardening Brief for Codex

You are not implementing new product scope.
You are helping close Phase 1 cleanly.

## Current state
Phase 1 code has already been implemented.
Reported implemented items:
- OGN direct target taps restored
- OGN zoom-band icon sizing and label visibility kept restyle-only
- cluster-only production files removed
- watched-aircraft overlay reapplies after style recreation
- supporting runtime/config seams kept narrow
- relevant unit tests and `enforceRules` passed

## Your job
Do a **post-implementation hardening pass** focused on:
1. validating the implementation against the intended Phase 1 scope,
2. tightening any weak edges discovered during review,
3. preparing the change for manual QA and PR readiness,
4. not broadening scope.

## Rules
- Do **not** implement Phase 2.
- Do **not** add clustering, spiderfy, regrouping, ranking changes, or shared ADS-B/OGN policy extraction.
- Do **not** redesign architecture unless a narrow bug fix absolutely requires it.
- Prefer narrow fixes over cleanup refactors.
- If everything already matches the brief, say so clearly and keep changes minimal.

## What to review carefully
### 1. OGN tap-selection path
Confirm the final runtime/overlay path is correct and robust:
- direct glider taps reach the intended target,
- no overlay layer steals the hit unexpectedly,
- selection still works after zoom-band restyling,
- selection still works after style recreation.

### 2. OGN zoom-band behavior
Confirm Phase 1 is still restyle-only:
- no source rebuild is triggered only because viewport zoom changed,
- icon sizing is driven from base size × viewport multiplier,
- label visibility is only gated by zoom bands,
- behavior is stable on initial render and on camera idle updates.

### 3. Watched-aircraft overlay recovery
Confirm the style recreation fix is clean:
- overlay reapplies after style recreation,
- icon scale is correct immediately after recreation,
- no duplicate source/layer creation,
- no stale state carried across style reloads.

### 4. Base-size semantics
Confirm user-configured OGN icon size is still treated as base size:
- rendered size is derived from the base size,
- rendered size is not persisted back into settings,
- changing settings updates all zoom bands correctly.

## What to do
### A. Code review against intended Phase 1 scope
Review the touched files and verify the implementation is still aligned with the original Phase 1 goals.

### B. Fix only concrete issues you find
Examples of acceptable fixes:
- lifecycle hole,
- missed zoom propagation path,
- style recreation duplication bug,
- incorrect label visibility handling,
- tap-selection regression,
- state restoration issue.

Examples of unacceptable changes:
- introducing clustering,
- changing target ranking rules,
- raising/lowering `MAX_TARGETS`,
- rewriting overlay ownership,
- shared policy refactor across traffic systems.

### C. Strengthen tests only where there is an actual gap
Add or adjust tests only if there is a real missing assertion in the current touched slice.
Focus on:
- zoom-band restyle behavior,
- tap-selection continuity,
- style recreation reapply behavior,
- base-size vs rendered-size semantics.

### D. Prepare the manual QA handoff
Produce a concise manual QA checklist with pass/fail placeholders.
Do not claim manual QA was executed unless it actually was.

## Manual QA checklist to include in your output
1. Dense OGN zoom in/out
2. Initial launch at wide zoom
3. Initial launch at close zoom
4. OGN icon-size setting change across bands
5. Watched-aircraft zoom scaling across bands
6. Close-zoom OGN tap selection
7. Satellite/contrast icon mode scaling check

For each item, leave:
- Result: PASS / FAIL / NOT RUN
- Notes:

## Output required from you
Provide:
1. concise summary of any code changes made,
2. files changed,
3. tests run and results,
4. manual QA checklist with NOT RUN placeholders unless actually executed,
5. any remaining risks or follow-up items,
6. explicit statement whether the branch is:
   - ready for manual QA,
   - ready for PR gate,
   - or blocked by a specific issue.

## Success condition
Best outcome:
- no new scope added,
- any narrow issues fixed,
- Phase 1 remains contained,
- branch is cleanly ready for manual QA and then PR hardening.
