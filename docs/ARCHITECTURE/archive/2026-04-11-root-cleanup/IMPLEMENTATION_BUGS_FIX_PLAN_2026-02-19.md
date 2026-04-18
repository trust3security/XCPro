# IMPLEMENTATION_BUGS_FIX_PLAN_2026-02-19.md

## 0) Metadata

- Date: 2026-02-19
- Status: In progress
- Owner: XCPro Team
- Trigger: Field failures reported during flight on 2026-02-19
- Scope: crash stability, profile bootstrap correctness, map startup responsiveness

## 1) User-Reported Symptoms

- App crashed multiple times while flying.
- App sometimes asked to create a new profile even though profiles already existed.
- On map startup, FAB buttons and map interactions were unresponsive for about 10 seconds.

## 2) Confirmed Bugs (Deep Pass)

### BUG-01 (Critical): Sensor-start failure can crash app/service

Evidence:
- `feature/map/src/main/java/com/trust3/xcpro/vario/VarioServiceManager.kt:162`
- `feature/map/src/main/java/com/trust3/xcpro/vario/VarioServiceManager.kt:168`
- `app/src/main/java/com/trust3/xcpro/service/VarioForegroundService.kt:71`
- `feature/map/src/main/java/com/trust3/xcpro/map/LocationSensorsController.kt:133`

Problem:
- `startSensorsOnMainThread()` catches `Throwable` and rethrows `RuntimeException`.
- Callers launch coroutines without local error containment.
- Cancellation/transient platform start failures can escalate into process-level crash behavior.

Impact:
- Explains in-flight instability and repeated crash loops when sensor startup is retried.

### BUG-02 (Critical): Profile bootstrap race causes false "no profile" state

Evidence:
- `app/src/main/java/com/trust3/xcpro/MainActivityScreen.kt:79`
- `app/src/main/java/com/trust3/xcpro/MainActivityScreen.kt:83`
- `feature/profile/src/main/java/com/trust3/xcpro/profiles/ProfileViewModel.kt:25`
- `feature/profile/src/main/java/com/trust3/xcpro/profiles/ProfileRepository.kt:31`
- `feature/profile/src/main/java/com/trust3/xcpro/profiles/ProfileRepository.kt:40`

Problem:
- UI gate toggles to profile-creation flow as soon as state is empty/null.
- Repository initializes with empty/null state and hydrates via two independent collectors.
- During startup, transient empty/null window is interpreted as "no profile exists."

Impact:
- Matches repeated prompts to create a new profile even when data exists.

### BUG-03 (High): Profile storage/parsing failure path degrades to empty list

Evidence:
- `feature/profile/src/main/java/com/trust3/xcpro/profiles/ProfileRepository.kt:48`
- `feature/profile/src/main/java/com/trust3/xcpro/profiles/ProfileRepository.kt:55`
- `feature/profile/src/main/java/com/trust3/xcpro/profiles/ProfileStorage.kt:32`
- `feature/profile/src/main/java/com/trust3/xcpro/profiles/ProfileStorage.kt:36`

Problem:
- Parse failures default to `emptyList()` with no resilient recovery strategy.
- DataStore flows are read without explicit error fallback (`catch`) at boundary.

Impact:
- Corruption/read errors can collapse profile state to empty, amplifying BUG-02 behavior.

### BUG-04 (High): Startup gating stacks to near-10s non-responsive window

Evidence:
- `app/src/main/java/com/trust3/xcpro/MainActivity.kt:37`
- `app/src/main/java/com/trust3/xcpro/MainActivity.kt:76`
- `feature/map/src/main/java/com/trust3/xcpro/map/MapInitializer.kt:36`
- `feature/map/src/main/java/com/trust3/xcpro/map/MapInitializer.kt:73`
- `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenSections.kt:211`

Problem:
- Fixed splash hold (`2_000ms`) plus style-load timeout (`8_000ms`) can serialize startup readiness.
- `onMapReady` callback is invoked only after `initializeMap` returns.

Impact:
- Consistent with user-observed ~10 second startup "struggle" and delayed interaction readiness.

### BUG-05 (Medium-High): Map lifecycle catch-up does not fully sync MapView lifecycle

Evidence:
- `feature/map/src/main/java/com/trust3/xcpro/map/MapLifecycleManager.kt:71`
- `feature/map/src/main/java/com/trust3/xcpro/map/MapLifecycleManager.kt:73`
- `feature/map/src/main/java/com/trust3/xcpro/map/MapLifecycleManager.kt:75`
- `feature/map/src/main/java/com/trust3/xcpro/map/MapLifecycleManager.kt:194`

Problem:
- `syncCurrentOwnerState()` only starts orientation/sensor checks.
- If lifecycle observer attaches after ON_CREATE, MapView lifecycle replay (`onCreate/onStart/onResume`) is not explicitly caught up.

