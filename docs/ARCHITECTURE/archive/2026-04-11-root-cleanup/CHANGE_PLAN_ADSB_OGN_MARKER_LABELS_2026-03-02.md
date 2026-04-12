# CHANGE_PLAN_ADSB_OGN_MARKER_LABELS_2026-03-02.md

## Goal
- Redesign traffic marker labels so map markers prioritize height and distance readability.
- Apply a shared typography style between ADS-B and OGN marker labels.

## Scope
- ADS-B marker overlay labels.
- OGN marker overlay secondary label semantics and typography.

## Out of Scope
- ADS-B/OGN details-sheet redesign.
- ADS-B proximity policy logic.
- OGN ingest/network/repository policy changes.

## Non-Negotiable Contracts

### Unit Contract
- Internal calculations remain SI (`meters`, `m/s`).
- Display conversion uses explicit UI boundary formatting (`UnitsPreferences` + `UnitsFormatter`).
- No hard-coded user-unit strings in marker label generation.

### Typography Contract (ADS-B + OGN)
- Font stack: `Open Sans Regular` first.
- Weight: regular (not bold).
- Text color: black.
- Halo: disabled.
- Label size: `12sp` (current `11sp + 1sp` for ADS-B).

### ADS-B Marker Label Contract
- Callsign is not displayed on marker labels.
- Marker label fields:
  - `height diff` (ownship-relative).
  - `distance`.
- Placement:
  - Target above ownship: top = `height diff`, bottom = `distance`.
  - Target below ownship: top = `distance`, bottom = `height diff`.
- Fallbacks:
  - Unknown relative altitude -> `height diff = --`.
  - Non-finite distance -> `distance = --`.
  - Unknown sign case defaults to top `height diff`, bottom `distance`.

### OGN Marker Label Contract
- Keep existing relative-altitude top/bottom placement behavior.
- Replace competition/callsign label semantics with identifier+distance:
  - If `Comp ID` is non-blank: identifier = `Comp ID`.
  - Else if `Registration` is non-blank: identifier = last 3 chars (uppercase).
  - Else identifier = `--`.
- Non-height label text:
  - If distance available: `"<identifier> <distance>"`.
  - Else: `"<identifier>"`.

## SSOT / Ownership
- ADS-B ownship-relative distance and altitude remain repository-derived (`AdsbTrafficUiModel` + ownship altitude input already owned upstream).
- Marker label strings are UI-runtime presentation state owned by map overlay mappers.
- No new repository SSOT is introduced.

## Phased Execution
1. Phase 0: Freeze contracts in this plan.
2. Phase 1: Add pure label mappers (ADS-B + OGN identifier+distance helper path).
3. Phase 2: Refactor ADS-B overlay from single horizontal label to top/bottom label layers.
4. Phase 3: Apply shared typography style in ADS-B and OGN overlays.
5. Phase 4: Add/update tests for mapper behavior and overlay wiring.
6. Phase 5: Run full verification gates.

## Verification
- Quick gate after each phase:
  - `./gradlew :feature:map:compileDebugKotlin`
- Final required gates:
  - `./gradlew enforceRules`
  - `./gradlew testDebugUnitTest`
  - `./gradlew assembleDebug`
