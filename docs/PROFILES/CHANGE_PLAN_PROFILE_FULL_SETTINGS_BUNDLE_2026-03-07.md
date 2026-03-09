# CHANGE_PLAN_PROFILE_FULL_SETTINGS_BUNDLE_2026-03-07

## Purpose

Define a production-grade phased implementation plan so XCPro profile backup/restore captures full user profile state and avoids startup/profile-id drift that makes settings appear missing.

This plan follows:

1. `docs/ARCHITECTURE/CHANGE_PLAN_TEMPLATE.md`
2. `docs/ARCHITECTURE/ARCHITECTURE.md`
3. `docs/ARCHITECTURE/CODING_RULES.md`
4. `docs/ARCHITECTURE/PIPELINE.md`
5. `docs/ARCHITECTURE/CODEBASE_CONTEXT_AND_INTENT.md`

## 0) Metadata

- Title: Full Profile Backup/Restore and Canonical Profile Identity
- Owner: XCPro Team
- Date: 2026-03-07
- Issue/PR: PROFILE-20260307-FULL-BUNDLE
- Status: Draft (Deep-pass + standards-gap + sixth-pass revision)

## 1) Scope

- Problem statement:
  - Users report startup states where profile/settings appear missing and they create a new profile.
  - Current backup sync writes profile metadata, but many settings live in other stores.
  - Multiple fallback profile IDs (`default-profile`, `default`, `__default_profile__`) create namespace drift.
- Why now:
  - User expectation is a true full profile save including card/layout/widget state and glider polar.
  - Existing backup folder flow (`Download/XCPro/profiles/`) is already present and should become authoritative for portable backups.
- In scope:
  - Canonical default profile identity contract and migration.
  - Versioned profile bundle schema and deterministic restore.
  - Export/import unification (remove profile-only bypass path).
  - Include all profile-relevant settings domains in bundle plan.
  - Keep user-accessible backup files in `Download/XCPro/profiles/` for USB/manual copy workflows.
- Out of scope:
  - Cloud/account sync.
  - Encryption/password protection.
  - Cached runtime data (weather tiles, transient network caches).
- User-visible impact:
  - Startup profile identity is stable.
  - Restoring a bundle restores expected profile settings, including glider polar and UI layout domains.

### 1.1 Scope Tiers (Explicit)

- Tier A: Full profile bundle (default target)
  - Profile identity + per-profile settings required to make one pilot profile portable.
- Tier B: Full app backup (optional extension)
  - App-global settings that are not profile-scoped today but may still be user-important.

### 1.2 Current-State Investigation (Deep-Pass Inventory)