Impact:
- Can produce delayed/unstable map startup behavior depending on timing.

### BUG-06 (Critical): Profile persistence write-order race can wipe existing profile data

Evidence:
- `feature/profile/src/main/java/com/trust3/xcpro/profiles/ProfileRepository.kt`

Problem:
- Profile mutations persisted `active_profile_id` and `profiles_json` in separate writes.
- Storage collectors can observe interim states and feed back into repository hydration.
- Under timing pressure this can collapse persisted profile state and surface false "create profile" prompts on next start.

Impact:
- Matches intermittent "create new profile" prompts despite previously existing profiles.

### BUG-07 (High): Map init coroutine swallowed cancellation and could run stale callbacks

Evidence:
- `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenSections.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/MapInitializer.kt`

Problem:
- Map init launch path used `runCatching` without cancellation rethrow.
- `getMapAsync` and post-init callbacks could still run after composition teardown.
- Late style callbacks could continue setup work without verifying that map instance was still active.

Impact:
- Increases crash/regression risk during lifecycle churn (background/resume/navigation teardown).

### BUG-08 (Medium): Startup map-style default mismatch causes style churn

Evidence:
- `app/src/main/java/com/trust3/xcpro/MainActivityScreen.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/MapStyleRepository.kt`

Problem:
- Different default style values across startup paths can emit extra early style commands.
- This introduces avoidable startup work and inconsistent first-load style behavior.

Impact:
- Can amplify startup instability/perceived lag on cold start.

## 3) Implementation Plan

## Phase 1: Crash Containment (BUG-01)

Changes:
- Refactor `VarioServiceManager.startSensorsOnMainThread()` to never throw unchecked runtime exceptions for expected startup failures.
- Handle `CancellationException` explicitly and rethrow only cancellation.
- Return typed outcome (`Result`/sealed status) to callers.
- Add local failure handling in:
  - `app/src/main/java/com/trust3/xcpro/service/VarioForegroundService.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/map/LocationSensorsController.kt`
- Ensure retries stay bounded and log actionable failure reason.

Acceptance:
- Sensor startup transient failures do not crash process/service.
- Retry path remains functional.

## Phase 2: Profile Bootstrap Correctness (BUG-02, BUG-03)

Changes:
- Add explicit bootstrap state in profile UI model (`loading`, `ready`, `error`).
- Gate create/select profile UI until bootstrap is `ready`.
- Replace dual independent repository collectors with deterministic combined hydration (`combine` profiles JSON + active profile ID).
- Add storage boundary error handling (`catch`) and corruption-safe fallback.
- Preserve last-known-good profile snapshot when parse fails; surface non-fatal error state instead of forcing empty.

Acceptance:
- App does not show create-profile prompt during initial hydration if profile exists.
- Storage read/parse failures do not silently force permanent empty-profile state.

## Phase 3: Startup Responsiveness and Map Lifecycle (BUG-04, BUG-05)

Changes:
- Remove fixed splash delay as a hard blocker; switch to readiness-gated splash with strict max cap.
- Decouple map interaction readiness from long style wait where possible:
  - avoid serially blocking `onMapReady` behind full style timeout.
- Reduce style timeout budget and ensure timeout path remains responsive.
- Extend lifecycle catch-up to replay missing `MapView` lifecycle methods based on current owner state.
- Validate startup overlay/data load sequencing to avoid duplicate heavy work during first render.

Acceptance:
- Map/FAB interactions become available promptly on cold start.
- No missing-map-lifecycle side effects during first launch path.

## Phase 4: Regression Net and Verification

Unit tests:
- Sensor startup failure/cancellation handling (no crash propagation).
- Profile hydration race scenarios (profiles present + delayed active ID).
- Profile parse/storage error fallback behavior.
- Lifecycle catch-up behavior for late observer attachment.

Integration/instrumentation:
- Cold-start interaction readiness test (map taps/FAB clicks within target budget).
- Startup under delayed style load test.

Required verification commands:
- `./gradlew enforceRules`
- `./gradlew testDebugUnitTest`
- `./gradlew assembleDebug`
- `./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"` (device/emulator when available)

## 4) Risk and Rollout

- Rollout order:
  1. Phase 1 and Phase 2 first (stability + profile correctness).
  2. Phase 3 startup changes behind measurable readiness checks.
- Add temporary debug telemetry for:
  - sensor start result/failure category
  - profile bootstrap state transition timeline
  - startup readiness timings (splash off, map ready, first FAB click)
- Remove or downgrade verbose telemetry after confidence window.

## 5) Done Criteria

