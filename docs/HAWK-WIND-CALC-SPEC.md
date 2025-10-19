# HAWK Wind Calculation — Feature, Functionality & UI Spec

> Purpose: Define the scope and UI needed to implement a HAWK‑style real‑time 3D wind estimation feature (for gliding) so a coding agent (e.g., Codex) can build it end‑to‑end.

---

## 1) Product Summary

**Problem**: Pilots need reliable, instantaneous wind (horizontal + vertical) to center thermals, plan cruising, and manage energy — without waiting for long averaging windows or doing circling‑only estimates.

**Solution**: Sensor‑fusion using an Extended Kalman Filter (EKF) to jointly estimate the 3D air‑mass motion from onboard sensors, delivering:
- Real‑time 3‑axis wind (speed & direction, including vertical component / w). 
- Instantaneous and averaged wind readouts.
- Dual‑needle vario (TEK vs. EKF estimates) to compare/validate.
- Attitude/AHRS view leveraged from the filter’s state.

**Outcomes**
- Instant wind; no circling required; resilient to gusts/turbulence.
- “No compensation” mode: independent of pilot speed changes; more accurate netto.

---

## 2) System Capabilities (Feature List)

1. **3D Wind Estimation**
   - Components: North (u), East (v), Vertical (w).
   - Update rate: 5–20 Hz (target 10 Hz visible refresh; internal filter can run faster).
   - Latency: < 200 ms UI latency target.
   - Units: m/s (speed, vertical); km/h or kt (convertible); direction in degrees true.
   - Outputs: Instantaneous vector + rolling average vector.

2. **Wind Visualization (Wind Screen)**
   - Blue arrow = instantaneous wind vector (magnitude = length, direction = bearing).
   - White arrow = averaged wind vector (configurable averaging window).
   - Numeric overlays: **WS** (wind speed), **WD** (wind direction), **WV** (vertical wind w in m/s), **Stability** (filter confidence 0–100%).
   - Option: trail/ghost arrows for last N seconds to show variability.

3. **Dual‑Needle Variometer**
   - Red needle = TEK‑derived vario (traditional total‑energy).
   - Light‑blue needle = EKF‑derived airmass vertical (netto/airmass climb rate).
   - Scale: ±10 kts or ±5 m/s configurable; tick density adaptive.
   - Delta banner: EKF − TEK (numeric) to expose compensation/lag differences.

4. **AHRS/Attitude View**
   - Artificial horizon using filter’s attitude state (roll/pitch, optional slip/ball).
   - Overlay: turn rate, bank angle, G‑estimate.

5. **Averaging & Smoothing Controls**
   - Rolling average window presets: OFF, 5 s, 10 s, 20 s, 30 s.
   - Outlier rejection (Hampel or median filter) toggle for gust spikes.

6. **Calibration & Alignment**
   - 3‑axis sensor alignment workflow (on‑ground + in‑flight fine tune):
     - Gyro bias & scale
     - Accelerometer bias (g alignment)
     - Magnetometer (if used) hard/soft‑iron
     - Airframe axis vs. sensor axis offsets
   - Static pressure source delay characterization (baro lag compensation).

7. **Data Logging**
   - 10 Hz log of states and innovations: {timestamp, u, v, w, wind_speed, wind_dir, confidence, needles (TEK/EKF), roll/pitch, IAS/TAS, GPS, baro altitude}.
   - Export: IGC enhancements (K records) and CSV.

8. **Hardware/Platform Support**
   - Works with IMU (3‑axis gyro + accel), baro, GNSS; optional airspeed (IAS/TAS) + magnetometer.
   - Runs on target nav/vario hardware or Android device with equivalent sensors.

9. **Safety/Degradation Modes**
   - Sensor health indicators; graceful fallback if a source drops (e.g., GNSS outage).
   - Confidence badge & UI watermark when degraded.

---

## 3) Functional Requirements

### 3.1 Inputs
- **IMU**: ω (p,q,r), specific force (ax, ay, az) at 100–200 Hz.
- **Baro**: Static pressure → altitude rate (filtered) at ≥10 Hz.
- **GNSS**: Ground speed vector, position, and climb at 1–10 Hz.
- **Airspeed** (optional but recommended): IAS/TAS for relative wind constraints.
- **Temperature** (optional): For air‑density/true‑airspeed correction.