| Domain | Store/File | Scope Today | Code Anchor | Planned Tier |
|---|---|---|---|---|
| Profile list + active profile | DataStore `profile_preferences` (`profiles_json`, `active_profile_id`) | Profile core SSOT | `feature/profile/.../ProfileStorage.kt` | Tier A |
| Profile backup folder mirror | MediaStore path `Download/XCPro/profiles/` | Profile metadata mirror | `feature/profile/.../ProfileBackupSink.kt` | Tier A |
| Card templates/cards/positions/visibilities | DataStore `card_preferences` with `profile_<id>_...` keys | Profile-scoped | `dfcards-library/.../CardPreferences.kt` | Tier A |
| Flight data management last mode/tab | SharedPreferences `FlightMgmtPrefs` | Mixed (tab global, mode profile-keyed) | `feature/map/.../FlightMgmtPreferencesRepository.kt` | Tier A |
| Look and feel (status bar, card style) | SharedPreferences `LookAndFeelPrefs` | Profile-keyed | `feature/map/.../lookandfeel/LookAndFeelPreferences.kt` | Tier A |
| Theme + custom colors | SharedPreferences `ColorThemePrefs` | Profile-keyed | `feature/map/.../ThemePreferencesRepository.kt` | Tier A |
| Map widget positions (hamburger/settings/flight mode/ballast) | SharedPreferences `MapPrefs` | Global keys | `feature/map/.../MapWidgetLayoutRepository.kt` | Tier A |
| Variometer widget layout | SharedPreferences `MapPrefs` | Global keys | `feature/variometer/.../VariometerWidgetRepository.kt` | Tier A |
| Glider config and polar | SharedPreferences `glider_prefs` | Global keys | `feature/map/.../GliderRepository.kt` | Tier A |
| Levo vario config + audio settings | DataStore `levo_vario_preferences` | Global | `feature/map/.../vario/LevoVarioPreferencesRepository.kt` | Tier A |
| Thermalling automation settings | DataStore `thermalling_mode_preferences` | Global | `feature/map/.../thermalling/ThermallingModePreferencesRepository.kt` | Tier A |
| OGN traffic settings | DataStore `ogn_traffic_preferences` | Global | `feature/map/.../ogn/OgnTrafficPreferencesRepository.kt` | Tier A |
| OGN trail selection | DataStore `ogn_trail_selection_preferences` | Global | `feature/map/.../ogn/OgnTrailSelectionPreferencesRepository.kt` | Tier A |
| ADS-B traffic settings | DataStore `adsb_traffic_preferences` | Global | `feature/map/.../adsb/AdsbTrafficPreferencesRepository.kt` | Tier A |
| Rain overlay settings | DataStore `weather_overlay_preferences` | Global | `feature/map/.../weather/rain/WeatherOverlayPreferencesRepository.kt` | Tier A |
| Forecast overlay/settings | DataStore `forecast_preferences` | Global | `feature/map/.../forecast/ForecastPreferencesRepository.kt` | Tier A |
| Wind manual override | DataStore `wind_preferences` | Global | `feature/map/.../weather/wind/data/WindOverrideRepository.kt` | Tier A |
| Units preferences | DataStore `units_preferences` | Global | `core/common/.../UnitsRepository.kt` | Tier B (candidate Tier A if requested) |
| Map orientation settings | SharedPreferences `map_orientation_prefs` | Global | `feature/map/.../MapOrientationPreferences.kt` | Tier B (candidate Tier A) |
| QNH manual value | DataStore `qnh_preferences` | Global | `feature/map/.../QnhPreferencesRepository.kt` | Tier B (candidate Tier A) |
| Trail settings | SharedPreferences `map_trail_prefs` | Global | `feature/map/.../trail/MapTrailPreferences.kt` | Tier B (candidate Tier A) |
| Home waypoint | SharedPreferences `HomeWaypointPrefs` + file `home_waypoint.json` | Global | `core/common/.../HomeWaypointRepository.kt`, `WaypointModels.kt` | Tier B (candidate Tier A) |
| App map style selection | file `configuration.json` (`app.mapStyle`) | Global | `feature/map/.../MapStyleRepository.kt`, `ConfigurationRepository.kt` | Tier B (candidate Tier A) |
| Nav drawer expansion state | file `configuration.json` (`navDrawer`) | Global | `feature/map/.../ConfigurationRepository.kt` | Tier B |
| Waypoint file enabled-state registry | file `configuration.json` (`waypoint_files`) | Global operational setting | `feature/map/.../flightdata/WaypointFilesRepository.kt`, `ConfigurationRepository.kt` | Tier B (explicit decision required) |
| Legacy selected-templates config map | file `configuration.json` (`selected_templates`) | Global legacy setting | `feature/map/.../ConfigurationRepository.kt` | Tier B (likely exclude if deprecated) |
| Recent waypoints | SharedPreferences `RecentWaypoints` | Global user history | `feature/map/.../tasks/RecentWaypointsRepository.kt` | Tier B |
| Racing/AAT task working state + task files | SharedPreferences + files under app internal tasks directories | Global task workflow data | `feature/map/.../tasks/racing/RacingTaskStorage.kt`, `feature/map/.../tasks/aat/persistence/AATTaskFileIO.kt` | Tier B (explicit decision required) |
| Task type + migration policy flags | SharedPreferences (`task_coordinator_prefs`, `task_storage_migration_prefs`) | Global operational state | `feature/map/.../tasks/data/persistence/TaskPersistenceAdapters.kt`, `LegacyCupStorageCleanupPolicy.kt` | Tier B (likely exclude from profile bundle) |
| Credential fallback policy flag | SharedPreferences `forecast_provider_credentials_policy` | Global security policy | `feature/map/.../forecast/ForecastCredentialsRepository.kt` | Tier B (likely exclude from profile bundle) |
| ADS-B metadata sync checkpoint | DataStore `adsb_metadata_sync_checkpoint` | Sync/cache checkpoint (non-user setting) | `feature/map/.../adsb/metadata/data/AircraftMetadataSyncCheckpointStore.kt` | Explicit exclude |
| Forecast/OpenSky credentials | EncryptedSharedPreferences | Secret material | `feature/map/.../forecast/ForecastCredentialsRepository.kt`, `feature/map/.../adsb/OpenSkyCredentialsRepository.kt` | Explicit exclude |
| App first-run seeding state | SharedPreferences `first_time_setup` | App install/bootstrap metadata | `app/.../FirstTimeSetupManager.kt` | Explicit exclude |
| Manual profile export/import dialog path | JSON contract `ProfileExport` (`profiles` only) | Separate bypass path | `feature/profile/.../ProfileExportImport.kt` | Replace in Tier A flow |

### 1.3 Deep-Pass Findings Missed Previously

1. Default identity drift is real and high impact.
- Canonical default in repository: `default-profile`.
- UI/viewmodel fallback IDs use literal `default` in multiple callsites.
- DFCards fallback uses `__default_profile__`.

2. Theme persistence has overlapping ownership plus one observer mismatch.
- `LookAndFeelPreferences` and `ThemePreferencesRepository` both target `ColorThemePrefs` keyspace.
- `LookAndFeelPreferences.observeColorThemeId()` currently observes the wrong SharedPreferences instance (`lookAndFeelPrefs` instead of `colorPrefs`).

3. There are two import/export flows.
- Folder sync path in `ProfileBackupSink`.
- Separate dialog path in `ProfileExportImport` that imports profile-only JSON and generates new profile IDs.

4. Additional settings domains likely expected in "full profile save" were not explicitly phased.
- Units, orientation, QNH, trail, home waypoint, nav drawer expansion.

5. Package/sandbox split is a frequent operational cause of "missing profile" symptoms.
- `applicationId = com.example.openxcpro`, `debug` uses suffix `.debug` (different sandbox).

### 1.4 Third-Pass Findings (This Repass)

1. A null-active escape path still exists in profile selection UX.
- `ProfileSelectionContent` exposes "Skip for now" when profiles exist but active is null.
- This can continue flow without repairing active profile invariants.

2. Map visible-mode hydration can remain stale when profile is null.
- `FlightDataManager.loadVisibleModes(...)` no-ops on null profile ID and keeps previous in-memory visibility.

3. Multiple viewmodels still transiently hydrate from literal `"default"`.
- `ThemeViewModel`, `ColorsViewModel`, `LookAndFeelViewModel`, and `FlightMgmtPreferencesViewModel` initialize with `"default"` before active profile binding.
- This can briefly read/write wrong profile-key namespace during startup/profile transitions.

4. Status bar style fallback still bypasses profile-keyed fallback policy.
- `MainActivity.applyUserStatusBarStyle(null)` resolves directly to transparent, not canonical fallback profile style.

