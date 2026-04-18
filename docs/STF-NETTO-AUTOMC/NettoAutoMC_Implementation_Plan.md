
# Netto / Auto-MC / Speed-to-Fly Implementation Plan (Minimal v1)

Status: In progress (implementation landed, tests complete)
Owner: XCPro team
Date: 2026-02-01

This plan is required by CODING_RULES.md (Section 15A). It ties the specs to code changes,
defines SSOT ownership, and lists required tests. This is a "minimal viable, not dumb" build.

Related specs:
- GLIDE_NETTO_AND_AUTO_MC_SPEC.md
- SPEED_TO_FLY_SPEC.md
- XC_PRO_EXECUTION_ORDER.md

## 0) Scope (Minimal v1)
Keep only five core pieces:
1) Active glider profile (polar + IAS_min/IAS_max + ballast/bugs).
2) Wind estimate + single confidence number.
   - Updated in circles only.
   - Time-only decay after last circling solve (half-life 7 minutes).
3) IAS from TAS + ISA density (no OAT).
4) Glide-netto (single output).
   - Gated to straight flight.
   - Distance window default 0.6 km.
5) Speed-to-fly.
   - MC = manual or Auto-MC.
   - Numeric search over polar.
   - Smoothing + rate limit.

## 1) Non-goals for v1
- No EKF wind selection (circling only).
- No distance-based wind decay (time-only).
- No trend glide-netto output (single window only).
- No auto mode detection (manual CRUISE / FINAL_GLIDE only).
- No OAT integration.

## 2) SSOT Ownership and Flow
Required architecture: UI -> domain -> data.

SSOT owners:
- Wind estimate + confidence: WindSensorFusionRepository (data).
- Active glider profile and polar: GliderRepository (data).
- IAS/TAS estimation, glide-netto, Auto-MC, speed-to-fly: domain use cases.
- UI state: ViewModels only (map / vario screens).

Data flow:
Sensors -> WindSensorFusionRepository -> WindState + WindConfidence
Sensors -> FlightDataCalculatorEngine -> CalculateFlightMetricsUseCase
GliderRepository -> StillAirSinkProvider / PolarCalculator -> domain
CalculateFlightMetricsUseCase -> FlightMetricsResult -> FlightDisplayMapper
-> CompleteFlightData -> RealTimeFlightData -> UI + cards

## 3) Core Decisions (locked)
- New output: "Levo Netto" (glide-netto) is separate from existing netto.
- "Levo Netto" card label: LEVO NETTO.
- Missing wind -> show NO WIND.
- Missing polar -> show NO POLAR.
- IAS bounds default: use polar min/max, then clamp to known speed limits (VNE/VA/etc) when available.
- Wind confidence decay: time-only, half-life 7 minutes after last circling solve.
- Glide-netto distance window: 0.6 km (single output).
- Modes: manual only (CRUISE / FINAL_GLIDE toggle).

## 3.1) Implementation Status (as of 2026-02-01)
- [x] Wind confidence: circling-only solves, time-only decay, confidence recomputed each GPS tick.
- [x] IAS/TAS: ISA density conversion in WindEstimator; polar sink uses IAS throughout.
- [x] IAS bounds: GliderConfig iasMin/iasMax optional; defaults from polar range; clamp max to speed limits (VNE/VA/VRA/VW/VT).
- [x] Levo Netto: straight-flight gated, 0.6 km distance window, wind confidence >= 0.1 required.
- [x] Auto-MC: updates on thermal exit, median of last 6 thermals, rate limit.
- [x] Speed-to-fly: numeric polar search, MC_eff uses glide-netto with wind confidence weighting, smoothing + rate limit.
- [x] UI: new LEVO NETTO card (Performance), vario secondary line replaced with Levo Netto, red NO WIND / NO POLAR.
- [x] Settings: Auto-MC toggle, manual MC slider disabled when Auto-MC enabled.

## 4) Data Model Changes
Add new fields (ASCII only; actual names below):
- FlightMetricsRequest: macCreadySetting, autoMcEnabled, flightMode.
- FlightMetricsResult: levoNettoMs, levoNettoValid, levoNettoHasWind, levoNettoHasPolar,
  levoNettoConfidence, autoMcMs, autoMcValid, speedToFlyIasMs, speedToFlyDeltaMs,
  speedToFlyValid, speedToFlyMcSourceAuto, speedToFlyHasPolar.
- CompleteFlightData: levoNetto*, autoMacCready*, speedToFly*.
- RealTimeFlightData: levoNetto*, autoMacCready*, speedToFly*.

Add wind confidence in WindState:
- WindState: confidence [0.0..1.0], lastCirclingClockMillis.

Add optional IAS bounds in glider config:
- GliderConfig: iasMinKmh, iasMaxKmh.

## 5) Implementation Phases

Phase 1 - Wind confidence (circling only, time decay) [DONE]
- Update WindSensorFusionRepository to:
  - Publish wind only from CirclingWind results.
  - Store lastCirclingClockMillis (monotonic).
  - Compute confidence = baseQuality * decay.
  - Decay: conf = base * 0.5^(dt / 7min).
- Keep wall time out of domain logic (use monotonic time).

