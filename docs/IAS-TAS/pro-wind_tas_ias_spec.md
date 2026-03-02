# Wind / TAS / IAS module for Android (Galaxy S22 Ultra) — **STRICT SI** implementation spec for Codex agent

> **Non‑negotiable rule:** **All internal calculations MUST use SI units**.  
> Convert to/from user display units (knots, °C, hPa) **only at the UI boundary**.

---

## 0) What you’re building

Add a “Wind + TAS + IAS” mode to an Android app (target device: Samsung Galaxy S22 Ultra) that:

1. Records **GPS ground speed + course** while the pilot flies **4 full 360° circles** at steady power/altitude.
2. From those circles, estimates:
   - **Wind speed** (display: kt)
   - **Wind direction** (display: deg **FROM**, meteorological)
   - **TAS** (True Airspeed, display: kt)
3. With wind/TAS known, estimates **IAS** (Indicated Airspeed, display: kt) using air density from **static pressure** (phone barometer) + **OAT** (user-entered).

**Important reality check**
- The phone does not have pitot/static. So IAS here is **computed/estimated**, not measured.
- Users must enter **OAT** (phone temperature is not valid in cockpit).
- Circles should be flown at near-constant altitude and airspeed.

---

## 1) Unit policy (STRICT)

### 1.1 Internal SI units (domain/math layer)
Use **only**:
- Speed / velocity components: **m/s**
- Pressure: **Pa**
- Temperature: **K**
- Density: **kg/m³**
- Time: **s** (or ms timestamps, but convert deltas to seconds)
- Angles for trig: **radians** (bearing/track may be stored as degrees but convert before trig)

**Never** store knots/°C/hPa in the domain layer.

### 1.2 UI/display units (presentation layer only)
- Display speed: **kt** (or user-selected)
- Display pressure: **hPa** (optional), but convert to Pa for computations
- Display temperature input: **°C**, convert to K immediately at boundary

### 1.3 Single conversion source of truth
Create `core/units/Units.kt` (or equivalent) and **only** use it for conversions:

```kotlin
object Units {
    const val MPS_TO_KT = 1.9438444924406
    const val KT_TO_MPS = 0.514444444444
    fun mpsToKt(v: Double) = v * MPS_TO_KT
    fun ktToMps(v: Double) = v * KT_TO_MPS
    fun hPaToPa(pHpa: Double) = pHpa * 100.0
    fun cToK(tC: Double) = tC + 273.15
}
```

---

## 2) User flow

### 2.1 Wind solve session
- User opens **Wind Solve** screen.
- Instructions: “Hold steady altitude and airspeed. Fly 4 full circles (same direction).”
- User taps **Start**.
- App records GPS velocity vectors continuously.
- App detects **circle completion** (auto) *or* user taps **Lap** at each circle end (manual fallback).
- After circle 4 completes:
  - App computes wind + TAS per circle.
  - App combines circles (weighted by fit quality).
  - App displays final results + quality metrics.

### 2.2 IAS estimate
- Results screen includes OAT input (°C).
- App reads baro pressure (hPa) and converts to Pa.
- App computes density ratio `σ` and `IAS_est`.

---

## 3) Data you must collect

### 3.1 GPS (required)
From Android Location updates (`FusedLocationProviderClient`):
- `groundSpeedMps` from `Location.getSpeed()` (m/s)
- `trackDeg` from `Location.getBearing()` (degrees, 0=N, 90=E)
- `tMillis`
- (optional) speed/bearing accuracy fields if available

**Sampling goal:** 2–5 Hz minimum.

### 3.2 Barometric pressure (required for IAS estimate)
From SensorManager:
- `Sensor.TYPE_PRESSURE` gives **hPa** → convert to **Pa** immediately at boundary.

### 3.3 OAT (required for IAS estimate)
User-entered:
- `oatC` (°C) → convert to `oatK` immediately at boundary.

---

## 4) Coordinate system & conversions (SI)

Use North-East axes:
- `vn` = component toward **true north** (m/s)
- `ve` = component toward **east** (m/s)

Convert from GPS speed + track:
- `s = groundSpeedMps`
- `θ = radians(trackDeg)`
- `vn = s * cos(θ)`
- `ve = s * sin(θ)`

Direction from vector (TO-direction):
- `toDeg = (degrees(atan2(ve, vn)) + 360) % 360`
  - using `atan2(east, north)` so 0° = north.