5. Profile delete does not orchestrate downstream profile-key cleanup.
- `ProfileRepository.deleteProfile(...)` updates profile core state only.
- Card/theme/look-and-feel/flight-mode profile-keyed entries are not centrally cascade-cleared.

6. DFCards visibility hydration still misses profiles that only have visibility keys.
- `FlightProfileStore.hydrateFromPreferences(...)` derives target profile IDs from template/card maps (+active profile), not from visibility-key presence itself.

7. Profiles screen still renders active badge from model field drift source.
- `Profiles.kt` checks `profile.isActive` instead of `uiState.activeProfile?.id == profile.id`.

### 1.5 Fourth-Pass Findings (Deep Repass)

1. Canonical default profile existence is not enforced once profile storage is non-empty.
- `ensureBootstrapProfile(...)` inserts `default-profile` only when the full list is empty.
- If snapshots/imports contain profiles but no canonical default entry, runtime can continue indefinitely without a protected default profile.

2. Backup cleanup policy is destructive for user-managed archives in `Download/XCPro/profiles/`.
- `cleanupStaleManagedFiles(...)` deletes any `profile_*.json` not in the current expected set.
- This conflicts with the stated USB/manual-copy workflow because historical/manual backup files in that same folder can be removed automatically.

3. Parse-failure bootstrap can trigger destructive backup sync on cold start.
- On parse failure with empty `lastKnownGoodProfiles`, repository still calls `scheduleProfileBackupSync(emptyList(), null)`.
- Sink then writes an empty index and cleanup can remove all managed profile files, turning a read error into backup data loss.

4. `UserProfile.preferences` and `UserProfile.polar` are shadow state, not runtime SSOT for active settings.
- Profile settings UI edits these fields, but active runtime theme/units/glider polar are sourced from separate repositories (`Theme/LookAndFeel`, `UnitsRepository`, `GliderRepository`).
- This creates false confidence that profile metadata edits represent full runtime configuration.

5. Profile settings actions navigate optimistically even if persistence fails.
- `ProfileSettingsScreen` pops back immediately on save/delete before repository result returns.
- Failure states (for example default-profile delete rejection) are hidden, increasing user confusion around what actually changed.

### 1.6 Fifth-Pass Findings (Research + Scope Closure)

1. Tier A matrix still needed explicit closure for several persistent settings repositories.
- Vario, thermalling, OGN, ADS-B, weather, forecast, and wind override stores must be explicitly classified and test-covered.

2. Backup folder ownership must be explicit to avoid cross-build cleanup collisions.
- `debug` and `release` package variants can target the same user-visible folder, and wildcard managed cleanup can remove files not owned by the current build.

3. Android system backup rules can conflict with manual profile bundle restore if left implicit.
- Manifest already enables `allowBackup`, `dataExtractionRules`, and `fullBackupContent`; plan must define include/exclude policy and restore precedence.

4. Parse-failure degraded startup needs an explicit user recovery path, not only internal logging.
- Without a visible safe-mode flow, users can create replacement profiles while previous state remains recoverable but undiscoverable.

5. Production-grade closure needs an explicit include/exclude matrix for non-profile persistent data.
- Tasks, recent waypoints, credential policy flags, bootstrap metadata, and secrets require explicit policy decisions to avoid accidental leakage or silent omissions.

### 1.7 Sixth-Pass Findings (Deep Code Re-Audit)

1. Backup file contracts are currently incompatible across the two existing flows.
- Folder sync writes `ProfileBackupDocument`/`ProfileBackupIndex`.
- Dialog import expects `ProfileExport` (`profiles` list).
- Result: files produced in `Download/XCPro/profiles/` are not reliably importable through current import dialog.

2. Managed backup writes are non-atomic and can delete last-good artifacts before replacement succeeds.
- `writeJsonFile(...)` deletes existing file before insert/write/commit; write failure can leave no valid replacement file.

3. Backup sync scheduling does not enforce latest-wins ordering at repository boundary.
- `scheduleProfileBackupSync(...)` launches fire-and-forget snapshots.
- Sink serializes writes with a mutex, but no sequence token prevents an older queued snapshot from being the last completed write under scheduler reordering.

4. Degraded startup UI path is currently a dead-end message surface.
- `MainActivityScreen` renders "Profile Storage Error" text only when active profile is null with bootstrap error, without recovery actions.

5. Startup skip path intent and behavior are inconsistent.
- `ProfileSelectionContent` exposes "Skip for now", but in the primary startup route `onProfileSelected` is passed as no-op (`{}`), so skip does not perform meaningful recovery/navigation.

6. `ProfileCreationRequest.copyFromProfile` is currently unused.
- The model indicates clone/copy semantics, but repository/UI create path ignores it, increasing likelihood of user-created profiles that do not carry expected existing settings.

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| Profile list + active profile id | `ProfileStorage` / `ProfileRepository` | `snapshotFlow`, `profiles`, `activeProfile` | Parallel mutable profile owner |
| Canonical profile ID mapping | New `ProfileIdResolver` contract | normalize/migrate helpers | Per-feature ad-hoc default IDs |
| Card template/card/visibility/position state | `CardPreferences` | Flow APIs | Backup-side writable runtime mirror |
| Look-and-feel and theme state | Theme/look-and-feel repos | Flow/get APIs | Conflicting profile-theme authority |
| Flight mode preference by profile | `FlightMgmtPreferencesRepository` | Flow/get APIs | Duplicate last-mode stores |
| Widget + variometer layout | map/variometer layout repos | adapter snapshot API + runtime read/write | Shadow widget stores |
| Glider config/polar | `GliderRepository` | `config`, `selectedModel`, `effectiveModel` | Unlinked glider copy |
| Vario/thermalling/traffic/weather/forecast/wind settings | existing owning repositories (`LevoVario`, `ThermallingMode`, `OgnTraffic`, `OgnTrailSelection`, `AdsbTraffic`, `WeatherOverlay`, `ForecastPreferences`, `WindOverride`) | adapter snapshot/apply API + existing runtime flows | Shadow profile metadata fields claiming runtime ownership |
| Tier B global settings (units/orientation/qnh/trail/home waypoint/nav drawer) | current owning repos | existing API + snapshot adapter | New runtime owner in profile module |
| Backup bundle documents | backup adapter layer only | serialized artifacts | Treating bundle file as runtime SSOT |

