# Aircraft Map Decluttering Task for Codex

## How to use this file

This task has **two passes**.

- **Pass 1 = inspect only. Do not change code yet.**
- **Pass 2 = implement after the human reviews Pass 1.**

If Plan mode is available, use it first.

Suggested prompt to Codex:

> Read `CODEX_AIRCRAFT_DECLUTTERING.md` and complete **Pass 1 only**. Do not edit code in Pass 1. Write the report to `docs/aircraft-declutter-current-state.md` and also summarize it in the chat.

---

## Product context

We have a map screen that shows **multiple aircraft icons**.

Current UX problem:
- When the user zooms out and aircraft are close together, the icons visually fight with each other and the map gets noisy.
- We want behavior closer to **decluttering / collision handling**.
- At lower zoom levels, nearby aircraft should stop visually overlapping in a messy way.
- At higher zoom levels, as screen-space separation increases, the icons should naturally spread back toward their true positions.

Important clarification:
- We are **not** asking for marker clustering into a count bubble unless the repository already does that and it is clearly the intended product behavior.
- We want to preserve individual aircraft visibility where practical.
- We want **render-time screen-space decluttering**, not mutation of the true aircraft coordinates.

---

## What I need from you in Pass 1

### Goal

Inspect the current codebase and explain **how aircraft icons are rendered today**, where overlap comes from, and the safest place to implement decluttering.

### Your job in Pass 1

1. Find the code that:
   - loads or receives aircraft/traffic positions
   - transforms aircraft coordinates into map or screen positions
   - renders aircraft icons, symbols, labels, headings, halos, or hit targets
   - responds to zoom/pan/viewport changes
   - handles selection, hit-testing, alerts, or z-order

2. Explain the current architecture end-to-end:
   - which files are involved
   - which module owns map rendering
   - which module owns aircraft state
   - whether the renderer is DOM, canvas, WebGL, native map markers, or something else
   - where icon size, anchor point, scale, and rotation are defined
   - whether there is already any decluttering, collision handling, culling, clustering, or label placement logic

3. Explain the current behavior specifically for overlap:
   - why nearby aircraft appear to overlap today
   - whether the overlap is purely geographic, purely screen-space, or both
   - whether zoom changes icon size, screen projection, or draw ordering
   - whether current behavior is deterministic or likely to jitter if we add offsets

4. Identify the best insertion point for decluttering:
   - before projection
   - after projection in screen space
   - inside the map layer/source logic
   - inside the draw loop
   - inside a layout step
   - or via built-in map-library collision features

5. Recommend the **lowest-risk implementation approach** that fits the existing stack.

### Pass 1 output format

Write your findings to:

`docs/aircraft-declutter-current-state.md`

Use exactly these sections:

1. `Summary`
2. `Relevant files and symbols`
3. `Current aircraft rendering flow`
4. `How zoom currently affects rendering`
5. `Why overlap happens today`
6. `Existing overlap or label handling`
7. `Best insertion point for decluttering`
8. `Recommended implementation approach`
9. `Risks and gotchas`
10. `Questions or unknowns`

### Evidence requirements

For every major claim:
- include the **file path**
- include the **symbol/function/class name** where possible
- distinguish clearly between **confirmed behavior** and **inference**

### Hard rule for Pass 1

Do **not** change code.
Do **not** create a broad refactor.
Do **not** jump ahead into implementation.
The output should be an evidence-based architecture/read-path report.

---

## What I want in Pass 2

Only do this after the human explicitly asks for implementation.

### Implementation goal

Add **screen-space aircraft decluttering / collision handling** so nearby aircraft icons remain readable at low zoom, while returning toward their true projected positions as the user zooms in or as aircraft separate on screen.

### Constraints

1. **Do not mutate true aircraft coordinates.**
   - Lat/lon / world coordinates remain the source of truth.
   - Any separation should be a **visual display offset in pixels/screen space** only.

2. **Preserve individual aircraft identity.**
   - No count-bubble clustering unless explicitly requested later.
   - Taps, selection, highlighting, and alerts should still map to the correct aircraft.

3. **Keep motion stable.**
   - Avoid frame-to-frame jitter.
   - Use deterministic ordering and stable slot assignment.
   - Reuse prior offsets when possible.
   - Add hysteresis so icons do not constantly snap between layouts.

4. **Respect zoom.**
   - Decluttering should be strongest when zoomed out and icons are crowded in screen space.
   - Offsets should reduce smoothly as zoom increases or as aircraft separate.
   - At sufficiently high zoom, displayed positions should converge back to the normal projected positions.

5. **Respect existing rendering patterns.**
   - Prefer the smallest clean change.
   - Reuse existing layer/state/update patterns.
   - If the current map/rendering library has a built-in collision feature that truly fits this need, evaluate it first before inventing a custom system.

6. **Do not degrade important UX.**
   - Preserve heading/rotation of aircraft icons.
   - Preserve alert/emergency/selected aircraft prominence.
   - Preserve z-order rules where meaningful.

### Preferred technical approach

Unless the existing stack strongly suggests a better built-in solution, prefer this approach:

1. Compute normal projected screen positions for all visible aircraft.
2. Detect collisions in **screen space** using icon bounds plus padding.
3. Form collision groups for aircraft whose rendered bounds intersect or nearly intersect.
4. Keep one aircraft at or near its original projected position when appropriate.
5. Assign deterministic visual offsets to the others using a stable radial/ring/spiral slot pattern.
6. Cache/reuse previous offsets per aircraft to avoid jitter.
7. Decay offsets back toward zero as collision pressure drops.
8. If an icon is offset materially, consider a subtle leader line/tether only if it matches the existing design language.

### Selection and priority rules

If priorities are needed, prefer something like:
- selected or focused aircraft first
- alerting/important aircraft next
- then deterministic fallback such as aircraft ID / callsign order

Do not invent a product ranking rule if the codebase already has one.

### Configurability

Make thresholds easy to tune, ideally via named constants or a small config object:
- collision padding
- max offset radius
- zoom threshold(s)
- hysteresis/smoothing factor
- leader-line threshold

### Verification requirements

After implementation:
1. Explain the chosen algorithm in plain English.
2. List changed files and why.
3. Run the smallest relevant tests/checks.
4. If possible, add or update focused tests around:
   - collision grouping
   - deterministic offset assignment
   - offset reset at higher zoom / lower crowding
5. Provide a short manual QA checklist for:
   - 2 nearby aircraft
   - 5+ crowded aircraft
   - selected aircraft in a crowded group
   - zooming in and out repeatedly
   - panning while traffic updates

### Definition of done for Pass 2

Done means:
- nearby aircraft icons no longer render as an unreadable pile at low zoom
- the underlying true positions remain unchanged
- the visual layout is stable, predictable, and testable
- the change is minimal enough to review confidently

---

## Extra guidance

If the repo is large or has multiple apps/packages:
- first identify which app/package owns the map screen
- stay focused on the narrowest relevant area
- avoid unrelated cleanup

If there is already a built-in label declutter or symbol collision system:
- document exactly what it already does
- explain whether aircraft icons can reuse it safely
- prefer native/library features only if they satisfy the product behavior without awkward hacks

If the current rendering path makes decluttering risky:
- say so plainly
- propose the smallest intermediate refactor needed
- keep the refactor scoped to enabling decluttering

