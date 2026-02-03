# Implementing a HAWK‑Like Variometer Experience in XCPro on a Galaxy S22 Ultra Using Only Built‑In Sensors

## Technical goals and non‑negotiable constraints

A “HAWK‑like experience” on a phone should be defined as: **fast, low‑latency vertical speed**, **few false lift/sink spikes in turbulence**, and **a reasonable, confidence‑gated wind estimate**—not as a literal clone of LXNAV HAWK’s instantaneous 3D wind triangle estimator. HAWK’s published technical description and LXNAV materials emphasize an EKF that fuses **IMU + GPS + static and dynamic pressure (airspeed)** to estimate 3D airmass motion and eliminate horizontal‑gust false indications that plague TE‑style variometry. A phone has **no dynamic pressure / pitot airspeed input**, so the same observability and “instant 3D wind” behavior is not achievable with built‑in sensors alone. citeturn0search39turn3search0turn3search1turn3search8

What *is* realistically achievable on the Samsung Galaxy S22 Ultra is a robust **vertical-channel fusion stack** (baro + IMU) that yields a vario feel closer to a modern “e‑vario” than naive “differentiate baro altitude,” plus a **circling‑drift wind estimate** with clear limitations. This matches how established glide computers treat wind estimation when only GPS is available: circling wind works with GPS alone, while cruise (“zigzag”) wind generally requires an airspeed source. citeturn3search6turn3search10

## Sensor acquisition and Android system constraints you must design around

### What sensors you can rely on in the S22 Ultra

Samsung’s official Galaxy S22 Ultra specifications list the key onboard sensors needed for this project: **accelerometer**, **gyroscope**, **barometer**, and **geomagnetic (magnetometer)**, plus other sensors that are not relevant to variometry. The same official spec page also lists multi‑constellation GNSS support (GPS, GLONASS, BeiDou, Galileo, QZSS), which matters for wind estimation and for flight logging. citeturn5search2

### Android sensor APIs and the timing model

Use the standard Android sensor framework (`SensorManager` + `SensorEventListener`) and treat sample timing as data-driven:

- **Do not trust requested delays as actual sample periods.** On Android, the delay you request is not a hard guarantee; you must compute `dt` from event timestamps and handle jitter. citeturn0search1turn7search0  
- Use **`SensorEvent.timestamp`** as your authoritative time base. AOSP explicitly documents it as monotonic and using the same time base as `SystemClock.elapsedRealtimeNanos()`. citeturn0search14turn0search10  
- Understand and respect the **device coordinate system**: Android’s sensor axes are defined relative to the device’s *natural orientation* and **do not swap when the screen rotates**. Your fusion code must not assume portrait is the natural orientation and must explicitly map the device frame into your “body” frame. citeturn7search0turn7search24  

### Sampling rate limits, permissions, and background restrictions

Your design must explicitly account for modern Android constraints:

- **Sampling rate caps**: Android documentation states that when using `registerListener()`, sensor sampling rate is limited to **200 Hz**; using `SensorDirectChannel`, the effective limit is typically **RATE_NORMAL (~50 Hz)**. citeturn0search1turn1search1  
- **High-rate permission**: AOSP code and Android docs indicate that without `android.permission.HIGH_SAMPLING_RATE_SENSORS`, the minimum delay is capped at **5000 µs (200 Hz)**, and requesting very high rates without declaring the permission can trigger `SecurityException` in applicable scenarios. citeturn0search17turn0search13  
- **Foreground requirement**: On Android 9+ (API 28+), apps running in the background do not receive events from continuous sensors such as accelerometers and gyroscopes. The official guidance is to gather sensor data in the foreground or via a **foreground service**. citeturn1search1turn1search0  
- **Foreground service types (Android 14+)**: Android requires declaring appropriate foreground service types and permissions for foreground services on Android 14+ (API 34+). citeturn1search9turn4search5  

### GNSS / location APIs for wind estimation and logging

Two layers matter:

- **Fused location provider (recommended for app-level wind + track)**: Android’s guidance shows how to request periodic location updates and notes that accuracy/frequency depend on request options and permissions. citeturn1search2  
- **Raw GNSS measurements (optional, for research-grade logging)**: Android’s raw GNSS documentation warns that support for specific fields is optional and varies by chipset (pseudorange, ADR/carrier phase, multi-frequency, etc.). Use this only if you are prepared for device variability and heavy signal processing. citeturn1search3turn1search7  