### 2.2 Dependency Direction

Confirmed target dependency flow remains:

`UI -> domain/use-case -> data`

- Modules/files touched:
  - `feature/profile` (bundle contract, resolver, restore orchestration)
  - `feature/map`, `feature/variometer`, `core/common`, `dfcards-library` (snapshot adapters)
  - `app` (DI wiring)
- Boundary risk:
  - Profile feature must depend on adapter interfaces, not concrete map/dfcards internals.

### 2.2A Boundary Moves (Mandatory)

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| Canonical default profile ID normalization | scattered literals in UI/dfcards/repos | shared `ProfileIdResolver` adapter | remove namespace drift | normalization tests + startup integration tests |
| Bundle assembly | `DownloadsProfileBackupSink` profile-only payload | `ProfileBundleAssembler` + per-domain snapshot adapters | full settings coverage | bundle section coverage tests |
| Import/export orchestration | `ProfileExportImport` profile-only contract + folder sync path | single bundle parser/restore use-case | deterministic restore and one contract | import/export integration tests |
| Color theme observation behavior | `LookAndFeelPreferences` mixed source reads | explicit per-store flow helpers + precedence policy | avoid stale/default theme state | repository flow tests |
| Profile metadata vs runtime settings ownership | `UserProfile.preferences` / `UserProfile.polar` shadow fields | explicit adapter mapping or removal of shadow fields | eliminate SSOT drift and false restore claims | ownership tests + migration tests |
| Managed backup file ownership and cleanup scope | wildcard file classification in `DownloadsProfileBackupSink` | manifest-based file ownership and app-id namespaced managed scope | prevent deletion of user archives and cross-build files | cleanup ownership tests |
| System backup policy ownership | implicit manifest/rules behavior | explicit profile-backup policy contract + restore precedence rules | avoid double-restore drift and secret leakage | rule/config tests + restore precedence tests |
| Backup contract compatibility ownership | split `ProfileExport` vs `ProfileBackupDocument` parsers | unified compatibility parser + one bundle contract | ensure user-visible backup files are importable | compatibility import tests |
| Backup write atomicity/ordering ownership | delete-before-write + unordered async sync launch | atomic replace strategy + sequence-aware latest-wins guard | prevent stale/failing writes from regressing backups | failure/ordering tests |

### 2.2B Bypass Removal Plan (Mandatory)

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| `ProfileRepository.scheduleProfileBackupSync(...)` -> profile-only sink payload | metadata-only backup write | route through bundle assembler with domain adapters | Phase 2 |
| `ProfileImportDialog`/`ProfileExportDialog` via `ProfileExportImport` | profile-only JSON bypassing settings restore | route dialogs through bundle parser + restore use-case | Phase 4 |
| UI fallbacks `activeProfile?.id ?: "default"` and dfcards `__default_profile__` | ad-hoc ID aliases | `ProfileIdResolver.canonicalOrDefault(...)` | Phase 1 |
| `ProfileSelectionContent` skip path with null active profile | allows flow continuation without active-profile repair | remove skip or force deterministic fallback activation | Phase 1 |
| `FlightDataManager.loadVisibleModes(null)` no-op | stale mode visibility across profile transitions | load from canonical fallback profile ID instead of returning early | Phase 1 |
| `MainActivity.applyUserStatusBarStyle(null)` hard transparent fallback | bypasses profile-keyed style fallback policy | resolve null through canonical fallback profile policy | Phase 1 |
| `ProfileRepository.ensureBootstrapProfile(...)` empty-only default insertion | non-empty snapshots can omit canonical default forever | enforce canonical-default presence repair on every successful hydrate/mutation | Phase 1 |
| `ProfileRepository.hydrateFromSnapshot(parseFailed)` still syncs backup | cold-start parse failure can wipe managed backups | skip/guard backup sync during parse-failed degraded state | Phase 2 |
| `DownloadsProfileBackupSink.cleanupStaleManagedFiles(...)` broad wildcard cleanup | deletes user archived `profile_*.json` files in shared folder | manifest-owned cleanup only (or move managed files under dedicated app subdir) | Phase 2 |
| Shared backup folder across package variants | release/debug can clean each other's managed files | app-id/build-scope managed namespace + ownership manifest | Phase 2 |
| Manifest/system backup policy implicit | Auto Backup/device transfer may restore overlapping profile state | explicit include/exclude + restore-precedence contract | Phase 2 |
| `ProfileBackupSink` output vs dialog import schema mismatch | user-managed backup files cannot be imported via current dialog path | compatibility parser for legacy `ProfileExport` + `ProfileBackupDocument` + bundle V1 | Phase 4 |
| Startup skip action no-op in primary profile-selection path | user can tap skip but app state remains unresolved | remove skip action or replace with explicit deterministic recovery action | Phase 1/6 |
| `ProfileSettingsScreen` immediate pop on save/delete | hides repository failures and implies false success | await mutation result and only navigate on success | Phase 6 |

### 2.2C Canonical Profile Identity Contract (Mandatory)

