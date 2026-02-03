# Designing a HAWK‑Inspired Variometer App on the Galaxy S22 Ultra Using Only Built‑In Sensors

## Feasibility framing and feature priorities

A phone can deliver a competent *vertical speed indicator* (VSI‑style variometer) by fusing barometric altitude change with inertial sensing, but it cannot truly replicate the defining measurement capability of the HAWK system: **instantaneous 3D wind‑triangle estimation** using a multisensor platform that includes **dynamic pressure (airspeed) sensing**. The HAWK algorithm is explicitly described as a complex nonlinear **extended Kalman filter (EKF)** processing IMU + GPS + **dynamic and static pressure**, and it is marketed/defined around 3D instantaneous wind estimation and immunity to horizontal gust artifacts that plague TEK‑style varios. citeturn0search7turn6view2turn6view0

That mismatch matters because “HAWK‑like gust filtering” is not mainly a display smoothing problem. In HAWK, gust immunity is achieved by **jointly estimating the air‑mass motion in 3D** with a wind model and turbulence/noise parameters (e.g., SIGWIND / wind variance). A phone has no built‑in dynamic pressure channel, so the wind triangle is fundamentally under‑observed unless you restrict yourself to low‑bandwidth, maneuver‑dependent estimation (e.g., circling drift). citeturn0search7turn6view0turn2search4

With that reality stated plainly, the right product goal is:

- **A phone‑native, robust, low‑latency variometer** (vertical speed + audio), with “gust filtering” implemented as **adaptive noise handling and outlier rejection** to prevent pressure/IMU transients from appearing as lift/sink.
- **Optional wind estimation** that is clearly labeled as *low‑bandwidth / averaged / confidence‑gated*, derived primarily from circling drift (where observable), rather than claiming “instantaneous real‑time wind” comparable to HAWK. citeturn2search4turn6view0turn0search7

### Prioritized core features for a HAWK‑inspired phone app

The priorities below are ordered by (a) feasibility on phone sensors and (b) impact on actual flying usability.

**Core flight instrument outputs (must‑have)**  
1) **Vertical speed (vario) with fast audio**: baro+IMU fusion output, plus a short “display average.” This is the heart of the app. Unstable sampling rates and sensor noise are a given on Android; design around timestamps and filter stability. citeturn0search1turn8search2turn7search14  
2) **Robust “gust filtering” for phone artifacts**: spike detection on baro pressure, adaptive measurement noise, and sanity checks using IMU consistency (details later). This is what makes a phone vario usable in turbulence and cockpit airflow. citeturn5view0turn4search2  
3) **Altitude display (relative + optionally QNH‑referenced)**: use pressure altitude for relative changes; if you allow QNH, compute using `SensorManager.getAltitude(p0, p)` and make it explicit that absolute altitude depends on sea‑level pressure input. citeturn8search1turn3search2turn7search14  
4) **Flight logging**: store timestamped raw sensor streams + fused outputs for tuning and post‑flight analysis. HAWK tuning itself is described using logged data and model parameters in LXNAV manuals; phone development needs this even more. citeturn6view0  

**HAWK‑inspired “intelligence” (high value, feasible if done honestly)**  
5) **Thermal assist cues**: trend indicators (10–20 s average vario), turn detection, and “entering lift” alerting. XCTrack’s long history shows that improving averaging and baro integration materially improves thermal assistant behavior in practice. citeturn2search21turn2search1  
6) **Wind estimation (circling drift)** with confidence gating: emulate the “circling wind” approach documented by XCSoar (works with GPS only; cruise wind methods generally require TAS). Present as an averaged estimate with a quality flag. citeturn2search4turn2search24turn2search27  

