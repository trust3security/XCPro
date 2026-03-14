# ProfileRepository Test Suite Split Phased IP

## 0) Metadata

- Title: Split `ProfileRepositoryTest.kt` by repository responsibility without behavior churn
- Owner: XCPro Team
- Date: 2026-03-15
- Issue/PR: RULES-20260306-14 remediation lane
- Status: In progress
- Progress:
  - Phase 1 complete: generic repository setup now lives in `ProfileRepositoryTestSupport.kt`, `ProfileRepositoryTest.kt` no longer owns duplicated inline storage/counter fixture code, and both constructor coverage paths remain explicit.
  - Phase 2 complete: write-side repository scenarios now live in `ProfileRepositoryMutationTest.kt`, pure clock/ID seam assertions now live in `ProfileRepositoryIdentityTimeOwnershipTest.kt`, and `ProfileRepositoryTest.kt` is reduced to bootstrap/recovery plus import-policy coverage.
  - Phase 3 complete: bootstrap, hydration, read-error, and recovery scenarios now live in `ProfileRepositoryBootstrapRecoveryTest.kt`, `ProfileRepositoryTest.kt` is reduced to import-policy coverage only, and every file in this lane is now under the `500`-line hard cap.
  - Phase 4 complete: import-policy scenarios now live in `ProfileRepositoryImportTest.kt`, both import constructor paths remain explicit, and the temporary `ProfileRepositoryTest.kt` host has been deleted.
  - Phase 5 complete: `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md` no longer lists the removed `ProfileRepositoryTest.kt` path under the active line-budget exception scope.
- Execution rules:
  - This is a test-architecture and line-budget remediation track, not a production behavior change.
  - Keep `ProfileRepository` as the only production SSOT owner; do not introduce test-only production seams.
  - Reuse the existing bundle-specific support file instead of folding bundle coverage back into the giant repository test file.
  - Remove duplication in test setup before moving scenarios; do not copy the current harness into several new files.
  - Land one responsibility split at a time and keep required repo gates green after each phase.

## 1) Scope

- Problem statement:
  - `app/src/test/java/com/example/xcpro/profiles/ProfileRepositoryTest.kt` is currently `907` lines and mixes repository mutation behavior, bootstrap/hydration/recovery behavior, import policy behavior, and explicit identity/time seam behavior in one file.
  - The file also carries two setup styles:
    - a scoped `RepositoryHarness` plus `createHarness(...)`
    - a second inline repository/storage fixture at the class level
  - That duplicated setup makes the suite harder to review and raises the risk of future tests locking the wrong seam.
- Why now:
  - The file remains part of the active line-budget deviation in `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md`.
  - Recent ownership work added more repository contract coverage, which is good, but pushed the file far beyond the default `<= 500` Kotlin budget.
  - The split can be done entirely in test code with no intended production behavior change.
- In scope:
  - Split the current repository test monolith into focused test files by responsibility.
  - Extract one shared repository test-support seam.
  - Preserve the current API-level repository assertions.
  - Keep bundle/export tests in their existing dedicated files unless a small support reuse is clearly justified.
- Out of scope:
  - No production `ProfileRepository` behavior changes.
  - No profile bundle/export workflow redesign.
  - No profile settings contributor refactors.
  - No repo-wide test naming/style rewrite beyond this suite.
- User-visible impact:
  - None intended.
- Rule class touched: Default

## 2) Focused Code-Pass Findings

### 2.1 Current Seam Inventory

The current file contains five distinct responsibilities:

| Responsibility | Current location | Why it should split |
|---|---|---|
| Generic repository fixture and diagnostics capture | top-level setup at lines 18-139 | setup ownership should be one shared support seam, not duplicated inline |
| Mutation behavior | tests at lines 141-346 and 1003-1018 | these tests lock create/update/set-active/delete contracts |
| Bootstrap, hydration, and recovery behavior | tests at lines 348-473 and 860-1000 | these tests lock read-status and fallback contracts |
| Import policy behavior | tests at lines 494-858 | this is the largest single behavior family and deserves its own review surface |
| Identity/time ownership behavior | tests at lines 159-189 and 821-857 | these tests lock the recent explicit ID/time seam and should stay visible as a dedicated contract |

### 2.2 Missed Drift In Earlier Split Advice

- The file does not just need "smaller files"; it needs one setup owner.
  - The duplicated fixture at lines 93-139 is the first thing to remove.
