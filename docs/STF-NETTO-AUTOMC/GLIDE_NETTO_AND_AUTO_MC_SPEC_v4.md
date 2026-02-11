
# Glide Netto & Auto-MacCready Specification (Phone-Only, No OAT)

This document defines **how glide-netto and Auto-MacCready (Auto-MC)** should work in XCPro when running on an Android phone (e.g. Samsung S22 Ultra) **without a pitot or real OAT sensor**, but **with reliable wind + TAS/IAS estimation from circling**.

It is written as an **implementation contract** for Codex: evaluate the current code against this spec and update where necessary. No ad-hoc behaviour.

---

## 1. Definitions (non-negotiable semantics)

### Glide-Netto
**Glide-netto = vertical movement of the airmass along a glide**, averaged over distance, not time.

- Positive â†’ rising airmass (street / convergence / weak lift)
- Negative â†’ sinking airmass

Glide-netto is **not a vario** and must never be treated as one.

### Auto-MacCready (Auto-MC)
Auto-MC estimates the **expected average climb rate in the next thermals**, based primarily on **recent achieved climbs**, not instantaneous sink/lift.

Glide-netto is used only as a **local correction** to speed-to-fly, not as the primary MC estimator.

---

## 2. Sensor Reality & Assumptions

- No pitot
- No real OAT
- Barometric pressure available
- GPS velocity vector available
- IMU available (bank / yaw rate)
- **Wind vector + TAS/IAS are reliably estimated during circling (20+ circles typical)**

This is sufficient for high-quality glide-netto and Auto-MC **if confidence gating is enforced**.

---

## 3. Air Density & IAS Handling (No OAT)

### 3.1 Pressure altitude
Use existing baro pipeline to compute pressure altitude.

### 3.2 ISA temperature (fallback)
If no OAT is available, use ISA:

```
T_ISA(Â°C) = 15 âˆ’ 6.5 Ã— (h_m / 1000)
T_K = T_ISA + 273.15
```

### 3.3 Air density

```
rho = p / (R Ã— T)
R = 287.05 J/(kgÂ·K)
rho0 = 1.225 kg/mÂ³
```

### 3.4 TAS â†’ IAS conversion (for polar lookup)

```
IAS â‰ˆ TAS Ã— sqrt(rho / rho0)
```

> **Do NOT use phone temperature sensors** (battery/CPU heat). ISA is the correct fallback and industry-standard.

### 3.5 Optional user correction
Advanced setting:
- **ISA temperature offset**: âˆ’10 Â°C â€¦ +10 Â°C (default 0)

---

## 4. Polar Sink Model

Polars are defined in **IAS/EAS**.

Minimum acceptable implementation:
- Quadratic or spline fit: `sink = f(IAS)` (sink negative)
- Ballast / bugs applied if available

Bank correction (optional, small effect on glide):
- Apply only if |bank| > 15Â°
- Clamp bank to avoid IMU spikes

---


## 4A. Glider Profiles & Polar Management (Multiple Gliders)

XCPro must support **different polars for different gliders** via a single, explicit **Glider Profile** source of truth.

### 4A.1 Required Glider Profile fields
Each glider profile must provide:
- `glider_id`, `name`
- `polar` (points or coefficients) defining `polar_sink(IAS, ballast, bugs)` where sink is negative (m/s)
- `IAS_min` (m/s): safe minimum (stall margin) for current configuration
- `IAS_max` (m/s): Vno/Vne or turbulence-limited maximum
- Optional: ballast model:
  - `ballast_max_kg` (or fraction)
  - mapping that adjusts polar for ballast
- Optional: `bugs_factor` or equivalent degradation model

### 4A.2 Single source of truth
All consumers must read from the active glider profile:
- Glide-netto uses the active profileâ€™s `polar_sink()` and configuration (ballast/bugs).
- Speed-to-fly uses the active profileâ€™s `IAS_min/IAS_max` bounds and `polar_sink()`.

No duplicated polar logic in multiple modules.

### 4A.3 Runtime switching
When the pilot changes glider profile:
- the active polar and limits must swap immediately and atomically (SSOT),
- downstream outputs must re-stabilize (smoothing) without transient spikes.


## 5. Measured Vertical Speed

Use **baro-derived vertical speed** as the primary signal.

Recommended:
- Robust differentiator
- Low-pass filter

```
w_meas = LPF(d(alt_baro)/dt, tau â‰ˆ 2.0 s)
```

Instant TE perfection is **not required** for glide-netto.

---

## 6. Wind & TAS Confidence Model

### 6.1 Wind/TAS solving
- Wind vector and TAS/IAS are solved during **circling** using heading diversity
- 20+ circles â‡’ high confidence

### 6.2 Confidence decay after leaving a thermal
Wind confidence must decay with **time or distance**:

Recommended:
- Half confidence every **5â€“10 minutes**, or
- Half confidence every **10â€“20 km** since last circling solve

When confidence is low:
- Glide-netto authority is reduced or frozen
- Speed-to-fly falls back to conservative behaviour

---

## 7. Glide-Netto Calculation

### 7.1 Raw glide-netto

```
V_air = V_ground âˆ’ Wind
IAS = f(TAS, density)

sink_expected = polar_sink(IAS)

glideNetto_raw = w_meas âˆ’ sink_expected
```

Because `sink_expected` is negative, subtracting it adds the gliderâ€™s expected sink back.

---

## 8. Glide Gating (Critical)

Glide-netto **must only update during straight glide**.

Update allowed only if:
- |bank| < 15â€“20Â°
- yaw rate / track curvature below threshold
- |dV/dt| small
- horizontal speed above thermal band
- wind confidence â‰¥ threshold

If gating fails:
- Freeze glide-netto, or
- Very slow decay toward zero

