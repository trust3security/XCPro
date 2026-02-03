# Operational Principles and Wind‑Gust Filtering in the LXNav HAWK Vario

## What the HAWK vario is and what “gust filtering” means in practice

The “HAWK vario” in LXNAV’s ecosystem is not just another damping/smoothing option on a classic pressure‑derived variometer. It is a separate measurement approach that estimates **three‑dimensional air‑mass motion** (wind vector including vertical air‑mass movement) in real time using **sensor fusion**, and then derives “vario” outputs from that estimate. citeturn9view0turn30view0turn8view0

This matters because the dominant “gust problem” for a conventional **TE (total‑energy) variometer** is *not* that it is “too noisy,” but that it has a **systematic false‑alarm mechanism**: **horizontal gusts (changes in horizontal air motion) can be interpreted as climb/sink even when there is no vertical air motion**. This behavior is rooted in the TE measurement principle and cannot be fully removed by simply filtering the TE signal harder without also losing real, time‑critical lift information. citeturn17view4turn7view0turn20view0turn19view1

The HAWK approach targets exactly that failure mode: it is designed so the **vertical output does not respond (unlike TE vario) to horizontal gusts**, reducing false “thermals” and improving early thermal detection. citeturn8view0turn30view0turn9view0

image_group{"layout":"carousel","aspect_ratio":"1:1","query":["LXNAV SxHAWK variometer","LXNAV SxHAWK 80mm variometer display","LXNAV Hawk vario dual needle instrument","LXNAV SxHAWK variometer rear ports"],"num_per_query":1}

## Sensors and system architecture

### Hardware platforms that run HAWK

LXNAV positions HAWK as a **software option** that runs on supported LXNAV hardware generations (and specific vario hardware such as V8/V80), and it is marketed as eliminating horizontal gust effects while also providing AHRS outputs. citeturn9view0

A concrete example platform is the **SxHAWK** stand‑alone instrument (57 mm / 80 mm). LXNAV describes it as containing an integrated high‑precision digital pressure sensor and an inertial platform, with sensors sampled **>100 Hz**, plus display/controls, GPS/FLARM connectivity, and an IGC‑approved flight recorder (platform features vary by configuration). citeturn32view2turn33view0turn35search0

### Sensor suite relevant to the vario and gust handling

Across LXNAV documentation and the linked technical papers describing HAWK, the key sensing elements used by the HAWK estimator are consistent:

- **Pressure sensing** for static and dynamic pressure (airspeed/TE‑related pneumatic signals). citeturn7view0turn8view0turn33view0  
- **GNSS/GPS** for ground‑referenced motion (groundspeed vector, position). citeturn7view0turn8view0turn32view2  
- **IMU** (tri‑axis accelerometer + tri‑axis gyro) for inertial dynamics and attitude propagation/correction. citeturn7view0turn8view0turn10view0  
- **Temperature sensing** is included in the measurement model in the technical description (used to correct pressure/airspeed relationships and sensor behavior). citeturn8view0turn33view0  

LXNAV explicitly ties its HAWK capability to a **high‑rate sensor and compute pipeline**: the SxHAWK manual notes >100 Hz sensor sampling, while the Segelfliegen‑derived technical description cites sensor processing rates ranging from roughly **10–100 Hz** depending on the sensor channel. citeturn32view2turn7view0

### Architectural split: “sensor box + estimator + display outputs”

The HAWK description is architecturally classic sensor‑fusion: a “sensor box” feeds an onboard processor running an estimator that maintains a state (wind, attitude, etc.) and publishes derived outputs. The Segelfliegen technical description characterizes the HAWK unit as an **ARM‑processor‑based computer** plus a sensor unit; it runs real‑time models of aircraft kinematics, air‑mass movement, and sensor imperfections, then performs recursive estimation. citeturn7view0turn16view2