- The current file is implicitly covering two production constructor seams.
  - The class-level fixture uses `ProfileRepository(storage, clock, profileIdGenerator)`, which exercises the convenience constructor with default internal scope and no-op collaborators.
  - The scoped harness uses `ProfileRepository(storage, internalScope, profileDiagnosticsReporter, clock, profileIdGenerator)`, which exercises the explicit-scope seam.
  - Phase 1 must preserve both constructor paths intentionally instead of accidentally standardizing on only one.
- Import policy is now the dominant behavior family.
  - The import block is large enough to stay under budget only if it gets its own file.
- The identity/time ownership tests should not disappear into generic mutation coverage.
  - They validate a specific architecture seam introduced by the recent profile ownership work.
- `app/src/test/java/com/example/xcpro/profiles/ProfileRepositoryBundleTestSupport.kt` is already the correct pattern for dedicated support ownership.
  - The new support file should mirror that style, but for generic repository behavior instead of bundle collaborators.
- Adjacent repository suites already own specialized collaborator seams.
  - `ProfileRepositoryDeleteCascadeTest.kt` owns cleaner-specific delete behavior.
  - `ProfileRepositoryBackupSyncTest.kt` owns backup sink and settings snapshot capture behavior.
  - Phase 1 should not fold those harnesses into the generic support seam unless a later focused pass proves it is worth the churn.

### 2.3 Phase 2 Seam-Pass Findings

- Phase 2 is narrower than the original split wording implied.
  - The explicit ownership file should stay small and only own direct clock/ID seam assertions that are not primarily import-policy behavior.
- The pure ownership-seam tests are:
  - `createProfile_usesInjectedClockAndIdGeneratorForOwnerMetadata`
  - `bootstrapDefaultProfile_usesInjectedClockForRecoveryMetadata`
- The import-path clock/ID tests are mixed-policy tests and should move with import later, not in Phase 2.
  - `importProfiles_preserveImportedPreferencesFalse_appliesDefaults`
  - `importProfiles_usesInjectedIdGeneratorWhenDuplicateIdNeedsReplacement`
  - Those tests validate import collision/defaulting policy first and explicit time/ID ownership second, so Phase 4 is their correct owner.
- `unknownReadError_marksHydratedAndReportsError` should remain with bootstrap/recovery later.
  - It intentionally uses a tiny local failing `ProfileStorage` seam instead of the generic harness.
  - Phase 2 should not try to normalize that into `ProfileRepositoryTestSupport.kt`.
- Convenience-constructor coverage must stay visible after Phase 2.
  - The mutation file should continue to exercise the class-level convenience harness for ordinary write-path behavior.
  - The identity/time file can continue to use the scoped harness because diagnostics and injected seams are the point of those tests.
- `setActiveProfile_mergesProfileIfMissing` belongs with mutation, not import.
  - It validates repository write-side active-profile ownership, even though the input looks like imported data.

### 2.4 Phase 3 Seam-Pass Findings

- The remaining host file is now structurally two families plus one stray write-side test.
  - Bootstrap/recovery family:
    - `bootstrap_marksRepositoryHydrated`
    - `bootstrap_emptyState_provisionsDefaultProfileAndActiveId`
    - `bootstrap_nonEmptySnapshot_withoutCanonicalDefault_insertsCanonicalDefault`
    - `parseFailure_preservesLastKnownGoodProfiles`
    - `parseFailure_withoutLastKnownGood_recoversDefaultProfileAndPersistsState`
    - `missingActiveProfileId_fallsBackToFirstProfile`
    - `ioReadError_preservesLastKnownGoodState`
    - `unknownReadError_marksHydratedAndReportsError`
    - `invalidEntriesAreIgnoredDuringHydration`
    - `nullEntriesAreIgnoredDuringHydration`
    - `recoverWithDefaultProfile_afterReadError_provisionsCanonicalDefaultAndClearsError`
    - `recoverWithDefaultProfile_preservesExistingProfilesAndSetsDefaultActive`
  - Import family:
    - all `importProfiles_*` tests still in `ProfileRepositoryTest.kt`
  - Stray mutation residue:
    - `createFirstProfile_usesAtomicStorageWrite`
- `createFirstProfile_usesAtomicStorageWrite` must not move into bootstrap/recovery.
  - It is a write-path atomicity test and belongs with `ProfileRepositoryMutationTest.kt`.
  - Phase 3 should either move it as part of the prep patch or leave it out of the bootstrap file intentionally.
