
# XC Pro

XC Pro is an **Android-based soaring instrument** focused on real-time Total Energy (TE) variometer performance,
sensor fusion, and deterministic replay/simulation using phone sensors and external GNSS.

It is designed for **experimental, training, and development use** -- not as a certified flight instrument.

---

## Key Features

- Total Energy (TE) variometer
- Sensor fusion (barometer, IMU, GNSS)
- Deterministic simulator / IGC replay mode
- Compose-first UI
- Offline-first operation
- Designed for low-latency audio and display feedback

---

## Planned / Investigating

### OGN live traffic (Open Glider Network)

- Add a live traffic map layer for nearby gliders/aircraft to improve situational awareness.
- OGN provides community receiver coverage with APRS data distribution and a device database
  that helps identify aircraft; data use must follow OGN rules (ODbL, respect privacy choices,
  no redistribution of data older than 24h).
- Initial display plan: map markers with position, track, ground speed, altitude, and last seen age;
  labels from the device database (CN/registration/type) when allowed; anonymized IDs when privacy
  opts out; stale/expired styling; range filter around map center or ownship.
- Informational only, not for collision avoidance or separation.

---

## Documentation

Start with `../../AGENTS.md` for agent/contributor entry instructions.
See `CONTRIBUTING.md` for contributor workflow details.
Pipeline overview: `PIPELINE.md` (diagram: `PIPELINE.svg`).

---

## Compliance Status (tracked)

Compliance is tracked in `KNOWN_DEVIATIONS.md`.
Current listed deviations: `None` (last verified in that file on 2026-02-04).
Refactor checklist: `../refactor/Agent-Execution-Contract-LevoCompliance.md`.

CI rule enforcement (local):
```
pwsh scripts/ci/enforce_rules.ps1
```

Gradle task (local):
```
gradlew enforceRules
```

Preflight (rules + build + unit tests):
```
preflight.bat
```

---

## Levo Vario Quickstart

If you touch the variometer or replay pipeline, start here:
- Read `../LevoVario/levo.md` for the end-to-end pipeline map.
- Entry points: `VarioForegroundService` -> `VarioServiceManager` -> `FlightDataCalculatorEngine`.
- Replay entry: `IgcReplayController` (replay clock is IGC time, not wall time).
- Time base rules: live deltas use monotonic time, output uses wall time
  (see `CODING_RULES.md` and `ARCHITECTURE.md`).
- Baro-gated loop: vario only advances on new baro samples (no accel-only ticks).

Tiny map:
```
Sensors -> SensorRegistry -> UnifiedSensorManager -> FlightDataCalculatorEngine
        -> FlightDataRepository -> FlightDataUiAdapter (MapScreenObservers) -> UI + Audio

IGC -> IgcReplayController -> ReplaySensorSource -> FlightDataCalculatorEngine
    -> FlightDataRepository -> FlightDataUiAdapter (MapScreenObservers) -> UI + Audio
```

Key files:
- `feature/map/src/main/java/com/example/xcpro/sensors/FlightDataCalculatorEngine.kt`
- `feature/map/src/main/java/com/example/xcpro/sensors/FlightDataCalculatorEngineLoops.kt`
- `feature/map/src/main/java/com/example/xcpro/sensors/domain/CalculateFlightMetricsUseCase.kt`
- `feature/map/src/main/java/com/example/xcpro/flightdata/FlightDataRepository.kt`
- `feature/map/src/main/java/com/example/xcpro/replay/IgcReplayController.kt`
- `feature/map/src/main/java/com/example/xcpro/audio/VarioAudioEngine.kt`
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenObservers.kt` (wrapped by `FlightDataUiAdapter`)

---

## How to Run on Device

### Requirements
- Android Studio (latest stable)
- JDK 17
- Android SDK installed
- A physical Android device (recommended for sensor work) or an emulator (limited sensors)

### Physical device setup (recommended)
1. Plug the device in via USB.
2. Enable **Developer options** and **USB debugging** on the phone.
3. Accept the RSA prompt on the phone when asked.
4. In Android Studio: select the device in the toolbar and press **Run**.

### Build / install from CLI
```bash
# Build debug APK
./gradlew assembleDebug

