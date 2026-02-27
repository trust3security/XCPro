# XCPro Crash Forensics Handoff (Phone Evidence)

Date: 2026-02-26  
Repository: `C:\Users\Asus\AndroidStudioProjects\XCPro`  
Device: `SM-S908E` (`adb serial: R5CT2084XHN`)  
App process analyzed: `com.example.openxcpro.debug` (and historical `com.example.xcpro.debug`)

## 1) Executive Summary

This is a **bug**, not normal behavior.

- On **2026-02-26**, phone crash logs show **49 fatal app crashes** for `com.example.openxcpro.debug`.
- **49/49** are `java.lang.OutOfMemoryError`.
- The dominant recurring stack points to:
  - `com.example.dfcards.dfcards.calculations.OpenMeteoElevationApi.fetchElevation(...)`
  - file: `dfcards-library/src/main/java/com/example/dfcards/dfcards/calculations/OpenMeteoElevationApi.kt:121`
- A large subset of crashes are memory-exhaustion side effects in Compose/Flow/Hilt code paths (secondary failure locations under global heap pressure).

## 2) Evidence Sources Collected

Commands used:

```bash
adb shell dumpsys dropbox --print data_app_crash > tmp_dropbox_data_app_crash.txt
adb logcat -b crash -d
adb shell ls /data/tombstones
adb shell head -n 140 /data/tombstones/tombstone_09
```

Artifacts:

- `tmp_dropbox_data_app_crash.txt` (full app crash reports from DropBox manager)
- `/data/tombstones/tombstone_00..31` (native crash records)

## 3) Crash Volume and Pattern

From `tmp_dropbox_data_app_crash.txt`:

- 2026-02-25: `total=9`, `OOM=9`
- 2026-02-26: `total=49`, `OOM=49`
- Last recorded crash on 2026-02-26: `16:37:36` (local device time in record)

Representative final crash record (2026-02-26 16:37:36):

- `OutOfMemoryError`
- TLS/socket stack (`Conscrypt` / `HttpURLConnection`)
- app frame at `OpenMeteoElevationApi.kt:121`

## 4) Dominant Signature (Primary Root-Cause Path)

Dominant recurring app frame among 2026-02-26 OOMs:

- `OpenMeteoElevationApi$fetchElevation$2.invokeSuspend(OpenMeteoElevationApi.kt:121)` -> **26 occurrences**

Typical stack shape:

- `ConscryptEngine.*` / `Linux.getsockname` / `HttpURLConnectionImpl.getResponseCode`
- `OpenMeteoElevationApi.fetchElevation(...)`
- coroutine worker dispatch (`kotlinx.coroutines`)

Interpretation:

- The heap is already critically low, and repeated elevation/network fetch activity is the most common failing edge where allocation finally fails.

## 5) Secondary OOM Manifestations (Likely Symptom, Not Primary Trigger)

Other OOM top frames observed on 2026-02-26:

- Compose snapshot/render (`androidx.compose.runtime.snapshots.*`, draw pipeline)
- Flow/coroutine internals (`StateFlow`, `Combine`, channels)
- Hilt ViewModel key map creation (`Dagger...getViewModelKeys`)
- Wind pipeline frames (`WindSensorInputAdapter`)

Interpretation:

- Once heap is near limit, any allocation point can crash. These are consistent with cascading failures after memory saturation.

## 6) Historical Native Crash Families (Tombstones)

Historical tombstones for XCPro processes show additional native crash families:

- `AUDIO_NATIVE` (6 records)
  - `AudioTrack::releaseBuffer` / `android_media_AudioTrack_writeArray`
  - app frames include `VarioToneGenerator` / `VarioBeepController`
- `MAPLIBRE_JNI_CALLBACK` (4 records)
  - JNI abort: `NativeMapView.onSpriteLoaded/onSpriteError` called on null object
- `MAPLIBRE_NATIVE_MUTEX` (2 records)
  - `pthread_mutex_lock called on a destroyed mutex`
  - stack includes `libmaplibre.so`
- `OOM_JNI_ABORT` (1 historical record)
  - JNI abort with pending `OutOfMemoryError`

Note: these are not all from 2026-02-26, but they indicate additional stability risks beyond the current OOM wave.

## 7) Code Areas Implicated by Current OOM Wave

Primary:

- `dfcards-library/src/main/java/com/example/dfcards/dfcards/calculations/OpenMeteoElevationApi.kt` (line 121 in crash stacks)
- `dfcards-library/src/main/java/com/example/dfcards/dfcards/calculations/SimpleAglCalculator.kt`
- `feature/map/src/main/java/com/example/xcpro/sensors/FlightCalculationHelpers.kt`

Secondary pressure paths observed in stacks:

- `feature/map/src/main/java/com/example/xcpro/weather/wind/data/*`
- Compose runtime/UI rendering (`feature/map/src/main/java/com/example/xcpro/map/ui/*`)

Historical native risk areas:

