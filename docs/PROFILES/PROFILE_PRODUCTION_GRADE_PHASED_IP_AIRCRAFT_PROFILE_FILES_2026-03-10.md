# PROFILE_PRODUCTION_GRADE_PHASED_IP_AIRCRAFT_PROFILE_FILES_2026-03-10

## Purpose

Define a production-grade phased implementation plan for XCPro's user-owned
aircraft profile files.

This plan is the engineering execution document for the product direction in:

- `docs/PROFILES/PROFILE_FILE_PORTABILITY_STRATEGY_2026-03-10.md`

Primary target outcomes:

1. One aircraft profile in the app maps to one aircraft profile file.
2. Switching aircraft is always internal and instantaneous after import.
3. Saving a profile in the app and saving a profile file are clearly different
   user actions.
4. A pilot can create, import, export, move, and re-activate aircraft setups
   without ambiguity.
5. Profile files grow from "basic metadata + current covered settings" toward a
   General-first full aircraft setup file without breaking architecture.

Assessment date: `2026-03-10`.

## Product Intent

The user mental model should be:

- `Aircraft Profile` = the thing they fly
- `Save Changes` = persist that profile inside XCPro
- `Save Profile File` = write one portable `.json` file
- `Load Profile File` = import one portable `.json` file
- `Save All Profiles` = one backup file for migration or archival

The current product still leaks implementation history:

- overview screen exposes `Export All`
- single-profile export exists, but only inside per-profile settings
- `Save` on the profile screen saves app state, not a file

That is functional, but not production-grade UX for a portable-file feature.

## Current State Snapshot

Current evidence:

- overview screen exposes `Import` and `Export All`:
  - `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/Profiles.kt`
- single-profile export exists in profile settings:
  - `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileSettingsScreen.kt`
- import/export dialog and SAF file flow already exist:
  - `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileExportImport.kt`
- runtime SSOT remains internal:
  - `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileRepository.kt`
  - `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileStorage.kt`
- portable filename convention exists:
  - `feature/profile/src/main/java/com/example/xcpro/profiles/AircraftProfileFileNames.kt`
- starter example files exist:
  - `docs/PROFILES/examples/xcpro-aircraft-profile-sailplane-asg-29-2026-03-10.json`
  - `docs/PROFILES/examples/xcpro-aircraft-profile-hang-glider-moyes-litespeed-rs-2026-03-10.json`
- basic import/switch test coverage exists:
  - `app/src/test/java/com/example/xcpro/profiles/ProfileExportImportTest.kt`
  - `app/src/test/java/com/example/xcpro/profiles/ProfileRepositoryBundleTest.kt`

## Phase Scores

- Score meaning: current completion against phase exit gate.
- `95-100`: production-ready for that phase.
- `85-94`: strong but still missing non-trivial closure.
- `<85`: not production-ready for that phase.

### Phase 0 - Contract and Terminology Freeze

Goal:
- Freeze product language and execution rules before broader UI and restore work.

Current evidence:
- `PROFILE_FILE_PORTABILITY_STRATEGY_2026-03-10.md`
- starter file naming and examples already exist
- runtime/file split is explicit in docs and code

Remaining gaps:
- user-facing labels are still mixed across overview and profile-detail screens
- `Save` vs `Save file` is still easy to misunderstand

Exit gate:
- one approved naming matrix used consistently in docs and code

Score: `82/100`

### Phase 1 - Single-Profile File UX First

Goal:
- Make single-profile save/load the obvious primary workflow.

Current evidence:
- single-profile export already exists in the profile settings screen
- import already exists from overview and selection flows

Remaining gaps:
- overview screen promotes `Export All`, not the single-profile workflow
- single-profile file save requires entering a second screen
- no row-level direct action for `Save File`

Exit gate:
- overview screen supports obvious single-profile save/load without forcing deep navigation

Score: `58/100`

### Phase 2 - Import Preview and Conflict Handling

Goal:
- Make loading a profile file safe, inspectable, and hard to misuse.

Current evidence:
- import dialog supports file selection
- repository already supports keep-current-active and collision policy

Remaining gaps:
- no preview of file contents before apply
- collision choices are not clearly presented as product actions
- imported scope details are too technical for normal users

Exit gate:
- import flow shows file preview, collision choice, activation choice, and clear included-settings summary

Score: `52/100`

### Phase 3 - General-First Settings Coverage

Goal:
- Expand the portable file from metadata-plus-current-sections toward the
  General-first aircraft setup target.

Current evidence:
- substantial snapshot/restore infrastructure already exists
- strategy doc has a General-first include matrix

Remaining gaps:
- not all desired `General` sections are implemented in the portable file
- `Orientation` and `Files` policy are still incomplete

Exit gate:
- General-first coverage is explicit, implemented, and tested section-by-section

Score: `68/100`

### Phase 4 - File/Package Policy for Portable Assets

Goal:
- Define what happens when a profile needs more than plain JSON preferences.

Current evidence:
- strategy doc already classifies raw URIs and raw imported files as exclusions

