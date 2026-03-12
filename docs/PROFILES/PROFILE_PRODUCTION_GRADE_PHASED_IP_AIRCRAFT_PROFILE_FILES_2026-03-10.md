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
   complete approved aircraft-setup file without breaking architecture.

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
  - `feature/profile/src/main/java/com/example/ui1/screens/Profiles.kt`
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

### Phase 1 - Aircraft-Profile Contract Enforcement

Goal:
- Make the `Aircraft Profile` artifact explicit and enforce it in code.

Current evidence:
- naming and portability docs exist
- a profile-scoped import whitelist already exists
- current aircraft-profile sections are known in code

Remaining gaps:
- single-profile export still serializes global settings sections
- aircraft-profile export and full-backup snapshot generation are not separate
- tests still assert global sections in the exported snapshot
- docs still include `home waypoint`, `OGN`, `Weather`, and similar global
  domains in aircraft-profile planning

Exit gate:
- single-profile export serializes only approved aircraft-profile sections, and
  docs/tests enforce the same contract

Score: `41/100`

### Phase 2 - Single-Profile UX and Import Safety

Goal:
- Make single-profile save/load the obvious and safe primary workflow.

Current evidence:
- overview already exposes `Load Profile File`, `Save All Profiles`, and row
  `Save Profile File` actions
- import dialog supports file selection
- repository already supports keep-current-active and collision policy

Remaining gaps:
- legacy `Load` / `Save` config actions still sit beside profile-file actions
- no preview of file contents before apply
- collision choices are not clearly presented as product actions
- import scope and bundle wording are still too technical for normal users
- the same direct-apply import dialog is reused from overview, profile-settings,
  and profile-selection entry points, so the unsafe flow is duplicated
- parsed bundle models do not retain enough metadata for preview UI
  (`schema version`, `exportedAtWallMs`)
- post-import feedback still summarizes only `ProfileImportResult`, not the
  full bundle import outcome or failed settings sections
- per-profile overview actions still do not expose `Duplicate` and `Delete`
- legacy config actions are still visually co-located with the aircraft-profile
  workflow instead of clearly demoted as secondary/legacy

Exit gate:
- the overview screen and import flow make one-profile save/load obvious without
  exposing internal bundle concepts

Score: `54/100`

### Phase 3 - Approved Aircraft-Settings Coverage

Goal:
- Lock, verify, and harden the approved aircraft-profile settings set.

Current evidence:
- substantial snapshot/restore infrastructure already exists
- cards, layout, look/theme, glider, units, map style, trail, orientation, and
  QNH are already wired

Remaining gaps:
- aircraft-profile coverage is not enforced by explicit export tests
- section ownership docs and code paths have drifted
- section-by-section migration validation is still incomplete

Exit gate:
- the approved aircraft-profile whitelist is explicit, implemented, and tested
  end-to-end

Score: `76/100`

### Phase 4 - Full Backup and File-Backed Asset Policy

Goal:
- Keep aircraft profiles deterministic while defining a separate full-backup
  path.

Current evidence:
- current JSON format already supports broader bundle payloads
- managed backup flow already exists

Remaining gaps:
- aircraft-profile and full-backup artifacts still share one snapshot path
- no final full-backup contract for global settings sections
- no package decision for waypoint/airspace assets if they are ever migrated

Exit gate:
- aircraft profile vs full backup is explicit in docs and code, with file-backed
  assets kept out of aircraft profiles

Score: `47/100`

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
- `feature/profile/src/main/java/com/example/ui1/screens/Profiles.kt`
- `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileSettingsScreen.kt`
- `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileActionButtons.kt`
- `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileExportImport.kt`
- docs under `docs/PROFILES/`

Tests to add/update:
- UI text tests for renamed actions

Exit criteria:
- no profile screen uses legacy ambiguous action names

### Phase 1 - Aircraft-Profile Contract Enforcement

Goal:
- codify the split between `Aircraft Profile` export and `Full Backup`

Implement:
- introduce an explicit aircraft-profile section whitelist
- route single-profile export through the aircraft-profile contract instead of
  the full backup snapshot
- keep full-backup serialization available as a separate path
- align docs and UI copy with:
  - aircraft-profile sections
  - global-only sections
  - file-backed exclusions
- make sure import feedback and defaults match the aircraft-profile contract

Files to change:
- `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileRepository.kt`
- `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileRepositoryBundleCoordinator.kt`
- `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileSettingsSnapshot.kt`
- `feature/profile/src/main/java/com/example/xcpro/profiles/AppProfileSettingsSnapshotProvider.kt`
- `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileExportImport.kt`
- docs under `docs/PROFILES/`

Tests to add/update:
- export exclusion tests for global-only sections
- bundle contract tests for aircraft-profile vs full-backup behavior
- import equivalence tests proving a normal aircraft-profile file behaves the
  same under `PROFILE_SCOPED_SETTINGS` and `FULL_BUNDLE`
- no-global-mutation tests proving aircraft-profile import does not overwrite
  global settings
- updated snapshot tests that no longer treat global sections as aircraft-profile
  content

Exit criteria:
- single-profile export never serializes global-only sections
- importing a normal aircraft-profile file cannot mutate global-only settings

### Phase 2 - First-Class Single-Profile Actions and Safe Import

Goal:
- make the overview screen the primary workflow surface and keep import hard to
  misuse

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
- move advanced/import-scope language out of the normal aircraft-profile path:
  - no `managed *_bundle_latest.json` wording in the primary dialog
  - no `All included settings` / `strict restore` controls in the primary dialog
  - keep any advanced restore options behind a separate backup/troubleshooting path