### 3.2 States & Outputs (EKF)
- **Vehicle attitude & biases**: quaternion/roll‑pitch‑yaw, gyro/accel biases.
- **Wind state**: [u, v, w] in earth frame.
- **Scale factors & delays** (optional augmented states) for baro/airspeed.
- **Derived**: wind speed |W|, wind direction (0–360°), netto climb (w), confidence.

### 3.3 Processing Pipeline
1. **Pre‑filtering**: de‑spike sensors; synchronize to common time base.
2. **Prediction**: propagate attitude & wind with process noise tuned for turbulence.
3. **Measurements**:
   - GNSS velocity residuals → constrain sum of aircraft airspeed vector + wind.
   - Baro/vertical rate → constrain vertical channel.
   - (If available) IAS/TAS → relative wind magnitude constraint.
4. **Update**: EKF innovation gating; Mahalanobis gating for outliers.
5. **Post‑processing**: compute instantaneous vs. averaged wind; confidence index.

### 3.4 Performance Targets
- Horizontal wind accuracy: ≤2–3 kt (steady), ≤5 kt (gusty) typical.
- Direction accuracy: ≤10° steady.
- Vertical airmass (w): ≤0.3 m/s steady air.
- Convergence: <5 s after significant maneuver.

---

## 4) UI/UX Specification

### 4.1 Wind Screen (Primary)
- **Canvas**: circular compass rose (360°), aircraft at center.
- **Arrows**:
  - Blue arrow: from aircraft center, pointing TO where the wind is blowing FROM (standard meteorological convention). Length scales with |W|; non‑linear scaling to keep on screen.
  - White arrow: same but averaged vector.
- **Numerics** (top row): `WS: 18 kt` · `WD: 235°` · `WV: +0.6 m/s` · `CONF: 82%`.
- **Badges**: GNSS, IMU, BARO, IAS status (green/amber/red).
- **Controls** (touch/rotary): AVG window button; “Freeze” snapshot; Share.
- **Optional**: wind rose history (last 60 s) as faded mini‑arrows.

### 4.2 Dual‑Needle Variometer
- **Scale**: user‑selectable ±2/±5/±10 m/s (or ±4/±10/±20 kt).
- **Needles**:
  - Red = TEK vario.
  - Light‑blue = EKF/airmass vertical (w).
- **Labels**: digital readouts under dial: `TEK +1.8`, `EKF +2.1` m/s.
- **Delta** chip: `Δ +0.3` m/s; tap to open explanation.
- **Audio** (future): tone from EKF w (frequency w/ deadband & sink alarm).

### 4.3 AHRS View
- **Horizon tape** with pitch ladder, bank ticks, slip/ball.
- **Overlays**: turn rate, bank angle, load factor.
- **Context**: small wind bug (blue) in the corner with numeric WS/WD.

### 4.4 Settings Pages
- **Sensor Alignment**: step‑by‑step wizard with live residuals.
- **Averaging**: set EKF process noise presets (Calm / Normal / Turbulent). 
- **Units**: m/s / kt / km/h; degrees True vs. Magnetic (if magnetometer).
- **Data**: logging on/off, sample rate, export.
- **Warnings**: thresholds for confidence & sensor loss.

### 4.5 States & Empty‑States
- **Startup**: show “Acquiring wind…” progress with sensor badges.
- **Degraded**: grey out blue arrow; show “GNSS degraded — averaged only”.
- **No‑GNSS Mode**: keep AHRS and TE vario; suspend wind vector and flag UI.

---

## 5) Algorithms (Engineering Outline)

### 5.1 State Vector (example)
```
x = [q_wxyz, b_gx, b_gy, b_gz, b_ax, b_ay, b_az, u, v, w]
```
- **q_wxyz**: attitude quaternion.
- **b_g*** / **b_a***: gyro/accel biases.
- **u,v,w**: wind components in NED.

### 5.2 Process Model
- Attitude: quaternion kinematics from gyro minus bias.
- Wind random walk: \.dot{u,v,w} ~ N(0, Q_w) tuned by turbulence preset.