## Vertical‑channel EKF design for low‑latency vario with adaptive gust/pressure‑transient suppression

This section gives a practical EKF design that you can implement in XCPro without rewriting your whole navigation stack.

### Design pattern that works on phones

Use a **two-stage estimator**:

- **Stage A (attitude / gravity alignment)**: estimate the gravity direction (and optionally full quaternion) using gyro + accelerometer; magnetometer is optional and often harmful in cockpit environments. Android’s own guidance explains why the *game rotation vector* avoids magnetic field dependence and can be more robust when you “don’t care about north,” at the cost of yaw drifting over time. For vertical speed, yaw drift is irrelevant; what you need is a good estimate of “up vs down.” citeturn8search6turn7search0turn7search11  
- **Stage B (vertical KF/EKF)**: fuse baro-derived altitude with gravity-compensated vertical acceleration to estimate vertical speed with low latency and low drift. This structure is consistent with published baro‑IMU fusion literature: strapdown rotation of specific force using an attitude estimate, then complementary/Kalman filtering to recover height and vertical velocity. citeturn2search12turn3search3turn3search18  

You can implement Stage B as a **linear Kalman filter** if you convert pressure to altitude first; it becomes an EKF if you (a) keep the measurement in pressure space or (b) include nonlinear measurement models (pressure–altitude) inside the filter. For your requirements (“vertical-channel EKF”), the most faithful approach is to use pressure directly as the measurement and keep the hypsometric relationship inside the measurement function. Android explicitly discusses pressure-to-altitude conversion and stresses that absolute altitude needs sea-level pressure (QNH) but **altitude differences** remain useful even with standard atmosphere assumptions. citeturn5search5turn5search24turn2search20  

### State, inputs, and measurement models

A practical vertical-channel EKF state:

- \(h\): relative altitude (m)  
- \(v\): vertical speed (m/s)  
- \(b_a\): vertical acceleration bias (m/s²), modeled as a random walk  
- Optional \(b_p\): pressure bias (Pa), if you want extra robustness to slow pressure drift

A recommended discrete-time process model (run at IMU rate, e.g., 100–200 Hz):

\[
\begin{aligned}
h_k &= h_{k-1} + v_{k-1}\Delta t + \tfrac{1}{2}(a_{z,k}-b_{a,k-1})\Delta t^2 \\
v_k &= v_{k-1} + (a_{z,k}-b_{a,k-1})\Delta t \\
b_{a,k} &= b_{a,k-1} + w_{b}
\end{aligned}
\]

Where \(a_{z,k}\) is the estimated vertical linear acceleration in an Earth-up frame derived from the phone’s accelerometer and an attitude estimator. Android’s sensor coordinate rules and rotation matrix methods are the foundation for this transformation. citeturn7search0turn7search15turn2search7  

A pressure measurement model (EKF update at baro rate):

\[
z_k = p_k = f(h_k) + v_k
\]

Where \(f(h)\) is a pressure–altitude relation. If you don’t want to carry full atmospheric modeling, you can implement a local linearization about a baseline pressure \(p_{ref}\) and treat the measurement as altitude instead, noting Android’s documented advice that absolute altitude requires sea-level pressure while altitude differences are still meaningful. citeturn5search5turn5search12  

### Attitude / gravity: pick a pragmatic approach

For XCPro, prioritize robustness and low integration risk:

- **Best “fast and good enough” option on S22 Ultra**: use `TYPE_GAME_ROTATION_VECTOR` to obtain a quaternion/rotation matrix that isn’t corrupted by cockpit magnetic disturbances. Android explicitly describes this sensor and its drift characteristics. citeturn8search6turn7search11  
- **Fallback / research option**: implement a Mahony/Madgwick-style complementary filter using gyro integration with accelerometer correction. Peer-reviewed literature and widely used references describe these complementary filter families and their stability properties; this is a heavier lift but gives you full control over tuning and failure detection. citeturn8search14turn8search19turn8search18  

### Adaptive noise filtering: how “gust filtering” should work on a phone