- `feature/map/src/main/java/com/example/xcpro/audio/VarioToneGenerator.kt`
- `feature/map/src/main/java/com/example/xcpro/audio/VarioBeepController.kt`
- map lifecycle/runtime (`feature/map/src/main/java/com/example/xcpro/map/*` + MapLibre integration)

## 8) Hardening Already Present in Current Working Tree

The following mitigations already exist in this workspace branch:

- AGL update coalescing worker (prevents pile-up):
  - `feature/map/src/main/java/com/example/xcpro/sensors/FlightCalculationHelpers.kt`
- Removal of duplicate AGL fetch path; flight-state now consumes SSOT AGL from `FlightDataRepository`:
  - `feature/map/src/main/java/com/example/xcpro/sensors/FlightStateRepository.kt`
- Bounded LRU elevation cache:
  - `dfcards-library/src/main/java/com/example/dfcards/dfcards/calculations/ElevationCache.kt`
- Failed-fetch retry backoff in AGL calculator:
  - `dfcards-library/src/main/java/com/example/dfcards/dfcards/calculations/SimpleAglCalculator.kt`
- HTTP connection cleanup/log allocation reduction:
  - `dfcards-library/src/main/java/com/example/dfcards/dfcards/calculations/OpenMeteoElevationApi.kt`

Important: these changes still require post-install soak validation against real device crash rate.

## 9) Fix Plan for Codex (Implementation Checklist)

1. Add runtime counters and periodic debug snapshot for AGL fetch path:
   - in-flight fetch count, fetch success/fail, cache hit rate, throttle/backoff counters.
2. Enforce strict per-time budget on remote elevation fetches:
   - no repeated fetch attempts at same grid cell within cooldown window, even across transient failures.
3. Ensure single owner for AGL fetch scheduling:
   - verify no secondary callers bypass `FlightCalculationHelpers` coalescing path.
4. Add memory guardrails:
   - stop optional expensive work when heap free ratio is critically low; degrade gracefully.
5. Add regression tests:
   - high-frequency GPS/baro simulation -> verify bounded number of elevation calls.
   - verify no duplicate AGL fetch streams.
6. Validate audio native path stability:
   - serialize `AudioTrack` write/release lifecycle and add race tests for rapid start/stop.
7. Validate map lifecycle race safety:
   - avoid callbacks mutating/dereferencing destroyed MapLibre objects.

## 10) Release Acceptance Gates

