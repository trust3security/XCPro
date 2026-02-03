# Feasibility and Technical Design for Replicating HAWK‑Style Variometry on a Samsung Galaxy S22 Ultra Using Only Built‑In Sensors

## Sensor inventory on the Samsung Galaxy S22 Ultra and what each can realistically contribute

Official Samsung specifications for the Galaxy S22 Ultra list the core sensors that matter for any phone‑based variometer: **accelerometer**, **gyroscope**, **barometer (pressure)**, and **geomagnetic (magnetometer)**, plus other sensors that are largely irrelevant to variometry (proximity, light, Hall, fingerprint). citeturn6view0turn6view1 Those four (accel/gyro/baro/magnetometer) align with the minimal sensor set used in most consumer “IMU + baro” flight instruments. citeturn3view1

Samsung’s official spec sheets also list satellite navigation support (constellations), which is essential because any attempt to approximate HAWK’s wind estimation needs a stable ground‑velocity vector from GNSS: **GPS, GLONASS, BeiDou, Galileo, and QZSS** are explicitly listed for the S22 Ultra. citeturn6view1

From a HAWK replication perspective, the **most important sensor omission** is also the most decisive: a phone has **no differential (“dynamic”) pressure measurement for true airspeed (TAS)**—no pitot/static system, no calibrated air‑data ports, no controlled pneumatic plumbing. In the HAWK technical description, dynamic pressure sensing is a first‑class input to the estimator. citeturn15view0 This single missing measurement drives most of the feasibility limits discussed later.

### Practical sensor roles for a phone variometer

A phone can produce an effective “vertical speed indicator” using:

- **Barometer (TYPE_PRESSURE)** to estimate altitude change and vertical speed via the hydrostatic approximation (pressure → altitude) and differentiation/filtering; this approach is widely demonstrated in the research literature. citeturn10view1turn8view1  
- **Accelerometer + gyroscope** to create a “fast vario” (low latency) by extracting the vertical specific force (after removing gravity via an attitude estimate) and fusing it with the barometric altitude channel (to prevent accelerometer integration drift). citeturn10view1turn3view1  
- **GNSS (GPS etc.)** primarily as a slow stabilizer/check and for horizontal navigation. Raw GNSS measurements are accessible on modern Android devices, but measurement field support varies by chipset. citeturn5view0turn6view1

A phone can also provide **software/virtual sensors** (rotation vector, gravity, linear acceleration), which can simplify implementation because Android may deliver fused orientation estimates derived from the underlying IMU sensors. citeturn3view1

## Android sensor and location APIs, attainable sampling rates, and hard constraints

### Android sensor framework basics that matter in flight instrumentation

Android’s sensor framework provides discovery and runtime capability querying: you can enumerate sensors and query attributes like vendor, resolution, range, and power usage. citeturn3view1 This is important because Samsung does not publish a full public spec of barometer/IMU noise density, bias stability, or sensor part numbers for the S22 Ultra; the only robust approach is to **measure and characterize at runtime** (and in flight logs). citeturn3view1turn6view1

Android also explicitly warns that requested sampling delays are **only suggestions** and that the system and other apps can alter the effective sampling interval; the recommended practice is to use event timestamps to compute actual sample rate. citeturn4view2turn3view1 This matters because a variometer’s tuning depends strongly on consistent input timing.

### Sampling rate ceilings and permissions on Android 12+

Android 12+ introduces explicit sensor rate limiting “to protect potentially sensitive information,” specifically impacting accelerometer, gyroscope, and geomagnetic field sensors. citeturn4view0 The documented limits are:

- Using `registerListener()`: sampling is limited to **200 Hz**. citeturn4view0  
- Using `SensorDirectChannel`: sampling is limited to **RATE_NORMAL (usually ~50 Hz)**. citeturn4view0  
- If an app needs higher rates, it must declare the `HIGH_SAMPLING_RATE_SENSORS` permission or it may trigger a `SecurityException`. citeturn4view0  
- If the user disables microphone access using device toggles, the motion/position sensors are always rate‑limited regardless of that permission. citeturn4view0