HAWK exposes “wind variance” parameters (SIGWIND) controlling how nervous vs smooth the wind/netto estimate is, and LXNAV documentation explicitly contrasts HAWK dynamics vs TEK dynamics and notes TEK false signals in horizontal gusts. A phone cannot reproduce HAWK’s 3D airmass estimation, but you *can* implement the same core idea: **adapt measurement trust based on detected turbulence/pressure corruption**. citeturn3search1turn3search0turn0search39  

On a phone, most “false thermals” come from **pressure transients and mounting/IMU artifacts**, not TEK physics. Barometer research surveys emphasize that measured pressure is affected by many factors beyond altitude, including motion-related effects (relative air velocity, local airflow, environmental changes), and that sensor properties/variability matter. citeturn5search0turn5search3  

Implement two mechanisms inside the EKF:

1) **Innovation gating (robust update acceptance)**  
Compute innovation \(r = z - \hat{z}\) and innovation covariance \(S\). If normalized innovation squared (NIS) is too large, reject the update or inflate \(R\) temporarily. This is standard Kalman-filter practice and is consistent with core Kalman filter references. citeturn2search2  

2) **Time‑varying baro measurement noise \(R_k\)**  
Estimate a “pressure turbulence index” from robust statistics on short windows (described below) and set:
\[
R_k = R_{base}\cdot(1 + \alpha \cdot TI_k)
\]
So in stable pressure conditions the filter anchors on baro (low drift), while in pressure-corrupted conditions it relies more on inertial short-term dynamics (low latency), then re-anchors once baro stabilizes. This is the phone analogue of SIGWIND-style tuning: a single pilot-facing “Turbulence rejection / stability” control mapped to estimator noise assumptions. citeturn3search1turn5search0  

### Implementation pseudo-code for the EKF tick

Below is a practical sequencing model (no Android code shown yet). It assumes you run a fixed update loop (e.g., 100 Hz) and feed in the most recent sensor samples.

```text
Inputs:
  imu: accel_raw(t), gyro_raw(t), rot_quat(t) or attitude_estimator
  baro: pressure(t)
State:
  x = [h, v, ba]
  P = covariance

Loop at IMU cadence:
  dt = t - t_prev

  # 1) Attitude / gravity alignment
  R_nb = rotation matrix nav<-body (from game rotation vector or Mahony/Madgwick)
  a_body = accel_raw
  a_nav  = R_nb * a_body
  a_z    = a_nav.z - g

  # 2) Predict (process update)
  x_pred, P_pred = propagate(x, P, a_z, dt, Q)

  # 3) Baro update if new pressure sample arrived
  if baro_new:
     z = pressure
     z_pred = pressure_from_height(x_pred.h)  # EKF measurement model
     H = d(pressure_from_height)/dh evaluated at x_pred.h
     r = z - z_pred
     S = H * P_pred * H^T + R_baro_dynamic

     # Robust gating
     if r^2 / S > gate_threshold:
         R_baro_dynamic *= big_factor  # or skip update
     else:
         K = P_pred * H^T * inv(S)
         x_pred = x_pred + K * r
         P_pred = (I - K*H) * P_pred

  x, P = x_pred, P_pred

Outputs:
  vario_instant = x.v
  vario_display = low-pass(vario_instant, tau_display)
  vario_avg10s  = moving_average(vario_display, 10s)
```

This architecture is directly aligned with the “strapdown + fusion” approach described in baro‑IMU vertical channel fusion literature, while incorporating robust gating and adaptive measurement trust motivated by barometer variability research. citeturn2search12turn5search0turn2search2  

## Barometric pressure QC and preprocessing pipeline

### Why baro QC is a first-class module (not just “a filter constant”)

Phone barometers are sensitive enough to detect meter-scale vertical changes, but pressure readings are influenced by sensor variability, environment, airflow, and movement. A large barometer survey emphasizes that the usefulness of barometers hinges on understanding factors affecting atmospheric pressure and sensor properties. In a glider cockpit, local airflow changes around the phone can easily become “fake climb/sink” unless you harden the pipeline. citeturn5search0turn5search3  