Before release candidate:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
./gradlew connectedDebugAndroidTest --no-parallel
```

Device validation (required):

1. Install debug build with fixes.
2. Clear crash buffers, run 60+ minute realistic flight simulation/use.
3. Re-check:
   - `adb logcat -b crash -d`
   - `adb shell dumpsys dropbox | findstr data_app_crash`
   - `/data/tombstones` delta
4. Pass criteria:
   - no repeated OOM wave,
   - no new audio/map native fatal signatures,
   - stable memory trend in `dumpsys meminfo`.

## 11) Notes for Handoff

- This document is intended for direct Codex execution context.
- Raw forensic file to inspect first: `tmp_dropbox_data_app_crash.txt`.
- If crash signatures change after new build install, regenerate this report with fresh timestamps and counts before final sign-off.

## 12) Re-pass Findings (Missed in Initial Sweep)

Additional code-pass findings after re-auditing the hot path and replay/wind/metadata edges:

1. Time-based backoff is still missing on AGL fetch failures
- Evidence:
  - `SimpleAglCalculator` currently uses call-count retry skips (`FAILED_FETCH_RETRY_SKIP_CALLS = 30`), not elapsed-time backoff.
  - Files:
    - `dfcards-library/src/main/java/com/example/dfcards/dfcards/calculations/SimpleAglCalculator.kt:20`
    - `dfcards-library/src/main/java/com/example/dfcards/dfcards/calculations/SimpleAglCalculator.kt:44`
    - `dfcards-library/src/main/java/com/example/dfcards/dfcards/calculations/SimpleAglCalculator.kt:55`
- Risk:
  - Under high cadence, retries can still occur every few seconds and continue stressing TLS/HTTP allocation under low-memory conditions.

2. Coordinate validation allows NaN through to network request path
- Evidence:
  - `OpenMeteoElevationApi.fetchElevation(...)` rejects out-of-range values but does not reject `NaN`.
  - File:
    - `dfcards-library/src/main/java/com/example/dfcards/dfcards/calculations/OpenMeteoElevationApi.kt:106`
- Risk:
  - Invalid sensor values can still trigger network calls (`latitude=NaN&longitude=NaN`) and repeated error handling/allocation churn.

3. Replay path still triggers AGL network updates (determinism and load risk)
- Evidence:
  - AGL update is called unconditionally inside metrics use case:
    - `feature/map/src/main/java/com/example/xcpro/sensors/domain/CalculateFlightMetricsUseCase.kt:181`
  - `FlightMetricsRequest` currently has no replay/live flag:
    - `feature/map/src/main/java/com/example/xcpro/sensors/domain/CalculateFlightMetricsUseCase.kt:461`
  - Request creation in emitter has no replay discriminator:
    - `feature/map/src/main/java/com/example/xcpro/sensors/FlightDataEmitter.kt:74`
- Risk:
  - Replay sessions can continue to invoke live terrain fetch behavior, increasing request volume and violating deterministic replay expectations.

4. ADS-B metadata on-demand retry map has no eviction policy
- Evidence:
  - Long-lived retry state is stored in:
    - `feature/map/src/main/java/com/example/xcpro/adsb/metadata/data/AircraftMetadataRepositoryImpl.kt:125`
  - Entries are removed on success, but no periodic TTL prune/cap is present.
- Risk:
  - In high-traffic sessions with many one-off ICAO misses, map growth can become unbounded and add background memory pressure.

5. Audio stop/release race risk remains (historical native crash family alignment)
- Evidence:
  - Beep loop writes audio continuously:
    - `feature/map/src/main/java/com/example/xcpro/audio/VarioBeepController.kt:97`
  - Stop cancels job but does not join completion before audio stop:
    - `feature/map/src/main/java/com/example/xcpro/audio/VarioBeepController.kt:69`
  - Tone generator operations (play/write/stop/release) are not synchronized:
    - `feature/map/src/main/java/com/example/xcpro/audio/VarioToneGenerator.kt:196`
    - `feature/map/src/main/java/com/example/xcpro/audio/VarioToneGenerator.kt:343`
  - Engine release triggers stop/release sequence while loops may still be unwinding:
    - `feature/map/src/main/java/com/example/xcpro/audio/VarioAudioEngine.kt:354`
- Risk:
  - Concurrent `AudioTrack` write vs stop/release can trigger native instability (matches historical `AUDIO_NATIVE` tombstone family).

6. Map scale-bar listener lifecycle is not detached
- Evidence:
  - Attach gate is one-way and not reset:
    - `feature/map/src/main/java/com/example/xcpro/map/MapScaleBarController.kt:25`
    - `feature/map/src/main/java/com/example/xcpro/map/MapScaleBarController.kt:33`
  - Listener is added once via anonymous callback:
    - `feature/map/src/main/java/com/example/xcpro/map/MapScaleBarController.kt:35`
  - No corresponding `removeOnLayoutChangeListener(...)` path exists in map lifecycle cleanup.
- Risk:
  - Stale callbacks across map/runtime churn can target outdated map/mapView state, and new map instances may miss listener re-attach after lifecycle churn.

7. Airspace GeoJSON cache can grow without bound
- Evidence:
  - Repository-level static caches are mutable maps with no cap/TTL:
    - `feature/map/src/main/java/com/example/xcpro/utils/AirspaceRepository.kt:41`
    - `feature/map/src/main/java/com/example/xcpro/utils/AirspaceRepository.kt:42`
  - GeoJSON cache key includes class-selection hash, and each miss stores full GeoJSON payload:
    - `feature/map/src/main/java/com/example/xcpro/utils/AirspaceRepository.kt:174`
    - `feature/map/src/main/java/com/example/xcpro/utils/AirspaceRepository.kt:184`
  - Existing prune only removes old `lastModified` entries for the same file; old selection variants remain:
    - `feature/map/src/main/java/com/example/xcpro/utils/AirspaceRepository.kt:181`
- Risk:
  - Repeated airspace class/filter changes can accumulate large GeoJSON strings in-process and increase long-session heap pressure.

8. Crash count normalization update
- Evidence:
  - `tmp_dropbox_data_app_crash.txt` contains:
    - 2026-02-25: 9 OOM crashes
    - 2026-02-26: 49 OOM crashes
    - Total in artifact window: 58 OOM crashes
  - Dominant top app frame remains `OpenMeteoElevationApi.fetchElevation(...)`.

### Plan Delta From Re-pass

Add these must-fix items before release qualification:

1. Replace call-count backoff with monotonic time-based exponential backoff + circuit breaker in AGL fetch path.
2. Enforce strict coordinate finite/range validation (reject `NaN`/`Infinity`) before URL construction.
3. Add explicit replay mode signal to metrics request and hard-disable online terrain fetch logic in replay.
4. Add TTL/cap pruning for `onDemandAttemptByIcao24` in ADS-B metadata repository.
5. Serialize audio lifecycle transitions (`write` vs `stop/release`) and gate stop until beep loop is fully quiesced.
6. Add explicit map listener attach/detach lifecycle for scale-bar callback.
7. Add bounded/evicting policy for `AirspaceRepository` GeoJSON cache (LRU + stale-key prune on file delete/change).
8. Add tests for:
   - invalid coordinate rejection,
   - replay no-network AGL behavior,
   - time-based retry intervals/circuit-breaker transitions,
   - metadata retry-map pruning,
   - airspace GeoJSON cache cap/eviction behavior under class-selection churn,
   - rapid audio start/stop/release race resilience.