For a phone variometer, **200 Hz IMU** is typically enough for a responsive vertical acceleration channel; the harder limit is usually the **barometer** and **GNSS update rate**, not IMU rate. The key design implication is that you should design the estimator to be stable and accurate at **≤200 Hz IMU** and at whatever rate barometer and GNSS are actually delivered on the device. citeturn4view0turn4view2

### Foreground execution and background restrictions

Android documentation notes that on Android 9+ (API 28+), apps in the background may not receive events from sensors that use continuous or on‑change reporting modes; the recommended approach is foreground operation or a foreground service. citeturn4view0 Flight instrumentation needs continuous sensing, so you should assume a foreground service is mandatory for reliability.

### GNSS data access: standard location vs raw GNSS

Two GNSS access paths matter:

- **Standard fused location provider / Location API**: gives `Location` objects including bearing, altitude, and velocity (if available); update frequency and accuracy depend on providers, permissions, and request options. citeturn5view1  
- **Raw GNSS measurement API** (`GnssMeasurementsEvent`): Android states raw GNSS measurement support is mandatory on Android 10+ devices, but the presence of specific raw fields (pseudorange rate, carrier phase/ADR, multi‑frequency) varies with chipset. citeturn5view0turn0search3

The raw GNSS API can support more sophisticated velocity estimation (e.g., Doppler‑based), but it substantially raises implementation complexity and still does **not** substitute for a true airspeed sensor when the goal is instantaneous wind triangle estimation. citeturn15view0turn5view0

## Mapping HAWK’s estimator and “gust filtering” mechanism to phone‑only sensors

### What HAWK actually does, technically

HAWK is explicitly described as a multisensor fusion system whose algorithm is a nonlinear **extended Kalman filter (EKF)** and whose defining property is that the **vertical component (vario) does not respond to horizontal gusts**, unlike TE variometers. citeturn15view0turn7search0

The HAWK sensor platform is described in the OSTIV 2024 paper as including: **3‑axis acceleration**, **3‑axis rotation**, **dynamic and static pressure sensors**, and **GPS** (plus a temperature sensor in the measurement model). citeturn15view0 It computes the **3D wind triangle** of wind, ground‑speed, and **true airspeed** in real time. citeturn15view0

HAWK’s gust immunity is not a cosmetic “needle damping” feature. The key is that horizontal gust‑driven disturbances that would contaminate TE signals can be attributed within the estimator to the **horizontal wind components** (and other modeled states) without spuriously appearing as vertical air‑mass motion. citeturn15view0turn7search0

### The direct sensor‑to‑sensor mapping: what matches and what does not

**What maps well from HAWK → S22 Ultra:**

- **3‑axis acceleration**: phone accelerometer exists. citeturn6view0turn3view1  
- **3‑axis rotation**: phone gyroscope exists. citeturn6view0turn3view1  
- **GPS/GNSS**: phone GNSS exists, multi‑constellation. citeturn6view1turn5view0  
- **Static pressure**: phone barometer exists (ambient pressure). citeturn6view0turn3view1  

**What does not map (the blocker):**

- **Dynamic pressure / TAS measurement**: HAWK uses sensors for *dynamic and static pressure* specifically. citeturn15view0 A phone barometer is not a differential pressure sensor and is not a calibrated pitot system; there is no built‑in TAS sensor stream you can access via Android sensors. citeturn3view1turn15view0