Wind reporting:
- Circle fit returns wind **TO** (air mass motion).
- Meteorological wind is **FROM**:
  - `windFromDeg = (windToDeg + 180) % 360`

---

## 5) Core math: wind + TAS from circle data (SI)

### 5.1 Principle
Ground velocity: `g(t) = a(t) + w`
- `g(t)` = ground velocity vector from GPS (m/s)
- `a(t)` = airspeed vector of magnitude ≈ TAS (m/s)
- `w` = wind vector (m/s)

During a 360° turn at constant TAS, `g(t)` samples form a circle in velocity-space:
- center = wind vector `(we, wn)` (m/s)
- radius = TAS (m/s)

### 5.2 Circle fit (recommended)
Represent each sample as:
- `x_i = ve_i` (m/s)
- `y_i = vn_i` (m/s)

Fit:
- `(x - a)^2 + (y - b)^2 = r^2`
  - center `(a,b)` = `(we, wn)` (m/s)
  - radius `r` = TAS (m/s)

Algebraic least squares:
- Solve for `D,E,F` in: `x^2 + y^2 + D*x + E*y + F = 0`

Sums:
- `Sx  = Σ x`
- `Sy  = Σ y`
- `Sxx = Σ x^2`
- `Syy = Σ y^2`
- `Sxy = Σ x*y`
- `Sz  = Σ (x^2 + y^2)`
- `Sxz = Σ x*(x^2 + y^2)`
- `Syz = Σ y*(x^2 + y^2)`
- `N   = count`

Solve:

```
[ Sxx  Sxy  Sx ] [ D ] = -[ Sxz ]
[ Sxy  Syy  Sy ] [ E ]   -[ Syz ]
[ Sx   Sy   N  ] [ F ]   -[ Sz  ]
```

Then:
- `we = a = -D / 2`
- `wn = b = -E / 2`
- `tasMps = r = sqrt(we*we + wn*wn - F)`

Compute:
- `windSpeedMps = hypot(we, wn)`
- `windToDeg = toDirectionDeg(we, wn)`
- `windFromDeg = (windToDeg + 180) % 360`

**Matrix solve**
- Implement a robust 3×3 Gaussian elimination with partial pivoting.
- Return null if singular/unstable.

### 5.3 Fit quality metrics (required)
Residual per sample:
- `d_i = hypot(x_i - we, y_i - wn)`
- `err_i = d_i - tasMps`

Metrics:
- `rmse = sqrt(mean(err_i^2))` (m/s)
- `cv = rmse / tasMps` (unitless)

Reject circle if:
- `cv > 0.08` (tunable)
- `N < 80` (tunable)
- `tasMps < 12.9` (≈ 25 kt) (tunable)

Weight for combination:
- `weight = 1 / max(rmse, 0.05)^2` (cap)

---

## 6) Detecting “one full circle” in the stream

### 6.1 Provide BOTH auto + manual
- AUTO: detect 360° from GPS track changes
- MANUAL: Lap button as fallback

### 6.2 Auto detection (track unwrap)
1. Read `trackDeg` each GPS sample.
2. Unwrap into `trackUnwrappedDeg`:
   - `delta = wrapTo180(trackDeg - prevTrackDeg)` in [-180, +180]
   - `trackU += delta`
3. Store `trackU_start` at circle start.
4. Circle complete when `abs(trackU - trackU_start) >= 340` (360 - margin).
5. Enforce:
   - minimum circle duration (e.g., ≥ 20 s)
   - consistent turn direction (sign of deltas)

---

## 7) Using 4 circles

### 7.1 Per-circle result
For each circle segment:
- build list of `(ve, vn)` in **m/s**
- fit circle → `CircleResult`
- keep `rmse`, `cv`, `N`

### 7.2 Combine circles (weighted)
Wind:
- `weFinal = Σ(we_k * w_k) / Σ w_k`
- `wnFinal = Σ(wn_k * w_k) / Σ w_k`

TAS:
- `tasFinalMps = Σ(tas_k * w_k) / Σ w_k`

Compute final wind:
- `windSpeedMps = hypot(weFinal, wnFinal)`
- `windFromDeg = (toDirectionDeg(weFinal, wnFinal) + 180) % 360`

Also compute confidence:
- std dev of TAS, wind speed
- circular std dev of direction (optional)

---

---

## 7.3 Rolling wind estimator (so it improves while you keep circling)

**Goal:** As the pilot continues to thermal (many circles), continuously refine wind (and therefore TAS) while resisting garbage data and adapting to changing wind.

