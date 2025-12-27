# TAS (Phone-Only) Implementation Plan

## Goal
Provide a **pilot-facing TAS card** and a **stable TAS estimate** using **phone sensors only** (GPS + baro + IMU optional), matching XCSoar’s fallback behavior:
- If real airspeed is not available, but **ground speed + wind + flying** are available, compute **estimated TAS** from the **air vector magnitude**.

## XCSoar reference (what we’re mirroring)
- TAS fallback is implemented in `C:\Users\Asus\AndroidStudioProjects\XCSoar\src\Computer\BasicComputer.cpp` in `ComputeAirspeed()` (case 3).
- It only uses the “GS + wind” method when:
  - `basic.ground_speed_available`
  - `calculated.wind_available`
  - `calculated.flight.flying`
- It computes an air vector and sets:
  - `basic.true_airspeed = hypot(x_air, y_air)`
  - `basic.airspeed_real = false`
  - `basic.indicated_airspeed = true_airspeed / AirDensityRatio(altitude)` (when altitude is known)
- XCSoar wind direction convention is “**wind FROM**” (meteorological). This is why it **adds** wind to the ground vector in that code path.

## What we have today (this repo)
### Data availability
- Wind is produced by `feature/map/.../weather/wind`:
  - `CirclingWind` outputs wind using GPS-only circling detection (works with phone-only).
  - `WindEkfGlue` currently requires `CompleteFlightData.trueAirspeed` (problematic for phone-only; see below).
- TAS/IAS fields already exist and flow to the UI/card system:
  - `FlightMetricsResult.trueAirspeedMs` → `CompleteFlightData.trueAirspeed` → `RealTimeFlightData.trueAirspeed`
  - Cards already support IAS via card id `ias`; there is **no TAS card** yet.

### Key problems that block “XCSoar-like” TAS
1) **Wind vector sign/semantics mismatch**
   - Our `WindVector` is documented as **wind TO** (airmass velocity): `feature/map/.../weather/wind/model/WindVector.kt`.
   - But `WindEstimator.fromWind()` currently does `tas = |ground + wind|`, which only matches XCSoar if wind is stored as “wind FROM”.
   - The unit test `WindEstimatorTest.fromWind_returns_tas_and_indicated()` currently encodes the **wrong physics** for “wind TO”.

2) **Wind expiration is too aggressive for gliding use**
   - `WindRepository` drops wind after `STALE_MS = 120_000L` (2 minutes).
   - In real soaring you can have multi-minute glides between circles; clearing wind will make TAS (and anything derived from it) jump/disappear.
   - XCSoar keeps estimated wind valid for a long time (order of 1 hour for estimated wind validity).

3) **EKF wind currently risks being self-referential on phone-only**
   - `WindEkfGlue` requires TAS; today our pipeline often supplies `trueAirspeed = groundSpeed` as a fallback.
   - That makes the EKF effectively try to solve wind using a value that already “contains” wind, which biases wind toward zero and can destabilize TAS.
   - XCSoar only runs its EKF when airspeed is “real” (pitot/dynamic pressure), not estimated.

## Target behavior (phone-only)
1) Wind estimate comes from **circling wind** (GPS-only), and is held through glides.
2) TAS estimate is computed from:
   - GPS ground vector (track + ground speed)
   - minus wind-to vector (because our wind is “TO”)
3) TAS should be **stable**:
   - no null→0→value steps during normal flight
   - no hard drops because wind “expired” after 2 minutes

## Implementation plan (code changes)
### A) Fix TAS math + validity (domain)
1. Fix `WindEstimator.fromWind()` to use correct vector math for **wind-to**:
   - `air = ground - windTo`
   - `tas = |air|`
2. Update/replace `WindEstimatorTest` to match the corrected convention and add at least:
   - Pure headwind example (TAS > GS)
   - Pure tailwind example (TAS < GS)
   - Crosswind example (Pythagorean)
3. Stop treating GPS ground speed as “TAS valid”:
   - Keep ground speed as ground speed.
   - Only set `tasValid=true` when TAS is derived from wind (or another real airspeed method in the future).
   - Keep using `FusionBlackboard.resolveAirspeedHold()` to mask brief dropouts (10 s hold), but don’t fabricate TAS from GS.

### B) Make wind persistent enough for real flight
1. Increase `WindRepository.STALE_MS` to match soaring reality and XCSoar’s intent (suggest: **1 hour**).
2. Avoid toggling wind available/unavailable in a way that causes UI steps:
   - Prefer “keep last estimate until replaced” rather than clearing at a short age.
   - (No user-facing “STALE/source” indicators—just keep the value stable.)

### C) Disable EKF wind when TAS is not real (phone-only correctness)
1. Gate `WindEkfGlue.update()` usage so it only runs when the TAS input is **real** (future-proofing for pitot/TE devices).
2. For strict phone-only mode, rely on circling wind + hold.

## UI plan: add TAS card
1. Add a new card definition in `dfcards-library/src/main/java/com/example/dfcards/CardLibraryCatalog.kt`:
   - `id = "tas"`
   - `title = "TAS"`
   - `unit = "kt"` (actual unit conversion already comes from `UnitsPreferences`)
   - Category: `ESSENTIAL` (or `PERFORMANCE` if you prefer it not in the default essentials list)
2. Add formatter support in `dfcards-library/src/main/java/com/example/dfcards/CardDataFormatter.kt`:
   - Placeholder: treat like other speed cards.
   - Value: use `liveData.trueAirspeed` when `tasValid` and value > 0.1; otherwise show placeholder.
   - Keep the secondary label minimal (e.g., `null` or `"TAS"`). No “STALE” messaging.
3. (Optional) Add `"tas"` to one of the default templates in `dfcards-library/src/main/java/com/example/dfcards/FlightTemplates.kt` if we want it visible out-of-the-box.

## Acceptance criteria (what “done” means)
- When wind is available, TAS:
  - increases with headwind and decreases with tailwind (physically correct)
  - is smooth through normal circling/glide transitions (no sudden drops after 2 minutes)
- TAS card:
  - displays a stable numeric TAS when valid
  - shows placeholder when TAS truly can’t be estimated (e.g., before any wind has been computed)
- Unit tests cover TAS vector math and prevent regressions.