This missing dynamic‑pressure input matters because wind estimation is fundamentally tied to the wind triangle:
\[
\vec{V}_{ground} = \vec{V}_{air} + \vec{W}
\]
HAWK has \(\vec{V}_{ground}\) (GPS) and sufficient information to constrain \(\vec{V}_{air}\) (TAS from dynamic pressure plus attitude/aero constraints), making \(\vec{W}\) observable in real time. citeturn15view0 A phone has \(\vec{V}_{ground}\) but lacks a direct measurement of \(\|\vec{V}_{air}\|\), so \(\vec{W}\) becomes weakly observable or unobservable without additional assumptions and maneuvers.

### What “gust filtering” means in a phone context vs in HAWK

In HAWK, “gust filtering” specifically targets TE’s false indications caused by **horizontal gusts**; HAWK’s vario does not respond to them. citeturn15view0turn7search0

A phone‑only variometer is typically **not TE‑compensated** in the same aerodynamic sense unless you build a TE model. If you simply compute vertical speed from pressure altitude change, a purely horizontal gust does not necessarily create a TE‑style false climb signal—but you inherit a different major problem: vertical speed becomes contaminated by **energy exchange due to pitch/speed changes of the aircraft**, because you are measuring aircraft vertical motion, not vertical airmass motion (netto). HAWK explicitly computes vertical airmass movement (and then derives “potential climb rate” by subtracting theoretical sink rate). citeturn15view0turn12view0

So the honest framing is:

- **Phone‑only can replicate a responsive “VSI‑like” vario.** The physics and literature support pressure‑sensor‑derived vertical velocity as practical, and also note that pressure is typically less noisy than accelerometer integration. citeturn10view1turn11view0  
- **Phone‑only cannot replicate HAWK’s core measurement principle (instantaneous 3D wind triangle) because there is no TAS/dynamic pressure measurement.** citeturn15view0  
- **Therefore, phone‑only cannot truly replicate HAWK’s gust immunity in the same way**, because HAWK’s immunity is a property of a joint wind/TAS/state estimate, not merely smoothing. citeturn15view0turn7search0

### What can be approximated anyway: low‑bandwidth wind estimation from maneuvers

Even without airspeed sensors, limited wind estimation is possible using kinematics over time, especially during circling. Two independent sources in the gliding community describe essentially this principle:

- XCSoar’s user manual states wind can be estimated from **drift in circling (GPS fixes)**; its “zigzag” wind estimation method in cruise requires **true airspeed measurements from an intelligent variometer**, which a phone alone does not provide. citeturn16view2  
- FlySkyHy (not Android, but explicitly documents the method) states wind is computed from **displacement while circling** and from track/heading deviation in straight flight; it also explicitly warns that “air speed” computed from ground speed and estimated wind “cannot be entirely relied upon,” which is exactly the observability/latency problem you will face on Android too. citeturn20view1

This supports a practical conclusion: a phone app can approximate average horizontal wind over tens of seconds (and mainly when maneuvering), but “instantaneous 3D wind” (and therefore HAWK‑grade gust immunity) is not achievable with phone sensors alone. citeturn15view0turn16view2

## Proposed Android app architecture and signal‑processing workflow to approximate HAWK‑like behavior

### Design goals and what “success” looks like

Given the sensor constraints, the best‑possible phone‑only design target is:

- A **low‑latency, stable climb/sink output** (vertical speed of the phone/aircraft) suitable for thermalling cues.
- A **robust rejection of spurious spikes** caused by cockpit pressure transients, vibration, and attitude changes—this is the phone‑appropriate analogue of “gust filtering.”
- An **optional, low‑bandwidth wind estimate** (horizontal) only when conditions support observability (circling or deliberate maneuvers), clearly labeled as averaged/estimated.

This is fundamentally different from HAWK, which jointly estimates wind/TAS states in an EKF using dynamic and static pressure plus GPS and IMU. citeturn15view0turn7search0

### High‑level module architecture

A workable architecture is layered and explicitly time‑synchronized:

1. **Sensor acquisition layer**  
   - `SensorManager` streams: pressure, accelerometer, gyroscope, magnetometer (optional). citeturn3view1turn4view2  
   - `Location` updates and/or raw GNSS measurements. citeturn5view0turn5view1  
   - Run in a foreground service to avoid background sensor cutoffs. citeturn4view0  