- The bootstrap/recovery file should not inherit the convenience harness.
  - Every real bootstrap/recovery test already uses `createScopedProfileRepositoryTestHarness(...)` except `unknownReadError_marksHydratedAndReportsError`, which intentionally uses a tiny local failing `ProfileStorage`.
  - That means `ProfileRepositoryBootstrapRecoveryTest.kt` should be a scoped-harness/local-storage file with no class-level convenience fixture.
- The import residue will still need the convenience harness and `FakeClock`.
  - After Phase 3, `ProfileRepositoryTest.kt` will effectively be an import-policy host until Phase 4 moves it into `ProfileRepositoryImportTest.kt`.
- Line-budget pressure is lower after Phase 3, but the ownership split still matters.
  - The expected bootstrap/recovery file is comfortably under `500`.
  - The remaining import host should also fall under `500`, which means Phase 4 can focus on removing the umbrella host cleanly rather than racing the hard cap.

### 2.5 Phase 4 Seam-Pass Findings

- The remaining host file is now a clean import-policy seam.
  - `ProfileRepositoryTest.kt` contains only `importProfiles_*` scenarios.
  - There is no remaining bootstrap/recovery or mutation residue in the host.
- Phase 4 still has two distinct import-constructor seams that must remain visible.
  - The ordinary import-policy tests use the class-level convenience harness:
    - active-profile preservation
    - metadata preservation
    - atomic write behavior
    - invalid-entry skipping
    - name collision policy
    - replace-existing policy
    - all-invalid no-op behavior
    - imported-preferences reset/defaulting
    - duplicate-ID regeneration
  - One import-specific ownership seam uses the scoped harness:
    - `importProfiles_usesInjectedIdGeneratorWhenDuplicateIdNeedsReplacement`
  - Phase 4 must preserve both, not standardize everything onto one constructor path.
- The import file should continue to own the mixed import/defaulting clock seam.
  - `importProfiles_preserveImportedPreferencesFalse_appliesDefaults` uses the convenience harness clock and validates import policy plus time/default ownership together.
  - That test now correctly belongs to import, not to the pure ownership file.
- `ProfileRepositoryTest.kt` should be deleted, not left as an empty alias file.
  - Once the import tests move, the temporary migration host no longer adds value.
  - Deleting it keeps the suite honest and prevents new mixed-responsibility tests from drifting back in.
- The support seam is already sufficient for Phase 4.
  - No support-file changes should be needed to finish the split.
  - If the import file needs special setup, it should use the existing support factories directly rather than growing new helpers.

## 3) Target Test Ownership Contract

### 3.1 File Ownership Plan

| File | New / Existing | Owner / Responsibility | Why Here | Why Not Another File |
|---|---|---|---|---|
| `app/src/test/java/com/example/xcpro/profiles/ProfileRepositoryTestSupport.kt` | New | Generic repository harness, fake storage, write counters, diagnostics capture, and `createHarness(...)` | shared setup seam for all repository test families | bundle support stays bundle-specific |
| `app/src/test/java/com/example/xcpro/profiles/ProfileRepositoryMutationTest.kt` | New | create/update/set-active/delete repository behavior | one file for write API contracts | bootstrap/import concerns are separate |
| `app/src/test/java/com/example/xcpro/profiles/ProfileRepositoryBootstrapRecoveryTest.kt` | New | hydration, parse failure, read error, fallback, and recovery contracts | one file for read-side repository lifecycle | mutation/import tests should not mix with read-status contracts |
| `app/src/test/java/com/example/xcpro/profiles/ProfileRepositoryImportTest.kt` | New | import collision policy, active-profile policy, validation skips, metadata preservation, and duplicate-ID behavior | largest distinct behavior cluster | keeping it in mutation/bootstrap files would recreate the same review problem |
| `app/src/test/java/com/example/xcpro/profiles/ProfileRepositoryIdentityTimeOwnershipTest.kt` | New | explicit injected clock/ID seam ownership coverage | preserves visibility of the ownership contract | these tests are architecture-specific and should not be buried |
| `app/src/test/java/com/example/xcpro/profiles/ProfileRepositoryTest.kt` | Existing, then removed or reduced to zero | temporary migration host only | allows phased movement without rebasing every test at once | final state should not keep a giant umbrella file |