### 5.3 Measurement Models (examples)
- **GNSS velocity**: `v_gps ≈ R_body_to_earth * v_air_body + [u v w]` with `|v_air_body|` constrained by IAS/TAS (if present).
- **Baro climb**: `w_baro ≈ w + ε` (after baro lag compensation).
- **Optional sideslip** (if probe): lateral airspeed to help crosswind.

### 5.4 Filter Details
- EKF with discrete‑time prediction at IMU rate; measurement update at sensor rates.
- Innovation gating with χ² thresholds; adaptive R tuning when turbulence rises.
- Confidence metric from normalized innovation squared (NIS) and observability.

### 5.5 TEK vs. EKF Vario
- **TEK** (reference): uses energy conservation and speed compensation.
- **EKF**: provides airmass vertical (netto) directly from state `w` (less sensitive to stick movement and gust “false lift”).

---

## 6) Data Model & APIs

### 6.1 Internal Topics (pub/sub style)
- `imu.raw` {ax,ay,az,p,q,r, dt}
- `baro.altitude` {h_msl, climb_rate}
- `gnss.vel` {vn,ve,vd, speed, cog}
- `airspeed.tas` {tas, quality}
- `wind.ekf` {u,v,w, speed, dir, conf}
- `vario.tek` {vz}
- `vario.ekf` {vz}
- `attitude` {roll,pitch,yaw, valid}

### 6.2 UI Bindings
- Wind screen subscribes to `wind.ekf`; dual‑needle subscribes to both `vario.*`.
- Settings write to `config.*` and trigger `filter.reset` when needed.

### 6.3 File I/O
- Append‑only CSV and IGC K‑records with time‑aligned samples.

---

## 7) Configuration
- **Averaging windows**: [0, 5, 10, 20, 30] s.
- **Process noise (Q_w)** presets: [Calm, Normal, Turbulent].
- **Units**: SI/Imperial; heading reference (True/Mag).
- **Audio**: enable, climb/sink thresholds, deadband.
- **Degraded thresholds**: GNSS `hdop > X`, speed < Y kt, confidence < Z%.

---

## 8) Acceptance Criteria (High‑Level)
1. From power‑on with valid GNSS, show first credible wind vector < 5 s.
2. During straight‑and‑level at constant IAS, EKF wind speed variance over 10 s < 2 kt² and direction variance < 100 deg² (calm preset).
3. In 360° circling in steady thermal, EKF vertical (w) correlates (r>0.8) with achieved climb; TEK shows larger transient spikes on stick input.
4. Losing GNSS for ≤10 s degrades confidence and freezes average arrow; system recovers without reset when GNSS returns.
5. Data export contains synchronized records at ≥10 Hz for key topics.

---

## 9) Test Plan (Brief)
- **Bench**: IMU bias stability, baro lag sweep, GNSS spoof (dropouts/gust replay).
- **Ground run**: taxi/turns to validate attitude & arrow behavior.
- **Flight**: straight‑and‑level, pull‑ups, slips, thermalling, ridge runs; compare against reference winds (soundings/other instruments).

---

## 10) UX Wireframe Notes (for implementers)
- Wind page: full‑screen compass with central aircraft, blue/white arrows; top numeric ribbon; bottom toolbar (AVG, Freeze, Share, Settings).
- Vario page: circular gauge with red/blue needles; digital repeats underneath; small wind badge at top‑right.
- AHRS page: standard horizon; small wind bug & numeric WS/WD.

---

## 11) Future Extensions
- Audio vario based on EKF `w` (tones, sink alarm).
- Thermal assistant (circle fitter) seeded by EKF wind.
- Ridge/wave mode presets.
- Sensor redundancy (dual GNSS, dual baro) with voting.

---

## 12) Glossary
- **TEK**: Total Energy Compensation (traditional vario).
- **EKF**: Extended Kalman Filter.
- **AHRS**: Attitude and Heading Reference System.
- **Netto**: airmass vertical velocity.

---

## 13) Implementation Notes
- Use fixed‑point or fast math on embedded targets; ensure numeric stability.
- Time synchronization is critical; use monotonic clock and timestamp every sample.
- Carefully tune Q/R; expose presets rather than raw matrices to end‑users.
- Log innovations/NIS to diagnose “false thermals” and tuning drift.
