# KNOWN_DEVIATIONS.md

Audit date: 2026-01-27

This file lists known deviations from ARCHITECTURE.md and CODING_RULES.md.
Each entry must include an issue ID, owner, and expiry date.

## Current deviations

None.

## Verification

Last verified: 2026-01-28 (unit tests re-run after MapOverlayWidgetGesturesTest moved to Robolectric)
- Commands: ./gradlew testDebugUnitTest (re-run), ./gradlew enforceRules, ./gradlew lintDebug, ./gradlew assembleDebug, powershell -File scripts/ci/enforce_rules.ps1, preflight.bat

## Resolved deviations

1) Timebase usage in domain/fusion
- Rule: injected clock only; no System.currentTimeMillis in domain or fusion.
- Resolved: 2026-01-27
- Notes: Removed direct system time usage; injected Clock or explicit timestamps.

2) DI: pipeline constructed inside manager
- Rule: core pipeline components must be injected.
- Resolved: 2026-01-27
- Notes: Sensor fusion pipeline provided via Hilt; managers no longer construct it directly.

3) ViewModel purity
- Rule: ViewModels must not touch SharedPreferences or UI types.
- Resolved: 2026-01-27
- Notes: SharedPreferences moved to repositories/use-cases; Compose types removed from ViewModels; tests updated.

4) Compose lifecycle collection
- Rule: use collectAsStateWithLifecycle for UI state.
- Resolved: 2026-01-27
- Notes: Replaced collectAsState with collectAsStateWithLifecycle in UI layers.

5) Vendor string policy
- Rule: no "xcsoar"/"XCSoar" literals in production Kotlin source.
- Resolved: 2026-01-27
- Notes: Vendor literals removed/renamed in production Kotlin.

6) ASCII hygiene
- Rule: no non-ASCII characters in production Kotlin source.
- Resolved: 2026-01-27
- Notes: Non-ASCII characters replaced with ASCII equivalents in production Kotlin.