Android itself encourages computing altitude differences rather than chasing absolute altitude without QNH, which implicitly supports the idea of treating baro as a relative sensor and focusing on stable differences and robust filtering. citeturn5search5turn5search12  

### QC methods that work well for cockpit pressure corruption

Implement QC in a dedicated module *before* the EKF update:

**Robust outlier detection with Hampel filtering**  
A Hampel filter identifies outliers using a sliding window median and median absolute deviation (MAD), replacing points that exceed a threshold (e.g., 3σ-equivalent MAD scaling). The Hampel filter is widely used in time series outlier detection and is explicitly described in modern literature. citeturn2search13turn2search33  

**Median filtering for single‑sample spikes**  
A short window median filter (e.g., 5–11 samples depending on baro rate) suppresses isolated spikes without adding the lag of a long low-pass.

**Derivative and curvature gates**  
Compute robust estimates of \(dp/dt\) and \(d^2p/dt^2\). If you see extreme pressure curvature that is physically implausible for the aircraft’s actual vertical motion profile, mark the sample as suspect and inflate \(R_k\) rather than trusting it.

**Cross-check against IMU vertical dynamics (soft check, not a hard rule)**  
Vertical acceleration doesn’t show constant-velocity vertical motion, so it cannot “prove” that baro is wrong. But it can identify certain artifacts:
- Pressure spikes with near-zero IMU disturbance often indicate local airflow changes.
- Large IMU shocks with pressure flat may indicate mounting vibration.

Use this only to scale confidence, never as an absolute accept/reject rule.

### Pressure-to-altitude handling: do it the Android-supported way

For absolute altitude display:
- Use a user-provided QNH / sea-level pressure reference. Android’s `getAltitude(p0,p)` docs explicitly state sea-level pressure must be known (often from airport databases) for absolute altitude accuracy; otherwise use standard atmosphere as an approximation and accept that absolute altitude won’t be accurate. citeturn5search5turn5search24  

For variometer (relative vertical speed):
- Use altitude differences computed against a baseline pressure reference or standard atmosphere, which Android explicitly says gives good results for altitude differences even without sea-level pressure. citeturn5search5turn5search12  

## XCPro software architecture and integration design

### Recommended modular architecture for XCPro

Because you’re adding a safety-critical real-time pipeline (continuous sensors + audio feedback), isolate it from UI and from navigation features. A practical module split:

- **`sensors/`**: Android-only collectors (baro, IMU, rotation vector, GNSS) + timestamp normalization + ring buffers.
- **`fusion/`**: pure Kotlin math (attitude estimator if custom, vertical EKF, QC filters, wind estimation) with no Android dependencies. This enables fast local unit tests on the JVM. citeturn6search0  
- **`vario/`**: output shaping (audio tone mapping, display smoothing, hysteresis, deadband).
- **`logging/`**: binary/CSV recorder with schema versioning to support post-flight analysis and regression tests.
- **`flightservice/`**: foreground service controlling sensor acquisition lifecycle and exposing observable streams (Flow/StateFlow) to UI.

This structure aligns with Android’s testing guidance: core logic should be tested with local tests, while sensor collectors and service behavior are validated with instrumented tests on real devices. citeturn6search0turn6search1  

### Reliable acquisition pipeline: don’t compute inside `onSensorChanged()`

Android best practice is to keep `onSensorChanged()` lightweight; you want this anyway because your EKF will run frequently. Use this pattern:

- `onSensorChanged()` pushes `SensorEvent` values into a lock-free ring buffer (or a coroutine Channel) with the event timestamp.
- A dedicated “fusion thread” (single-thread executor or coroutine dispatcher) pulls samples, aligns them to a common tick, runs QC + EKF, and emits outputs.

You do this because sensor rates can be high and because blocking the sensor callback risks overruns and jitter. Android’s sensor framework documentation emphasizes rate variability and the need to compute actual sample timing from timestamps. citeturn1search1turn0search14turn7search0  

### Foreground service and notification behavior

Implement an explicit “In Flight” mode:

- Start a foreground service when the user begins “Flight Instruments” mode.
- Show a persistent notification with a clear stop action; Android’s foreground service documentation emphasizes user visibility and the need to declare service types on newer Android versions. citeturn4search5turn1search9turn4search1  
- Keep sensors registered only while the service is active; Android background sensor restrictions make it unreliable otherwise. citeturn1search1turn1search0  