### 7.3.1 Core idea
- Each accepted circle produces a measurement of wind components:
  - `z = [weMps, wnMps]ᵀ`
- Maintain an estimate:
  - `x = [weMps, wnMps]ᵀ`
- Update `x` only when a circle passes strict quality gates.
- Combine circles over time using either:
  - **EMA on components** (simple, robust), or
  - **2D Kalman filter** (more principled, handles variable confidence).

**Important:** Always filter **components**, not direction. Direction becomes stable automatically once components stabilize.

### 7.3.2 Quality gates (must-have)
Reject a circle if any of these fail (tune later):
- `cv > 0.08`
- `rmseMps > 1.5`  (≈ 3 kt)
- `N < 80`
- mean ground speed < 12.9 m/s (≈ 25 kt)
- excessive speed variation within circle:
  - `std(speedMps) > 1.5` m/s (≈ 3 kt)
- (optional if you have baro/GPS altitude):
  - `abs(deltaAltMeters) > 60` m (~200 ft) during circle

These stop thermalling “mess” from poisoning the estimate.

### 7.3.3 Confidence weight from fit error
Convert fit error into a scalar confidence:
- `sigmaMeas = clamp(rmseMps, 0.3, 3.0)`  
  (0.3 m/s ≈ 0.6 kt, 3.0 m/s ≈ 5.8 kt)
- Weight is effectively `1 / sigmaMeas²`

### 7.3.4 Option A (recommended first): EMA on wind components
Maintain:
- `weHat`, `wnHat` (m/s)
- `lastUpdateMillis`

Pick a time constant `tauSec`:
- tactical wind: `tauSec = 300` (5 min)
- strategic wind: `tauSec = 1800` (30 min)

At each accepted circle measurement `z` at time `t`:
1. Compute time step:
   - `dt = (t - lastUpdateMillis) / 1000.0`
2. Convert to smoothing factor:
   - `alphaBase = 1 - exp(-dt / tauSec)`
3. Modulate alpha by quality (smaller error => larger alpha):
   - `q = (0.8 / sigmaMeas)` clamped to `[0.25, 2.0]`
   - `alpha = clamp(alphaBase * q, 0.02, 0.35)`
4. Update:
   - `weHat = weHat + alpha * (weMeas - weHat)`
   - `wnHat = wnHat + alpha * (wnMeas - wnHat)`

This gives “gets better the longer you circle” and still adapts when wind changes.

### 7.3.5 Option B: 2D Kalman filter (upgrade path)
State: `x = [we, wn]ᵀ`
- Prediction: random walk (wind slowly changes)
  - `x_k|k-1 = x_k-1|k-1`
  - `P_k|k-1 = P_k-1|k-1 + Q`
- Measurement: circle solution
  - `z_k = x_k + v`, with `R` derived from `sigmaMeas`

Choose:
- `Q = qVar * I`, with `qVar = (0.05 m/s)² per second * dt`
  - Increase qVar if you want faster adaptation.
- `R = (sigmaMeas²) * I`

Update equations (standard Kalman):
- `K = P (P + R)⁻¹`
- `x = x + K (z - x)`
- `P = (I - K) P`

This automatically blends measurements by confidence.

### 7.3.6 Altitude bins (prevents wind shear contamination)
Wind often changes with altitude. To avoid averaging shear into nonsense:

Maintain **separate rolling estimators** per altitude band:
- bands: 0–300 m AGL, 300–900, 900–1800, 1800–3000, >3000 (example)
- Use GPS altitude if nothing else; baro altitude if you already compute it.

When a circle completes:
- determine `bandId`
- update that band’s estimator only
- UI can show:
  - “Wind at current band” (primary)
  - optional “Wind overall” (secondary)

### 7.3.7 Rolling TAS improvement (what to do with TAS)
TAS from each circle is `tasMps_k` (radius).

Options:
- **Simple:** Keep TAS as per-session average (4-circle result) and don’t roll it continuously.
- **Better:** Roll TAS with the same gating/EMA logic:
  - Use `rmse`-derived sigma as confidence.
  - Keep `tasHatMps` with its own EMA/Kalman.

**Do not** update TAS from low-quality thermalling circles unless you also enforce speed-stability gate (std(speedMps)).

### 7.3.8 UI/UX expectations
- Show “Wind improving…” indicator while accumulating accepted circles.
- Show count:
  - total circles observed
  - circles accepted (used for rolling estimate)
  - last circle quality (GOOD/OK/POOR)