- No crash reproduction from sensor startup retry path in flight scenario.
- No false create-profile prompt when valid profile data exists.
- Startup interaction delay materially reduced from current near-10s path.
- All required checks pass and no new architecture deviations introduced.

## 6) Implementation Progress (2026-02-19, pass 2)

Completed in this pass:
- BUG-06 fix: profile persistence now supports atomic state writes (`profiles_json` + `active_profile_id`) to remove write-order race windows.
- BUG-07 fix: MapView host init now rethrows cancellation and ignores stale async callbacks after scope disposal; style setup now verifies active map instance before applying overlays.
- BUG-08 fix: default map style source aligned to `Topo` across map-style repository and first-time setup seed path.

Pending verification:
- `./gradlew enforceRules`
- `./gradlew testDebugUnitTest`
- `./gradlew assembleDebug`
- Device path when available:
  - `./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"`

## 7) Battery-Drain Bug Addendum (2026-02-19, pass 3)

### BUG-09 (Critical): App forces screen-on for entire activity lifetime

Evidence:
- `app/src/main/java/com/trust3/xcpro/MainActivity.kt:74`

Problem:
- `FLAG_KEEP_SCREEN_ON` is set unconditionally during app startup.
- The flag is not tied to explicit flight mode, charging state, replay mode, or user preference.

Impact:
- Prevents normal display sleep and can dominate battery burn even when pilot is not actively flying.

Implementation fix:
- Gate keep-screen-on behind explicit runtime state (for example: active flight/replay session) and a user setting.
- Clear the flag when that state is false.

Acceptance:
- Screen can sleep normally when user is idle/non-flying.
- Screen stays awake only during intentional flight/replay sessions.

### BUG-10 (Critical): Foreground vario service auto-starts on app launch and is sticky

Evidence:
- `app/src/main/java/com/trust3/xcpro/MainActivity.kt:91`
- `app/src/main/java/com/trust3/xcpro/MainActivity.kt:221`
- `app/src/main/java/com/trust3/xcpro/service/VarioForegroundService.kt:72`
- `app/src/main/java/com/trust3/xcpro/service/VarioForegroundService.kt:92`

Problem:
- Service starts immediately after permission grant on app launch.
- Service returns `START_STICKY`, so OS can restart it after process death.
- Service starts sensor/flight pipeline in `onCreate`.

Impact:
- Sensor/audio pipeline can continue running outside intentional flight usage windows.
- Increases background CPU/sensor/GPS power draw.

Implementation fix:
- Introduce explicit "flight session active" command path to start/stop service.
- Use non-sticky behavior unless there is an active session requiring continuity.
- Add lifecycle telemetry for service start reason and stop reason.

Acceptance:
- Service does not auto-run indefinitely after a casual app open.
- Service lifecycle matches explicit flight/replay intent.

### BUG-11 (High): High-rate sensor stack stays active once service is running

Evidence:
- `feature/map/src/main/java/com/trust3/xcpro/vario/VarioServiceManager.kt:66`
- `feature/map/src/main/java/com/trust3/xcpro/vario/VarioServiceManager.kt:71`
- `feature/map/src/main/java/com/trust3/xcpro/sensors/SensorRegistry.kt:47`
- `feature/map/src/main/java/com/trust3/xcpro/sensors/SensorRegistry.kt:49`
- `feature/map/src/main/java/com/trust3/xcpro/vario/VarioServiceManager.kt:45`
- `feature/map/src/main/java/com/trust3/xcpro/vario/VarioServiceManager.kt:139`

Problem:
- Barometer/rotation/accel sensors use game-rate delays.
- GPS cadence policy can drop to 200ms fast interval.
- No "low-power idle profile" when not in active flight mode.

Impact:
- Sustained sensor wakeups and GPS callbacks materially increase battery consumption.

Implementation fix:
- Add explicit power profiles (`IDLE`, `TRACKING`, `FLIGHT`) and switch cadence/sensor set by profile.
- In `IDLE`, use slower GPS interval and disable non-critical high-rate sensors.
- Add hysteresis to avoid profile flapping.

Acceptance:
- Idle mode shows substantially lower sensor callback rate versus flight mode.
- Flight mode preserves current responsiveness targets.

### BUG-12 (High): Frame-driven display loop runs continuously while map is visible

Evidence:
- `feature/map/src/main/java/com/trust3/xcpro/map/ui/effects/MapComposeEffects.kt:146`
- `feature/map/src/main/java/com/trust3/xcpro/map/ui/effects/MapComposeEffects.kt:147`
- `feature/map/src/main/java/com/trust3/xcpro/map/ui/effects/MapComposeEffects.kt:149`
- `feature/map/src/main/java/com/trust3/xcpro/map/ui/effects/MapComposeEffects.kt:150`

