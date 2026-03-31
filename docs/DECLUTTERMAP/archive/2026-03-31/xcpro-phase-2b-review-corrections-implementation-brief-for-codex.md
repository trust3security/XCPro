> Superseded on 2026-03-31.
> This one-off correction prompt was archived after the branch implemented the
> requested narrow fixes. Use `../../README.md` for current declutter status and
> `../../xcpro-phase-2a-close-proximity-declutter-plan.md` for the active
> ownership contract.

# XCPro Phase 2B Review Corrections Implementation Brief For Codex

Implement this correction pass only.

Use plan mode if available, but do not stop at planning.

---

## Goal

Implement a narrow correction pass for the packed-group traffic declutter work.

Fix only these three review findings:

1. fan-out radius must scale from the packed-group footprint
2. ADS-B packed-group seeds must match the actual rendered target set
3. OGN packed-group election must exclude off-screen traffic

Do not broaden scope beyond those fixes.

---

## Read First

Read these before coding:

1. `../../AGENTS.md`
2. `../ARCHITECTURE/PLAN_MODE_START_HERE.md`
3. `../ARCHITECTURE/ARCHITECTURE.md`
4. `../ARCHITECTURE/CODING_RULES.md`
5. `../ARCHITECTURE/PIPELINE.md`
6. `README.md`
7. `xcpro-close-proximity-declutter-brief.md`
8. `xcpro-phase-2a-close-proximity-declutter-plan.md`

Use the active DECLUTTERMAP docs above as the source of truth.
Do not revive archived declutter prompts or older implementation paths.

---

## Requested Outcome

Correct the current packed-group runtime behavior so that:

- selected-group fan-out is readable on real Android densities
- ADS-B packed-group label/fan-out state matches the actual emitted features
- OGN packed-group primary election and fan-out use only the visible/rendered
  target subset

This is a runtime display correction pass only.

---

## Findings To Fix

### 1) Scale fan-out radius from packed-group footprint

Current issue:

- `TrafficSelectedGroupFanoutLayout` uses fixed radii (`36px`, `+20px`)
- both overlays derive packed-group collision size from a minimum `40dp`
  footprint
- on xxhdpi/xxxhdpi devices the first fan-out ring can still overlap the anchor
  and sibling aircraft

Required behavior:

- derive fan-out radii from the packed-group collision footprint used for
  packed-group grouping
- the first ring must separate non-primary members from each other and from the
  anchored selected target on realistic Android densities
- keep layout deterministic
- keep the selected target anchored at truth

Primary file:

- `feature/traffic/src/main/java/com/example/xcpro/map/TrafficSelectedGroupFanoutLayout.kt`

### 2) Build ADS-B packed-group seeds from the actual renderable target set

Current issue:

- `AdsbTrafficOverlay` currently seeds packed-group logic from
  `targets.take(maxTargets)` before feature validation
- `buildAdsbTrafficOverlayFeatures(...)` still iterates until it finds
  `maxTargets` renderable features
- if early ADS-B entries are invalid, a later valid target can render but never
  participate in full-label admission or selected-group fan-out

Required behavior:

- build packed-group label and fan-out inputs from the same renderable subset
  that the ADS-B feature and leader-line builders can actually emit
- later valid targets must not lose label/fan-out eligibility because earlier
  invalid targets were skipped
- keep ordering deterministic
- preserve existing max-target behavior

Primary files:

- `feature/traffic/src/main/java/com/example/xcpro/map/AdsbTrafficOverlay.kt`
- `feature/traffic/src/main/java/com/example/xcpro/map/AdsbTrafficOverlayFeatureProjection.kt`

### 3) Exclude non-visible OGN traffic from packed-group election

Current issue:

- `OgnTrafficOverlay` currently seeds packed-group election/fan-out from all
  overlay targets
- `buildOgnTrafficOverlayFeatures(...)` later drops targets outside
  `visibleBounds`
- an off-screen OGN target can win primary-label election or consume a fan-out
  slot even though it is never rendered

Required behavior:

- compute packed-group primary election and selected-group fan-out from the
  visible/rendered subset only
- visible aircraft must not be suppressed or displaced around an off-screen
  aircraft
- keep behavior deterministic and local to runtime display logic

Primary files:

- `feature/traffic/src/main/java/com/example/xcpro/map/OgnTrafficOverlay.kt`
- `feature/traffic/src/main/java/com/example/xcpro/map/OgnTrafficOverlayFeatureSupport.kt`

---

## Hard Constraints

