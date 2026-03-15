# CardLibraryCatalog Split Phased IP

## Purpose

Release-grade phased plan to split
`dfcards-library/src/main/java/com/example/dfcards/CardLibraryCatalog.kt`
by catalog ownership, remove the last active default line-budget exception under
`RULES-20260306-14`, and preserve all existing catalog exports, IDs, category
groupings, and card ordering.

Read first:

1. `docs/ARCHITECTURE/ARCHITECTURE.md`
2. `docs/ARCHITECTURE/CODING_RULES.md`
3. `docs/ARCHITECTURE/CODEBASE_CONTEXT_AND_INTENT.md`
4. `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md` (`RULES-20260306-14`)
5. `docs/refactor/Kotlin_Line_Budget_Compliance_Phased_IP_2026-03-06.md`

## 0) Metadata

- Title: Card library catalog ownership split
- Owner: XCPro Team
- Date: 2026-03-15
- Issue/PR: `RULES-20260306-14` closeout lane
- Status: Complete

## 1) Problem Statement

Current file:

- `dfcards-library/src/main/java/com/example/dfcards/CardLibraryCatalog.kt`

Current line count:

- `506`

Why it is oversized:

- One production file currently owns all category-local card definitions plus
  the aggregation exports.
- The category seams are already explicit in code:
  - `essentialCards`
  - `varioCards`
  - `navigationCards`
  - `performanceCards`
  - `timeWeatherCards`
  - `competitionCards`
  - `advancedCards`

Why now:

- This is the final active `RULES-20260306-14` scope file in
  `KNOWN_DEVIATIONS.md`.
- The split is low-risk because the semantic ownership boundaries already exist
  in the monolith.

Focused seam/code repass findings:

- `CardLibrary.kt` depends on `allCardDefinitions` order for `searchCards(...)`
  result ordering.
- `CardLibrary.kt` depends on `cardsByCategory` list order for
  `getCardsByCategory(...)`.
- The current coverage test `CardIdCoverageTest.kt` verifies ID-set equality,
  but does not lock order or per-category ordering.
- Multiple docs under `docs/Cards` and other product docs reference
  `CardLibraryCatalog.kt` as the place where new card definitions live; the
  split needs doc-sync, not just code movement.

Out of scope:

- New card definitions
- Card ID renames
- Category reclassification
- Card ordering changes
- New builder/helper abstractions unless duplication is proven during
  implementation

## 2) Current Contracts To Preserve

Publicly relevant internal exports that must remain stable:

- `cardCatalogSections`
- `allCardDefinitions`
- `cardsByCategory`

Known consumers:

- `dfcards-library/src/main/java/com/example/dfcards/CardLibrary.kt`
- `dfcards-library/src/test/java/com/example/dfcards/CardIdCoverageTest.kt`

Required invariants:

- `allCardDefinitions.map { it.id }` remains unchanged
- `allCardDefinitions.map { it.id }` order remains unchanged
- `cardCatalogSections` ordering remains unchanged
- `cardsByCategory[category]` contents remain unchanged
- `cardsByCategory[category]` order remains unchanged
- No card description/title/unit/icon drift
- `CardLibrary.searchCards(...)` preserves current catalog iteration order
- `CardLibrary.getCardsByCategory(...)` preserves current per-category order

## 3) Ownership Model

Target ownership after the split:

- `CardLibraryCatalog.kt`
  - owns aggregation order only
  - exports `cardCatalogSections`, `allCardDefinitions`, and `cardsByCategory`
  - must not own inline category definition blocks

- `CardLibraryEssentialCatalog.kt`
  - owns only `ESSENTIAL` card definitions

- `CardLibraryVarioCatalog.kt`
  - owns only `VARIO` card definitions

- `CardLibraryNavigationCatalog.kt`
  - owns only `NAVIGATION` card definitions

- `CardLibraryPerformanceCatalog.kt`
  - owns only `PERFORMANCE` card definitions

- `CardLibraryTimeWeatherCatalog.kt`
  - owns only `TIME_WEATHER` card definitions

- `CardLibraryCompetitionCatalog.kt`
  - owns only `COMPETITION` card definitions

- `CardLibraryAdvancedCatalog.kt`
  - owns only `ADVANCED` card definitions

Implementation shape requirement:

- Each category file should expose one `internal` top-level list only.
- `CardLibraryCatalog.kt` remains the sole owner of aggregation exports.
- Do not expose new public/global registry surfaces beyond the existing exports.

## 4) Architecture Contract

