# Profiles Improvement Workboard

Created: 2026-02-16  
Status: Active  
Owner: XCPro Team

## Purpose

Track profile-system improvements in one place so work can continue safely over time.

## Scope

In scope:

1. User profile lifecycle (create/select/update/delete).
2. Profile-driven card/template/mode visibility behavior.
3. Profile-scoped look and feel and theme settings.
4. Profile UX consistency across map, drawer, and settings screens.

Out of scope for this board (tracked separately):

1. Import/export improvements.

## How To Use This File

1. Add new items to the backlog table with an ID, status, and acceptance criteria.
2. Move status through `Todo -> In Progress -> Done`.
3. When done, add a short result note and date in Change Log.
4. If a rule exception is required, add it to `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md`.

## Current Snapshot

| Area | State |
|---|---|
| Startup profile gating | Working |
| Profile create/select/delete persistence | Working |
| Profile-scoped flight cards | Working |
| Profile-scoped theme/look and feel | Working |
| Selection screen edit action | Broken |
| Profiles screen ACTIVE badge source | Inconsistent |
| Quick switcher active marker | Poor/unclear |
| Settings save/delete result handling | Inconsistent |
| Profile settings route resilience during hydration | Risky |
| Profile delete cleanup across downstream stores | Missing |
| Null-active fallback consistency at runtime | Inconsistent |
| UI preference access layering | Inconsistent |
| Color theme settings ownership | Split |
| Corrupt profile payload recovery behavior | Risky |
| Repository-level profile input validation | Incomplete |
| Active-profile delete fallback policy | Implicit |
| Set-active contract clarity (select vs upsert) | Ambiguous |
| dfcards clear-profile in-memory cleanup completeness | Incomplete |
| Profile delete confirmation UX | Missing |
| Profile list/active-id persistence atomicity | Risky |
| Profile UI active-state projection freshness | Risky |
| Repository mutation serialization under concurrency | Missing |
| Repository mutation rollback/commit semantics | Missing |
| Active-profile invariant repair inside mutation paths | Incomplete |
| Selection-screen action gating during loading | Risky |
| Profile timestamp clock discipline | Inconsistent |
| dfcards profile visibility hydration across profile switches | Risky |
| Profile visibility switching test coverage | Missing |
| Profile settings editable-state freshness | Risky |
| Manage Account profile edit entrypoint | Broken |

## Prioritized Backlog