---

## 9. Distance-Based Averaging (NOT seconds)

Glide-netto must be averaged over **distance**, not time.

### 9.1 User-visible setting
**Glide-Netto Averaging Distance** (km):

Presets:
- 0.3 km
- 0.6 km (DEFAULT)
- 1.0 km
- 1.5 km
- 2.0 km

### 9.2 Distance â†’ time constant

```
tau_seconds = window_m / max(TAS_mps, TAS_min)
```

This automatically adapts:
- 80-kt glider â†’ longer tau
- 120-kt glider â†’ shorter tau

But both average the **same length of sky**.

### 9.3 Optional second output
- **Trend Glide-Netto**: fixed long window (â‰ˆ1.5 km)


### 9.4 Single Glide-Netto Policy (Cruise Only)

XCPro computes **one glide-netto value** intended for **cruise use**.

- Default averaging window: **0.6 km** (distance-based), with
  `tau_seconds = window_m / max(TAS_mps, TAS_min)`.
- Glide-netto is **advisory airmass information** for glide line evaluation.
- Glide-netto must be gated to straight glide and must respect wind confidence.

**Final glide rule (non-negotiable):**
- On final glide pages/modes, glide-netto may be displayed as *advisory*,
  but **must NOT influence speed-to-fly or MacCready**.
- Final-glide speed-to-fly uses `MC_base` only.



---


### 9.5 Optional Final-Glide Negative-Only Trend Bias (Conservative)

XCPro may optionally apply a **conservative final-glide bias** that reacts only to **persistent sink**.
This exists to handle cases where the airmass is systematically worse than assumed for minutes.

**Hard constraints (all must be true):**
- Wind confidence is **HIGH** (implementation: `wind_conf >= conf_high`, default `conf_high = 0.70`)
- Arrival margin is shrinking **faster than expected** (see 9.5.2)
- A **trend netto** is consistently negative:
  - `glideNetto_trend <= -0.3 m/s`
  - sustained for `>= 3â€“5 minutes` (default 4 minutes)

**Behavior constraint (non-negotiable):**
- This bias may **only increase** speed-to-fly (i.e., increase effective MC).
- It must never slow the glider down on final glide.

#### 9.5.1 Trend netto source
Use a long-window glide-netto (trend) suitable for final glide. If only one glide-netto is implemented, compute an internal trend via a long time window (>= 3â€“5 minutes) and do not display it as a twitchy needle.

#### 9.5.2 â€œArrival margin shrinking faster than expectedâ€
Define:
- `arrival_margin_m` = predicted arrival height above required (meters)
- `arrival_margin_rate_mps` = d(arrival_margin_m)/dt (m/s), low-passed (tau 30â€“60s)

Condition triggers when:
- `arrival_margin_rate_mps < -MARGIN_LOSS_RATE` for >= 60s
- Default `MARGIN_LOSS_RATE = 0.2 m/s` (12 m/min), tuneable

#### 9.5.3 Implementation sketch
When all conditions are true:
- Compute extra MC:
  - `mc_extra = clamp(abs(glideNetto_trend), 0, 0.5)`
- Apply:
  - `MC_eff_final = clamp(MC_base + mc_extra, MC_min_eff, MC_max)`

Otherwise:
- `MC_eff_final = MC_base`



## 10. Auto-MacCready (Auto-MC)

### 10.1 What Auto-MC estimates

```
MC â‰ˆ expected average climb in next thermals
```

It is **not** driven by instantaneous vario or glide-netto alone.

---

### 10.2 Thermal detection
Thermal segment when:
- |bank| > ~20Â° sustained
- average vertical speed > +0.3 m/s
- speed below cruise band

Thermal ends when:
- |bank| < ~15Â° sustained or
- speed returns to cruise

---

### 10.3 Thermal climb measurement
During a thermal compute:
- `w_avg` = median / trimmed mean of vertical speed
- duration
- height gain
- confidence score

Store last **3â€“6 thermals**.

---

### 10.4 Auto-MC update rule
Update **only at thermal exit**:

```
MC_raw = weighted_median(last thermals w_avg)
MC_auto = rate_limited(MC_auto â†’ MC_raw)
MC_auto = clamp(MC_min, MC_max)
```

Recommended limits:
- Max change rate: 0.2â€“0.4 m/s per minute
- Typical MC range: 0â€“4 m/s (configurable)

---

## 11. Glide-Netto + Auto-MC Interaction

Glide-netto modifies **speed-to-fly**, not baseline MC:

```
MC_effective = clamp(MC_auto âˆ’ glideNetto, 0, MC_max)
```

- Rising air â†’ slightly slower
- Sink â†’ slightly faster

This matches professional instrument behaviour.

---

## 12. UI & UX Requirements

- Display **Glide-Netto (m/s)** with confidence indicator
- Do NOT drive normal vario tones from glide-netto
- Optional: show **speed delta** (Â±kt) instead of raw MC math
- Show wind confidence (High / Medium / Low)

---

## 13. Failure & Degradation Rules

- Low wind confidence â†’ freeze or heavily smooth glide-netto
- No recent circling â†’ degrade confidence over time
- Sensor spikes â†’ gated out, never fed into MC or glide-netto

---

## 14. Validation & Testing (Required)

Use IGC replay mode to validate:
- Still air â†’ glide-netto â‰ˆ 0
- Known sink band â†’ glide-netto negative
- Weak lift line â†’ glide-netto positive
- 80 kt vs 120 kt â†’ same behaviour for same distance window
- Thermal exit â†’ Auto-MC updates once, smoothly

---

## 15. Non-Goals

- This spec does **not** attempt pitot-grade accuracy
- This spec prioritizes **correct behaviour and stability** over twitchy precision

---

**End of specification**