The OSTIV 2024 paper provides an explicit EKF block‑diagram framing: IMU + GPS + barometric/pressure + pitot/dynamic pressure feed an EKF that estimates state variables including orientation and wind, using kinematic equations, quaternion attitude propagation, wind modeling, and sensor bias terms. citeturn8view0turn16view4

## How it functions as a variometer

### The “baseline” variometer chain in the SxHAWK platform

In the SxHAWK manual, LXNAV is very direct about the basic variometer signal path:

- The **vario signal is derived from the altitude signal**, and altitude/speed pneumatic signals come from high‑quality pressure sensors (so “no flask is necessary” in their wording). citeturn33view0  
- The system applies **temperature and altitude compensation** to these signals. citeturn33view0  
- The variometer presentation includes selectable **time constants (0.1 s to 5 s)** and additional “electronic processing” modes for the vario signal. citeturn33view0  

This is the conventional digital‑pressure variometer approach: measure pressure‑derived altitude and compute its time derivative, then apply controlled filtering so the needle/audio are flyable. citeturn33view0turn27view0

### Total‑energy compensation still exists, but it is not the HAWK method

LXNAV also keeps classic TE functionality alongside HAWK:

- The SxHAWK manual describes **two TE correction methods**: electronic TE compensation (based on speed changes with time) and pneumatic compensation with a TE probe, with TE quality depending heavily on installation and leak‑free plumbing. citeturn33view0turn16view1  
- LXNAV’s “two‑needle” concept explicitly contrasts TEK and HAWK: the TEK computation is shown on one needle (red), while the HAWK/EKF value is shown on the other (blue/light‑blue in LXNAV’s materials). citeturn9view0turn10view0turn30view0  

In other words, the platform can still behave like a traditional TE variometer—and will inherit TE’s gust sensitivity mechanisms—unless the pilot chooses to fly using the HAWK outputs for the relevant phase of flight. citeturn30view1turn8view0

### Output semantics: why HAWK “vario” is not identical to TEK “vario”

A key operational distinction is that HAWK’s primary internal estimate is **vertical air‑mass movement (“netto”)**, and the displayed “HAWK vario” is then a **derived quantity** (often described as “potential climb rate”) after subtracting sink‑rate terms from a polar model. citeturn30view0turn31view0turn8view0

LXNAV’s own HAWK chapter provides a compact mapping between classical computed values (TEK‑derived netto/relative) and HAWK‑derived values (netto as an estimate, and vario as netto minus sink_rate terms). citeturn30view1

That distinction is directly relevant to gust filtering: TEK is fundamentally tied to energy/pressure dynamics of the *glider*, while HAWK is framed as an estimator of *the air mass* (wind vector), which can remain “quiet” in dz even when the aircraft experiences horizontal gust‑related pressure fluctuations. citeturn30view0turn8view0turn7view0

## HAWK algorithm and the technical mechanism for rejecting wind gusts

### The core estimator: a nonlinear EKF that jointly estimates 3D wind and flight state

Both LXNAV’s official materials and the associated technical papers converge on the same claim: HAWK uses an **extended Kalman filter (EKF)** as the central signal‑processing method. citeturn9view0turn10view0turn7view0turn8view0

Operationally, the EKF structure is described in the Segelfliegen technical write‑up as a two‑step recursive cycle:

- **Time update (prediction)** via a process model (aircraft kinematics + wind model + sensor imperfections). citeturn7view0  
- **Measurement update (correction)** by comparing predicted measurements to real sensor signals (IMU, GPS, static pressure, dynamic pressure), weighting residuals, and updating the state estimate and its covariance (“statistical accuracy”). citeturn7view0  

The OSTIV 2024 paper is more explicit about the modeled state components: position/velocity kinematics, quaternion‑based attitude propagation, a wind model, and accelerometer/gyro bias terms in the estimator. citeturn8view0turn16view4

### Why horizontal gusts break TEK and why HAWK can reject them

