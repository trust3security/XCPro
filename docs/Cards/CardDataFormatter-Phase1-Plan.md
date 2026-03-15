
# CardDataFormatter Phase 1 Plan - Typed IDs and Spec Scaffolding

Date: 2026-01-29
Status: Draft
Depends on: CardDataFormatter-Phase0-Inventory.md

## Objectives
- Introduce a typed CardId wrapper to reduce stringly-typed usage.
- Add a formatting spec table scaffold without changing formatting output.
- Keep persistence and template storage backward compatible (strings stay on disk).

## Compliance Notes (ARCHITECTURE.md / CODING_RULES.md / CONTRIBUTING.md)
- Keep production Kotlin source ASCII-only (use escapes if needed).
- No vendor names in production Kotlin source or public APIs.
- No timebase changes or System time access added to domain/fusion logic.
- Keep formatter logic pure and testable; avoid Android/Compose types in new core types.
- Add KDoc for any public API and a top-of-file header describing role/invariants.
- Use `// AI-NOTE:` for intent-critical decisions (ex: legacy id aliasing).

## Non-Goals
- No formatter behavior changes.
- No DataStore migrations or template ID rewrites.
- No timebase changes (local_time timer remains as-is).

## Plan (Detailed)

### Step 1: Add typed CardId wrapper (dfcards-library)
Files:
- dfcards-library/src/main/java/com/example/dfcards/CardId.kt (new)

Actions:
1) Create `@JvmInline value class CardId(val raw: String)`.
2) Add companion object constants for all known IDs (as raw strings).
3) Add `knownIds: Set<String>` for validation.
4) Provide helpers:
   - `fun isKnown(): Boolean`
   - `fun toKnownOrNull(): KnownCardId?` (if using an enum)
   - `fun fromRaw(raw: String): CardId`
5) Keep the typo id `satelites` as a constant to preserve existing data.

Notes:
- Keep all identifiers ASCII-only.
- Do not change any persisted keys or templates in this phase.
- Add `// AI-NOTE:` near legacy-id aliasing to prevent accidental "fixes".

### Step 2: Introduce KnownCardId enum (optional but recommended)
Files:
- dfcards-library/src/main/java/com/example/dfcards/KnownCardId.kt (new)

Actions:
1) Add an enum listing all known cards, matching the card catalog ids.
2) Provide a mapping table in CardId to convert raw string -> KnownCardId.
3) Use KnownCardId for exhaustive `when` in formatter later.

Decision point:
- If enum feels too heavy, keep only CardId + knownIds set and defer enum.

### Step 3: Add formatting spec table scaffold
Files:
- dfcards-library/src/main/java/com/example/dfcards/CardFormatSpec.kt (new)

Actions:
1) Define `CardFormatSpec` with:
   - `placeholderPrimary`
   - `placeholderSecondary`
   - `formatPrimary(liveData, units)`
   - `formatSecondary(liveData, units)`
2) Create a `Map<KnownCardId, CardFormatSpec>` or `Map<String, CardFormatSpec>`.
3) Wire the table but keep behavior identical to existing formatter.
4) Ensure all labels and placeholders remain unchanged for now.
5) Keep CardFormatSpec free of Android/Compose types to preserve JVM-testability.

### Step 4: Wire CardLibrary to typed IDs (no behavior change)
Files:
- dfcards-library/src/main/java/com/example/dfcards/CardLibrary.kt
- dfcards-library/src/main/java/com/example/dfcards/CardDataFormatter.kt

Actions:
1) Update CardLibrary.mapLiveDataToCard to wrap the string id into CardId.
2) Update CardDataFormatter to accept CardId (or both overloads).
3) Keep the public API that accepts String for compatibility.

### Step 5: Unit tests for ID coverage
Files:
- dfcards-library/src/test/java/com/example/dfcards/CardIdCoverageTest.kt (new)

Actions:
1) Assert every CardDefinition id is present in CardId.knownIds.
2) Assert every KnownCardId has a spec entry.
3) Assert template ids are valid known IDs.
4) Assert no unknown IDs are introduced accidentally.
5) Keep tests JVM-only; no Android dependencies.

## Acceptance Criteria
- New CardId wrapper exists and is used at the formatting boundary.
- No changes to persisted data, templates, or UI output.
- Tests guarantee full coverage between catalog and formatter/spec.
- No non-ASCII characters added to Kotlin sources.

## Risks and Mitigations
- Risk: Large refactor surface if CardId is threaded too deep.
  Mitigation: Keep CardId usage at the formatting boundary in Phase 1.
- Risk: Enum mismatch with typo ids (satelites).
  Mitigation: Preserve the legacy raw string and alias to a correctly named enum if needed.

## Notes from Phase 0 (checked)
- local_time is updated via a wall-clock timer, so formatter changes should not affect runtime time display.
- All catalog IDs are currently handled by CardDataFormatter; maintain parity.