### Audio vario output (tones) that behaves like an instrument

Implement audio using an audio engine that is stable under jitter:

- Request and manage **audio focus**; Android explicitly documents audio focus as the correct mechanism to avoid conflicts and to handle preemption cleanly. citeturn4search0turn4search16  
- Drive audio from the **filtered instantaneous vertical speed** \(v\) (not the long average), but apply:
  - a small deadband near 0,
  - hysteresis to prevent rapid beeping toggles,
  - a “sink tone” optional mode.

### Logging for tuning and credibility

You will not tune this well without logs. Log:

- raw pressure (hPa),
- raw accel/gyro (m/s², rad/s),
- rotation quaternion (if used),
- derived \(a_z\),
- EKF state and covariance summary,
- QC flags (outlier replaced, gating events, turbulence index),
- GNSS samples and “flight mode” state.

This lets you replicate LXNAV-style “tune using recorded data,” which is explicitly discussed in HAWK parameter documentation (SIGWIND / internal parameters driving output dynamics). citeturn3search1turn0search39  

## Calibration, mounting, and pilot-facing tuning

### Mounting is part of the sensor model—treat it that way

- The phone must be **rigidly mounted** with a consistent orientation. If the phone moves relative to the airframe, your gravity compensation becomes garbage.
- Shield the phone from **local airflow** (vents, canopy leaks, direct blast over the barometer port). Barometer research highlights that many factors affect pressure and that motion/airflow effects can cause pressure variability unrelated to true altitude change. citeturn5search0turn5search3  

image_group{"layout":"carousel","aspect_ratio":"1:1","query":["glider cockpit smartphone mount","soaring cockpit phone mount suction","paragliding cockpit phone mount","sailplane cockpit phone holder"],"num_per_query":1}

### Calibration procedures you should implement in-app

**Initial “still” bias calibration**  
When the phone is stationary (detected via low gyro magnitude and accel magnitude near \(g\)), average:
- gyro bias (if you run your own AHRS),
- vertical accel bias estimate \(b_a\) for the vertical EKF.

Android also provides *uncalibrated* gyro measurements and bias estimates; its documentation explains that uncalibrated gyroscope data omits drift compensation and provides bias estimates, which can be useful for custom filtering. citeturn4search3turn4search11turn7search11  

**Orientation mapping / “mounting alignment”**  
Provide a “Mounting alignment” page:
- ask user to place phone so “screen faces pilot,” “top points forward,” etc.
- compute a fixed rotation \(R_{mount}\) between device frame and aircraft “body frame.”
This is essential because Android’s sensor axes are tied to the device’s natural orientation and never swap when the screen rotates. citeturn7search0turn7search12  

**QNH / absolute altitude (optional but expected by pilots)**  
If you show MSL altitude, provide:
- manual QNH entry, or
- “set current altitude” at takeoff (compute implied sea-level pressure).
Android explicitly states that sea-level pressure must be known for accurate absolute altitude; otherwise standard atmosphere is only an approximation. citeturn5search5turn5search24  

### Pilot-facing tuning parameters that map to real math

Avoid exposing raw Q/R entries. Provide three pilot-relevant controls:

- **Responsiveness** (fast/medium/slow): maps to process noise on \(v\) and your display smoothing time constant.
- **Turbulence rejection** (low/medium/high): maps to baro QC aggressiveness, innovation gate threshold, and the multiplier on adaptive \(R_k\).
- **Audio aggressiveness**: maps to deadband and tone slope.

Tie these to loggable parameters so you can compare flights and reproduce results.

## Wind estimation via circling drift and how to communicate it honestly

### Low-bandwidth wind estimation method you can implement with phone sensors

Use the well-established “circling wind” concept:

- Detect circling mode from GNSS track curvature (and optionally gyro yaw rate).
- During stable circling, estimate wind as the **mean ground-velocity vector** over a sufficiently complete turn. In ideal constant-airspeed circles, the airspeed vector averages to ~0 over a full circle, so average ground speed approximates wind. In real flight, it’s imperfect—so you need confidence gating.

