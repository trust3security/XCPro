
# Speed-to-Fly Specification (Uses Polar + Auto-MC + Glide-Netto)

This document defines how XCPro must compute **recommended cruise speed (speed-to-fly)** for a sailplane using:
- a **polar curve** (sink vs IAS),
- **Auto-MacCready (MC_auto)** (expected thermal strength),
- **glide-netto** as a local airmass correction,
- phone-derived **IAS/TAS** estimates (no pitot),
- strong **confidence gating** to avoid oscillation.

This is an implementation contract for Codex.

---

## 1. Inputs, Outputs, Units

### Inputs
- `IAS_mps` : current IAS estimate (m/s) (preferred), else `IAS ~ TAS*sqrt(rho/rho0)`
- `V_ground_vec_mps` : GPS ground velocity vector (m/s)
- `Wind_vec_mps` : wind vector (m/s)
- `wind_conf` : [0..1]
- `w_meas_mps` : baro-derived vertical speed (m/s)
- `glideNetto_fast_mps` : distance-window glide-netto (tactical), gated to straight glide
- `glideNetto_trend_mps` : optional long-window trend glide-netto
- `MC_auto_mps` : Auto-MC baseline (m/s), updated only at thermal exit
- `polar_sink(IAS_mps, ballast, bugs)` : expected sink (negative m/s)
- `ballast_state`, `bugs_state` : optional
- `mode` : {CRUISE, FINAL_GLIDE, SEARCH}
- `pilot_MC_override_mps` : optional manual MC (if enabled)

### Outputs
- `IAS_target_mps` : recommended cruise IAS (m/s)
- `delta_speed_kt` : IAS_target - IAS_current (kt)
- `stf_confidence` : {HIGH, MED, LOW} + numeric confidence
- Optional: `IAS_target_smooth_mps` : smoothed command for UI stability

---


## 1A. Glider Profiles & Polar Selection (Multiple Gliders)

Speed-to-fly must be computed using the **currently selected glider profile**.

Required behaviour:
- The active glider profile owns:
  - the polar used by `polar_sink(IAS, ballast, bugs)`
  - `IAS_min` and `IAS_max` bounds
  - ballast/bugs configuration and limits
- The numerical search in Section 4 must clamp candidate speeds to:
  - `[IAS_min, IAS_max]` for the active glider profile
- UI units (kt) must be converted at the edges only; internal math stays in SI.


## 2. Core Principle (time-per-distance minimization)

Implement MacCready speed-to-fly numerically by minimizing **total time per distance**:

For candidate speed `V`:
- glide time per unit distance: `t_glide = 1 / V`
- altitude lost per unit distance: `h_lost = sink_down(V) * t_glide`
  - where `sink_down(V) = max(0, -polar_sink(V))`  (positive downward sink)
- climb time per unit distance (expected in next thermal): `t_climb = h_lost / MC_eff`

Total time per distance:
```
J(V) = t_glide + t_climb
     = (1/V) * (1 + sink_down(V) / MC_eff)
```

Choose `V` that **minimizes** `J(V)` over allowed IAS range.

This avoids fragile derivative/tangent math and works with any polar representation.

---

## 3. Effective MacCready (Auto-MC + Glide-Netto)

### 3.1 Baseline MC selection
- If manual MC override enabled: `MC_base = pilot_MC_override`
- Else: `MC_base = MC_auto`

### 3.2 Glide-netto correction (local airmass)
Use glide-netto to modify MC for speed-to-fly:

```
MC_eff = clamp(MC_base - glideNetto_fast, MC_min_eff, MC_max)
```

Recommended:
- `MC_min_eff = 0.1 m/s` (prevents divide-by-zero and insane slowdowns)
- `MC_max` user-configurable (typical 4.0 m/s)

Interpretation:
- Rising air (positive glide-netto) -> MC_eff decreases -> recommended speed decreases
- Sink (negative glide-netto) -> MC_eff increases -> recommended speed increases