### 4.1 Allowed Change Shape

- Move existing category lists into new files.
- Keep aggregation exports stable in `CardLibraryCatalog.kt`.
- Keep file/module/package boundaries unchanged.

### 4.2 Forbidden Change Shape

- No new cross-module dependencies.
- No category merging or splitting beyond the existing category boundaries.
- No helper abstraction layer unless multiple category files clearly need shared
  code after extraction.
- No order normalization, sorting, or “cleanup” that changes current display or
  ID order.

## 5) Implementation Phases

### Phase 0 - Baseline Freeze

- Goal:
  - Freeze the current catalog contract before moving definitions.
- Checks:
  - verify current `CardIdCoverageTest` still represents the source of truth
  - record current category order from `cardCatalogSections`
  - inventory doc references that mention `CardLibraryCatalog.kt` as the edit
    location
- Tests to add/update:
  - extend or add coverage to lock ordered catalog IDs and per-category order
- Exit criteria:
  - contract invariants are explicit and no rename/order change is planned
  - order-preservation coverage exists, not just set coverage

### Phase 1 - Extract Core Category Files

- Goal:
  - Move the largest early category blocks out first while preserving current
    names and contents.
- Files to create:
  - `CardLibraryEssentialCatalog.kt`
  - `CardLibraryVarioCatalog.kt`
  - `CardLibraryNavigationCatalog.kt`
- File ownership:
  - each file owns one category list only
- `CardLibraryCatalog.kt` changes:
  - imports/references extracted lists
  - retains aggregation exports
- Exit criteria:
  - no export drift
  - aggregator still preserves category order
  - no new public API surface is introduced

### Phase 2 - Extract Remaining Category Files

- Goal:
  - Finish the category-local extraction and reduce the aggregator to
    composition only.
- Files to create:
  - `CardLibraryPerformanceCatalog.kt`
  - `CardLibraryTimeWeatherCatalog.kt`
  - `CardLibraryCompetitionCatalog.kt`
  - `CardLibraryAdvancedCatalog.kt`
- `CardLibraryCatalog.kt` changes:
  - keep only category assembly and derived exports
- Documentation changes:
  - update docs that still point contributors to a single monolithic
    `CardLibraryCatalog.kt` edit location where that guidance would become stale
- Exit criteria:
  - `CardLibraryCatalog.kt` is comfortably under budget
  - all category-local definitions live in category-owned files only

### Phase 3 - Contract Verification And Closeout

- Goal:
  - Prove there was no catalog drift and close the deviation honestly.
- Required checks:
  - `./gradlew enforceRules --no-configuration-cache`
  - `./gradlew testDebugUnitTest --no-configuration-cache`
  - `./gradlew assembleDebug --no-configuration-cache`
- Targeted verification:
  - `dfcards-library` test lane, especially `CardIdCoverageTest`
- Additional verification:
  - ordered-ID/category regression coverage for `CardLibrary` lookups
- Exit criteria:
  - no remaining `CardLibraryCatalog.kt` line-budget exception
  - `KNOWN_DEVIATIONS.md` no longer lists this file under `RULES-20260306-14`
  - `scripts/ci/enforce_rules.ps1` no longer exempts this file

## 6) Reviewer Checklist

- Does each new file own exactly one category?
- Does `CardLibraryCatalog.kt` now own aggregation only?
- Are `cardCatalogSections`, `allCardDefinitions`, and `cardsByCategory`
  preserved with the same order and contents?
- Does `CardLibrary.searchCards(...)` still return matches in the same catalog
  order?
- Does `CardLibrary.getCardsByCategory(...)` still preserve current per-category
  order?
- Were any card IDs, titles, icons, descriptions, or units changed?
- Did the implementation avoid adding unnecessary helper abstractions?
- Were stale docs that referenced the monolithic edit location updated?
- Did the required Gradle checks pass?

## 7) Completion Summary

Completed on 2026-03-15:

- Phase 0: added explicit order-lock coverage for catalog IDs and per-category
  ordering.
- Phase 1: extracted `ESSENTIAL`, `VARIO`, and `NAVIGATION` into focused
  category-owned files.
- Phase 2: extracted `PERFORMANCE`, `TIME_WEATHER`, `COMPETITION`, and
  `ADVANCED` into focused category-owned files and kept
  `CardLibraryCatalog.kt` as the sole aggregation owner.
- Phase 3: removed the line-budget exception, updated stale card docs, and
  closed the remaining `RULES-20260306-14` production-file scope after
  verification.