Phase 2 - IAS/TAS from wind + ISA density [DONE]
- Use WindEstimator to compute TAS from ground vector - wind vector.
- Convert TAS -> IAS using ISA density ratio (no OAT).
- Ensure polar sink uses IAS, not TAS.

Phase 3 - Glide-netto (Levo Netto) [DONE]
- New domain calculator:
  - Input: baro vertical speed (w_meas), IAS, wind confidence, flight state.
  - Gating: straight flight only (not circling, low turn rate, speed ok).
  - Distance window 0.6 km: tau = window_m / max(TAS, TAS_min).
  - Output: single glide-netto value + valid flag.
- Feed through FlightMetricsResult -> UI.

Phase 4 - Auto-MC [DONE]
- Thermal detection from existing circling + vertical speed.
- Update Auto-MC only at thermal exit.
- Store last 3-6 thermals; weighted median; rate limit.

Phase 5 - Speed-to-fly [DONE]
- Numeric search over polar using MC_eff = MC_base - glideNetto.
- Clamp to IAS_min/IAS_max from glider profile.
- Smoothing and rate limiting.
- Manual mode only (CRUISE / FINAL_GLIDE toggle).

Phase 6 - UI and Cards [DONE]
- Add "levo_netto" card to Performance (label LEVO NETTO).
- Update CardFormatSpec for rendering, NO WIND / NO POLAR labels.
- Replace the Vario secondary line with Levo Netto (no third line).
- Use red text when Levo Netto is unavailable (NO WIND / NO POLAR).

## 6) Tests (Required)
Unit tests:
- [x] Wind confidence decay with time-only half-life.
- [x] Glide-netto gated during circling / turning.
- [x] Glide-netto steady straight flight in still air -> ~0.
- [x] Auto-MC updates only at thermal exit.
- [x] Speed-to-fly search respects IAS bounds and smoothing.

Replay tests:
- [x] Still air replay -> Levo Netto ~0.
- [x] Sustained sink/lift bands -> Levo Netto negative/positive.
- [x] After wind confidence decays -> glide-netto authority drops.

## 7) Risks and Mitigations
- Wrong time base: enforce monotonic time in domain logic.
- Polar missing: return NO POLAR and mark invalid.
- Wind missing: return NO WIND and mark invalid.
- UI jitter: enforce smoothing + rate limiting for speed-to-fly.

## 8) Acceptance Criteria
- Levo Netto appears as a separate card and in Levo Vario UI.
- Glide-netto is distance-windowed, straight-flight gated, wind-confidence aware.
- Speed-to-fly is stable, respects IAS_min/IAS_max, and uses MC_base.
- All tests in Section 6 pass.

## 9) Follow-up Plan: XCSoar-style always-visible Levo Netto + wind footer (Planned)
Goal: match XCSoar behavior so Levo Netto always shows a numeric value, even with no wind or polar,
while preserving `levoNettoValid` for glide-netto gating. Add a wind speed/direction footer on the card.

Implementation steps:
1) Domain fallback (LevoNettoCalculator)
   - File: feature/map/src/main/java/com/trust3/xcpro/sensors/domain/LevoNettoCalculator.kt
   - When wind or polar is missing, return a fallback value instead of freezing or blanking:
     - sink = sinkAtSpeed(IAS) if polar and IAS valid; else sink = 0.
     - value = w_meas - sink (w_meas is baseline vario).
   - Keep `valid=false` unless straight flight + wind confidence + polar + speed gates pass.
   - Preserve distance-window smoothing only for valid glide-netto updates.

2) Flight metrics wiring
   - File: feature/map/src/main/java/com/trust3/xcpro/sensors/domain/CalculateFlightMetricsUseCase.kt
   - Keep `levoNettoHasWind` / `levoNettoHasPolar` as availability flags.
   - Ensure `levoNettoMs` is set from the (possibly fallback) calculator output,
     but `levoNettoValid` remains true only for glide-netto.

3) Card formatting (primary always numeric, footer is wind)
   - File: dfcards-library/src/main/java/com/example/dfcards/CardFormatSpec.kt
   - LEVO_NETTO primary value always uses `liveData.levoNetto`.
   - Secondary value shows wind footer when available:
     - Format: "<speed><unit>/<dir>Deg" (example: "8kt/287Deg").
     - Use UnitsFormatter.speed and normalize direction 0..359.
   - If polar missing -> show "NO POLAR" in footer (takes precedence).
   - If wind missing -> show "NO WIND" in footer.

4) Levo Vario UI (secondary line remains numeric)
   - File: feature/map/src/main/java/com/trust3/xcpro/map/ui/OverlayPanels.kt
   - Keep numeric Levo Netto on the secondary line regardless of validity.
   - Keep red error tint when wind or polar is missing.

5) Tests
   - feature/map/src/test/java/com/trust3/xcpro/sensors/domain/LevoNettoCalculatorTest.kt
     - Add cases for no wind / no polar -> value uses w_meas - sink (sink=0 if polar missing), valid=false.
   - dfcards-library/src/test/java/com/example/dfcards/CardFormatSpecTest.kt
     - Add LEVO_NETTO cases for footer formatting and NO WIND/NO POLAR fallbacks.

6) Docs
   - Add `docs/Cards/levo_netto/LevoNetto.md` (end-to-end flow + UI display rules).