### 3.3 Authority limiting by confidence
If `wind_conf` is below threshold or glide gating fails:
- reduce glide-netto authority smoothly:

```
alpha = clamp((wind_conf - conf_low) / (conf_high - conf_low), 0..1)
glideNetto_used = alpha * glideNetto_fast
MC_eff = clamp(MC_base - glideNetto_used, MC_min_eff, MC_max)
```

Default thresholds:
- `conf_low = 0.35`
- `conf_high = 0.70`

When `alpha=0`, speed-to-fly uses MC_base only.

---

## 4. Candidate Speed Search (Numerical)

### 4.1 Speed bounds
Define bounds in IAS:
- `IAS_min` = stall margin (e.g., 1.3*stall, flap-dependent) + safety margin
- `IAS_max` = Vne/Vno limit (profile-dependent) and turbulence limits

Also clamp to a practical app range (e.g., 60-160 kt equivalent).

### 4.2 Discrete search
Evaluate `J(V)` on a grid:
- 1-2 kt step is sufficient
- Find the minimum
- Optionally refine locally (parabolic fit) for smoothness

### 4.3 Handling MC near zero
If `MC_base` (and thus `MC_eff`) is very small:
- treat as "best L/D mode"
- choose speed that minimizes sink per distance (max L/D), i.e. minimize `sink_down(V)/V`

---

## 5. Mode-Specific Behaviour

### 5.1 CRUISE mode
- Use full algorithm with `MC_eff`
- Use glide gating (straight flight required for glide-netto influence)

### 5.2 SEARCH mode
- Bias toward slightly slower speeds (more time in lift)
- Cap speed-up in sink to avoid constant chasing
Example:
- `MC_eff_search = clamp(MC_eff, 0.2, 2.5)`

### 5.3 FINAL_GLIDE mode
Final glide is safety-critical. Be conservative:
- Option A (recommended): ignore glide-netto in final glide (use MC_base only)
- Option B: allow only trend glide-netto with reduced authority:
  - `glideNetto_used = 0.5 * alpha * glideNetto_trend`

---

## 6. Smoothing & Hysteresis (Prevent Oscillation)

Speed commands must not jump around.

### 6.1 Output smoothing
Apply a low-pass to the target speed:
- `tau_speed_cmd` = 2-6 s (UI stability)
- Use slower smoothing when confidence is low

### 6.2 Rate limiting
Limit change rate:
- e.g., max +/-2 kt/s on output

### 6.3 Deadband
To avoid "hunting" around the optimum:
- if |delta| < 2 kt, display 0 or "hold"

---

## 7. Data Quality Gates (Non-negotiable)

Do not apply glide-netto correction unless:
- |bank| < 15-20deg
- yaw rate / curvature small
- |dV/dt| small
- wind_conf >= conf_low
- recent wind solution exists (time/distance decay not exhausted)

If any fail:
- `alpha = 0` (glide-netto has zero authority)
- speed-to-fly uses MC_base only

---

## 8. UI Requirements

- Display:
  - `MC_base` (Auto or Manual)
  - `MC_eff` (after glide-netto correction) (optional, advanced)
  - `IAS_target` and `delta_speed`
  - confidence indicator: HIGH/MED/LOW
- Never show wildly changing deltas; smoothing required
- Provide a "pilot sanity" mode:
  - "Conservative" (less aggressive speed-up in sink)

---

## 9. Validation Plan (IGC Replay Required)

Test cases:
1. Still air glide (no lift/sink): glide-netto ~0, speed-to-fly matches MC_base.
2. Sustained sink band: glide-netto negative, recommended speed increases smoothly.
3. Sustained lift line: glide-netto positive, recommended speed decreases smoothly.
4. 80 kt vs 120 kt gliders (different polars): same qualitative behaviour; no oscillation.
5. Wind confidence drop: glide-netto authority fades to zero; speed-to-fly remains stable.
6. Final glide mode: conservative behaviour per mode rules.

---

**End of specification**