| ID | Priority | Status | Problem | Proposed Fix | Acceptance Criteria |
|---|---|---|---|---|---|
| PROFILES-20260217-01 | Critical | Todo | No guaranteed default profile on first run or invalid active ID. | Add repository reconciliation to seed default profile and enforce active fallback. | App always has one valid active profile after hydration. |
| PROFILES-20260217-02 | Critical | Todo | Fallback profile IDs are inconsistent (`"default"` vs `"__default_profile__"`). | Standardize fallback policy and migrate old fallback-keyed data if needed. | Theme/cards/look-and-feel/flight-mode prefs resolve to one fallback profile identity. |
| PROFILES-20260217-03 | High | Todo | Color theme observe path in look-and-feel reads wrong prefs store. | Fix `observeColorThemeId` to observe `colorPrefs` keys. | Look-and-feel color theme reacts immediately to theme changes. |
| PROFILES-20260217-04 | High | Todo | Default profile can be removed by current generic delete flow. | Add repository and UI guard to block default-profile deletion. | Delete actions are disabled/rejected for default profile. |
| PROFILES-20260217-05 | High | Todo | Startup gating is UI-driven and can transiently run without reconciled profile state. | Add explicit profile hydration/reconciled-ready state before startup gate decision. | No profile-selection flicker/race on cold start with existing valid data. |
| PROFILES-20260217-09 | High | Todo | Profile settings route can pop immediately on transient null profile lookup. | Replace immediate pop with hydration-aware loading/lookup guard; only exit on confirmed missing profile after hydration. | Opening `profile_settings/{id}` is stable and does not auto-close during normal hydration. |
| PROFILES-20260217-10 | High | Todo | Deleting a profile does not clear profile-scoped data in downstream stores. | Add delete-cascade cleanup service/use-case for cards, theme, look-and-feel, and flight-mgmt preferences. | Deleted profile leaves no orphaned `profile_<id>_*` preference keys in supported stores. |
| PROFILES-20260217-14 | High | Todo | "Skip for now" can continue app flow with null active profile. | Replace skip behavior with deterministic fallback (activate default profile) or remove skip when active profile is required. | No runtime path continues with `profiles.isNotEmpty()` and `activeProfile == null`. |
| PROFILES-20260216-01 | High | Todo | Edit button in profile selection does nothing. | Wire edit action to navigate to settings or inline edit dialog. | Tapping Edit opens real edit flow for selected profile. |
| PROFILES-20260216-02 | High | Todo | Profiles screen ACTIVE badge checks `UserProfile.isActive` instead of repository active ID. | Derive active row from `uiState.activeProfile?.id == profile.id`. | Active badge always matches selected profile after app restart and profile switch. |
| PROFILES-20260216-03 | Medium | Todo | Quick switcher current profile marker renders blank text. | Replace blank text with visible check/label. | Current profile is clearly identifiable in switcher menu. |
| PROFILES-20260216-04 | Medium | Todo | Settings screen navigates back before save/delete success/failure feedback. | Await operation result before pop and surface errors. | Failed operation keeps user on screen with clear error message. |
| PROFILES-20260217-11 | Medium | Todo | Status bar fallback ignores stored `"default"` profile style when active profile is null. | Route null-active fallback through the canonical fallback profile ID policy. | Status bar style behavior matches look-and-feel fallback policy in null-active scenarios. |
| PROFILES-20260217-12 | Medium | Todo | Flight-mode visibility refresh is skipped when active profile is null, leaving stale in-memory mode visibility. | Use canonical fallback profile ID instead of no-op in `loadVisibleModes`. | Mode visibility state is deterministic even when active profile is temporarily null. |
| PROFILES-20260217-15 | Medium | Todo | Flight-data mode preferences can transiently hydrate from `"default"` before active-profile-specific mode is applied. | Make `lastFlightMode` hydration keyed to resolved active profile before applying `setFlightMode`, or gate application until profile ID is bound. | Profile switch does not momentarily apply mode from wrong profile key. |
| PROFILES-20260217-13 | Medium | Todo | UI composable creates `CardPreferences(context)` directly, bypassing DI/use-case boundary. | Move profile quick-actions mode visibility read behind injected use-case/repository API. | No direct preference-wrapper construction inside profile UI composables. |
| PROFILES-20260217-16 | Medium | Todo | Profile DataStore flows have no read-error recovery path. | Add DataStore flow `catch` handling and repository-safe fallback behavior for I/O read failures. | Profile streams stay alive and recover deterministically on recoverable storage read errors. |
| PROFILES-20260217-17 | Medium | Todo | `updateProfile` reports success even when target profile ID is missing. | Enforce not-found check and return failure result when no profile row is updated. | Edit/save surfaces an explicit error for stale/missing profile IDs. |
| PROFILES-20260217-18 | Low | Todo | Profile settings save allows empty profile names. | Add validation on edit save and repository/update guards for blank names. | Empty-name profile updates are rejected with user-visible feedback. |
| PROFILES-20260217-19 | Low | Todo | Profile helper APIs/composables appear unused, causing drift. | Remove dead code or rewire usage explicitly and add ownership notes/tests. | No unowned profile helper paths remain. |
| PROFILES-20260217-20 | Medium | Todo | Color-theme preference ownership is split across `ThemePreferencesRepository` and `LookAndFeelPreferences`. | Consolidate color-theme key ownership under one repository/use-case and make other callers delegate. | Exactly one owner reads/writes `profile_<id>_color_theme`; observers stay consistent across screens. |
| PROFILES-20260217-21 | High | Todo | Corrupt profile JSON falls back to empty list with no non-destructive recovery strategy. | Add parse-failure quarantine/recovery policy (retain last-known-good state or explicit repair flow) before any destructive persistence. | Malformed stored payload does not silently erase profile history on subsequent writes. |
| PROFILES-20260217-22 | Medium | Todo | `createProfile` relies on UI validation and does not enforce repository-level input invariants. | Validate/normalize create request fields in repository (`trim`, non-blank name) and fail invalid requests. | Blank/invalid create requests fail regardless of caller path. |
| PROFILES-20260217-23 | Medium | Todo | Active-profile delete fallback uses first remaining row, not an explicit canonical policy. | Define deterministic fallback policy (default profile first; otherwise explicit ordered rule) and enforce in repository. | Deleting active profile resolves fallback predictably and independent of list ordering. |
| PROFILES-20260217-24 | Medium | Todo | `setActiveProfile` implicitly inserts unknown profiles through a select path. | Split semantics: `setActiveProfile` should fail on unknown ID; keep profile creation/upsert in explicit APIs only. | Selecting a missing profile returns explicit failure and does not mutate profile list. |
| PROFILES-20260217-25 | Medium | Todo | dfcards `clearProfile` helper leaves `profileModeVisibilities` in-memory rows intact. | Extend clear-profile handler to remove visibility map entry with cards/templates and persisted keys. | Clearing profile removes all in-memory and persisted profile-scoped card state. |
| PROFILES-20260217-26 | Low | Todo | Profile delete actions run without confirmation in selection/settings UIs. | Add confirmation dialog and invariant-aware messaging before delete dispatch. | Delete requires explicit confirmation and shows guarded messaging for blocked deletions. |
| PROFILES-20260217-27 | High | Todo | `profiles_json` and `active_profile_id` are persisted via separate edits, not one atomic profile-state write. | Add storage API for combined write (profiles + active ID) and route repository mutations through atomic persistence path. | On-disk profile list and active ID are persisted as one consistent state transition. |
| PROFILES-20260217-28 | Medium | Todo | `ProfileViewModel` profiles collector can retain stale active profile (`active ?: previous`) when repository emits null. | Replace stale fallback projection with deterministic source-of-truth mapping (active flow or combined state flow). | UI active profile always reflects current repository state without stale carryover. |
| PROFILES-20260217-29 | Medium | Todo | Repository mutations are unsynchronized and can race under concurrent operations. | Serialize profile mutations with mutex/single-writer strategy and test concurrent create/update/delete behavior. | Concurrent operations preserve profile/state consistency with no lost updates. |
| PROFILES-20260217-30 | High | Todo | Repository mutation methods update in-memory state before persistence and can fail without reverting runtime state. | Add transactional mutation boundary (persist-first commit or explicit rollback snapshot) for create/select/update/delete paths. | Failed profile mutations leave runtime state unchanged and storage-aligned. |
| PROFILES-20260217-31 | High | Todo | Create/delete mutation paths do not always repair active-profile invariant when active state is already null/invalid. | Enforce post-mutation reconciliation so non-empty profile list always resolves a valid active profile. | After mutation paths, `profiles.isNotEmpty()` always implies valid non-null active profile. |
| PROFILES-20260217-32 | Medium | Todo | Selection-screen Continue/Skip actions are not gated by loading state and can execute during in-flight profile mutations. | Disable/gate selection exit actions while loading and only allow continuation after active-profile commit. | Selection flow cannot proceed with stale/null active state while `isLoading` is true. |
| PROFILES-20260217-33 | Low | Todo | `UserProfile` timestamp defaults use direct `System.currentTimeMillis()` calls. | Move timestamp assignment to repository policy with injected clock and remove direct model time defaults. | Profile metadata timestamps are deterministic and clock-injected in tests/runtime. |
| PROFILES-20260217-34 | High | Todo | dfcards visibility hydration excludes profiles that only have persisted visibility keys, so switching those profiles can seed defaults and diverge map vs flight-data visibility state. | Hydrate/lazy-load visibility state from persisted visibility keys for selected profiles before default seeding. | Switching to any profile preserves persisted visibility values and map + flight-data screens stay aligned. |
| PROFILES-20260217-35 | Medium | Todo | No unit coverage for visibility hydration/persistence behavior across profile switches in dfcards. | Add tests for persisted visibilities with and without template/card mappings and profile-switch transitions. | Regression tests fail if profile visibility resets to defaults incorrectly. |
| PROFILES-20260217-36 | Low | Todo | `ProfileSettingsScreen` editable state is remembered without source-profile key and can save stale profile data after source changes. | Key/reset local editable state from the latest source profile identity/snapshot. | Settings editor always reflects current profile snapshot and avoids stale-save drift. |
| PROFILES-20260217-37 | Medium | Todo | Manage Account screen exposes an "Edit Profile" action with TODO/no-op behavior. | Wire action to existing profile settings/selection flow and keep navigation behavior consistent with other profile entrypoints. | Tapping "Edit Profile" from Manage Account opens a real profile-management destination. |
| PROFILES-20260216-05 | Medium | Todo | `UserProfile.isActive` field is redundant and drifts from true active source. | Remove field or enforce sync strategy from repository. | No UI or logic depends on stale `isActive` state. |
| PROFILES-20260216-06 | Medium | Todo | No canonical profile architecture reference inside `docs/03_Features`. | Keep architecture doc current with each profile-flow change. | `Profiles_Current_Architecture.md` updated in PRs that change profile flow. |
| PROFILES-20260217-06 | Medium | Todo | `UserProfile.preferences` fields are mostly disconnected from runtime setting repositories. | Decide ownership boundary and either wire or remove dead profile-preference fields. | Profile settings screen controls only fields that have real runtime effect. |
| PROFILES-20260217-07 | Medium | Todo | `copyFromProfile` request field is modeled but unused. | Implement copy behavior or remove field from API/model. | No dead copy contract remains in creation flow. |
| PROFILES-20260217-08 | Low | Todo | `lastUsed` timestamp is never updated on profile selection. | Update `lastUsed` on successful active-profile changes. | Recently used ordering/metadata can be trusted. |