- No repository, ViewModel, domain, or persistence ownership changes.
- Do not mutate authoritative aircraft coordinates.
- Keep packed-group logic runtime-only in `feature:traffic`.
- Do not redesign ranking, clustering, regroup pills, compact labels, or
  cross-overlay OGN/ADS-B collision behavior.
- Do not change `MAX_TARGETS` policy except as needed to make packed-group
  inputs match the already-rendered subset.
- Do not introduce randomness.
- Do not use archived DECLUTTERMAP docs as the active contract.

---

## Ownership And Boundaries

Authoritative state owners stay unchanged:

- OGN traffic state -> `OgnTrafficRepository`
- ADS-B traffic state -> `AdsbTrafficRepository`
- selection state -> existing map traffic selection owners

This pass may only change render-local derived state:

- packed-group full-label admission inputs
- packed-group fan-out inputs
- render-local display coordinates

These derived values must remain:

- recomputable each render
- runtime-owned
- non-persisted
- non-authoritative

Dependency direction must remain:

`UI -> domain -> data`

No new bypasses.

---

## Reference Pattern To Reuse

Reuse existing owners and tests where possible:

- `feature/traffic/src/main/java/com/example/xcpro/map/TrafficPackedGroupLabelControl.kt`
- `feature/traffic/src/main/java/com/example/xcpro/map/TrafficSelectedGroupFanoutLayout.kt`
- `feature/traffic/src/test/java/com/example/xcpro/map/TrafficPackedGroupLabelControlTest.kt`
- `feature/traffic/src/test/java/com/example/xcpro/map/TrafficSelectedGroupFanoutLayoutTest.kt`
- `feature/traffic/src/test/java/com/example/xcpro/map/OgnTrafficOverlayFeatureTargetsTest.kt`
- `feature/traffic/src/test/java/com/example/xcpro/map/AdsbTrafficOverlayFeatureProjectionTest.kt`

Prefer the smallest safe patch over a shared-architecture rewrite.

---

## Likely Files To Touch

- `feature/traffic/src/main/java/com/example/xcpro/map/TrafficSelectedGroupFanoutLayout.kt`
- `feature/traffic/src/main/java/com/example/xcpro/map/AdsbTrafficOverlay.kt`
- `feature/traffic/src/main/java/com/example/xcpro/map/AdsbTrafficOverlayFeatureProjection.kt`
- `feature/traffic/src/main/java/com/example/xcpro/map/OgnTrafficOverlay.kt`
- `feature/traffic/src/main/java/com/example/xcpro/map/OgnTrafficOverlayFeatureSupport.kt`
- targeted tests under `feature/traffic/src/test/java/com/example/xcpro/map/`

If a helper is needed, keep it small and purpose-specific.

---

## Tests To Add Or Update

Add or update focused tests for:

- fan-out layout scales from collision footprint rather than fixed pixel radii
- ADS-B later-valid target still participates in full-label admission when an
  earlier invalid target is skipped
- ADS-B later-valid target still participates in selected-group fan-out when an
  earlier invalid target is skipped
- ADS-B leader-line generation stays aligned with the same renderable subset
- OGN off-screen target cannot win packed-group primary over a visible target
- OGN off-screen target cannot consume a selected-group fan-out slot
- existing deterministic layout behavior remains green
- existing no-selection / no-packed-group behavior remains green

Prefer narrow unit tests first, then small overlay-mapping tests.

---

## Verification

Run the smallest sufficient verification tier first:

Focused tests:

```bash
./gradlew :feature:traffic:testDebugUnitTest
```

Required gates:

```bash
./gradlew enforceRules
./gradlew assembleDebug
```

Escalate to broader tests only if the touched runtime path makes lighter checks
misleading.

---

## Acceptance Criteria

The correction pass is complete only if all are true:

1. fan-out radii scale from the packed-group collision footprint
2. selected-group fan-out is readable and separated on realistic densities
3. ADS-B packed-group label/fan-out state is derived from the actual renderable
   subset
4. later valid ADS-B targets are not silently excluded from packed-group logic
5. OGN packed-group primary election uses only the visible/rendered subset
6. off-screen OGN targets do not consume primary or fan-out slots
7. selected targets remain anchored at true coordinates
8. authoritative traffic coordinates remain unchanged
9. Phase 2A label suppression semantics remain intact
10. no new architecture-rule violations are introduced

---

## Required Output

Return:

1. concise plan
2. exact files changed and what each file owns
3. commands run
4. test and verification results
5. whether each of the 3 findings is fully resolved
6. remaining risks
7. stop
