# FIX_PROFILE_BOOTSTRAP_READ_FAILURE_2026-02-20.md

## 0) Metadata

- Date: 2026-02-20
- Owner: XCPro Team
- Scope: profile bootstrap/read-error path (second pass on finding #1)
- Goal: prevent false empty-profile state and destructive persistence during storage read failures

## 1) What Was Missed In Pass 1

### MISS-01 (Critical): read-error fallback is treated as real empty profile state

Evidence:
- `feature/profile/src/main/java/com/trust3/xcpro/profiles/ProfileStorage.kt:43`
- `feature/profile/src/main/java/com/trust3/xcpro/profiles/ProfileStorage.kt:55`
- `feature/profile/src/main/java/com/trust3/xcpro/profiles/ProfileRepository.kt:67`

Problem:
- `IOException` is converted to `emptyPreferences()` at storage boundary.
- Repository reads null JSON as valid empty state (`emptyList()`), not as degraded read.
- This collapses in-memory profiles to empty even when persisted data exists.

Impact:
- Startup can route to profile selection/create flow even though profiles exist.

### MISS-02 (Critical): hydration path can wipe active profile id during degraded read

Evidence:
- `feature/profile/src/main/java/com/trust3/xcpro/profiles/ProfileRepository.kt:51`
- `feature/profile/src/main/java/com/trust3/xcpro/profiles/ProfileRepository.kt:82`
- `feature/profile/src/main/java/com/trust3/xcpro/profiles/ProfileRepository.kt:92`
- `feature/profile/src/main/java/com/trust3/xcpro/profiles/ProfileRepository.kt:104`

Problem:
- On degraded read, `profiles` can become empty and `resolvedId` becomes null.
- `applyActiveProfile` can then persist null active id (`writeActiveProfileId(null)`).
- This turns transient read failure into durable state loss.

Impact:
- Next clean startup can still lose active-profile resolution.

### MISS-03 (High): bootstrap error is collected but not used in startup gating or visible recovery UX

Evidence:
- `feature/profile/src/main/java/com/trust3/xcpro/profiles/ProfileViewModel.kt:50`
- `app/src/main/java/com/trust3/xcpro/MainActivityScreen.kt:103`
- `feature/profile/src/main/java/com/trust3/xcpro/profiles/ui/ProfileSelectionContent.kt:82`

Problem:
- `bootstrapError` exists in UI state but is not used in startup route decisions.
- Selection screen only shows `state.error` (mutation errors), not bootstrap read/hydration errors.

Impact:
- User sees "no profile" UX instead of a recoverable storage-read warning.

### MISS-04 (High): profile settings route can auto-close during normal hydration window

Evidence:
- `feature/profile/src/main/java/com/trust3/xcpro/profiles/ProfileSettingsScreen.kt:29`
- `feature/profile/src/main/java/com/trust3/xcpro/profiles/ProfileSettingsScreen.kt:35`

Problem:
- Route pops immediately when `profile == null`, without checking hydration completion.

Impact:
- Profile settings can fail to load and bounce back before repository hydration completes.

### MISS-05 (Medium): no regression tests for degraded-read + no-destructive-write contract

Evidence:
- `app/src/test/java/com/trust3/xcpro/profiles/ProfileRepositoryTest.kt`

Problem:
- Tests cover parse failure fallback, but not storage read errors after valid state.
- No test asserts "never clear active id on read failure."

Impact:
- Regressions can reintroduce false-empty and destructive write behavior.

### MISS-06 (Critical): non-IOException read failures can permanently stall profile hydration

Evidence:
- `feature/profile/src/main/java/com/trust3/xcpro/profiles/ProfileStorage.kt:47`
- `feature/profile/src/main/java/com/trust3/xcpro/profiles/ProfileStorage.kt:60`
- `feature/profile/src/main/java/com/trust3/xcpro/profiles/ProfileRepository.kt:44`
- `app/src/main/java/com/trust3/xcpro/MainActivityScreen.kt:93`

Problem:
- Storage rethrows non-`IOException` failures from DataStore read flow.
- Repository does not wrap `combine(...).collect` with outer restart/error handling.
- If stream fails before first successful emission, `bootstrapComplete` can remain false forever.

Impact:
- App can stay stuck on startup loading spinner and profile flow never becomes usable.

### MISS-07 (Critical): dual independent DataStore flows allow mixed snapshots and active-profile corruption

Evidence:
- `feature/profile/src/main/java/com/trust3/xcpro/profiles/ProfileStorage.kt:41`
- `feature/profile/src/main/java/com/trust3/xcpro/profiles/ProfileStorage.kt:53`
- `feature/profile/src/main/java/com/trust3/xcpro/profiles/ProfileRepository.kt:45`
- `feature/profile/src/main/java/com/trust3/xcpro/profiles/ProfileRepository.kt:51`
- `feature/profile/src/main/java/com/trust3/xcpro/profiles/ProfileRepository.kt:85`

Problem:
- `profilesJsonFlow` and `activeProfileIdFlow` are separate collectors over the same DataStore source.
- On partial/degraded emissions, repository can combine valid profiles JSON with null/empty active id.
- Hydration then falls back to `profiles.firstOrNull()` and may persist that fallback id.

Impact:
- Active profile can silently switch or be cleared, and users can see wrong/missing profile after restart.

### MISS-08 (High): all-or-nothing parse has no post-parse validation for required profile fields

Evidence:
- `feature/profile/src/main/java/com/trust3/xcpro/profiles/ProfileRepository.kt:72`
- `feature/profile/src/main/java/com/trust3/xcpro/profiles/ui/ProfileSelectionList.kt:96`
- `feature/profile/src/main/java/com/trust3/xcpro/profiles/ui/ProfileSelectionList.kt:107`

Problem:
- Parsed `UserProfile` entries are accepted without validating required fields.
- Malformed/legacy payloads can deserialize into invalid objects and later fail in UI dereferences.

Impact:
- Profile screens can crash while rendering rows/details, stopping profile loading UX.

## 2) Fix Design

### Phase A: make storage read failures explicit, not indistinguishable from empty state

Changes:
- Replace dual raw nullable flows with one atomic storage snapshot contract (single DataStore collector):
  - `profilesJson`
  - `activeProfileId`
  - `readStatus` (OK or READ_ERROR)
- Do not map read errors to semantic "empty data."

Target files:
- `feature/profile/src/main/java/com/trust3/xcpro/profiles/ProfileStorage.kt`
- `feature/profile/src/main/java/com/trust3/xcpro/profiles/ProfileRepository.kt`

### Phase B: harden repository hydration to preserve last-known-good state

Changes:
- Track last-known-good parsed profiles and active id in repository memory.
- On read error:
  - keep existing `_profiles` and `_activeProfile` unchanged
  - set non-fatal bootstrap/degraded error state
  - never persist "repair" writes
- Gate `applyActiveProfile` persistence so hydration never writes null active id due to degraded reads.
- Avoid persistence side-effects from hydration when snapshot quality is degraded/unknown.
- Add top-level stream failure containment so non-IO flow failures do not stall hydration forever.

Target files:
- `feature/profile/src/main/java/com/trust3/xcpro/profiles/ProfileRepository.kt`

### Phase C: fix startup and screen gating behavior

Changes:
- `MainActivityScreen` should not infer "no profiles" from degraded-read state.
- Show explicit recovery/error surface when bootstrap is degraded.
- `ProfileSelectionContent` should render bootstrap error (not only mutation error).
- `ProfileSettingsScreen` should:
  - show loading while not hydrated
  - only pop when hydrated and requested profile is confirmed missing.

Target files:
- `app/src/main/java/com/trust3/xcpro/MainActivityScreen.kt`
- `feature/profile/src/main/java/com/trust3/xcpro/profiles/ui/ProfileSelectionContent.kt`
- `feature/profile/src/main/java/com/trust3/xcpro/profiles/ProfileSettingsScreen.kt`

### Phase D: regression tests for missed cases

Add tests:
- Repository keeps last-known-good profiles on storage read `IOException`.
- Repository does not call/persist null active id during degraded-read hydration.
- Repository does not switch active profile when profile JSON is valid but active-id read is degraded.
- Non-IO storage read failure cannot leave `bootstrapComplete` permanently false.
- Hydration from malformed payload drops/quarantines invalid entries instead of crashing UI path.
- Startup gate does not show create/select flow solely due degraded read.
- Profile settings route does not auto-close before hydration completes.

Target files:
- `app/src/test/java/com/trust3/xcpro/profiles/ProfileRepositoryTest.kt`
- New UI/viewmodel tests under profile feature/app module as appropriate.

## 3) Acceptance Criteria

1. A transient profile storage read failure does not collapse loaded profiles to empty.
2. Active profile id is never cleared by hydration during degraded-read conditions.
3. Mixed snapshot emissions cannot silently switch active profile.
4. Non-IO read failures cannot leave app in permanent hydration spinner.
5. Startup no longer routes to create/select profile due read degradation alone.
6. Profile settings route is hydration-safe and does not pop prematurely.
7. New regression tests fail if any of the above contracts regress.

## 4) Verification

Required:
- `./gradlew enforceRules`
- `./gradlew testDebugUnitTest`
- `./gradlew assembleDebug`

When device/emulator is available:
- `./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"`

## 5) Implemented In This Fix

Implemented:
- `ProfileStorage` now exposes a single atomic snapshot flow with explicit read status (`OK`, `IO_ERROR`, `UNKNOWN_ERROR`) so degraded reads are not interpreted as valid empty state.
- `ProfileRepository` hydration now consumes atomic snapshots and:
  - preserves in-memory state on storage read degradation,
  - marks bootstrap complete with surfaced error instead of stalling,
  - avoids destructive repair writes during degraded or parse-failed hydration,
  - sanitizes malformed profile lists defensively (including `null` entries and invalid/duplicate records).
- Startup/profile UI gating now distinguishes degraded bootstrap from valid empty-profile first-run:
  - `MainActivityScreen` shows explicit storage error state when hydrated with no active profile and bootstrap error.
  - `ProfileSelectionContent` renders bootstrap errors and disables skip in degraded state.
  - `ProfileSettingsScreen` waits for hydration before deciding profile is missing.
- Added/updated profile repository regression tests for:
  - degraded storage read state preservation,
  - atomic write path,
  - stream failure hydration completion,
  - invalid and `null` entry sanitization.

Additional pass hardening:
- Parse-failure active resolution now favors last-known-good active id rather than the degraded snapshot id.
- `sanitizeProfiles` now accepts nullable decoded entries and drops them safely.

## 6) Verification Results (2026-02-20)

Passed:
- `./gradlew :app:testDebugUnitTest --tests "com.trust3.xcpro.profiles.ProfileRepositoryTest"`
- `./gradlew enforceRules testDebugUnitTest assembleDebug`