### 3.2 Reference Pattern Check

| Reference File | Why It Is Similar | Pattern To Reuse | Planned Deviation |
|---|---|---|---|
| `app/src/test/java/com/example/xcpro/profiles/ProfileRepositoryBundleTestSupport.kt` | already isolates bundle-specific fake collaborators into a focused support file | dedicated support owner separate from scenario files | new support file will be generic repository support rather than bundle support |
| `app/src/test/java/com/example/xcpro/profiles/ProfileRepositoryBundleTest.kt` | existing split between test support and scenario assertions | scenario-only test file with minimal setup noise | this plan applies the same separation to the main repository suite |

### 3.3 Support Seam Contract

The new generic support seam must provide:

| Capability | Required? | Why |
|---|---|---|
| configurable initial `ProfileStorageSnapshot` | Yes | bootstrap/hydration/recovery tests need explicit read-status and seed state |
| `writeProfilesCalls`, `writeActiveCalls`, `writeStateCalls` counters | Yes | atomic-write and no-op import assertions depend on exact write path tracking |
| injectable `FakeClock` | Yes | identity/time ownership and defaults-reset tests depend on explicit wall-time control |
| injectable `ProfileIdGenerator` | Yes | duplicate-ID replacement and owner-metadata tests depend on deterministic IDs |
| diagnostics event capture | Yes | parse-failure and recovery tests assert diagnostic reporting |
| explicit `CoroutineScope` injection | Yes | scoped constructor seam must remain covered |
| one convenience-constructor coverage path | Yes | the default internal-scope/no-op collaborator constructor is currently exercised and should remain intentionally covered |

Rules:
- The support file owns generic setup only; it must not absorb scenario assertions.
- Specialized bundle/delete/backup harnesses remain separate unless a later dedicated plan says otherwise.

## 4) Architecture Contract

### 4.1 SSOT Ownership

No production SSOT changes are allowed in this plan.

| Test concern | Authoritative owner | Exposed as | Forbidden duplicates |
|---|---|---|---|
| Generic repository fixture setup | `ProfileRepositoryTestSupport.kt` | factory/harness helpers | duplicate inline storage fixtures in scenario files |
| Mutation contract assertions | `ProfileRepositoryMutationTest.kt` | repository API tests | import/bootstrap assertions mixed into mutation file |
| Bootstrap/recovery assertions | `ProfileRepositoryBootstrapRecoveryTest.kt` | repository API tests | read-status and recovery cases duplicated elsewhere |
| Import policy assertions | `ProfileRepositoryImportTest.kt` | repository API tests | collision/duplicate-ID policy mixed into unrelated files |
| Identity/time seam assertions | `ProfileRepositoryIdentityTimeOwnershipTest.kt` | repository seam tests | ownership assertions buried in general mutation/import files |

### 4.2 Dependency Direction

Required test dependency flow remains:

`Test -> ProfileRepository public API -> production collaborators/fakes`

Rules:
- Tests must continue to validate repository behavior through public methods and exposed state.
- Do not add test-only backdoors to production code to make the split easier.
- Support files may own fake collaborators, but they do not become alternate production owners.

### 4.3 Boundary Moves

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| generic repository harness setup | `ProfileRepositoryTest.kt` | `ProfileRepositoryTestSupport.kt` | remove duplicated fixture ownership | all repository tests compile and pass |
| mutation assertions | `ProfileRepositoryTest.kt` | `ProfileRepositoryMutationTest.kt` | narrow review surface | targeted mutation tests |
| bootstrap/recovery assertions | `ProfileRepositoryTest.kt` | `ProfileRepositoryBootstrapRecoveryTest.kt` | isolate read-status lifecycle behavior | targeted bootstrap/recovery tests |
| import assertions | `ProfileRepositoryTest.kt` | `ProfileRepositoryImportTest.kt` | isolate high-volume policy matrix | targeted import tests |
| identity/time seam assertions | `ProfileRepositoryTest.kt` | `ProfileRepositoryIdentityTimeOwnershipTest.kt` | preserve architectural seam visibility | targeted ownership tests |
| constructor seam coverage | implicit mixed setup in `ProfileRepositoryTest.kt` | explicit retained tests in support-consuming scenario files | preserve coverage of convenience and explicit-scope constructors | targeted repository tests compile and pass |

### 4.4 Enforcement Coverage