2. **Time alignment & resampling**  
   - Use sensor timestamps; treat configured delays as hints. citeturn4view2  
   - Resample async streams to a common estimator tick (e.g., 50–100 Hz), with IMU as the “clock.”

3. **Preprocessing**  
   - Pressure → altitude change (relative), remove slow drift trends when necessary (weather/cabin pressure drift). The literature emphasizes that barometers are better for **relative** vertical change than absolute altitude. citeturn8view1turn11view0  
   - IMU calibration and bias handling (online estimation).

4. **State estimation**  
   - Vertical channel EKF (or complementary filter) that fuses baro altitude with gravity‑compensated vertical specific force.

5. **Output synthesis**  
   - Instantaneous vario (fast), plus short/long averages, plus audio.  
   - Optional wind estimate page derived from maneuver windows.

### A concrete filtering workflow that is implementable on S22 Ultra

Below is a workflow that mirrors the *structure* of HAWK (predict/update, outlier handling, tunable process noise), while operating within phone sensor limits.

```text
          ┌─────────────────────────────────────────────────┐
          │                    INPUTS                        │
          │  Pressure (baro)  IMU (accel+gyro)  GNSS (GPS)   │
          └───────────────┬─────────────────────────────────┘
                          ▼
┌────────────────────────────────────────────────────────────────┐
│                  PRE-PROCESSING & QC                            │
│  - pressure outlier gating (median/Hampel, spike detection)      │
│  - IMU bias/scale sanity checks                                  │
│  - attitude estimate (gyro integration + accel stabilization)    │
│  - compute vertical specific force a_z (earth frame)             │
└──────────────────────────┬─────────────────────────────────────┘
                           ▼
┌────────────────────────────────────────────────────────────────┐
│      VERTICAL CHANNEL EKF (or complementary filter)             │
│  State example: x = [h, v, b_a]^T                               │
│    h = altitude (relative), v = vertical speed, b_a = accel bias│
│  Predict:                                                       │
│    v_k = v_{k-1} + (a_z - b_a) * dt                             │
│    h_k = h_{k-1} + v_k * dt                                     │
│    b_a random walk                                              │
│  Update:                                                        │
│    z_baro = h (from pressure)                                   │
│    Optional z_gnss = v or h (low weight, low rate)              │
│  Adaptive noise:                                                │
│    Increase R_baro when cockpit pressure is unstable             │
│    (e.g., high dP/dt spikes not matched by IMU)                 │
└──────────────────────────┬─────────────────────────────────────┘
                           ▼
┌────────────────────────────────────────────────────────────────┐
│                    OUTPUT LAYER                                 │
│  - fast vario: v(t) (audio)                                     │
│  - displayed vario: short avg (e.g., 1–2 s)                      │
│  - integrated avg: long avg (e.g., 10–20 s)                      │
│  - optional "wind estimate" module (circling window fit)         │
└────────────────────────────────────────────────────────────────┘
```

This “adaptive measurement noise” idea is the phone analogue of HAWK’s tunable wind model parameters (where larger wind noise density yields more turbulent wind estimates, smaller yields smoother). citeturn15view0turn12view0 It is also consistent with Android’s guidance that sampling and noise characteristics vary and must be handled in software. citeturn4view2turn3view1

### Approximating HAWK’s wind and gust rejection without TAS

You cannot compute the HAWK wind triangle the way HAWK does—because HAWK’s sensor platform explicitly includes dynamic pressure for TAS. citeturn15view0 But you can implement two limited wind estimators:

- **Circling drift wind** (low bandwidth, works mainly in thermals): estimate mean wind as the drift of the ground track center during sustained circling. This is explicitly described as a valid method in XCSoar. citeturn16view2  
- **Straight‑flight track/heading wind** (fragile, requires heading and assumptions): infer wind by comparing heading (magnetometer/gyro‑derived) with GPS track and ground speed; but without TAS the magnitude is not well constrained, and magnetometers are often unreliable in cockpits due to interference—an issue HAWK explicitly calls out as “cannot be compensated” for magnetic sensors. citeturn15view0turn3view1

If you include wind estimation, the UI must clearly label it as **estimated/averaged**, not “instantaneous real time wind,” because HAWK’s own critique of TE wind is that reliable horizontal wind needs long averaging during thermalling when measured conventionally. citeturn15view0turn16view2

## Expected performance versus dedicated HAWK hardware

### What will work well

A Galaxy S22 Ultra can deliver a credible **climb/sink indication** as long as the pressure sensor is not exposed to strong cockpit pressure transients. The research literature shows pressure‑sensor‑derived vertical velocity is practical and often less noisy than accelerometer integration; accelerometer-only integration suffers from drift/error accumulation. citeturn10view1

Smartphone barometers are broadly considered capable of tracking altitude changes on the order of ~1 meter in many contexts, and barometric pressure is influenced by both static and dynamic environmental factors—so post‑processing is essential. citeturn11view0turn8view1

From a computation standpoint, the S22 Ultra has ample processing power for a 50–200 Hz EKF; the constraints are **sensor observability and measurement quality**, not CPU. The main engineering effort is robust filtering and field tuning, not raw compute. citeturn4view0turn15view0

### Where the phone will fall short, decisively

A phone‑only solution cannot replicate these HAWK properties:

- **Instantaneous 3D wind triangle (wind, ground speed, true airspeed)**: HAWK explicitly relies on dynamic and static pressure sensors plus GPS in its platform description. citeturn15view0  
- **HAWK’s specific gust immunity claim** (“vario does not respond to horizontal gusts unlike TE vario”) as a measurement‑principle property: that claim is rooted in the joint estimation of wind/TAS/state using the HAWK multisensor platform. citeturn15view0turn7search0  
- **Netto / airmass vertical movement comparable to HAWK**: HAWK explicitly computes vertical airmass movement and then subtracts theoretical sink rate to form a “potential climb rate.” citeturn15view0turn12view0 Doing that on a phone requires an accurate airspeed and aero model; without TAS, you are forced into approximations that can be directionally wrong in wind. citeturn16view2turn20view1

Put bluntly: a phone can be a decent variometer; it cannot be a HAWK clone.

### Operational edge cases that will degrade phone performance

Even for “just a variometer,” phones have unique vulnerabilities:

- **Cabin/vent pressure coupling**: smartphone barometers sense ambient pressure near the device and are affected by environmental dynamics; literature emphasizes barometric pressure is influenced by static and dynamic properties of the environment. citeturn11view0 In a glider cockpit, canopy leaks, vents, and local airflow can create pressure artifacts that look like vertical motion unless you isolate the phone from direct airflow. (This is a physical installation problem, not just filtering.) citeturn11view0turn8view1  
- **Attitude/gravity separation errors**: IMU‑based “fast vario” depends on a good attitude estimate; if the phone shifts or vibrates, gravity projection errors leak into the vertical acceleration channel and create false lift/sink spikes. citeturn3view1turn10view1  
- **Android platform constraints**: rate limiting (200 Hz) and foreground requirements must be handled; otherwise performance can collapse when the OS throttles sensors. citeturn4view0turn4view2

## Existing Android and community approaches that approximate parts of the problem

### XCSoar on Android: baro vario and wind estimation limits

XCSoar’s manual explicitly recommends using a barometric sensor (external or internal if present) for proper audio variometer performance. citeturn16view2 It also documents that its continuous wind display is derived from **wind drift during thermal flight (climb mode)**, and that a more cruise‑applicable “zigzag” wind estimation method requires **GPS fixes and true airspeed measurements**, which are only available when connected to an intelligent variometer that outputs TAS. citeturn16view2 This is direct support from an established glide computer that phone‑only wind estimation is limited—exactly the barrier you hit trying to replicate HAWK without dynamic pressure.