**User experience and reliability (non‑negotiable on Android)**  
7) **Foreground operation mode**: a dedicated “In Flight” mode that runs as a foreground service (persistent notification), because modern Android restricts background access to continuous sensors and because you need predictable continuity. citeturn1search5turn1search1turn1search17  
8) **Battery/thermal management**: user‑selectable “Performance profiles” (IMU rate, GNSS rate, logging detail). Android’s sensor sampling behavior is not fully under app control; your UI should expose tradeoffs. citeturn0search1turn4search2  
9) **Tuning UI that pilots can understand**: “Responsiveness” and “Turbulence rejection” sliders that map to concrete filter parameters (process noise, adaptive R, spike thresholds), similar in spirit to HAWK’s SIGWIND (wind variance smoothing) but applied to a phone‑appropriate model. citeturn6view0turn6view1  

## Sensor requirements and Android API design for the S22 Ultra

### What sensors the S22 Ultra actually exposes (from primary specs)

Samsung’s official S22 Ultra business specifications list the key onboard sensors: **accelerometer**, **barometer**, **gyro**, and **geomagnetic (compass)**, plus others not directly useful for variometry. It also lists multi‑constellation location support: **GPS, GLONASS, BeiDou, Galileo, QZSS**. citeturn0search0

This is the minimum viable stack for a phone variometer, but it is not equivalent to HAWK’s sensor platform because HAWK explicitly includes **dynamic and static pressure** plus GPS and IMU. citeturn0search7turn6view0

### Android sensor stack: types, units, coordinate frames, and timebase

**Pressure sensor**  
- Use `Sensor.TYPE_PRESSURE` for ambient air pressure; Android documents it as ambient pressure measured in hPa/mbar. The AOSP `SensorEvent` source clarifies: `values[0]` is atmospheric pressure in hPa (millibar). citeturn3search2turn3search22  
- Convert pressure to altitude using `SensorManager.getAltitude(p0, p)` where `p0` is sea‑level pressure; Android documents that absolute altitude depends on knowing sea‑level pressure and warns that using standard atmosphere makes absolute altitudes inaccurate. citeturn8search1turn8search10  

**Accelerometer / gyroscope**  
- Accelerometer includes gravity; Android documentation explicitly discusses the need to remove gravity and provides a standard low‑pass / high‑pass separation approach. citeturn8search2turn8search7  
- Gyroscopes drift (bias); Android’s motion sensor guidance notes gyroscope noise/drift introduces errors that require compensation and suggests using other sensors to estimate drift. citeturn7search5  
- Consider using *uncalibrated* sensors where available for better modeling: AOSP documentation specifies that uncalibrated accelerometer readings come without bias correction (though factory and temperature compensation still apply) and provide a bias estimate, which can be useful in a Kalman filter. citeturn7search0turn7search19  

**Magnetometer**  
- A magnetometer can support heading/orientation, but cockpit magnetic interference is common. If you use it, treat it as optional and aggressively health‑check it (field magnitude gating, interference detection). Android documents the standard way to combine gravity + geomagnetic field vectors into a rotation matrix via `getRotationMatrix()` and then `getOrientation()`. citeturn7search2turn7search16  

**Coordinate system and timestamps**  
- Sensor data is reported in a coordinate system defined relative to the device screen; Android documentation emphasizes axes are not swapped when screen orientation changes. You must explicitly map device axes into a consistent “body frame” for your filter. citeturn7search3turn7search20  
- Use `SensorEvent.timestamp` as your timing source: AOSP specifies it uses the same time base as `SystemClock.elapsedRealtimeNanos()`. This is the correct way to compute `dt` for integration and filtering. citeturn7search14turn7search8  

### Sampling rates, limitations, and required permissions

**Sampling rate limits (Android 12+)**  
- Android explicitly caps sensor sampling via `registerListener()` to **200 Hz**, and `SensorDirectChannel` to **RATE_NORMAL (~50 Hz)**. citeturn0search1turn1search0  
- If an app requests rates faster than 200 Hz on Android 12+ without declaring `android.permission.HIGH_SAMPLING_RATE_SENSORS`, a `SecurityException` can occur (documented as a compatibility change). citeturn0search16turn0search1  
- Practical recommendation: design the estimator to run well at **100–200 Hz IMU** and accept that the barometer may run slower and with variable delivery.