| Risk | Rule Reference | Guard Type | File/Test |
|---|---|---|---|
| giant test monolith remains above line budget | line-budget policy in `ARCHITECTURE.md` and `CODING_RULES.md` | `enforceRules` + review | split files listed in this plan |
| setup duplication returns after split | file ownership rules in `AGENTS.md` | review + shared support file | `ProfileRepositoryTestSupport.kt` |
| repository behavior drifts during test move | test-contract preservation | targeted unit tests | new split test files |
| identity/time seam loses explicit coverage | ownership rules in architecture docs | targeted unit tests | `ProfileRepositoryIdentityTimeOwnershipTest.kt` |
| constructor overload coverage is dropped during support extraction | no-op/test wiring and ownership rules | targeted unit tests + review | `ProfileRepositoryTestSupport.kt` consumers |
| specialized delete/backup harness ownership gets blurred | file ownership rules | review | `ProfileRepositoryDeleteCascadeTest.kt`, `ProfileRepositoryBackupSyncTest.kt` |

## 5) Implementation Phases

### Phase 0 - Baseline lock and inventory

- Goal:
  - Freeze the current repository test contract and final split map before moving code.
- Files to change:
  - docs only
- Tests to add/update:
  - none
- Exit criteria:
  - test responsibilities are mapped
  - target file ownership is explicit
  - no production files are touched

### Phase 1 - Extract shared repository test support

- Goal:
  - Remove duplicate setup ownership from `ProfileRepositoryTest.kt`.
- Files to change:
  - create `ProfileRepositoryTestSupport.kt`
  - trim `ProfileRepositoryTest.kt` to consume the shared support seam
- Ownership/file split changes in this phase:
  - one harness owner for fake storage, write counters, diagnostics reporter, clock, and ID seam wiring
  - preserve one explicit test path for the convenience constructor and one for the explicit-scope constructor
- Tests to add/update:
  - existing repository tests only; no scenario moves required yet
- Exit criteria:
  - no duplicated inline storage fixture remains
  - all scenario files use the same generic support seam
  - constructor-seam coverage remains explicit

### Phase 2 - Split mutation and identity/time seam tests

- Goal:
  - Move write-side repository behavior and ownership-specific seam tests out first.
- Files to change:
  - `ProfileRepositoryMutationTest.kt`
  - `ProfileRepositoryIdentityTimeOwnershipTest.kt`
  - `ProfileRepositoryTest.kt` reduced accordingly
- Ownership/file split changes in this phase:
  - mutation contract owner separated from ownership-seam contract owner
- Tests to add/update:
  - move existing mutation tests without semantic changes
  - move only pure ownership-seam tests into the identity/time file
  - leave import-path clock/ID tests in the monolith for the later import split
- Exit criteria:
  - mutation file stays under `500`
  - identity/time seam remains explicitly covered in a focused small file
  - convenience-constructor coverage is still present in the mutation lane
  - import-specific clock/ID tests have not been moved prematurely
  - remaining monolith is materially smaller

### Phase 3 - Split bootstrap, hydration, and recovery tests

- Goal:
  - Isolate repository read-status and fallback behavior into its own suite.
- Files to change:
  - `ProfileRepositoryBootstrapRecoveryTest.kt`
  - `ProfileRepositoryTest.kt`
- Ownership/file split changes in this phase:
  - read-side repository lifecycle coverage gets its own owner
- Tests to add/update:
  - move bootstrap, parse-failure, read-error, invalid-entry, and recovery tests intact
  - keep `unknownReadError_marksHydratedAndReportsError` on its local failing-storage seam
  - move `createFirstProfile_usesAtomicStorageWrite` out of the host file, but not into bootstrap/recovery
- Exit criteria:
  - bootstrap/recovery file stays under `500`
  - bootstrap/recovery file uses scoped harness or local failing-storage seams only
  - remaining host contains only import-related residue
  - no mutation atomic-write test remains in the host file

### Phase 4 - Split import policy tests and remove the monolith

- Goal:
  - Finish the largest remaining behavior family and delete or empty the original giant file.
- Files to change:
  - `ProfileRepositoryImportTest.kt`
  - `ProfileRepositoryTest.kt` removed
- Ownership/file split changes in this phase:
  - import policy is the sole owner of import-specific assertions
- Tests to add/update:
  - move import policy scenarios intact
  - preserve convenience-harness coverage for ordinary import policy
  - preserve scoped-harness coverage for injected duplicate-ID replacement