Remaining gaps:
- no final package strategy for waypoint-file portability, if required
- no explicit separate bundle/package design for file-backed profile assets

Exit gate:
- `Files` policy is closed: pure JSON only, or JSON plus a defined package format

Score: `28/100`

### Phase 5 - Recovery, Managed Backup, and Product Cohesion

Goal:
- Make `Save All Profiles`, managed backup snapshots, and manual file workflows
  feel like one coherent product.

Current evidence:
- managed backup mirroring exists
- canonical one-file all-profiles export exists through current bundle path

Remaining gaps:
- product language still exposes legacy `Export All`
- manual single-profile flow and managed backup flow are not clearly differentiated in UI
- import guidance still needs better user-facing explanation

Exit gate:
- users can distinguish:
  - save this aircraft file
  - save all aircraft profiles
  - import a profile file
  - recover from managed backup

Score: `61/100`

### Phase 6 - Release Verification and Production Closure

Goal:
- Close the feature with release-grade verification, docs, and operator guidance.

Current evidence:
- unit tests cover starter files and import/switch basics
- debug builds currently assemble

Remaining gaps:
- no dedicated instrumentation pass for profile file UX
- no final manual QA checklist for the aircraft-profile-file product flow
- repo-wide unit suite currently has unrelated traffic failures, so release evidence must be explicitly scoped

Exit gate:
- automated and manual evidence pass for create/import/export/switch/move/recover flows

Score: `55/100`

## Architecture Contract

### SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| Profile list + active profile | `ProfileRepository` / `ProfileStorage` | `profiles`, `activeProfile`, `snapshotFlow` | UI-side profile registry |
| Portable aircraft profile file | bundle codec + export/import edge | JSON document only | treating file as runtime SSOT |
| Included settings sections | existing owning repositories | snapshot/apply adapters | shadow profile document as runtime owner |
| Export filename generation | edge helper | `AircraftProfileFileNames` | ad-hoc per-screen file names |

### Dependency Direction

Confirmed target dependency flow remains:

`UI -> use case -> repository/data`

No phase in this plan should:

- move file I/O into ViewModel logic
- make UI the owner of profile state
- make portable JSON the live runtime source of truth

### Time Base

| Value | Time Base | Why |
|---|---|---|
| file `exportedAtWallMs` metadata | Wall | user-facing export timestamp |
| exported filename date | Wall | user-facing file naming |
| runtime switch behavior | none added | profile switching should stay internal |

Explicitly forbidden:

- using wall time to decide runtime profile selection
- making replay behavior depend on import/export timestamps

### Replay Determinism

- Deterministic for same input: Yes
- Randomness used: No new randomness should be introduced
- Replay/live divergence rules:
  - this work must not affect replay logic
  - file naming and export timestamps are outside replay-critical paths

## Data Flow

Before:

`Profile in app -> Save -> internal storage`

`Profile in app -> per-profile settings screen -> Export -> JSON file`

`Profiles overview -> Export All -> multi-profile JSON file`

After target:

`Profiles overview -> Save Profile File -> JSON file`

`Profiles overview -> Load Profile File -> import -> internal storage -> Activate`

`Profiles overview -> Save All Profiles -> multi-profile JSON file`

`Switch aircraft -> select internal active profile -> hydrate profile-scoped repos`

## Implementation Phases

### Phase 0 - Product Language and Navigation Contract

Goal:
- freeze final user-facing words before additional UI work

Required decisions:
- `Save` -> `Save Changes`
- `Export` -> `Save Profile File`
- `Import` -> `Load Profile File`
- `Export All` -> `Save All Profiles`

Files to change:
- `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/Profiles.kt`
- `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileSettingsScreen.kt`
- `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileActionButtons.kt`
- `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileExportImport.kt`
- docs under `docs/PROFILES/`

Tests to add/update:
- UI text tests for renamed actions

Exit criteria:
- no profile screen uses legacy ambiguous action names

### Phase 1 - First-Class Single-Profile Actions on Overview Screen

Goal:
- make the overview screen the primary workflow surface

Implement:
- top-level actions:
  - `New Aircraft Profile`
  - `Load Profile File`
  - `Save All Profiles`
- per-profile actions:
  - `Activate`
  - `Save File`
  - `Duplicate`
  - `Delete`

Files to change:
- `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/Profiles.kt`
- `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileExportImport.kt`
- navigation call sites if needed

Tests to add/update:
- row action tests
- overview action visibility tests
- active-profile save-file action test

Exit criteria:
- a user can save one aircraft profile file directly from the overview screen

### Phase 2 - Safe Import Flow

Goal:
- turn file import into a product-grade guided flow

Implement:
- import preview sheet/dialog showing:
  - profile name
  - aircraft type
  - aircraft model
  - schema version
  - exported date
  - included settings scope
- collision options:
  - import as new
  - replace matching profile
- post-import options:
  - activate now
  - keep current active

Files to change:
- `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileExportImport.kt`
- `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileViewModel.kt`
- `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileRepository.kt`

