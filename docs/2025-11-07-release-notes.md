# Release Notes – 7 Nov 2025

## Background Vario Service
- XC Pro now runs the variometer pipeline even when the UI is backgrounded. A persistent notification indicates that flight sensors and audio feedback are active.
- Benefits:
  - Audio + TE/netto stay continuous when the pilot locks the screen or switches apps.
  - Unified sensor ownership prevents duplicate listeners and missed samples after Doze.
- Impacted modules: `VarioForegroundService`, `VarioServiceManager`, `FlightDataRepository`, `LocationManager`, Map overlays.

## Aircraft-Polar-Aware Netto
- Netto/speed-to-fly now subtracts sink derived from the pilot’s configured glider polar (`PolarCalculator`), including bugs and water ballast.
- Legacy fallback curve only triggers when no aircraft data exists; a log note highlights the fallback so QA can trace it.

## Action Items
- QA: verify background notification + audio behavior on Pixel 8 / Samsung S22 (Android 14) with app minimized for >10 min.
- Docs: update user guide (nav drawer ▸ Polar) to mention that polar edits now immediately affect netto/audio cues.
- Future: add notification actions (“Pause Vario”, “Stop”) to comply with Play foreground-service UX guidance.