- Exit criteria:
  - no repository scenario file exceeds `500` lines
  - `ProfileRepositoryImportTest.kt` stays under `500`
  - `ProfileRepositoryTest.kt` is deleted
  - import-specific clock/ID behavior remains covered in the import lane
  - both relevant import constructor paths remain explicit

### Phase 5 - Closeout and deviation cleanup

- Goal:
  - Close the line-budget exception for this file path.
- Files to change:
  - `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md`
  - optional broader line-budget tracking docs if they still reference the old monolith
- Tests to add/update:
  - none beyond regression confirmation
- Exit criteria:
  - the scoped file path is no longer part of the oversized-file exception set
  - required repo checks pass

## 6) Test Plan

- Unit tests:
  - keep all current repository assertions; this is a relocation refactor, not a semantic rewrite
- Replay/regression tests:
  - not applicable
- UI/instrumentation tests:
  - not required
- Degraded/failure-mode tests:
  - parse failure, IO/unknown read status, invalid hydration data, recovery path
- Boundary tests for removed bypasses:
  - ensure support extraction does not add test-only production bypasses

Change-type coverage matrix:

| Change Type | Required Proof | Planned Evidence |
|---|---|---|
| Ownership move / file split | Boundary lock tests | repository test suite remains green after each phase |
| Identity/time seam coverage move | Unit tests | dedicated ownership test file still covers injected clock/ID behavior |
| Read-status/recovery split | Unit tests | bootstrap/recovery suite passes unchanged assertions |
| Import policy split | Unit tests | import suite passes unchanged assertions |

Required checks per implementation phase:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Suggested targeted lane during implementation:

```bash
./gradlew :app:testDebugUnitTest --tests "com.example.xcpro.profiles.*"
```

## 7) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| mechanical test moves accidentally change assertions | High | move tests with no semantic edits; keep names stable where possible | XCPro Team |
| support extraction creates another giant helper file | Medium | keep support file setup-only; do not move scenario assertions into it | XCPro Team |
| import test file still grows too large | Medium | keep identity/time tests separate; move only import behavior into import file | XCPro Team |
| ownership file absorbs import-policy tests and becomes another mixed seam | Medium | keep only pure creation/bootstrap clock-ID tests in Phase 2; move import-path ownership tests in Phase 4 | XCPro Team |
| bootstrap/recovery file accidentally absorbs mutation residue | Medium | treat `createFirstProfile_usesAtomicStorageWrite` as write-side cleanup, not read-side coverage | XCPro Team |
| bootstrap file inherits the wrong constructor seam | Medium | use scoped harness/local failing-storage only; leave convenience-harness coverage outside this phase | XCPro Team |
| import split drops scoped duplicate-ID replacement coverage | Medium | keep `importProfiles_usesInjectedIdGeneratorWhenDuplicateIdNeedsReplacement` on the scoped harness in the import file | XCPro Team |
| empty temporary host file is left behind and attracts new mixed tests | Medium | delete `ProfileRepositoryTest.kt` once import coverage is moved | XCPro Team |
| bundle test support gets mixed into generic support | Low | keep bundle/export support isolated in its existing file | XCPro Team |
| support extraction drops convenience-constructor coverage | Medium | retain one narrow scenario path that still uses the convenience constructor intentionally | XCPro Team |
| Phase 1 scope expands into delete/backup harness cleanup | Medium | leave specialized harnesses alone in this plan unless later evidence justifies a separate cleanup | XCPro Team |

## 8) Acceptance Gates

- No production behavior changes.
- No production file ownership changes.
- No test file in this split lane remains above `500` lines.
- Generic repository setup exists in one support file only.
- Identity/time seam tests remain explicit after the split.
- Coverage of both relevant repository constructor seams remains explicit.
- Required checks pass:
  - `./gradlew enforceRules`
  - `./gradlew testDebugUnitTest`
  - `./gradlew assembleDebug`

## 9) Recommendation

Do this refactor as a focused test-suite split, not as part of another profile lane.

Best execution order:
1. extract generic support
2. move mutation + identity/time tests
3. move bootstrap/recovery tests
4. move import tests
5. close the deviation

That order removes duplicated setup first, keeps architectural seam coverage visible, and lets the largest behavior family (`importProfiles`) move last without blocking earlier line-budget wins.