- Canonical default profile ID: `default-profile`.
- Legacy aliases to support migration reads only: `default`, `__default_profile__`.
- New writes must never persist under legacy aliases.
- Startup and all profile-scoped settings paths must resolve through one shared normalization helper.

### 2.2D Backup and Storage Policy Contract (Mandatory)

- User-visible manual bundle location remains `Download/XCPro/profiles/`.
- Managed app-written files must be constrained to an app-owned namespace/manifest (for example app-id/build-scoped subpath or explicit managed index ownership), and cleanup must only delete files that are explicitly owned by that manifest.
- User archival/history files in the same top-level folder must never be deleted by automated cleanup.
- SAF export/import destination support must be retained or added so users can choose USB/SD/document-provider locations; persisted URI permissions are required for repeated access.
- Secret credentials are excluded from profile bundles.
- Android Auto Backup/device-transfer policy must explicitly declare:
  - what is included/excluded for profile stores and bundle artifacts,
  - precedence when both system restore and manual bundle restore are present,
  - guardrails preventing duplicate/conflicting restores.

### 2.2E Degraded Startup Safe-Mode Contract (Mandatory)

- On profile snapshot parse failure, app must enter a visible degraded mode.
- Degraded mode must block destructive backup sync and present explicit recovery actions:
  - retry parse,
  - restore from bundle file,
  - reset to canonical default profile with user confirmation.
- Recovery outcome must produce explicit diagnostics in UI/logs (success/failure summary).

### 2.3 Time Base

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| Bundle generation timestamp | Wall | metadata only |
| Profile `createdAt`, `lastUsed` | Wall | existing profile model contract |
| Restore operation ordering | N/A | deterministic sequencing |

Explicitly forbidden comparisons:

- Monotonic vs wall
- Replay vs wall

### 2.4 Threading and Cadence

- Dispatcher ownership:
  - `IO`: JSON parsing, MediaStore/file operations, preference snapshots/apply.
  - `Main`: UI feedback only.
  - `Default`: optional checksums/diffing if introduced.
- Primary cadence/gating sensor:
  - Event-driven sync from profile/settings mutation with coalescing guard.
- Hot-path latency budget:
  - Backup sync off-main; `< 200 ms p95` for typical payload.

### 2.5 Replay Determinism

- Deterministic for same input: Yes
- Randomness used: No
- Replay/live divergence rules:
  - Bundle/profile changes must not alter replay clocks or replay data-path behavior.

### 2.6 Enforcement Coverage (Mandatory)

| Risk | Rule Reference | Guard Type (lint/enforceRules/test/review) | File/Test |
|---|---|---|---|
| Profile key drift (`default`/`__default_profile__`) | SSOT consistency | unit + integration tests | new `ProfileIdResolver` tests + startup/profile flow tests |
| Theme flow mismatch due wrong prefs observer | state ownership consistency | unit tests | look-and-feel/theme repository tests |
| Missing bundle sections | correctness | unit + integration tests | bundle schema tests |
| Partial restore mixes old/new state | determinism/state safety | failure-mode integration tests | restore transactional tests |
| Profile/global migration regressions | architecture + UX | unit + instrumentation tests | migration + profile-switch tests |
| Package sandbox confusion | app identity stability | review + docs gate | troubleshooting docs + build config checks |

### 2.7 Visual UX SLO Contract (Mandatory for map/overlay/replay interaction changes)

| Outcome | SLO ID | Baseline | Target | Validation/Test | Phase Gate |
|---|---|---|---|---|---|
| Widget drag/update remains responsive after profile-scoping migration | `MS-UX-02` | current baseline in `docs/MAPSCREEN/02...` | no regression | map widget interaction tests + package evidence | Phase 5 |
| No recomposition-driven sync loops from profile restore wiring | `MS-ENG-06` | current baseline | no regression | lifecycle/sync assertions + QA traces | Phase 5 |

## 3) Data Flow (Before -> After)

Before:

`ProfileRepository -> ProfileBackupSink -> profile-only files`

`ProfileExportImport dialog path -> profile-only import with new IDs`

`Feature UIs -> ad-hoc profile fallback IDs (default / __default_profile__)`

After:

`ProfileRepository + ProfileIdResolver + Settings Snapshot Adapters -> ProfileBundleAssembler -> ProfileBundleStore (Download/XCPro/profiles)`

Restore:

`Bundle file -> Bundle parser/validator -> Restore use-case -> ordered adapter apply -> repositories/stores -> ProfileRepository active profile`

## 4) Implementation Phases

### Phase 0 - Baseline and Contract Freeze

- Goal:
  - Freeze Tier A/Tier B scope, include/exclude matrix, and schema envelope before code migration.
- Files to change:
  - `docs/PROFILES/CHANGE_PLAN_PROFILE_FULL_SETTINGS_BUNDLE_2026-03-07.md`
  - `docs/PROFILES/PROFILE_STORAGE_AND_SETTINGS_SCOPE.md`
- Tests to add/update:
  - Baseline tests documenting current behavior gaps.
  - Settings inventory closure test/spec to assert every persistent store is classified (Tier A/Tier B/Exclude) with owner.
- Exit criteria:
  - Approved domain matrix and canonical profile-ID contract.
  - Explicit include/exclude decisions for non-profile stores and secrets.

### Phase 1 - Canonical Profile Identity and Startup Hardening

- Goal:
  - Remove default profile ID drift and stabilize startup namespace behavior.