Martin Dinges’ OSTIV work on TE variometry explains the underlying physics: TE variometers can show “additional signals” caused by **horizontal components of the flow field**, because TE readings depend on airspeed dynamics; a non‑homogeneous wind field (gusts) produces airspeed variations that appear in the TE signal even when the glider is not truly climbing in a vertical airmass updraft. citeturn17view4turn13search3

The Segelfliegen technical description makes the “can’t be compensated” point sharply: even a perfectly compensated TEK can show **horizontal wind changes as climb or sink although there is no vertical air motion**, and it attributes this to the one‑dimensional energy‑conservation measurement method. citeturn7view0

HAWK’s rejection mechanism is not primarily “a stronger low‑pass filter.” Instead, it changes the estimation problem:

- HAWK estimates the **full 3D wind triangle** in real time (groundspeed vector + true airspeed vector + wind vector). citeturn8view0turn7view0turn16view4  
- When a horizontal gust perturbs measured dynamic pressure / airspeed, the EKF has the degrees of freedom to attribute that change to **horizontal wind components (dx, dy)** (and related sensor/attitude dynamics) rather than incorrectly forcing that disturbance into **dz (vertical wind)**. citeturn30view0turn8view0turn7view0  

LXNAV’s own HAWK manual states this explicitly in operational terms: a change in horizontal velocity (horizontal wind gust) is interpreted by TEK as a change in vertical velocity (creating false TEK readings), whereas HAWK measures air‑mass motion in three dimensions and is designed to avoid those false indications. citeturn30view0turn8view0

### The wind model and the key “gust filter knob”: SIGWIND / wind variance

The most direct, firmware‑exposed mechanism for gust suppression in HAWK is the user setting **SIGWIND** (often surfaced as “wind variance”):

- LXNAV defines it as smoothing both **horizontal wind** and **vertical wind (netto vario)** readings; higher values produce more “nervous” behavior, and a recommended value around **0.11** is provided in manuals. citeturn16view1turn31view0  
- The associated wind model (in both the LXNAV manual chapter and the Segelfliegen technical description) treats wind as the sum of a **slowly varying component** plus a **rapid random disturbance**, with the implication that more turbulent air corresponds to larger random increments. citeturn17view0turn16view3turn31view0  

Crucially, the Segelfliegen write‑up directly ties SIGWIND to the estimator’s statistical assumptions: pointer fluctuations are modeled as Gaussian, with a standard‑deviation parameter governing how “likely” increments are; because the true parameter is unknown, pilots tune SIGWIND empirically using recorded 100 Hz sensor logs, and the paper describes small SIGWIND strongly suppressing deviations while large SIGWIND yields fast but nervous output. citeturn7view0turn17view2turn17view1

The OSTIV 2024 paper formalizes the same idea in estimator language: wind is modeled as a **random walk** with a noise term, and a higher wind noise density parameter yields faster/more turbulent wind changes, while a smaller value yields smoother changes. citeturn8view0turn16view4

In practical filtering terms, SIGWIND acts like a **process‑noise tuning parameter** for the wind state inside the EKF: smaller values behave like a stronger low‑pass constraint on how quickly the estimated wind vector is allowed to move; larger values allow the estimator to chase faster changes (including gust‑like transients) at the cost of noisier output. citeturn7view0turn8view0turn17view1

### Additional smoothing layers: displayed averages and classic needle/audio filtering

HAWK’s EKF estimate is not the only smoothing in the system:

- LXNAV exposes a **horizontal wind average** and **vertical wind average** period, used to compute displayed average wind and average vertical values over configurable windows (with typical recommended defaults such as ~30 s horizontal and ~10 s vertical in LXNAV materials). citeturn16view1turn31view0  
- Independent of HAWK, the S‑series vario stack includes traditional filtering knobs: a **Vario Needle Filter** (time constant), **Vario Sound Filter**, separate filters for netto/relative/SC, and a “Smart filter” that limits needle slew rate (higher damping at low settings). citeturn16view5turn14view3  

These are important because they illustrate two distinct philosophies living side‑by‑side in LXNAV:

1) **Model‑based rejection** (HAWK EKF + wind model + SIGWIND). citeturn8view0turn30view0turn17view1  
2) **Display‑layer smoothing** (classic time constants / damping that trade responsiveness for reduced “bumpiness”). citeturn16view5turn33view0  

HAWK’s core claim is that it reduces false readings without relying on heavy “slow‑down the vario” filtering—because it changes what is being estimated. citeturn9view0turn8view0turn7view0

### Robustness to outliers

The OSTIV 2024 paper notes another relevant engineering piece: the system can “fall out of lock” in rare conditions, and **an algorithm was implemented to prevent loss of lock due to random signal outliers**. The paper does not specify the exact technique, but in Kalman filtering practice this often corresponds to residual gating, innovation saturation, or robust weighting schemes. (This final identification of typical methods is an inference, not explicitly stated for HAWK.) citeturn8view0

### Visual workflow of the gust‑rejection pipeline

The block‑diagram below is a synthesis of the EKF structure described in the Segelfliegen technical document (sensor box → process model + measurement model → residual weighting → state update) and the OSTIV 2024 EKF depiction (IMU/GPS/pressure + aerodynamic constraints). citeturn16view2turn16view4

```text
              ┌────────────────────────────────────────────────────┐
              │                 SENSOR INPUTS                      │
              │  IMU (accel+gyro) | GPS | static P | dynamic P     │
              └───────────────┬────────────────────────────────────┘
                              │  (10–100+ Hz channels)
                              ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                         PRE-PROCESSING                                  │
│  - temperature/altitude compensation (pressure signals)                  │
│  - calibration, bias handling (modeled in EKF state)                     │
└───────────────────────────────┬─────────────────────────────────────────┘
                                ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                      EXTENDED KALMAN FILTER (EKF)                        │
│  Time update:                                                           │
│   - glider kinematics + attitude (quaternions)                           │
│   - wind model (random-walk / slow+random disturbance)                   │
│  Measurement update:                                                     │
│   - predict sensor readings                                               │
│   - compute residuals (measured - predicted)                              │
│   - robust weighting / outlier handling (reported, not detailed)          │
│   - update state + covariance                                             │
│                                                                         │
│  Key tuning for "gust vs smooth": SIGWIND / wind variance (process noise)│
└───────────────────────────────┬─────────────────────────────────────────┘
                                ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                       CORE ESTIMATES (STATE)                             │
│  - wind vector d = (dx, dy, dz)   ← dz is vertical airmass movement      │
│  - attitude / AHRS                                                        │
│  - TAS-related states + sensor biases                                     │
└───────────────────────────────┬─────────────────────────────────────────┘
                                ▼
┌─────────────────────────────────────────────────────────────────────────┐
│               DERIVED OUTPUTS + DISPLAY-LAYER FILTERS                    │
│  - HAWK "netto" = dz (vertical wind)                                     │
│  - HAWK "vario" = netto - sink_rate(polar, TAS, bank, sideslip)          │
│  - optional averaging windows: horiz/vert wind average                    │
│  - classic needle/audio time constants (separate from EKF)                │
└─────────────────────────────────────────────────────────────────────────┘
```

## What official LXNav documentation and firmware notes say about gust filtering

LXNAV’s own public materials are unusually explicit (for avionics) about the gust problem and how HAWK frames its solution:

- The **Version 9 firmware announcement** calls HAWK a “3 dimensional real time wind calculation, vario that eliminates horizontal wind gusts,” states it uses an **EKF** to estimate all three dimensions of air‑mass movement, claims **no compass** is required, and emphasizes **no false thermals due to wind gusts** alongside “no compensation needed” messaging. citeturn9view0  
- The **SxHAWK product page** makes the marketing claim “No Thermal gusts” and repeats that the platform applies an EKF to estimate 3D air‑mass movement “instantaneously.” It also describes the inertial platform and pneumatic sensor set used for fast vario and wind calculations. citeturn10view0  
- The **SxHAWK user manual** describes classic variometer behavior (pressure‑derived altitude derivative) and TE compensation options, then—within the HAWK chapter—states as a “unique differentiator” (i) real‑time horizontal wind + vertical air‑mass movement, (ii) **no false climb indication due to horizontal gusts** in cruise and in turbulent thermals, and (iii) earlier thermal indication vs TEK. citeturn33view0turn30view0  
- The manuals expose HAWK tuning parameters and their intended effect: **SIGWIND/wind variance** changes how “nervous” the wind/netto readings are, and separate averaging windows exist for horizontal wind and vertical wind. citeturn16view1turn31view0  
- LXNAV documents that TEK dynamic behavior depends on the pilot’s averaging time constant and false signals from horizontal gusts, while HAWK behavior depends on SIGWIND and internal parameters; manuals even note examples where HAWK peaks earlier in a thermal assistant context. citeturn30view1turn31view0  

A critical operational caveat also appears in LXNAV’s own manuals: **AHRS/installation alignment matters**. Misalignment leads to systematic errors in the HAWK algorithm, and LXNAV specifies compensation limits (e.g., ±10° pitch offset) beyond which the algorithm may not work properly. citeturn31view0

## External technical commentary and comparisons

### Academic/technical literature that explains the same gust issue

The Dinges OSTIV paper (2003) gives the classic explanation: TE variometers inherently respond to horizontal flow components because the TE signal is driven by airspeed/pressure dynamics; it explicitly treats “horizontal wind gusts” as a phenomenon that produces undesirable TE indications. citeturn17view4turn13search3

The Segelfliegen/Meyr–Huang technical write‑up (published as an English “long” version) goes further by framing gust false alarms as a fundamental limitation of TE measurement and then motivating the EKF wind‑triangle approach as a way to compute 3D airmass motion without a magnetic heading sensor, relying on observability from time‑varying motion. citeturn6view0turn7view0turn16view2

The OSTIV 2024 paper crystallizes HAWK’s performance goal in detection‑theory terms: reduce false alarms (gust‑driven “thermals”) while improving detection probability with earlier indication, and it again states that TE vario gust sensitivity is a consequence of the measurement principle “that cannot be compensated.” citeturn8view0

### Pilot community reports: what users say “works” and what remains tricky

Pilot discussions in the LXNav user group consistently report that HAWK dramatically reduces gust‑driven needle swings compared with TE indications and that wind/thermal features feel more time‑aligned with “seat‑of‑pants” cues, especially after leveling and setting SIGWIND near manual recommendations. citeturn28view0turn28view1

Those same discussions also highlight that practical tuning can be non‑obvious: multiple users report experimenting with SIGWIND and vertical wind averaging to trade steadiness vs responsiveness, and some report cases where HAWK appears “too optimistic” by several knots in some conditions. citeturn28view0turn28view1

Notably, LXNAV’s own manual acknowledges a related phenomenon: reports of average HAWK vario being too high in weak thermals (up to ~0.5 m/s) and lists plausible causes such as incorrect polar/glider data or pressure system issues, emphasizing that the cause can be multifactorial. citeturn34view0

### A technical comparison of gust‑filtering approaches

The table below compares **how gust handling is achieved** across several well‑known variometer approaches. It focuses on *wind‑gust rejection*, not general navigation or UI features.

