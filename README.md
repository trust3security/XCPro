# XC Pro

XC Pro is an **Android-based soaring instrument** focused on real-time Total Energy (TE) variometer performance,
sensor fusion, and deterministic replay/simulation using phone sensors and external GNSS.

It is designed for **experimental, training, and development use** — not as a certified flight instrument.

---

## Key Features

- Total Energy (TE) variometer
- Sensor fusion (barometer, IMU, GNSS)
- Deterministic simulator / IGC replay mode
- Compose-first UI
- Offline-first operation
- Designed for low-latency audio and display feedback

---

## Documentation

See `CONTRIBUTING.md` for required reading order and contributor workflow.

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
- Android: (set your real minimum here; e.g. 10 / API 29)
- Required sensors for full functionality:
  - **Barometer** (pressure sensor)
  - **IMU** (accelerometer + gyroscope)

### Optional / supported
- GNSS:
  - Phone internal GNSS works
  - External GNSS receivers may be supported (document the models you support here)

### Degraded behaviour if missing
- No barometer: altitude/vario quality degrades significantly (fallbacks become noisier)
- No gyroscope/accelerometer: TE compensation and smoothing degrade
- No external GNSS: long-term vertical stability and track accuracy may be worse (depends on device)

---

## Configuration / Profiles

- Profiles define tuning and behaviour (e.g., smoothing, audio mapping, display preferences).
- Config location: (document where you store these; e.g., app storage path or assets)
- If you use `local.properties` keys (maps/API/etc), document them here:
  - `YOUR_KEY_NAME=...`

### Files (airspace / IGC)
- Airspace: (how to load and where files go)
- IGC: (how to import / select files for replay)

---

## Project Structure

High-level module map (edit to match your repo):

- `app/` — Android entrypoints, navigation shell, DI wiring
- `data/` — sensor sources, repositories, persistence
- `domain/` — use-cases, pure models, TE/filter math
- `ui/` — Compose screens, ViewModels, UI state models
- (add your actual modules here: `core/`, `feature/`, `dfcards-library/`, etc.)

Where to start for new work:
- New UI: `ui/` + ViewModel changes (no direct `data/` access)
- New sensor source: `data/` (repository remains SSOT owner)
- New math/filter: `domain/` use-case (pure + unit tested)

---

## Simulator / Replay

- Purpose: deterministic testing and development without real flight
- How to run:
  1. (steps to select replay/simulator mode)
  2. (how to point it at an IGC file or deterministic seed)
- Outputs / logs:
  - (where logs go)
  - (where exported data goes)

---

## Known Issues / Limitations

- Not a certified flight instrument; do not rely on it as the sole source of flight-critical information.
- Emulator sensor behaviour is not representative; use a physical device for validation.
- (Add 1–3 current limitations here: performance, device-specific sensor quirks, missing features, etc.)

---

## Safety Notice

This software is provided for **experimental and informational use only**.
It must **not** be relied upon as the sole source of flight-critical information.
The pilot remains solely responsible for safe operation of the aircraft.

---

## License

Add a `LICENSE` file at repo root and state the license here.
Example:
- MIT / Apache-2.0 / Proprietary (pick one and be explicit)

---

## Support / Contact

- Bugs / feature requests: open a GitHub Issue (preferred)
- Security/privacy reports: (email or private channel if you want one)

---

## Screenshots / Demo

Add at least one screenshot or a short GIF/video link here.

---

## Changelog / Releases

If you tag releases, link release notes here (or maintain a `CHANGELOG.md`).