- Files to change:
  - `feature/profile/...` (shared ID normalization helper)
  - `feature/profile/.../ProfileRepository.kt` (canonical default-presence invariant repair)
  - `feature/profile/.../ui/ProfileSelectionContent.kt`
  - `app/.../MainActivityScreen.kt`
  - `app/.../MainActivity.kt`
  - `app/.../AppNavGraph.kt`
  - `feature/map/.../map/FlightDataManager.kt`
  - `feature/map/.../flightdata/FlightMgmtPreferencesViewModel.kt`
  - `feature/map/.../MapScreenRootStateBindings.kt`
  - `feature/map/.../Theme.kt`
  - `feature/map/.../ColorsScreen.kt`
  - `feature/map/.../LookAndFeelScreen.kt`
  - `feature/map/.../FlightDataMgmt.kt`
  - `dfcards-library/.../FlightVisibility.kt`, `FlightCardStateMapper.kt` (migration-aware normalization)
- Tests to add/update:
  - ID alias normalization tests (`default`, `__default_profile__` -> `default-profile`).
  - Startup hydration tests ensuring active profile remains canonical and non-null.
  - Startup hydration tests ensuring canonical default profile entry exists even when non-empty legacy snapshots omit it.
  - Selection-flow tests: no path continues with `profiles.isNotEmpty()` and null active profile.
  - Selection-flow tests: skip action is either removed or mapped to deterministic recovery behavior (never no-op).
  - Map visibility tests: null profile ID resolves deterministic fallback visibility, not stale state.
  - Status-bar fallback tests: null profile resolves through canonical fallback policy.
  - Flight-mode preference tests: no transient wrong-profile-key mode apply from `"default"` initialization.
- Exit criteria:
  - No new reads/writes to legacy default aliases outside explicit migration adapters.
  - Canonical default profile entry exists and is protected in all successful hydrated/mutated states.
  - No runtime path continues with non-empty profile list and null active profile.

### Phase 2 - Bundle Schema V1 and Storage Engine

- Goal:
  - Introduce versioned bundle envelope with section manifest, deterministic IO, explicit backup ownership policy, and atomic latest-wins writes.
- Files to change:
  - `feature/profile/.../ProfileBackupSink.kt` (or replacement bundle store)
  - `feature/profile/.../ProfileRepository.kt` (degraded parse-failure backup-sync guard)
  - `app/src/main/AndroidManifest.xml` (verify/adjust backup contract references if needed)
  - `app/src/main/res/xml/backup_rules.xml`
  - `app/src/main/res/xml/data_extraction_rules.xml`
  - new bundle models (`ProfileBundleV1`, section metadata, compatibility parser)
- Tests to add/update:
  - schema round-trip tests
  - malformed/unsupported version rejection
  - managed-file cleanup tests
  - parse-failure degraded-state tests: no destructive cleanup/write when snapshot parse fails on cold start
  - shared-folder safety tests: user-managed archival files are preserved
  - cleanup ownership tests across debug/release namespace coexistence
  - backup-policy tests asserting expected include/exclude coverage and restore precedence contract
  - atomic-write failure tests (write failure cannot remove previous last-good managed files)
  - sync-ordering tests ensuring latest snapshot wins under async scheduling/reordering
- Exit criteria:
  - Stable V1 bundle can be written/read with explicit section versioning.
  - Backup sync cleanup cannot delete non-managed/user archival files in shared profile folder.
  - Managed cleanup does not remove files owned by a different app-id/build namespace.
  - Auto Backup/device transfer policy is explicit and consistent with manual bundle restore.
  - Managed writes are atomic from user perspective (no delete-before-success data-loss window).
  - Latest profile snapshot deterministically wins even under asynchronous sync scheduling.

### Phase 3 - Snapshot Adapter Coverage (Export)

- Goal:
  - Populate Tier A sections and define Tier B optional sections.
- Files to change:
  - `dfcards-library/.../FlightProfileStore.kt` hydration coverage for visibility-only profile states
  - `dfcards-library/.../CardPreferences.kt` adapter surface
  - `feature/map/.../FlightMgmtPreferencesRepository.kt` adapter surface
  - `feature/map/.../lookandfeel/LookAndFeelPreferences.kt` adapter surface
  - `feature/map/.../ThemePreferencesRepository.kt` adapter surface
  - `feature/map/.../map/widgets/MapWidgetLayoutRepository.kt` adapter surface
  - `feature/variometer/.../VariometerWidgetRepository.kt` adapter surface
  - `feature/map/.../glider/GliderRepository.kt` adapter surface
  - `feature/map/.../vario/LevoVarioPreferencesRepository.kt` adapter surface
  - `feature/map/.../thermalling/ThermallingModePreferencesRepository.kt` adapter surface
  - `feature/map/.../ogn/OgnTrafficPreferencesRepository.kt` adapter surface
  - `feature/map/.../ogn/OgnTrailSelectionPreferencesRepository.kt` adapter surface
  - `feature/map/.../adsb/AdsbTrafficPreferencesRepository.kt` adapter surface
  - `feature/map/.../weather/rain/WeatherOverlayPreferencesRepository.kt` adapter surface
  - `feature/map/.../forecast/ForecastPreferencesRepository.kt` adapter surface
  - `feature/map/.../weather/wind/data/WindOverrideRepository.kt` adapter surface
  - Tier B adapter candidates: units/orientation/qnh/trail/home waypoint/nav drawer
  - `feature/profile/...` bundle assembler wiring
- Tests to add/update:
  - per-adapter extraction tests
  - bundle section presence tests
  - theme precedence tests (LookAndFeel vs Theme repository key ownership)
  - dfcards visibility hydration tests for profiles with visibility keys but no template/card mappings
- Exit criteria:
  - Tier A bundle contains all required domains including glider polar, card/layout settings, vario/thermalling/traffic/weather/forecast/wind settings.
  - Profile visibility state restores consistently for all persisted visibility-bearing profiles.