**Discovering sensor capabilities at runtime**  
- Use `Sensor.getMinDelay()` to check the fastest supported acquisition interval; Android highlights this as the proper way to decide whether high‑rate features should be enabled. citeturn4search2turn4search18  

### GNSS and raw GNSS access

- For wind estimation and for sanity checks, you need GNSS ground speed and track. Basic `Location` updates can be requested via the fused location provider. citeturn2search6turn2search18  
- If you want deeper GNSS processing, Android’s raw GNSS measurements support is mandatory on Android 10+ devices, but the availability of specific measurement fields varies by chipset. Treat raw GNSS support as “best effort” and build a fallback to standard location updates. citeturn1search2turn1search6  

## Sensor fusion architecture tailored to phone sensors

This section proposes a design that borrows the *engineering structure* of HAWK (model‑based estimation + tunable smoothing parameters) while being honest about what phone sensors can and cannot observe.

### Core estimator goal

Estimate vertical speed **v** with:
- **Low latency** (IMU‑aided) so it “feels like a real vario.”
- **Low bias and low spurious spikes** (baro‑anchored).
- **Adaptive rejection** of pressure artifacts caused by motion/airflow and barometer quirks.

Research and practice repeatedly show that barometers are strong for relative altitude change but are affected by environmental factors and sensor properties; therefore, fusion and quality control are essential. citeturn5view0turn4search13  

### Recommended filter topology

Two viable designs on Android are:

**Complementary filter (simpler, robust)**  
- Use gyro+accel to estimate attitude; compute gravity‑removed vertical specific force; integrate to get fast vertical speed; correct drift using baro vertical speed / altitude.  
- Pros: easier to implement correctly; less tuning burden.  
- Cons: less principled uncertainty modeling; adaptive noise handling becomes ad hoc.

**Vertical‑channel EKF (recommended for a “HAWK‑inspired” app)**  
Use an EKF focused on the vertical channel. Unlike HAWK, you are not estimating full 3D wind triangle; you are estimating vertical motion with adaptive measurement noise.

A practical state vector:
\[
x = [h,\ v,\ b_a]^\top
\]
where:
- \(h\) = relative pressure altitude (meters, referenced to a baseline),
- \(v\) = vertical speed (m/s),
- \(b_a\) = vertical acceleration bias (m/s²), slowly varying.

**Process model (prediction)**  
At IMU rate:
- Rotate accelerometer into an earth frame using an attitude estimate (gyro‑based propagation with accel stabilization; magnetometer optional). Android documents standard orientation derivation using gravity and magnetic field sensors; the motion sensor docs explain gravity separation fundamentals. citeturn7search16turn8search2  
- Compute vertical specific acceleration \(a_z\) and update:
\[
v_k = v_{k-1} + (a_z - b_a)\Delta t,\quad
h_k = h_{k-1} + v_k\Delta t,\quad
b_{a,k} = b_{a,k-1} + w_b
\]

**Measurement model (update)**  
At barometer rate:
- Convert pressure to altitude \(h_{baro}\) (relative).  
- Update EKF with \(z = h_{baro}\).

Optionally, at GNSS rate:
- Incorporate GNSS vertical speed/altitude very lightly (GNSS altitude is typically noisier and more latency‑affected than baro). If using raw GNSS, do so conditionally. citeturn1search2turn2search6  

### Flowchart of the proposed processing pipeline