## Execution Phases

### Phase 1: Correctness Fixes

Target items:

1. PROFILES-20260217-01
2. PROFILES-20260217-02
3. PROFILES-20260217-03
4. PROFILES-20260217-04
5. PROFILES-20260217-05
6. PROFILES-20260217-09
7. PROFILES-20260217-10
8. PROFILES-20260217-11
9. PROFILES-20260217-12
10. PROFILES-20260217-14
11. PROFILES-20260216-01
12. PROFILES-20260216-02
13. PROFILES-20260216-03
14. PROFILES-20260216-04
15. PROFILES-20260217-21
16. PROFILES-20260217-23
17. PROFILES-20260217-25
18. PROFILES-20260217-26
19. PROFILES-20260217-27
20. PROFILES-20260217-28
21. PROFILES-20260217-29
22. PROFILES-20260217-30
23. PROFILES-20260217-31
24. PROFILES-20260217-32
25. PROFILES-20260217-34
26. PROFILES-20260217-35

Exit criteria:

1. Behavior is corrected in UI and state flows.
2. Relevant unit/UI tests added or updated.
3. No architecture rule violations.

### Phase 2: Data Model Cleanup

Target items:

1. PROFILES-20260216-05
2. PROFILES-20260217-06
3. PROFILES-20260217-07
4. PROFILES-20260217-08
5. PROFILES-20260217-13
6. PROFILES-20260217-15
7. PROFILES-20260217-16
8. PROFILES-20260217-17
9. PROFILES-20260217-18
10. PROFILES-20260217-19
11. PROFILES-20260217-20
12. PROFILES-20260217-22
13. PROFILES-20260217-24
14. PROFILES-20260217-33
15. PROFILES-20260217-36
16. PROFILES-20260217-37