Tests to add/update:
- preview parsing tests
- collision-option tests
- activation-choice tests
- malformed file and index-only file tests

Exit criteria:
- import is preview-first and clearly communicates what will happen

### Phase 3 - General-First Snapshot Expansion

Goal:
- close the main profile-settings coverage gaps

Priority order:
1. `General -> Orientation`
2. finish `General -> Look & Feel` and theme terminology closure
3. verify `General -> Units`
4. verify `General -> Polar`
5. verify `General -> OGN`, `ADS-b`, `Weather`, `Thermalling`

Files to change:
- `app/src/main/java/com/example/xcpro/profiles/AppProfileSettingsSnapshotProvider.kt`
- `app/src/main/java/com/example/xcpro/profiles/AppProfileSettingsRestoreApplier.kt`
- `app/src/main/java/com/example/xcpro/profiles/ProfileSettingsSectionSnapshots.kt`
- affected settings repositories in `feature/map`, `feature/traffic`, `core/common`, `dfcards-library`

Tests to add/update:
- per-section round-trip tests
- starter-file plus populated-settings tests
- profile switch isolation tests after restore

Exit criteria:
- the portable file covers the approved General-first matrix section-by-section

### Phase 4 - `Files` Policy Closure

Goal:
- resolve the remaining portability ambiguity around file-backed settings

Decision branches:
- Option A:
  - aircraft profile file remains JSON-only
  - file-backed assets stay out
- Option B:
  - introduce a separate packaged archive for profiles that carry portable file assets

Required decisions:
- home waypoint inclusion
- waypoint file enabled registry handling
- whether portable external assets need a package format

Files to change:
- `docs/PROFILES/PROFILE_FILE_PORTABILITY_STRATEGY_2026-03-10.md`
- repositories that own affected file-backed settings

Tests to add/update:
- include/exclude policy tests
- package manifest tests if archive format is introduced

Exit criteria:
- `General -> Files` is no longer marked pending

### Phase 5 - Recovery and All-Profiles Cohesion

Goal:
- make all backup and recovery entry points consistent

Implement:
- final wording for `Save All Profiles`
- clear distinction between:
  - single profile file
  - all-profiles backup
  - managed `*_bundle_latest.json`
- recovery copy and troubleshooting aligned with actual import targets

Files to change:
- `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/Profiles.kt`
- `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileExportImport.kt`
- `docs/PROFILES/PROFILE_TROUBLESHOOTING.md`
- `docs/PROFILES/MANUAL_E2E_PROFILE_RESTORE_CHECKLIST_2026-03-07.md`

Tests to add/update:
- overview export-all tests
- recovery-message tests
- import guidance tests

Exit criteria:
- a user can tell exactly when they are saving one profile vs all profiles

### Phase 6 - Release Hardening

Goal:
- close the feature with production evidence

Required verification:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Targeted verification when repo-wide tests are unstable:

```bash
./gradlew :app:testDebugUnitTest --tests "com.example.xcpro.profiles.*"
./gradlew :feature:profile:testDebugUnitTest
./gradlew :feature:profile:assembleDebug :app:assembleDebug
```

When device/emulator is available:

```bash
./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"
```

Manual QA checklist:
- create a new aircraft profile
- save changes in app
- save one profile file
- import that file
- switch to it
- save all profiles
- move the file and import on another install if available

Exit criteria:
- production evidence exists for single-profile and all-profiles workflows

## Immediate Priority Order

1. Phase 0: finalize action names
2. Phase 1: put single-profile `Save File` on the overview screen
3. Phase 2: add import preview and clearer collision choices
4. Phase 3: add `Orientation`
5. Phase 4: close `Files` policy
6. Phase 5: polish all-profiles backup and recovery copy

## Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| users confuse `Save Changes` with `Save Profile File` | profile file not created when expected | separate labels, separate button placement, tests | XCPro Team |
| overview still promotes all-profiles backup over single-profile save | wrong primary workflow | move single-profile actions to overview screen | XCPro Team |
| import remains opaque | duplicate or wrong-profile imports | preview-first import flow with clear collision actions | XCPro Team |
| settings coverage expands too quickly and destabilizes runtime | cross-profile bleed or failed restores | section-by-section rollout with round-trip tests | XCPro Team |
| file-backed assets are hand-waved | broken portability promises | explicit `Files` policy decision before claiming full coverage | XCPro Team |
| managed backup and manual export stay conceptually mixed | user distrust and support burden | distinct wording and docs for each path | XCPro Team |

## Acceptance Gates

- portable aircraft profile files remain projection artifacts, not runtime SSOT
- users can save one aircraft profile file without hidden navigation knowledge
- users can load one aircraft profile file with preview and collision clarity
- switching profiles never depends on reopening external storage
- all approved General-first sections are added only with snapshot/apply tests
- `Save All Profiles` remains a secondary backup/migration flow, not the primary UX
- docs and UI use consistent product language

## Recommended Deliverable Sequence

1. UI language cleanup
2. overview-screen single-profile actions
3. import preview and collision UX
4. settings coverage expansion
5. file/package policy closure
6. release verification and operator docs