- Provide toggle:
  - “Short-term wind (5 min)”
  - “Long-term wind (30 min)”
- If no good circles recently, freeze estimate and show “stale”.

### 7.3.9 Persistence
Persist rolling wind state so it survives app restarts:
- `weHat`, `wnHat`
- `P` if using Kalman
- per-band values if altitude bins enabled
- timestamp

Use DataStore (preferred) keyed by aircraft/profile.



## 8) IAS estimate from TAS (SI)

### 8.1 Air density from pressure + OAT
Constants (SI):
- `R = 287.05287` J/(kg·K)
- `rho0 = 1.225` kg/m³

Inputs:
- `pPa` = static pressure (Pa)
- `tK` = OAT (K)

Density:
- `rho = pPa / (R * tK)`
- `sigma = rho / rho0`

### 8.2 TAS ↔ IAS (approx)
Ignoring compressibility and instrument/position error:
- `EAS = TAS * sqrt(sigma)`
- `IAS_est ≈ EAS`

So (SI):
- `iasEstMps = tasMps * sqrt(sigma)`

Display:
- `iasEstKt = Units.mpsToKt(iasEstMps)`

### 8.3 Optional calibration
Setting:
- `iasScale` default `1.00`
- `iasEstMpsCal = iasEstMps * iasScale`

---

## 9) Kotlin module design (clean, testable)

### 9.1 Data classes (STRICT SI)
```kotlin
data class VelSampleSi(
    val tMillis: Long,
    val vnMps: Double,
    val veMps: Double,
    val speedMps: Double,
    val trackDeg: Double
)

data class CircleResultSi(
    val weMps: Double,
    val wnMps: Double,
    val tasMps: Double,
    val rmseMps: Double,
    val cv: Double,
    val n: Int
)

data class SessionResultSi(
    val weMps: Double,
    val wnMps: Double,
    val tasMps: Double,
    val windSpeedMps: Double,
    val windFromDeg: Double,
    val circlesUsed: Int,
    val qualityLabel: String
)
```

### 9.2 WindSolver API
```kotlin
interface WindSolverSi {
    fun reset()
    fun addSample(sample: VelSampleSi)
    fun markLap()
    fun tryAutoDetectCircle(): Boolean
    fun circleCount(): Int
    fun isComplete(): Boolean
    fun computeFinal(): SessionResultSi?
}
```

### 9.3 Android acquisition boundaries
- Location callback produces `speedMps` + `trackDeg` → convert to `vnMps/veMps` immediately.
- Pressure sensor produces `hPa` → convert to `Pa` immediately.
- OAT input produces `°C` → convert to `K` immediately.

**Rule:** After boundary conversion, **no non‑SI values travel into domain layer**.

---

## 10) UI requirements (MVP)
Wind Solve screen:
- Start/Stop
- Circle counter `0/4 … 4/4`
- Live readouts (display units):
  - groundspeed (kt)
  - track (deg)
  - pressure (hPa)
- Lap / Reset buttons
- Results:
  - Wind: `DDD° / SS kt` (FROM)
  - TAS: `TT kt`
  - IAS_est: `II kt`
  - Quality: GOOD/OK/POOR with rmse/cv

---

## 11) Test plan (must-have)

### 11.1 Synthetic unit tests (SI)
Generate known wind/TAS in SI:
- choose `(we, wn)` in m/s
- choose `tasMps`
- rotate airspeed direction 0..360
- `g = a + w`
- feed to solver
- assert wind and TAS within tolerance

Stress:
- non-uniform turn rate
- sample dropouts
- turbulence noise
- TAS variation ±5%

### 11.2 Field sanity checks
- Compare wind with METAR/avionics (expect differences with altitude)
- Compare IAS_est against aircraft IAS in stable cruise (use calibration)

---

## 12) In-app caveats (show these)
- Advisory only (not certified).
- Requires steady circles; turbulence reduces accuracy.
- OAT errors directly affect IAS_est.
- Baro needs stabilization after cockpit pressure changes.

---

## 13) Acceptance criteria
- Domain layer uses SI only (m/s, Pa, K, kg/m³). No knots/°C/hPa in math code.
- 4-circle session outputs wind + TAS reliably.
- Circle fit per circle runs < 50 ms on S22 Ultra.
- Bad circles rejected via cv/rmse thresholds.
- IAS_est updates immediately when OAT/pressure changes.
- Unit tests cover ≥ 10 simulated scenarios.

