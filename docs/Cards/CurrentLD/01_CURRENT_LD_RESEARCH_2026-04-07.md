# Current L/D Research

Date: 2026-04-07

## Purpose

This note records what "Current L/D" usually means in glider and
glider-computer usage, and where the term can drift.

The goal is not to copy one vendor's wording.
The goal is to anchor XCPro terminology so we do not silently mix:

- measured recent glide ratio
- measured through-air glide ratio
- theoretical polar L/D
- required glide ratio to a target

## 1. Base aerodynamic meaning

At the aircraft-performance level, glide ratio and lift-to-drag ratio are
closely related concepts.

FAA reference:

- FAA Glider Flying Handbook:
  `https://www.faa.gov/regulations_policies/handbooks_manuals/aviation/glider_handbook`
- PDF:
  `https://www.faa.gov/sites/faa.gov/files/Glider-Flying-Handbook.pdf`

Useful takeaway from the FAA material:

- glide ratio is the horizontal distance traveled for each unit of altitude lost
- lift/drag ratio is the aerodynamic efficiency ratio behind that behavior

That is the high-level aerodynamic baseline.

## 2. What glider computers usually show

In glider computers, a "current" glide metric is usually not a pure theoretical
polar number.

It is usually a recent measured performance number derived from recent flight
behavior over some time or distance window.

The exact window is product-specific.

## 3. Core distinction set

For XCPro review, these four concepts must stay separate:

- measured over-ground glide ratio
- measured through-air glide ratio
- theoretical polar L/D
- required L/D to a target

### Measured over-ground glide ratio

Typical meaning:

- recent ground distance traveled divided by altitude lost
- often derived from GPS path or groundspeed over a recent window

What wind does here:

- wind affects the number indirectly because groundspeed and ground track
  change with headwind, tailwind, drift, and crab angle
- no explicit wind-vector input is required for the metric to change

### Measured through-air glide ratio

Typical meaning:

- recent air distance traveled divided by altitude lost
- often based on TAS or an equivalent airspeed-based estimate

What wind does here:

- this metric needs an air-reference contract
- if direct airspeed is missing, wind may be needed to derive through-air
  motion from GPS motion

### Theoretical polar L/D

Typical meaning:

- still-air glide ratio from the active polar at a chosen speed

What wind does here:

- wind does not change the underlying still-air L/D
- wind matters later when that still-air model is used for speed-to-fly,
  arrival, or final-glide solves

### Required L/D

Typical meaning:

- distance remaining divided by available height to a target

What wind does here:

- wind is part of the navigation/glide solve
- this is not a "current efficiency" metric

## 4. Vendor examples

### XCSoar

Official manual:

- `https://download.xcsoar.org/releases/7.42/XCSoar-manual.pdf`

Useful manual semantics:

- `GR Inst` is an instantaneous glide ratio over ground
- it is based on ground speed divided by vertical speed
- the manual describes it over the last 20 seconds

Why this matters for XCPro:

- XCSoar explicitly distinguishes recent measured glide ratio from required
  glide ratio metrics such as `Final GR` and `Next GR`
- the "current" metric is operational and recent, not a polar reference
- it is still an over-ground measured concept, not a still-air aerodynamic
  efficiency claim

### Flytec / Brauniger 6030

Manual:

- `https://downloads.naviter.com/flytec/6030/Manuals/Flytec6030_EN_V321.pdf`

Useful manual semantics:

- the device distinguishes an actual glide ratio over ground
- it also distinguishes an "actual glide ratio through the air"
- the formulas differ because one uses groundspeed and the other uses TAS

Why this matters for XCPro:

- "Current L/D" is not universal terminology
- some systems explicitly separate ground-referenced and air-referenced
  measured glide ratios
- that split is directly relevant to the XCPro wind question

### LXNAV

Manual family entry point:

- `https://gliding.lxnav.com/manuals/`

Observed user-manual behavior in LXNAV documentation/search results:

- current glide ratio is also treated as a recent measured metric
- LXNAV documentation uses a much longer averaging window in at least one
  manual family than XCSoar does

Why this matters for XCPro:

- there is no single industry-standard averaging window for "current"
- the semantics must be locked by XCPro's owner path, not by vendor memory

## 5. Over-ground vs through-air

This is the most important terminology issue for XCPro.

If XCPro keeps `Current L/D` as an over-ground measured glide ratio:

- it stays close to what many pilots mean by "how am I gliding right now?"
- it remains a practical recent-performance number
- it is affected by wind, but only through the aircraft's ground path and
  groundspeed
- it does not need `WindState` as an explicit input

If XCPro redefines `Current L/D` as a through-air measured metric:

- it becomes more air-performance oriented
- it requires a stronger TAS / air-reference contract
- wind becomes part of the derivation or compensation path
- it moves semantically closer to aerodynamic efficiency than to simple recent
  glide performance over ground

Those are not just two implementations of the same metric.
They are two different pilot-facing meanings.

## 6. Why wind changes the meaning of the metric

This is the key point for external review.

If two gliders have the same still-air efficiency:

- a strong tailwind can make the over-ground glide ratio look much better
- a strong headwind can make the over-ground glide ratio look much worse

So when someone asks, "Should wind be part of Current L/D?", that can mean
two different things:

1. Should the metric simply continue to reflect the real ground result the
   pilot is getting now?
2. Or should the metric try to isolate air-relative efficiency from the wind?

Those lead to different instruments.

## 7. Practical distinction set for XCPro

For XCPro docs and UI, these terms should remain distinct:

- Current L/D:
  recent measured glide-efficiency metric from the live runtime owner path
- Polar L/D:
  theoretical still-air glide ratio from the active polar at current IAS
- Best L/D:
  best theoretical still-air glide ratio from the active polar
- Required glide ratio:
  glide ratio needed to reach an active target

These must not collapse into one another in docs, cards, or runtime fields.

## 8. Implications for XCPro naming

If XCPro keeps today's branch semantics:

- the label "Current L/D" effectively means a recent measured glide ratio over
  the recent flight path
- this is closer to over-ground performance wording than to strict
  air-relative aerodynamic wording

If XCPro makes wind or TAS an explicit part of the metric:

- the pilot-facing meaning changes
- the current `ld_curr` label may become ambiguous against:
  - `polar_ld`
  - `best_ld`
  - final-glide / required-L/D metrics

So the naming question is not separate from the wind question.

## 9. Research conclusion for XCPro

The safest XCPro interpretation is:

- "Current L/D" should mean a recent measured glide-efficiency metric
- the exact math window is XCPro-owned and must be documented from code
- XCPro must decide whether that measured metric is intentionally:
  - over-ground
  - through-air
- it must stay separate from:
  - `polar_ld`
  - `best_ld`
  - `final_gld`
  - waypoint/task required-glide metrics

That means XCPro should document Current L/D from branch truth first, and only
then decide whether the current implementation name is perfect or needs later
hardening.