### Phase 4 - Restore Pipeline and Import/Export Unification

- Goal:
  - Restore bundle content deterministically and retire profile-only import/export bypass.
  - Make one-file managed import the recommended user path for USB/manual workflows.
- Files to change:
  - new restore orchestrator in `feature/profile`
  - `ProfileExportImport.kt` routed to bundle parser/restore use-case (or deprecated)
  - UI dialogs/flows consuming unified result contract
  - `ProfileBackupSink.kt` emits managed `*_bundle_latest.json` as canonical one-file import target
- Tests to add/update:
  - full round-trip restore tests
  - failure-mode tests (invalid sections, unknown versions, partial payload)
  - compatibility import tests for legacy `ProfileExport` JSON and existing managed `profile_*.json` / `profiles_index.json` documents
  - compatibility tests for managed `*_bundle_latest.json` import success
  - index-only rejection message tests recommending `*_bundle_latest.json`
  - conflict policy tests (`replace`, `import as new id`, `keep current active`)
- Exit criteria:
  - User import path uses one bundle contract and returns deterministic apply summary.
  - Existing user-visible backup artifacts in `Download/XCPro/profiles/` are importable through the unified path.
  - Managed backup sync writes `*_bundle_latest.json` and UI/import guidance recommends it as first-choice import artifact.

### Phase 5 - Runtime Profile-Scoping Migration for Global Domains

- Goal:
  - Prevent cross-profile bleed for domains currently stored as global keys.
- Files to change:
  - `feature/profile/.../ProfileRepository.kt` delete-cascade orchestration
  - `feature/profile/.../ProfileModels.kt` plus adapters (resolve/remove shadow preferences/polar ownership drift)
  - map widget/variometer/glider repositories first
  - optional Tier B migrations (units/orientation/qnh/trail/home waypoint/nav drawer)
  - active-profile switch hydration wiring
  - `feature/map/.../screens/navdrawer/Profiles.kt` active badge source-of-truth correction
- Tests to add/update:
  - global-to-profile key migration tests
  - profile switch isolation tests
  - profile metadata/runtime ownership tests proving no shadow-profile fields silently diverge from runtime SSOT
  - map interaction regression tests for widget drag/resize
  - delete-cascade tests ensuring profile-keyed entries are removed for deleted profile ID
  - profiles list UI tests ensuring ACTIVE badge derives from repository active profile ID
- Exit criteria:
  - Targeted domains are profile-scoped at runtime with legacy fallback migration.
  - Deleting profile leaves no orphaned profile-keyed state in supported stores.

### Phase 6 - UX, Ops, and Documentation Hardening

- Goal:
  - Make behavior diagnosable and user recovery straightforward.
- Files to change:
  - `feature/profile/.../ProfileSettingsScreen.kt` mutation-result aware navigation/errors
  - profile startup safe-mode/recovery UX surfaces
  - profile settings/selection UX feedback
  - troubleshooting docs in `docs/PROFILES/`
  - structured logs/metrics for export/import outcomes
- Tests to add/update:
  - instrumentation tests for SAF import/export flow
  - UX-state tests for progress/success/failure
  - profile save/delete failure-visibility tests (no optimistic pop-back on failure)
  - degraded-startup safe-mode tests (parse failure -> recovery actions -> deterministic end state)
  - startup error-screen interaction tests: retry/restore/reset actions are visible and functional (no dead-end screen)
- Exit criteria:
  - End-to-end behavior is test-covered and operationally diagnosable.
  - Parse-failure startup surfaces clear recovery actions and avoids user-visible data-loss loops.
  - No startup profile-selection action path is a no-op.

## 5) Test Plan

- Unit tests:
  - `ProfileIdResolver` alias normalization and canonical output.
  - Look-and-feel/theme flow correctness with separate prefs stores.
  - bundle schema serializer/deserializer and section validation.
  - adapter extraction/apply tests for each Tier A domain.
  - adapter extraction/apply tests for vario/thermalling/OGN/ADS-B/weather/forecast/wind domains.
  - canonical-default-presence invariant tests for non-empty legacy snapshots/import payloads.
  - parse-failure degraded backup tests: no empty-index/cleanup wipe on cold-start parse errors.
  - backup shared-folder safety tests: user-managed archival files preserved.
  - managed cleanup ownership tests: build/app namespace isolation.
  - backup-policy config tests for `backup_rules.xml` and `data_extraction_rules.xml`.
  - backup compatibility parser tests (`ProfileExport`, managed `ProfileBackupDocument`/index, bundle V1 envelope).
  - backup atomicity tests proving prior backup artifacts survive replacement failures.
  - backup sequencing tests proving stale async snapshots cannot overwrite newer snapshot outcomes.
  - profile metadata/runtime ownership tests (`UserProfile.preferences/polar` vs runtime repos).
  - no-null-active invariant tests for selection/startup transitions.
  - delete-cascade cleanup tests for profile-keyed stores.
  - dfcards visibility hydration tests for visibility-only profiles.
- Replay/regression tests:
  - smoke check that replay pipeline behavior is unchanged.
- UI/instrumentation tests:
  - export/import through dialogs with resulting state verification.
  - startup profile hydration does not fall back to non-canonical profile IDs.
  - degraded startup safe-mode + recovery flow verification.
  - startup profile-selection skip/recovery UX verification (no no-op path).
- Degraded/failure-mode tests:
  - invalid JSON, unsupported schema versions, missing sections, storage I/O failures.
- Boundary tests for bypass removal:
  - import/export UI path uses bundle restore use-case.
  - backup sync path produces full section bundle, not profile-only payload.
  - status-bar fallback and map visible-mode fallback paths use canonical profile identity resolver.