Problem:
- A `withFrameNanos` loop runs continuously for display updates.
- Loop is not throttled by motion/state changes.

Impact:
- Keeps CPU/GPU update path hot at frame cadence even during static map periods.

Implementation fix:
- Add movement-driven throttling (for example: 1-5Hz when stationary, frame sync only while moving/replay).
- Skip per-frame work when neither location nor orientation changed beyond thresholds.

Acceptance:
- No continuous frame-loop CPU usage while map is static.
- Smoothness remains acceptable during motion/replay.

### BUG-13 (Medium-High): ADS-B center wait loop polls at 100ms when center unavailable

Evidence:
- `feature/map/src/main/java/com/trust3/xcpro/adsb/AdsbTrafficRepository.kt:522`
- `feature/map/src/main/java/com/trust3/xcpro/adsb/AdsbTrafficRepository.kt:525`
- `feature/map/src/main/java/com/trust3/xcpro/adsb/AdsbTrafficRepository.kt:604`

Problem:
- ADS-B loop waits for center via fixed 100ms polling.
- During startup/no-fix windows this causes frequent wakeups with no useful work.

Impact:
- Avoidable CPU wakeups and battery waste while waiting for first valid center.

Implementation fix:
- Replace fixed 100ms poll with event-driven await (flow/channel) or adaptive backoff.
- Seed center deterministically from camera/last-known location before enabling loop.

Acceptance:
- Waiting for first center does not generate high-frequency wakeups.
- ADS-B still starts promptly once center becomes available.

### BUG-14 (Medium): HAWK UI ticker wakes CPU even when HAWK card is hidden/disabled

Evidence:
- `feature/map/src/main/java/com/trust3/xcpro/hawk/HawkVarioUseCase.kt:23`
- `feature/map/src/main/java/com/trust3/xcpro/hawk/HawkVarioUseCase.kt:30`
- `feature/map/src/main/java/com/trust3/xcpro/hawk/HawkVarioUseCase.kt:32`
- `feature/map/src/main/java/com/trust3/xcpro/hawk/HawkConfig.kt:34`
- `feature/map/src/main/java/com/trust3/xcpro/map/MapScreenViewModel.kt:94`
- `feature/map/src/main/java/com/trust3/xcpro/map/MapScreenViewModel.kt:95`

Problem:
- `HawkVarioUseCase` ticker emits every 250ms regardless of `config.enabled`.
- `MapScreenViewModel` eagerly collects this flow for its full lifetime.

Impact:
- Unnecessary periodic wakeups even when HAWK UI is not in use.

Implementation fix:
- Gate ticker by `config.enabled` (emit once when disabled, no periodic loop).
- Use `SharingStarted.WhileSubscribed` for UI-facing HAWK state where possible.

Acceptance:
- When HAWK is disabled/hidden, no periodic 250ms ticker wakeups occur.
- HAWK state still updates correctly when enabled.

### BUG-15 (Medium): Replay trail updater keeps per-frame loop active while replay is selected

Evidence:
- `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenRuntimeEffects.kt:91`
- `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenRuntimeEffects.kt:93`
- `feature/map/src/main/java/com/trust3/xcpro/map/MapScreenViewModelStateBuilders.kt:91`
- `feature/map/src/main/java/com/trust3/xcpro/map/MapScreenViewModelStateBuilders.kt:92`

Problem:
- Replay suppress flag is true whenever replay selection is non-null.
- Snail-trail update loop runs every frame in that state when render-frame-sync is disabled.
- This includes replay-selected idle states, not only active playback.

Impact:
- Continuous per-frame work can persist after replay is paused/idle, increasing CPU drain.

Implementation fix:
- Gate replay frame loop on active playback status instead of selection-only state.
- Fall back to low-frequency updates when replay is selected but not playing.

Acceptance:
- No per-frame replay trail loop while replay is idle/paused.
- Replay trail remains smooth during active playback.

## 8) Battery Verification Plan

Instrumentation and profiling:
- Capture baseline + post-fix Android Studio Energy Profiler sessions:
  - idle on map screen (2 min)
  - active flight simulation (2 min)
  - replay paused and replay playing (2 min each)
- Capture `adb shell dumpsys batterystats` before/after controlled 15-minute runs.

Automated checks (add tests where feasible):
- Unit tests for power-profile switching logic and cadence decisions.
- Unit tests for ADS-B center-wait backoff/event behavior.
- Unit tests for HAWK ticker gating when disabled.
- Integration test to verify keep-screen-on toggles only for active flight/replay state.

Success criteria:
- Idle map path no longer exhibits continuous high-frequency update loops.
- No persistent service/sensor activity after user exits active flight/replay mode.
- Measured idle battery/CPU usage improves versus baseline on same device/build.