Exit criteria:

1. Single active-state source of truth.
2. No stale active flags in UI.
3. Migration path defined if persistence schema changes.

### Phase 3: Long-Term Modernization

Target references:

1. `PROFILES.md`

Target themes:

1. Welcome default profile.
2. Unified profile bundle persistence.
3. ProfileManager-style orchestration across subsystems.

## Per-Task Template

Copy this block when starting a new item:

```md
### <TASK_ID>
- Status:
- Owner:
- Branch:
- Scope:
- Files:
- Risks:
- Tests:
- Verification:
- Notes:
```

## Verification Checklist (Non-Trivial Changes)

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

When relevant:

```bash
./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"
```

## Decision Log

| Date | Decision | Rationale | Impact |
|---|---|---|---|
| 2026-02-16 | Keep import/export out of this board. | Focus immediate profile-quality fixes. | Separate tracking thread for data portability work. |
| 2026-02-16 | Use active profile ID as the source of truth. | Existing repository already persists this explicitly. | Simplifies UI consistency and testing. |
| 2026-02-17 | Prioritize default-profile reconciliation before UX polish work. | Eliminates startup/profile-key inconsistency cascades. | Stabilizes downstream profile-scoped settings and card hydration. |
| 2026-02-17 | Treat profile deletion as cross-store cleanup, not profile-list-only mutation. | Profile IDs key multiple preference stores beyond `ProfileRepository`. | Prevents orphaned profile data and stale fallback behavior. |
| 2026-02-17 | Do not allow selection-screen skip to leave app in null-active state. | Null-active state causes fallback-key drift and non-deterministic runtime behavior. | Startup/profile selection flow remains deterministic. |
| 2026-02-17 | Treat profile storage read failures as recoverable state, not fatal stream termination. | DataStore reads can fail transiently; profile SSOT must degrade safely. | Improves resilience and preserves predictable startup behavior. |
| 2026-02-17 | Treat profile JSON parse failures as a data-integrity event, not a normal empty-state fallback. | Empty fallback can mask corruption and lead to destructive overwrite on next save. | Requires explicit recovery/quarantine policy before persistence. |
| 2026-02-17 | Consolidate color-theme preference ownership under one repository. | Split ownership already caused observer inconsistency and duplicates policy logic. | Improves SSOT boundaries and reduces cross-screen drift. |
| 2026-02-17 | Keep profile selection semantics explicit: selecting profile must not auto-create it. | Blended select/upsert contract increases accidental state mutation risk. | Clear API contracts and safer profile lifecycle behavior. |
| 2026-02-17 | Treat clear-profile as full state cleanup (cards, templates, visibilities, persisted keys). | Partial in-memory cleanup can leave stale state after delete/clear operations. | Improves deterministic behavior after profile removal. |
| 2026-02-17 | Persist profile list and active ID as one state transition. | Split writes can leave partial on-disk state during failure/interruption windows. | Reduces storage inconsistency and startup reconciliation churn. |
| 2026-02-17 | Serialize repository profile mutations. | Concurrent mutation paths currently rely on unsynchronized read-modify-write flows. | Prevents lost updates and race-induced state drift. |
| 2026-02-17 | Treat profile mutation success/failure as an atomic runtime contract. | Returning failure after mutating in-memory state creates user-visible contradiction and drift from storage truth. | Requires transactional mutation handling (commit/rollback semantics). |
| 2026-02-17 | Block selection-flow exit actions while profile mutation/loading is in-flight. | Continue/skip actions during in-flight selection can commit stale/null active-state decisions. | Selection UX becomes deterministic and invariant-safe. |
| 2026-02-17 | Move profile timestamp stamping to repository clock policy. | Direct model-level wall-time defaults bypass injected clock discipline and hurt test determinism. | Timestamp behavior becomes testable and architecture-consistent. |
| 2026-02-17 | Treat profile-mode visibility as a persisted per-profile SSOT that must hydrate independently from card/template mappings. | Visibility keys can exist without template/card entries; default seeding on switch can otherwise overwrite user intent. | Prevents cross-screen visibility drift and preserves persisted profile visibility behavior. |
| 2026-02-17 | Keep profile settings editable state keyed to source profile identity. | Non-keyed remembered edit state can drift from refreshed source profile data. | Prevents stale-save writes from settings screen. |