Separately, the BlueFlyVario integration notes that XCSoar uses a **Kalman filter** to smooth barometric pressure data (and provides code pointers). citeturn19view0 While that specific implementation is for an external sensor, it accurately reflects the “baro altitude + Kalman smoothing” design pattern common in vario systems.

### XCTrack: practical phone constraints in the field

XCTrack’s documentation states acoustic vario is enabled only when a barometric pressure sensor is present (internal or external), and it discusses practical issues like turning off Android sleep optimizations for reliability. citeturn16view1 That operational guidance aligns with Android’s broader restrictions around sensor delivery and background behavior. citeturn4view0turn16view1

### Community evidence: phone‑sensor varios are feasible, but “erratic” without external sensors

FlySkyHy’s FAQ (again iOS, but this is still a strong empirical datapoint for phone‑only sensing) states that when relying on the phone’s internal accelerometer and pressure sensor, the result can be “decent” but occasionally “erratic,” and that external Bluetooth vario hardware is more reliable. citeturn20view0 The underlying reasons translate directly to Android: barometer sampling/latency characteristics, vibration, and installation effects.

FlySkyHy’s own instrument documentation also warns that airspeed inferred from ground speed and estimated wind “cannot be entirely relied upon.” citeturn20view1 That warning is exactly what a phone‑only HAWK‑style design runs into: without TAS measurement, the wind triangle becomes a chain of estimates whose errors compound.

## Recommendations for development strategy, calibration, and tuning on the S22 Ultra

### Development strategy that avoids dead ends

If the goal is explicitly “replicate HAWK,” the right strategy is to split the project into two deliverables:

1. **Phone‑only high‑quality variometer (VSI‑class)**: implement baro+IMU fusion with robust outlier handling and tunable responsiveness. This is feasible and can be excellent. citeturn10view1turn11view0  
2. **HAWK‑style wind/gust immunity approximation**: implement only what is observable (circling‑based mean wind), and do not claim instantaneous wind or HAWK‑equivalent gust filtering because the phone lacks dynamic pressure/TAS. citeturn15view0turn16view2

Trying to “EKF your way” to true airspeed without any air data will produce a model that looks plausible in calm conditions and fails exactly when pilots care most (gusty cruise and complex wind fields). citeturn15view0turn20view1

### Calibration and installation guidance that actually matters

A phone variometer’s installation is part of the signal chain:

- **Rigid mounting and fixed orientation**: treat the phone as an avionics box, not a handheld. Attitude estimation and gravity removal assume a stable sensor frame. citeturn3view1turn10view1  
- **Pressure isolation**: avoid direct airflow over the phone; pressure sensors respond to environmental dynamics and need post‑processing. citeturn11view0turn8view1  
- **Runtime characterization**: query sensor resolution, range, vendor/version, and measure effective sample rates using timestamps. Android explicitly recommends using timestamps because configured delay is only a suggestion. citeturn4view2turn3view1  

### Android implementation tuning knobs analogous to HAWK’s SIGWIND concept

HAWK exposes “wind variance” / SIGWIND that smooths horizontal and vertical wind (netto) and changes nervousness; the HAWK EKF paper describes the analogous mechanism as wind random walk noise density where larger values yield faster/more turbulent wind estimates. citeturn12view0turn15view0

A phone app should expose comparable, honest knobs—but apply them to what the phone can actually estimate:

- **Vario responsiveness**: EKF process noise / complementary filter blend factor (how much short‑term acceleration drives output vs baro). citeturn10view1turn11view0  
- **Pressure spike rejection**: outlier thresholding and a user “cockpit pressure turbulence” setting that increases baro measurement noise when high‑frequency pressure spikes occur. citeturn11view0turn4view2  
- **Short/long averaging**: separate short average (pilot control cue) and long average (thermal strength cue), similar in spirit to common flight apps and glide computers. citeturn16view1turn16view2  
- **Wind estimation confidence**: show a confidence metric tied to maneuver observability (e.g., “good” only after one or two stable circles), matching the documented reality of circling‑based wind estimation. citeturn16view2turn20view1

### Required permissions and OS settings to document for users

If you want reliable high‑rate sensing:

- Ensure the app runs in a **foreground service** when “in flight.” citeturn4view0  
- Be mindful of Android 12+ sensor rate limits and the `HIGH_SAMPLING_RATE_SENSORS` permission if you attempt >200 Hz, and warn users about the microphone privacy toggle side effect on motion sensor rates. citeturn4view0  
- For GNSS raw measurements, handle optional field availability and device differences; Android explicitly states field support varies by chipset. citeturn5view0

## Comparison table: S22 Ultra phone‑only app versus LXNav HAWK hardware

| Category | Galaxy S22 Ultra phone‑only app | LXNav HAWK (as implemented on LXNAV platforms) |
|---|---|---|
| Core sensing inputs | Accelerometer, gyro, magnetometer, barometer; GNSS (GPS/GLONASS/BeiDou/Galileo/QZSS) citeturn6view0turn6view1 | 3‑axis acceleration + 3‑axis rotation + **dynamic and static pressure** + GPS (+ temperature sensor in measurement model) citeturn15view0 |
| TAS / air data | **No TAS sensor available**; only inferential estimates using wind models and assumptions (low reliability) citeturn20view1turn16view2 | True airspeed is part of the wind triangle and supported by dynamic pressure sensing citeturn15view0 |
| Primary estimator | Vertical channel fusion (baro + IMU) via EKF/complementary filter; optional low‑bandwidth wind estimator | Nonlinear EKF jointly estimating wind, TAS, attitude, etc. citeturn15view0turn7search0 |
| “Wind gust filtering” mechanism | Robust spike rejection + adaptive noise to prevent cockpit pressure transients from appearing as vario; cannot replicate HAWK’s measurement‑principle gust immunity | Explicit property: vertical component (vario) does **not respond to horizontal gusts** unlike TE vario citeturn15view0turn7search0 |
| Wind estimation quality | Circling‑based drift wind only (averaged); cruise wind is weak without TAS; consistent with XCSoar’s requirement of TAS for “zigzag” cruise wind estimation citeturn16view2 | Real‑time wind in all axes as part of the joint estimate citeturn15view0turn7search0 |
| Sampling constraints | IMU rate limited (200 Hz unless special permission; also affected by privacy toggles); sensor delays are hints citeturn4view0turn4view2 | Dedicated avionics platform; HAWK described as computationally heavy and designed around its sensor suite citeturn15view0 |
| Installation sensitivity | Very high: phone mounting, airflow, temperature, OS throttling | High but designed for aircraft integration; HAWK manuals also emphasize alignment/leveling requirements for AHRS‑based estimation citeturn12view0 |

### Bottom-line feasibility assessment

- **Feasible**: building a high‑quality Android variometer on the S22 Ultra (baro + IMU fusion, robust filtering, good audio). This is well supported by both Android platform capabilities and the research literature on pressure‑derived vertical velocity. citeturn10view1turn3view1turn4view0  
- **Not feasible to truly replicate**: the HAWK measurement principle and its specific gust immunity (rejection of horizontal gust false alarms in a TE context), because HAWK’s estimator depends on dynamic pressure / TAS sensing and a multisensor platform that a phone does not have. citeturn15view0turn7search0  
- **Partially feasible**: approximating *some* HAWK‑adjacent features—especially wind estimation during circling—at lower bandwidth and with explicit confidence/averaging caveats. citeturn16view2turn20view1