```text
┌─────────────────────────────────────────────────────────────────────┐
│                           SENSOR ACQUISITION                         │
│  IMU: accel, gyro (100–200 Hz)    Baro: pressure (device-dependent)  │
│  Mag (optional)                   GNSS: fused location (+ optional raw)│
└───────────────────────────────┬─────────────────────────────────────┘
                                ▼
┌─────────────────────────────────────────────────────────────────────┐
│                         TIME ALIGNMENT & QC                          │
│  - Use SensorEvent.timestamp (elapsedRealtimeNanos time base)        │
│  - Estimate actual sample intervals (don’t trust requested rates)     │
│  - Basic plausibility checks (NaN, stuck sensor, discontinuities)     │
└───────────────────────────────┬─────────────────────────────────────┘
                                ▼
┌─────────────────────────────────────────────────────────────────────┐
│                       PREPROCESSING / FRAMES                         │
│  - Attitude estimate (gyro integration + accel stabilization)         │
│  - Gravity removal (Android-recommended approach)                     │
│  - Pressure → altitude via SensorManager.getAltitude or custom model  │
└───────────────────────────────┬─────────────────────────────────────┘
                                ▼
┌─────────────────────────────────────────────────────────────────────┐
│                 VERTICAL-CHANNEL FILTER (EKF or Complementary)        │
│  Predict at IMU rate; Update at baro rate (and GNSS lightly)          │
│  Adaptive noise: increase R_baro during pressure turbulence/spikes    │
└───────────────────────────────┬─────────────────────────────────────┘
                                ▼
┌─────────────────────────────────────────────────────────────────────┐
│                          OUTPUT GENERATION                            │
│  - Instant vario (audio) + short avg + thermal avg                    │
│  - Optional wind from circling drift (quality gated)                  │
│  - Logging (raw + fused channels)                                     │
└─────────────────────────────────────────────────────────────────────┘
```

This mirrors HAWK’s “model + tuning parameters” philosophy without pretending you can observe the same wind triangle on a phone. HAWK’s own manuals explicitly describe that HAWK vario dynamics depend on SIGWIND and internal parameters, while TEK depends on averaging and gust false signals. citeturn6view0turn0search3  

## Calibration, mounting, and user‑tunable parameters

A phone variometer’s “sensor quality” is not just the MEMS chips. It’s installation, pressure environment, and drift management.

### Mounting recommendations that materially affect performance

**Rigid mounting and fixed orientation**  
Because Android sensor axes are defined relative to device orientation and are not swapped with screen rotation, your filter assumes a stable device frame; a phone sliding in a pocket or wobbling on a suction mount will inject false accelerations and gravity projection errors. citeturn7search3turn7search20  

**Pressure environment control**  
Barometric readings are influenced by multiple factors beyond altitude. A comprehensive barometer survey highlights that measured pressure can be affected by weather, built environment, and notably **relative air velocity due to motion**; it describes how motion changes pressure near surfaces due to dynamic pressure and static pressure effects, producing transient pressure fluctuations. In flight, phone placement near vents/canopy leaks or in direct airflow can produce exactly these kinds of artifacts. citeturn5view0turn4search13  

Practical implication: mount the phone where airflow is stable—often behind a windscreen or inside a pocket‑like enclosure that still equalizes slowly to ambient pressure but damps fast airflow‑induced pressure spikes.

### Calibration procedures you should implement

**Pressure baseline and QNH handling**  
- Allow a “Set current altitude” button that adjusts the sea‑level pressure reference used for `getAltitude(p0,p)`. Android explicitly states that absolute altitude accuracy depends on knowing sea‑level pressure; provide a workflow that makes this explicit (manual QNH input or “set known altitude” at takeoff). citeturn8search1turn8search0  

**IMU bias characterization**  
- Gyro drift is a known issue; Android recommends compensating drift using other sensors. Provide a “still calibration” step: when the phone is stationary for N seconds, estimate gyro bias and vertical accel bias and initialize \(b_a\). citeturn7search5turn8search2  

**Magnetometer health checks (if used)**  
- Because magnetometers are easily disturbed, implement a “mag integrity” indicator (field magnitude out of expected range, rapidly changing bias). If integrity is low, fall back to gyro+accel attitude only for vertical channel (you don’t need compass heading for vertical speed). Android documents magnetometer usage for orientation matrices, but it does not guarantee accuracy in disturbed environments—your app must detect and adapt. citeturn7search16turn7search2  

### User‑adjustable tuning that pilots will actually use

Instead of exposing raw EKF Q/R parameters, offer three or four knobs that map to filter behavior:

- **Responsiveness (fast/medium/slow)**: maps primarily to accelerometer weighting (process noise on \(v\)) and the output smoothing constant.  
- **Turbulence rejection**: maps to spike detector thresholds and adaptive baro measurement noise scaling (see next section).  
- **Audio aggressiveness**: deadband near 0 m/s, climb/sink tone curve, and averaging used for audio. XCTrack documents a concrete audio behavior model (beep frequency/pitch scaling with climb), which you can use as a baseline. citeturn2search1  
- **Logging level**: off / basic / full raw.

This matches the spirit of HAWK’s tuning: “wind variance” (SIGWIND) is a single parameter described as smoothing horizontal and vertical wind/netto readings, trading stability vs nervousness. citeturn6view0turn6view1  

## Gust filtering as adaptive noise control and robust outlier rejection

### Why phone “gust filtering” is a different problem than HAWK “gust filtering”

HAWK’s gust filtering is largely about avoiding **false TEK climb/sink caused by horizontal gusts** via full 3D state estimation. HAWK documentation explicitly contrasts TEK false gust signals with HAWK’s dependence on SIGWIND and internal parameters. citeturn0search3turn6view0turn0search7  

A phone‑only app is primarily threatened by:

- **Pressure spikes** caused by airflow‑induced pressure changes around the device (relative air velocity effects are documented as significant for barometric sensing). citeturn5view0  
- **Mechanical vibration and mounting flex** that contaminate accelerometer readings (gravity removal errors). citeturn8search2turn7search5  
- **Sampling irregularities and OS throttling**, which can destabilize naïve differentiation and integration. citeturn0search1turn7search14  

### Technical recommendations for phone‑grade “gust filtering”

**Pressure quality control before altitude conversion**  
Implement a robust outlier detector on pressure samples \(p(t)\), such as:
- Median filter or Hampel filter on a short window (e.g., 0.5–1.0 s) to suppress single‑sample spikes.
- A derivative gate on \(dp/dt\) to detect implausibly fast pressure changes (especially if not corroborated by IMU vertical acceleration).

This is justified because the barometric sensing literature emphasizes multiple non‑altitude factors influence pressure, including motion‑related pressure fluctuations. citeturn5view0turn4search13  

**Innovation gating in the EKF update**  
Use the EKF innovation \(r = z_{baro} - \hat{h}\) and its covariance \(S\) to compute a normalized innovation squared (NIS):
\[
\text{NIS} = r^\top S^{-1} r
\]
If NIS exceeds a threshold, treat the baro measurement as an outlier for that update or temporarily inflate \(R_{baro}\).

This is the cleanest way to implement “gust filtering” when you cannot separately measure dynamic pressure: you accept that baro occasionally lies, and you make the filter resilient when it does.

**Adaptive measurement noise (“turbulence mode”)**  
Model \(R_{baro}\) as time‑varying:
- In calm conditions: low \(R_{baro}\) so baro anchors altitude and prevents drift.
- In turbulent pressure conditions: increase \(R_{baro}\) so the filter trusts IMU more for short periods.

HAWK’s published materials use an analogous concept: SIGWIND/wind variance controls how “nervous” the estimated wind/netto is; it is explicitly a smoothing parameter within the wind model. You can adopt the same *product concept* (one knob controlling how aggressively you smooth / reject fast fluctuations) even though the underlying states differ. citeturn6view0turn6view1turn0search7  

**Output smoothing separate from estimation**  
Do not “solve gusts” by making the estimator slow. Keep the estimator relatively responsive, then offer:
- “Instant” vario for audio with limited additional smoothing.
- A short (1–2 s) displayed average.
- A longer (10–20 s) thermal strength average.

XCTrack’s changelog explicitly mentions improvements to averaging of altitude changes to improve vario/thermal assistant behavior; this is operationally important. citeturn2search21turn2search1  

## Wind estimation using phone sensors and how to present it honestly

### What’s achievable without airspeed sensors

A phone can estimate **horizontal wind** in a limited, low‑bandwidth way from GNSS trajectories, especially while circling. This is consistent with how established glide computers handle wind when only GPS is available.

