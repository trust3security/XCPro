# Codex Task: Automate Quality Gates and Documentation Sync (XCPro)

Date: 2026-03-01
Scope: docs + CI + local architecture gate

## Goal

1) Keep architecture/timebase policy enforceable by CI and local tooling.
2) Keep governance docs aligned with what is actually enforced.
3) Use the real in-repo clock abstraction (not an invented API).

## Confirmed Clock/TimeSource Abstraction (do not guess)

Canonical abstraction and DI anchor:
- `core/time/src/main/java/com/trust3/xcpro/core/time/Clock.kt`
- `app/src/main/java/com/trust3/xcpro/di/TimeModule.kt`

Additional time adapter used in map-orientation path:
- `feature/map/src/main/java/com/trust3/xcpro/orientation/OrientationClock.kt`

Current gate baseline (2026-03-01):
- Command: `python scripts/arch_gate.py`
- Result: failed with 41 violations
- Typical offenders: profile export/import timestamps, map overlays/controllers using `SystemClock`, and direct `Date(...)` formatting in production UI/use-case paths

## Artifacts To Create or Update

- `.github/workflows/quality-gates.yml`
- `scripts/arch_gate.py`
- `docs/ARCHITECTURE/AGENT.md`
- `docs/ARCHITECTURE/ARCHITECTURE.md`
- `docs/ARCHITECTURE/CODEBASE_CONTEXT_AND_INTENT.md`
- `docs/ARCHITECTURE/CODING_RULES.md`
- `docs/ARCHITECTURE/CONTRIBUTING.md`
- `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md`
- `docs/ARCHITECTURE/PIPELINE.md`
- `docs/ARCHITECTURE/README.md`

## Required Policy

Production Kotlin must not call direct time APIs outside approved adapter files.

Forbidden patterns:
- `System.currentTimeMillis()`
- `System.nanoTime()`
- `SystemClock.*`
- `Instant.now(...)`
- `LocalDateTime.now(...)`
- `ZonedDateTime.now(...)`
- `OffsetDateTime.now(...)`
- `Calendar.getInstance(...)`
- `Date(...)`
- `kotlin.system.getTimeMillis()`

Allowed contexts:
- `src/test`, `src/androidTest`
- scripts/tooling/build logic (`scripts`, `buildSrc`, `.github`, `gradle`)
- explicit adapter files that bridge system time to abstractions

## Implementation Plan

### Phase A: Audit and locate time architecture

Use search to identify:
- direct time calls
- `Clock`/`TimeSource` usage
- monotonic source entry points

Record real file paths in docs and in PR summary.

### Phase B: Local architecture gate script

Implement/update `scripts/arch_gate.py`:
- fast, dependency-free, repo-wide Kotlin scan
- clear exclusions for tests/tooling dirs
- explicit output with file:line and pattern
- non-zero exit code on violations

### Phase C: CI workflow

Implement/update `.github/workflows/quality-gates.yml`:
- trigger on `pull_request` and push to `main`/`master`
- run:
  - `python scripts/arch_gate.py`
  - `./gradlew enforceRules`
  - `./gradlew testDebugUnitTest`
  - `./gradlew assembleDebug`

### Phase D: Documentation alignment (8 files)

1) `README.md`
- Keep deviation status as single-source-of-truth in `KNOWN_DEVIATIONS.md`.
- Add a short "How to validate locally" block.

2) `KNOWN_DEVIATIONS.md`
- Add required entry template (issue/owner/expiry/risk/mitigation/removal/exit criteria).
- Add rule that README must not duplicate deviation status.

3) `ARCHITECTURE.md`
- Remove ambiguous monotonic fallback wording.
- Require explicit adapter policy when monotonic time is unavailable.

4) `CODING_RULES.md`
- Add explicit automation entrypoints (`scripts/arch_gate.py`, CI workflow).
- Document safe process for adding/tightening rules.

5) `CONTRIBUTING.md`
- Add role-based command sets:
  - fast loop
  - PR loop
  - release loop

6) `PIPELINE.md`
- Add an "Automated Quality Gates" section describing what blocks regressions.

7) `AGENT.md`
- Add minimum evidence requirements per gate (real path citations + gate output).

8) `CODEBASE_CONTEXT_AND_INTENT.md`
- Add enforceable invariants section tied to gate artifacts and deviation source-of-truth.

### Phase E (optional): tighten to abstraction usage by layer

After baseline gates are stable:
- tighten rule so domain/fusion paths must use `Clock` abstraction, not direct APIs
- restrict direct platform time access to adapter/infrastructure boundaries

## Acceptance Criteria

- Gate script and workflow exist and run.
- Required docs are updated and no longer contradictory.
- `KNOWN_DEVIATIONS.md` remains authoritative for deviation status.
- Architecture/time policy references real XCPro paths (`Clock.kt`, `TimeModule.kt`).
- Current violations are either fixed in code or tracked with time-boxed entries in `KNOWN_DEVIATIONS.md`.

## Verification Commands

```bash
python scripts/arch_gate.py
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```