XCSoar’s manual explicitly states circling wind requires only a GPS source, while zigzag wind requires an intelligent vario with airspeed output, which the phone does not have. citeturn3search6turn0search39  

Also note: even in the XCSoar project, developers discuss that circling wind calculations depend on assumptions like “perfect circles” and “constant airspeed,” and they acknowledge room for improvement. That’s exactly why XCPro should display confidence and limitations. citeturn3search2turn3search6  

### Wind UI elements that prevent user self-deception

Display:

- wind arrow + speed,
- **confidence badge** (Poor / Fair / Good),
- “last updated” age,
- “source: circling drift.”

Confidence should be a function of:
- heading coverage (e.g., >300° within last N seconds),
- variance of ground speed magnitude,
- consistency of estimated wind across consecutive circles.

Make it explicit that this is **not instantaneous 3D wind** like HAWK claims; HAWK’s own marketing and firmware notes describe instantaneous 3D wind via sensor fusion and advanced DSP on compatible hardware. citeturn3search0turn3search8  

## GitHub integration steps, test strategy, and comparison to HAWK hardware

### Practical integration steps for XCPro (repo-agnostic but actionable)

Because I can’t see your GitHub repository structure in this chat, the steps below are organized so they drop cleanly into most Android app layouts.

1) Create a new feature branch following GitHub Flow (“branch → PR → review → merge”). GitHub’s own documentation emphasizes PR-based review as the core collaboration mechanism. citeturn6search19turn6search34  

2) Add packages/modules:
- `com.xcpro.sensors` (Android platform code)
- `com.xcpro.fusion` (pure Kotlin EKF/QC math)
- `com.xcpro.vario` (audio/UI shaping)
- `com.xcpro.logging`

3) Implement a stable internal API:
- `SensorSample` (timestamped), `BaroSample`, `ImuSample`, `GnssSample`
- `VarioOutput` (vario_instant, vario_display, vario_avg10s, qc_flags, confidence)

4) Build the flight service:
- foreground service controlling sensor registration and the fusion loop.
- comply with Android 14+ foreground service type declarations. citeturn1search9turn4search5  

5) Add automated tests early:
- **Local unit tests** for EKF math, QC behavior, and tuning mapping (use JVM tests for speed). citeturn6search0  
- **Instrumented tests** for sensor collectors and service lifecycle behaviors (registration/unregistration, timestamp sanity, permission errors). citeturn6search1  
- If you use Robolectric to test Android-ish logic locally, Android provides guidance for Robolectric strategies. citeturn6search3  

### Testing and validation in real flight conditions

A credible validation plan has three layers:

**Ground truth sanity tests (bench)**  
- elevator/stairs profiles: verify lag and noise; smartphone barometer papers demonstrate vertical velocity inference from pressure with the hydrostatic approximation. citeturn2search4turn2search20  
- fan/airflow tests: verify that Hampel/median QC + adaptive \(R_k\) prevents false “climb” spikes, motivated by documented pressure variability factors. citeturn5search0turn2search13  

**Flight A/B comparisons (the only tests pilots will believe)**  
- mount a dedicated vario alongside the phone and record both streams.
- compare:
  - noise floor in cruise,
  - thermal entry latency (time to positive vario),
  - false positive rate in gusty cruise,
  - stability of 10–20s averaged climb.

**Post-flight log-based tuning**  
- Use logs to tune:
  - baro QC thresholds,
  - EKF Q/R (especially adaptive \(R_k\)),
  - response curves for audio tones.

This is aligned with how serious wind/vario systems treat tuning: HAWK documentation explicitly discusses that TEK vs HAWK have different dynamics and that HAWK depends on internal parameters like SIGWIND. citeturn3search1turn3search0  

### Achievable features vs LXNav HAWK hardware