## Change Log

| Date | Update |
|---|---|
| 2026-02-16 | Created workboard and seeded prioritized backlog. |
| 2026-02-17 | Added deep-dive findings, critical default-profile tasks, and new phase ordering. |
| 2026-02-17 | Added second-pass findings for settings-route hydration risk, delete cleanup cascade, and null-active runtime consistency gaps. |
| 2026-02-17 | Added additional findings for skip-path null-active bypass and flight-mode hydration ordering race. |
| 2026-02-17 | Added third-pass findings for DataStore read recovery, update not-found handling, edit validation, and dead-code drift cleanup. |
| 2026-02-17 | Added fourth-pass findings for color-theme ownership split, parse-failure integrity risks, create-path validation gaps, and deterministic delete fallback policy. |
| 2026-02-17 | Added fifth-pass findings for set-active contract ambiguity, dfcards clear-profile visibility cleanup, and delete-confirmation UX safeguards. |
| 2026-02-17 | Added sixth-pass findings for persistence atomicity, UI active-state projection freshness, and repository mutation serialization gaps. |
| 2026-02-17 | Added seventh-pass findings for mutation rollback semantics, active-invariant repair after mutation, selection action-gating during loading, and timestamp clock-discipline gaps. |
| 2026-02-17 | Added eighth-pass findings for dfcards visibility hydration/switch consistency, missing visibility-switch tests, profile-settings editable-state freshness, and Manage Account profile-entrypoint TODO gap. |
