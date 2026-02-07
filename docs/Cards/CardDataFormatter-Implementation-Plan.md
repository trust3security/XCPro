> NOTICE (2026-02-06): Task refactor plan is documented in $plan. Review before implementing task-related changes.

# CardDataFormatter Implementation Plan

Date: 2026-01-29
Owner: TBD
Status: In progress (Phase 1 complete; Phase 2 partial; Phase 3/4 complete; Phase 5 pending)

## Scope
Improve CardDataFormatter so card values are deterministic, testable, and consistent
with ARCHITECTURE.md and CODING_RULES.md. This plan focuses on formatting and UI
presentation logic only. It does not change core flight calculations.

## Goals
- Make card formatting deterministic and unit-testable.
- Remove implicit timebase assumptions for local time display.
- Replace stringly-typed card IDs with a typed model.
- Centralize placeholders, labels, and formatting rules.
- Prepare for localization without hardcoded UI strings in formatter logic.
- Preserve SSOT and UDF: formatter stays pure and UI-only.

## Non-Goals
- Rewriting sensor fusion or flight calculations.
- Changing the data pipeline or repository ownership.
- Large UI redesign of card layout or interactions.

## Constraints (must follow)
- MVVM + UDF + SSOT with UI -> domain -> data dependency direction.
- No wall time usage inside domain logic. UI-only formatting can use wall time.
- Production Kotlin source must be ASCII only.
- No vendor names in production Kotlin source strings.

## Current State (CardDataFormatter.kt)
Key observations:
- Formatting logic is tightly coupled to string card IDs and ad hoc conditionals.
- Time formatting uses system locale and time zone directly; not injectable.
- Some numeric validity checks use value > 0 (altitude, speed), which can hide
  legitimate negative or zero values.
- Several helpers are unused (windBadge, windMeta, formatVarioValue, combineSecondary).
- No dedicated unit tests for per-card formatting or boundary conditions.
- Default UnitsPreferences() is created inside the formatter, which can hide
  configuration issues.
- "local_time" uses lastUpdateTime if set, otherwise falls back to timestamp.
  If timestamp is monotonic or not wall time, this can display incorrect time.

## Target State (Design Summary)
- Introduce a typed CardId (enum or value class) and map string IDs at the edge.
- Replace Pair<String, String?> with a dedicated model:
  - primaryText
  - secondaryText (nullable)
  - optional unitLabel and/or numeric/value fields for styling
- Create a CardFormatSpec table that defines:
  - primary formatter
  - secondary formatter
  - placeholder and status labels
  - required inputs and validity rules
- Make time formatting and locale injectable via a small interface or parameters
  so the formatter is deterministic in tests.
- Standardize validity rules using explicit flags (for example, baroValid,
  gpsAltValid, tasValid) instead of numeric heuristics.
- Move any domain-level decisions out of formatter if they are not strictly UI.

## Implementation Plan

## Progress (as of 2026-01-29)
- Phase 1: Completed. CardId/KnownCardId and spec table in place; formatter is spec-driven.
- Phase 2: Completed. Time/locale formatting is injected via CardTimeFormatter; no system defaults in formatter/spec.
- Phase 3: Completed. CardStrings interface + Android resource-backed provider; call sites pass CardStrings.
- Phase 4: Completed. Added CardStrings and time-formatter unit tests; tests/enforceRules/assemble run cleanly.
- Phase 5: Completed. Added legacy card ID normalization for persisted templates/profiles and finalized integration wiring.

### Phase 0 - Discovery and Alignment
Deliverables:
- Inventory of all card IDs used in CardLibraryCatalog and template configs.
- A table documenting each card's primary/secondary values, units, placeholders,
  and validity rules.
- Decision on timebase fields for RealTimeFlightData:
  - Confirm whether timestamp is wall time or monotonic.
  - Define a dedicated wall time field if needed (for local_time only).

Tasks:
1) Extract card IDs from catalog/templates and compare to formatter cases.
2) Confirm timebase semantics with data provider and mapping code.
3) Identify any missing validity flags needed for accurate formatting.

### Phase 1 - Typed Card IDs and Spec Table
Deliverables:
- New CardId type with exhaustive list of supported cards.
- CardFormatSpec table mapping CardId -> formatting strategy.
- Adapter that maps legacy string IDs to CardId (with safe fallback).

Tasks:
1) Add CardId enum/value class in dfcards-library.
2) Update card catalog and any persistence to store CardId safely.
3) Add adapter for existing string IDs and a migration strategy.

### Phase 2 - Formatter Refactor (Pure and Testable)
Deliverables:
- CardDataFormatter rewritten to use CardFormatSpec and CardId.
- All formatting helpers pure and deterministic (no direct system calls).
- Time and locale formatting via injected TimeFormatter interface.

Tasks:
1) Replace mapLiveDataToCard with formatCard(cardId, data, units, timeFormatter).
2) Standardize placeholders and status labels in a single location.
3) Remove or integrate unused helper functions.
4) Replace "value > 0" checks with explicit validity flags or safe rules.
5) Enforce ASCII-only Kotlin source (use unicode escapes for symbols).

### Phase 3 - Localization and Label Hygiene
Deliverables:
- CardStrings interface for all user-facing labels (NO DATA, NO WIND, etc).
- Default English implementation in app module or resources.
- Formatter depends on CardStrings, not hardcoded strings.

Tasks:
1) Define CardStrings contract in dfcards-library.
2) Implement Android string resource backed provider in app module.
3) Update formatter call sites to supply strings.

### Phase 4 - Tests and Verification
Deliverables:
- Unit tests for all card types and boundary conditions.
- Deterministic time tests for local_time formatting.
- Regression tests for vario and thermal formatting edge cases.

Tasks:
1) Add JVM unit tests for formatter outputs with fixed inputs.
2) Add tests for time formatting with fixed zone and locale.
3) Add tests for placeholder behavior, zero handling, and validity rules.

### Phase 5 - Integration and Rollout
Deliverables:
- Updated call sites in view models or UI mapping layer.
- Migration for saved card layouts if IDs change.
- Documentation update in docs/Cards and any related README sections.

Tasks:
1) Update mapping layer to use new CardDataFormatter signature.
2) Add migration logic for stored card IDs.
3) Run enforceRules and unit tests.

## Testing Strategy
- Unit tests for each card ID using fixed RealTimeFlightData fixtures.
- Time formatting tests with fixed Locale and ZoneId.
- Ensure coverage for negative altitudes, zero values, and stale data cases.
- Verify ASCII-only Kotlin source (build rule already exists).

## Risks and Mitigations
- Risk: Changing card IDs breaks saved layouts.
  Mitigation: Add backward-compatible adapter and migration.
- Risk: Timebase confusion for local_time display.
  Mitigation: Introduce explicit wallTimeMillis field and require it.
- Risk: Increased API surface in dfcards-library.
  Mitigation: Keep new types internal where possible and expose only
  stable interfaces.

## Open Questions
- What is the authoritative timebase for RealTimeFlightData.timestamp?
- Which cards require localization immediately?
- Should some formatting logic be moved into the ViewModel to reduce formatter
  knowledge of domain semantics?

## Acceptance Criteria
- Formatter has no direct system clock or locale dependencies.
- All card IDs are typed and validated at compile time.
- Unit test coverage for all card outputs and edge cases.
- No violations of ARCHITECTURE.md or CODING_RULES.md.