| Capability | XCPro phone-based module (S22 Ultra sensors only) | LXNav HAWK (dedicated system) |
|---|---|---|
| Low-latency vario feel | Achievable via baro+IMU fusion (vertical KF/EKF, adaptive baro trust) citeturn3search3turn5search0 | Achieved as part of the HAWK fused solution citeturn3search0turn0search39 |
| “Gust filtering” meaning | Suppress **pressure transients and phone artifacts** (QC + gating + adaptive \(R_k\)) citeturn5search0turn2search13 | Suppress **horizontal gust false indications** via 3D airmass estimation (EKF + air data) citeturn0search39turn3search0turn3search1 |
| Instantaneous 3D wind vector | Not achievable (no dynamic pressure / TAS input; only GNSS and IMU) citeturn0search39turn3search6 | Core feature: “instantaneous 3D wind” described/marketed by LXNAV; supported by sensor fusion approach citeturn3search8turn0search39turn3search0 |
| Wind estimation | Circling drift wind with confidence gating (low bandwidth) citeturn3search6turn3search2 | Real-time wind on all axes (as described by LXNAV/HAWK sources) citeturn3search8turn0search39 |
| Sampling stability | Constrained by Android limits (200 Hz cap) + foreground requirements citeturn0search1turn1search1turn1search9 | Dedicated avionics platform (purpose-built timing and sensor integration) citeturn0search39turn3search1 |
| Installation sensitivity | Very high (mounting rigidity + airflow/pressure environment dominate) citeturn5search0turn7search0 | High, but built for glider installation and calibrated pressure plumbing citeturn0search39turn3search1 |

### End-to-end workflow diagram for XCPro integration

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                           FOREGROUND FLIGHT SERVICE                      │
│  - starts/stops sensor listeners                                         │
│  - maintains notification + stop action                                   │
│  - exposes VarioOutput stream to UI + Audio                               │
└───────────────┬──────────────────────────────────────────────────────────┘
                ▼
┌───────────────────────────────┐       ┌───────────────────────────────┐
│  SENSOR COLLECTORS (Android)   │       │   GNSS COLLECTOR (Android)    │
│  baro / accel / gyro / rotvec  │       │   fused location (+ optional) │
│  - lightweight callbacks       │       │   - ring buffer + timestamps  │
│  - ring buffers + timestamps   │       └───────────────────────────────┘
└───────────────┬───────────────┘
                ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                    PREPROCESSING + QC (pure Kotlin)                       │
│  - pressure QC: median/Hampel, dp/dt gates, flags                         │
│  - attitude: game rotation vector OR Mahony/Madgwick                       │
│  - compute a_z (vertical linear acceleration)                              │
└───────────────┬──────────────────────────────────────────────────────────┘
                ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                     VERTICAL EKF (pure Kotlin)                            │
│  - IMU-rate predict step                                                   │
│  - baro update with innovation gating                                      │
│  - adaptive R_baro based on turbulence index + QC flags                    │
│  Outputs: v_instant, v_display, v_avg, qc_state                            │
└───────────────┬──────────────────────────────────────────────────────────┘
                ▼
┌───────────────────────────────┐       ┌───────────────────────────────┐
│  AUDIO VARIO ENGINE            │       │  WIND ESTIMATION (optional)   │
│  - audio focus management      │       │  - circling detection         │
│  - tone map + deadband         │       │  - drift wind + confidence    │
└───────────────┬───────────────┘       └───────────────┬───────────────┘
                ▼                                       ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                             UI + LOGGING                                  │
│  - vario needle/graph + avg climb + QC indicators                          │
│  - wind arrow + confidence                                                  │
│  - raw + fused logs for post-flight tuning                                 │
└──────────────────────────────────────────────────────────────────────────┘
```

This architecture is built to survive real Android constraints (foreground-only sensing, sampling caps, timestamp alignment) while delivering an instrument-like vario output and a defensible wind estimate. citeturn1search1turn0search1turn0search14turn3search6

### A blunt summary of what to implement first

If you want something pilots will actually use:

1) Get the **vertical vario** (baro+IMU fusion) stable and fast, with logs. citeturn3search3turn5search0  
2) Make QC + adaptive baro trust strong enough that cockpit airflow doesn’t create fake thermals. citeturn5search0turn2search13  
3) Add circling wind only after you have confidence gating and honest UI messaging. citeturn3search6turn3search2  

In Samsung Electronics’ published specs, the S22 Ultra provides the required sensor set (baro + IMU + magnetometer) and GNSS, but Android platform constraints and the lack of airspeed/dynamic pressure mean you should build for **robustness and honest limitations**, not for marketing parity with HAWK. citeturn5search2turn0search39turn3search8