Required checks:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Optional when relevant:

```bash
./gradlew connectedDebugAndroidTest
```

When Phase 5 touches map widget runtime behavior, attach MapScreen SLO evidence per:

- `docs/MAPSCREEN/02_BASELINE_PROFILING_AND_SLO_MATRIX_2026-03-05.md`
- `docs/MAPSCREEN/04_TEST_VALIDATION_AND_ROLLBACK_2026-03-05.md`

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| Canonical ID migration misses a callsite | settings appear missing under new namespace | central resolver + grep gate + migration tests | XCPro Team |
| Null-active profile paths continue into runtime | stale/wrong settings namespace and confusing UX | remove skip bypass + enforce active-profile invariant in startup/selection flows | XCPro Team |
| Canonical default profile entry absent in non-empty snapshots | cannot guarantee protected fallback profile semantics | enforce default-presence invariant repair on hydrate/mutate + invariant tests | XCPro Team |
| Theme source overlap remains ambiguous | user sees stale/default theme selections | explicit precedence contract + dedicated repository tests | XCPro Team |
| Parse-failure backup sync wipes shared-folder backups | backup data loss during degraded read scenarios | skip backup sync on parse-failed state + non-destructive cleanup policy | XCPro Team |
| Shared-folder cleanup deletes user archives | manual USB/history backups disappear unexpectedly | manifest-owned cleanup only or dedicated managed subpath + tests | XCPro Team |
| Managed cleanup collides across package variants/builds | one installed variant can remove another variant's managed backup files | app-id/build-scoped managed namespace + ownership manifest + coexistence tests | XCPro Team |
| Auto Backup/device transfer conflicts with manual bundle restore | duplicate or conflicting profile restore state | explicit backup rules contract + restore precedence policy + tests | XCPro Team |
| Secret material accidentally included in profile bundle | credential leakage and security incident | explicit secrets exclude matrix + schema validation + tests | XCPro Team |
| Backup contracts remain split/incompatible | user backup files cannot be restored via in-app import | compatibility parser + unified bundle contract + migration tests | XCPro Team |
| Delete-before-write backup replacement fails mid-write | previous backup artifact lost during failed write | atomic replace strategy + write-failure tests | XCPro Team |
| Async backup sync ordering allows stale snapshot to win | backup content regresses to older state after rapid mutations | sequence-aware latest-wins guard + ordering tests | XCPro Team |
| Startup storage error surface remains a dead-end | user cannot self-recover from parse/read failures | explicit retry/restore/reset actions in startup UI + instrumentation tests | XCPro Team |
| Profile metadata fields drift from runtime SSOT | user edits/exports profile data that does not drive runtime behavior | explicit owner mapping/removal and migration docs/tests | XCPro Team |
| Partial restore applies only some domains | user distrust and hidden regressions | ordered apply with summary + rollback-on-failure policy | XCPro Team |
| Runtime profile-scoping migration regresses UX | map/widget regressions | phased rollout + SLO validation + feature flags | XCPro Team |
| Debug vs release sandbox confusion persists | perceived data loss when switching builds | troubleshooting docs + explicit package identity messaging | XCPro Team |

## 7) Acceptance Gates

- No violations of `ARCHITECTURE.md` and `CODING_RULES.md`.
- Canonical default profile ID is enforced (`default-profile`) with migration compatibility for legacy aliases.
- Canonical default profile entry exists (and is protected) in all successful hydrated/mutated states.
- No runtime path continues with `profiles.isNotEmpty()` and `activeProfile == null`.
- Tier A bundle restores full profile state, including glider polar and card/layout/widget domains.
- Tier A bundle restores full profile state, including glider polar, vario settings, thermalling settings, OGN/ADS-B settings, weather/forecast settings, and wind override settings.
- Import/export user path is unified to one bundle contract.
- Profile delete flow performs supported downstream profile-key cleanup.
- Mode visibility/state hydration remains deterministic when active profile is temporarily unresolved.
- Backup sync cannot delete user archival files in `Download/XCPro/profiles/`.
- Backup sync cleanup is app/build namespace aware and cannot delete files owned by another variant.
- Parse-failed degraded hydration cannot trigger destructive empty-sync cleanup of managed backup files.
- Parse-failed startup enters explicit safe mode with user-visible recovery actions.
- Startup recovery screens are actionable (retry/restore/reset), not message-only dead ends.
- Existing `Download/XCPro/profiles/` managed artifacts are importable through the unified import path.
- Managed backup flow provides a one-file `*_bundle_latest.json` artifact and this is the recommended import path in UI/error guidance.
- Backup writes are atomic and latest-wins deterministic under rapid successive mutations.
- Profile-selection skip/recovery actions cannot be no-op.
- Auto Backup/device-transfer behavior is explicitly defined and validated against manual bundle restore policy.
- Profile metadata fields either map to runtime SSOT or are removed/migrated to avoid shadow-state drift.
- Bundle files remain projection artifacts, not runtime SSOT.
- Replay behavior remains deterministic.
- For map-widget migration phases, impacted SLOs are non-regressed (or an approved deviation is recorded).

## 8) Rollback Plan

- What can be reverted independently:
  - Bundle schema/assembler implementation.
  - UI import/export unification while retaining existing sync writer.
  - Runtime profile-scoping migration for global stores (feature-flag rollback).
- Recovery steps if regression is detected:
  1. Disable restore entrypoint and keep profile core persistence unchanged.
  2. Re-enable legacy import/export path temporarily if needed.
  3. Roll back most recent adapter or migration layer causing inconsistency.
  4. Re-run required Gradle gates and targeted profile restore tests before re-enable.