- ensure preview/result behavior is shared across all import entry points:
  - overview screen
  - profile settings screen
  - profile selection screen
- extend parsed import metadata so preview can show:
  - bundle/profile schema version
  - exported date/time if present
  - source format label
  - included settings summary
- replace snackbar/toast-only import results with a structured result summary for:
  - imported/skipped counts
  - collision action taken
  - failed settings sections
  - active-profile outcome

Files to change:
- `feature/profile/src/main/java/com/example/ui1/screens/Profiles.kt`
- `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileExportImport.kt`
- `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileViewModel.kt`
- `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileRepository.kt`
- `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileSelectionScreen.kt`
- `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileBundleCodec.kt`
- `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileImportFeedbackFormatter.kt`

Tests to add/update:
- preview parsing tests
- preview metadata tests (`version`, `exportedAtWallMs`, source format)
- preview confirm/cancel tests
- collision-option tests
- activation-choice tests
- malformed file and index-only file tests
- result-summary tests for failed settings sections and activation outcome
- shared entry-point tests proving overview/profile-settings/profile-selection all
  use the same preview-first import flow

Exit criteria:
- a user can save/load one aircraft profile from the overview flow with preview
  and clear included-settings language

### Phase 3 - Aircraft-Profile Coverage Closure

Goal:
- verify and harden the approved aircraft-profile sections only

Priority order:
1. card/template and flight-mode restore behavior
2. look and feel and theme
3. widget layouts
4. glider/polar/config
5. units, map style, snail trail, orientation, and QNH

Files to change:
- `feature/profile/src/main/java/com/example/xcpro/profiles/AppProfileSettingsSnapshotProvider.kt`
- `feature/profile/src/main/java/com/example/xcpro/profiles/AppProfileSettingsRestoreApplier.kt`
- `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileSettingsSectionSnapshots.kt`
- affected settings repositories in `feature/map`, `feature/traffic`, `core/common`, `dfcards-library`

Tests to add/update:
- per-section round-trip tests
- starter-file plus populated-settings tests
- profile switch isolation tests after restore

Exit criteria:
- the aircraft-profile file covers the approved aircraft section matrix
  section-by-section

### Phase 4 - Full Backup and `Files` Policy Closure

Goal:
- separate full backup from aircraft-profile scope and close the remaining
  file-backed portability ambiguity

Decision branches:
- Option A:
  - aircraft profile file remains JSON-only
  - file-backed assets stay out
- Option B:
  - introduce a separate packaged archive for profiles that carry portable file assets

Required decisions:
- full-backup artifact wording and entry point
- whether global settings are exported only through full backup
- whether portable external assets ever need a package format

Files to change:
- `docs/PROFILES/PROFILE_FILE_PORTABILITY_STRATEGY_2026-03-10.md`
- repositories that own affected file-backed settings
- backup/export entry-point code if full backup gets a dedicated path

Tests to add/update:
- include/exclude policy tests
- package manifest tests if archive format is introduced

Exit criteria:
- `home waypoint`, waypoint files, and airspace files are explicitly out of
  aircraft profile scope, and the full-backup policy is documented

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
- `feature/profile/src/main/java/com/example/ui1/screens/Profiles.kt`
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
- verify aircraft-profile settings restored:
  - units
  - orientation
  - card templates/positions
  - widget layout
  - glider/polar
  - map style
  - QNH
  - snail trail
- verify global-only settings remain unchanged after aircraft-profile import
- save all profiles
- move the file and import on another install if available

Exit criteria:
- production evidence exists for single-profile and all-profiles workflows

## Immediate Priority Order

1. Phase 0: finalize action names
2. Phase 1: enforce aircraft-profile export/import contract and tests
3. Phase 2: add preview-first import and simplify user-facing file workflow
4. Phase 3: harden approved aircraft-profile settings coverage
5. Phase 4: define full-backup path and keep file-backed assets out of aircraft profiles
6. Phase 5: polish all-profiles backup and recovery copy

## Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| users confuse `Save Changes` with `Save Profile File` | profile file not created when expected | separate labels, separate button placement, tests | XCPro Team |
| overview still promotes all-profiles backup over single-profile save | wrong primary workflow | move single-profile actions to overview screen | XCPro Team |
| import remains opaque | duplicate or wrong-profile imports | preview-first import flow with clear collision actions | XCPro Team |
| aircraft-profile import mutates global settings | pilots lose unrelated app setup during device move | explicit aircraft-profile whitelist plus no-global-mutation tests | XCPro Team |
| settings coverage expands too quickly and destabilizes runtime | cross-profile bleed or failed restores | section-by-section rollout with round-trip tests | XCPro Team |
| file-backed assets are hand-waved | broken portability promises | explicit `Files` policy decision before claiming full coverage | XCPro Team |
| managed backup and manual export stay conceptually mixed | user distrust and support burden | distinct wording and docs for each path | XCPro Team |

## Acceptance Gates

- portable aircraft profile files remain projection artifacts, not runtime SSOT
- users can save one aircraft profile file without hidden navigation knowledge
- users can load one aircraft profile file with preview and collision clarity
- switching profiles never depends on reopening external storage
- all approved aircraft-profile sections are added only with snapshot/apply tests
- importing an aircraft profile file does not overwrite global-only settings
- `Save All Profiles` remains a secondary backup/migration flow, not the primary UX
- docs and UI use consistent product language

## Recommended Deliverable Sequence

1. UI language cleanup
2. aircraft-profile contract enforcement and test closure
3. overview-screen single-profile actions plus import preview
4. approved aircraft-profile settings hardening
5. full-backup and file/package policy closure
6. release verification and operator docs