# Install on a connected device
./gradlew installDebug
```

> Note: Emulators typically do not provide a real barometer or realistic IMU behaviour. Use a phone for serious testing.

---

## Supported Hardware & Sensors

### Minimum
- Android 11 / API 30 (`minSdk = 30`)
- Required sensors for full functionality:
  - **Barometer** (pressure sensor)
  - **IMU** (accelerometer + gyroscope)

### Optional / supported
- GNSS:
  - Phone internal GNSS works
  - External GNSS receivers are supported via configured data sources when available

### Degraded behaviour if missing
- No barometer: altitude/vario quality degrades significantly (fallbacks become noisier)
- No gyroscope/accelerometer: TE compensation and smoothing degrade
- No external GNSS: long-term vertical stability and track accuracy may be worse (depends on device)

---

## Configuration / Profiles

- Profiles define tuning and behaviour (e.g., smoothing, audio mapping, display preferences).
- Profile storage:
  - DataStore name: `profile_preferences`
  - Keys: `profiles_json`, `active_profile_id`
- App runtime/file config:
  - `configuration.json` in app internal storage (`context.filesDir`)
- Build/local key used for map API:
  - `MAPLIBRE_API_KEY` (read via Gradle property into `BuildConfig`)

### Files (airspace / IGC)
- Airspace:
  - UI path: Flight Data -> Airspace -> Add Airspace File
  - Supported format: `.txt` (OpenAir-like)
  - Imported files are copied into app internal storage (`context.filesDir`)
  - Enabled files/classes are stored in `configuration.json`
- Waypoints:
  - UI path: Flight Data -> Waypoints -> Add Waypoint File
  - Supported format: `.cup`
  - Imported files are copied into app internal storage (`context.filesDir`)
  - Enabled file state is stored in `configuration.json`
- IGC replay:
  - UI path: Settings -> IGC Replay -> Choose IGC File
  - Uses Android document picker (SAF/OpenDocument)
  - Replay reads selected document URIs through `contentResolver`

---

## Project Structure

High-level module map:

- `app/` -- Android app shell, navigation graph, service entrypoints, top-level DI
- `core/common/` -- shared utils and logging primitives
- `core/geometry/` -- geometry/math helpers
- `core/time/` -- clock/time abstractions
- `core/ui/` -- shared UI primitives
- `feature/map/` -- map, sensors, fusion, replay, airspace/waypoint integrations
- `feature/profile/` -- profile models, storage, profile UI/workflows
- `feature/variometer/` -- variometer-specific UI/layout concerns
- `dfcards-library/` -- flight data cards runtime and view-model tiering

Where to start for new work:
- New map/sensor/replay behavior: `feature/map/` (keep SSOT and timebase rules)
- New profile behavior: `feature/profile/`
- Shared pure logic/time/geometry utilities: `core/*`
- App wiring/navigation/service lifecycle only: `app/`

---

## Simulator / Replay

- Purpose: deterministic testing and development without real flight
- How to run:
  1. Open Settings -> `IGC Replay`
  2. Tap `Choose IGC File` and select a log
  3. Optionally set speed and scrub timeline
  4. Tap `Start Replay` (map view resumes and replay drives pipeline)
- Outputs / logs:
  - Replay/session diagnostics are emitted to Logcat in debug builds
  - No dedicated replay export artifact is produced by this flow

---

## Known Issues / Limitations

- Not a certified flight instrument; do not rely on it as the sole source of flight-critical information.
- Emulator sensor behaviour is not representative; use a physical device for validation.
- Static analysis tasks `detekt` / `ktlintCheck` are not configured in this repo;
  use `enforceRules`, lint, and tests as the active quality gates.
- IGC replay quality depends on source log quality (timestamp/cadence/noise in the log data).

---

## Safety Notice

This software is provided for **experimental and informational use only**.
It must **not** be relied upon as the sole source of flight-critical information.
The pilot remains solely responsible for safe operation of the aircraft.

---

## License

No `LICENSE` file is present at repo root at this time.
Do not assume open-source licensing terms unless a license file is added.

---

## Support / Contact

- Bugs / feature requests: open a GitHub Issue (preferred)
- Security/privacy reports: use a private maintainer channel when available;
  avoid posting sensitive data in public issues.

---

## Screenshots / Demo

Screenshots/demo assets are not currently maintained in this document.

---

## Changelog / Releases

No release-note index is currently maintained in this file.