| System / approach | What counts as “gust filtering” | Primary method | Pilot‑adjustable knobs (typical) | Key trade‑offs |
|---|---|---|---|---|
| **LXNAV HAWK (EKF airmass estimator)** | Prevent TE‑style false climb/sink from **horizontal gusts** by estimating full 3D airmass motion; dz should not react to purely horizontal gusts. citeturn8view0turn30view0turn9view0 | Nonlinear **EKF sensor fusion** using IMU + GPS + pressure (static/dynamic) + aerodynamic constraints; wind modeled as slowly varying + random disturbance / random walk. citeturn16view4turn7view0turn17view1 | **SIGWIND / wind variance** (responsiveness vs steadiness), plus horizontal/vertical wind averaging windows; correct AHRS leveling is required for best results. citeturn16view1turn31view0turn17view1 | More computation + model dependence (polar, alignment, sensor integrity). Can still show biases if polar/pressure inputs are wrong; documented “too optimistic” cases exist. citeturn34view0turn8view0 |
| **Classic TEK variometer (pressure + TE compensation)** | Reduce “stick thermals,” but **cannot fundamentally remove** horizontal gust false indications; filtering mainly reduces jitter. citeturn7view0turn17view4turn8view0 | TE principle (energy conservation / pressure signals) + optional damping/time constants. citeturn33view0turn13search3 | Needle/audio time constants; additional smoothing filters; installation quality (TE probe/static) is critical. citeturn16view5turn33view0turn20view0 | Strong damping reduces false swings but adds lag and can hide real lift; false signals can be comparable in magnitude/duration to real lift at high TAS. citeturn20view0turn19view1 |
| **Borgelt Dynamis (TE vario with gust immunity claim)** | Marketed as TE behavior but “immune to horizontal gusts,” aiming to indicate only vertical air motion. citeturn20view1turn13search5 | Uses a multi‑sensor approach emphasizing **dual‑GNSS attitude/heading** and accelerometers to measure trajectory angle changes; not framed as a Kalman filter in their description. citeturn20view1 | System‑specific configuration; relies on GNSS geometry/availability; includes fallback behaviors in some versions. citeturn20view1 | Different sensor/installation complexity (antennas, equipment). Claims are manufacturer‑stated; independent open technical detail is limited in public docs. citeturn20view1turn19view0 |
| **Cambridge 301/302 “direct digital” pressure vario (with accel research direction)** | Recognizes gust sensitivity and argues for combining high‑rate digital pressure sensing with accelerometers to discriminate gust effects; describes this as a goal rather than a fully solved method in the cited Q&A. citeturn27view0 | Multi‑stage digital signal processing on pressure/airspeed; explores accel‑aided discrimination (fore/aft accel vs airspeed change) as a possible gust‑rejection technique. citeturn27view0 | Time‑constant adjustments and software‑based filtering; upgradeable firmware is emphasized. citeturn27view0 | Highlights the core trade‑off: speed vs turbulence/noise vs sensor limitations; suggests more data is required for full accel utilization. citeturn27view0 |
| **Common sport variometers (example: Flytec 6030)** | “Gust filtering” is primarily implemented as **time‑constant damping** (bumpy vs sluggish) and integrating/averaging varios for rough thermals. citeturn24view0turn24view2 | Adjustable time constants on analog/digital vario outputs; extra “dampening” on audio to reduce rapid pitch swings. citeturn24view0turn24view1 | Vario time constants (sub‑second to multiple seconds); integrating windows; audio damping parameters. citeturn24view0turn24view1 | Reduces noise but does not fundamentally fix TE‑style systematic gust errors; best understood as smoothing for usability. citeturn24view0turn19view1 |

### Bottom line from the comparative evidence

Across sources, there is a consistent technical story:

- **TEK**: you can smooth it, but the horizontal‑gust false‑signal issue is structural and becomes more problematic with higher TAS and certain atmospheric conditions. citeturn20view0turn17view4turn8view0  
- **HAWK**: it attempts to eliminate that structural problem by **estimating what the air is doing** (3D wind) rather than inferring lift from a 1D TE signal, with **SIGWIND** acting as the core “how gusty vs smooth should the estimate be” tuning parameter inside the EKF wind model. citeturn7view0turn8view0turn31view0  

For users, this implies a practical interpretation of “gust filtering” on HAWK: it is less about “turning down noise” and more about **changing which disturbances are allowed to project into dz** (vertical airmass movement) versus being absorbed into dx/dy (horizontal wind) and other modeled states. citeturn8view0turn30view0turn7view0