XCSoar documents two wind estimation methods:
- **Circling drift**: uses GPS fixes to estimate wind based on drift during thermalling; available with GPS alone.
- **ZigZag cruise estimation**: uses GPS fixes **and true airspeed**; only available when connected to an intelligent variometer outputting airspeed. citeturn2search4turn2search27  

That is the key constraint for a phone‑only app: you can do “circling wind,” but you cannot do robust cruise wind like HAWK does, because HAWK’s platform includes dynamic pressure/TAS and runs a full EKF wind triangle. citeturn0search7turn6view2  

### Recommended wind estimation feature set

**Circling drift wind (primary)**  
- Detect circling using GNSS track curvature and/or phone gyro turn rate (with careful gating to avoid false circles).  
- Estimate wind as the drift of the circle center over time (or by fitting the ground track to a model that includes wind drift).  
- Output: wind direction/speed + confidence (e.g., “Good” after ≥1 full stable circle; “Poor” otherwise).

This aligns with the broad approach referenced in both XCSoar docs and pilot community discussions about circle‑based wind. citeturn2search4turn2search24turn2search0  

**Quality gating and UI disclosure**  
Make it explicit on screen:
- “Circling wind (averaged)”  
- confidence indicator  
- last update age

Do not label it “instant wind” or “HAWK equivalent.” HAWK explicitly claims instantaneous wind from EKF; your phone estimate will be episodic and conditional. citeturn6view2turn0search7  

## Operating under Android constraints

### Foreground operation and background restrictions

- Android 9 introduced privacy changes that limit background apps’ access to device sensors; practical continuous sensing requires foreground operation. citeturn1search1turn1search17  
- Use a **foreground service** while “In Flight”; Android’s foreground services overview emphasizes the required persistent notification for user awareness. citeturn1search5  
- If targeting Android 14+, you must declare an appropriate **foreground service type** in the manifest and request the associated foreground service permission(s). citeturn8search15  

### Sensor rate limiting and permission management

- Plan around the 200 Hz cap for `registerListener()` and the ~50 Hz limit for `SensorDirectChannel`. citeturn0search1turn1search0  
- Don’t request >200 Hz unless you have a real need and are prepared for policy/permission friction (`HIGH_SAMPLING_RATE_SENSORS`). For a variometer, 100–200 Hz IMU is typically sufficient if your filter is designed correctly. citeturn0search16turn0search1  

### Location permissions and system settings

- Request location updates through the fused location provider; Android provides a dedicated guide for requesting regular updates. citeturn2search6  
- Use the “change location settings” flow so users can enable the needed system settings (GPS, higher‑accuracy modes) rather than failing silently. citeturn2search18  

### Audio behavior (vario tones) under modern Android

- Manage audio focus properly. Android’s audio focus documentation is explicit: request audio focus, handle preemption, and adjust behavior when focus is lost. citeturn8search3  
- If you want audio vario while screen‑off, treat it as a deliberate “In Flight” mode feature and document that it requires foreground operation (consistent with the general sensor/background reality). citeturn1search5turn1search17  

## Testing, validation, and comparison against HAWK hardware

### Validation strategy that produces credible results

Your goal is not to prove “it works on a calm day.” Your goal is to prove **robustness under turbulence and pressure artifacts**.

**Bench and ground tests (fast iteration)**  
- “Step response” test: move the device vertically by a known amount (stairs/elevator) and check lag and overshoot (baro dominates; IMU helps latency). Barometer literature supports ~meter‑scale sensitivity in typical contexts, but environmental factors matter. citeturn5view0turn4search1  
- “Pressure artifact” test: expose the phone to a fan/airflow changes near the barometer vent and verify your spike detector and adaptive \(R_{baro}\) prevent false lift spikes (ground truth is “no actual altitude change”). Motion‑related pressure fluctuation mechanisms are documented in barometer research. citeturn5view0turn4search13  
- “Vibration” test: mount the phone on a vibrating surface; verify that your attitude/gravity removal and EKF bias estimation prevent persistent false vario.

**Flight tests (truth‑based evaluation)**  
- Side‑by‑side with a dedicated instrument (ideally HAWK‑enabled or at least a high‑quality glider vario) and log both streams. LXNAV documentation notes that HAWK output dynamics differ from TEK due to gust false signals and SIGWIND; your comparisons should segment by cruise vs circling vs turbulence. citeturn6view0turn0search3  
- Metrics to compute from logs:
  - noise level in “steady air” (RMS vario),
  - lag to a thermal entry event (time‑to‑indication),
  - false alarm rate during turbulence (spurious climbs),
  - drift over a long constant‑altitude segment (bias control).

### Existing apps as practical baselines (and what they imply)

Several published apps demonstrate the feasibility of phone‑sensor variometry:
- XCTrack enables acoustic vario only when a barometric sensor is available (internal or external), showing that baro is treated as essential for usable vario in practice. citeturn2search1turn2search5  
- An open‑source “Variometer” app listing states it estimates vertical speed with internal barometric and acceleration sensors using a Kalman filter; the F‑Droid listing explicitly warns it won’t work correctly in a pressurized cabin—highlighting sensitivity to pressure environment. citeturn2search3turn2search19  
- theFlightVario markets an IMU‑centric approach that cross‑checks accelerometer/gyro/magnetometer with barometer for instant feedback and reduced false lift indications, reinforcing the product direction: **robust sensor fusion beats baro‑only differentiation**. citeturn2search15turn2search32  

These sources don’t prove correctness of any given algorithm, but they validate the feature set users expect and the practical constraints (baro necessity, mounting sensitivity, need for fusion). citeturn2search1turn2search19turn2search32  

### Comparison table: proposed S22 Ultra app vs HAWK hardware

| Dimension | Proposed S22 Ultra sensor‑only app | HAWK hardware capability (reference point) |
|---|---|---|
| Vertical speed (VSI‑style) | Achievable with baro+IMU fusion (EKF or complementary filter) | Achievable (dedicated sensors + estimation) citeturn6view0turn0search7 |
| “Gust filtering” meaning | Reject *phone* artifacts: airflow pressure spikes, vibration, sampling irregularity via adaptive noise + outlier gating | Reject *horizontal gust false TEK indications* via 3D air‑mass estimation; output depends on SIGWIND/wind model parameters citeturn6view0turn0search7turn0search3 |
| Instantaneous 3D wind vector | Not achievable; no TAS/dynamic pressure, so wind triangle is not directly observable | Achievable by design: EKF + GPS + IMU + dynamic/static pressure platform citeturn0search7turn6view2 |
| Wind estimation | Low‑bandwidth circling drift wind with confidence gating; cruise wind limited | Instantaneous horizontal + vertical wind estimates (claimed) citeturn6view2turn0search7 |
| Tuning parameter analogues | “Responsiveness / Turbulence rejection” sliders mapping to EKF noise and spike gating | Wind variance SIGWIND smooths horizontal and vertical wind/netto readings citeturn6view0turn6view1 |
| Sampling stability | Subject to Android limits (200 Hz cap via registerListener), OS interference, and device variability | Dedicated system designed for sensor timing and aviation use citeturn0search1turn0search7 |
| Installation sensitivity | Very high (device orientation and pressure environment dominate) | High, but engineered for aircraft plumbing and alignment; manuals discuss parameter tuning and dynamics citeturn6view0turn5view0 |

### Bottom line

If the product claim is “HAWK‑like instant 3D wind and gust‑immune netto,” a phone‑only app cannot deliver that because the phone lacks the air‑data inputs HAWK’s EKF platform assumes. citeturn0search7turn6view2

If the product claim is “a HAWK‑inspired variometer experience—fast, stable, low false‑lift spikes—built on phone sensors,” that is realistically achievable with disciplined engineering: timestamp‑correct acquisition, robust baro QC, a vertical‑channel EKF with adaptive noise, and honest wind estimation limited to circling drift. citeturn5view0turn2search4turn0search1