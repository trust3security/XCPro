# AGENT CONTRACT - PROFILE GAP CLOSURE AUTOMATION (PHASES 0-6)

## Purpose

Define a production-grade autonomous execution contract for implementing:

- `docs/PROFILES/PROFILE_PRODUCTION_GRADE_PHASED_IP_PROFILE_GAP_CLOSURE_2026-03-10.md`

This contract is focused on closing currently unsaved profile portability gaps
without violating architecture, determinism, or security boundaries.

## 0) Execution Authority

- Mode: autonomous.
- User prompts: not required for normal execution.
- Decision rule under ambiguity: choose architecture-consistent behavior and
  document rationale in execution notes.
- Forbidden shortcuts:
  - skipping phase gates,
  - putting business logic in UI/ViewModels,
  - bypassing repository/use-case boundaries,
  - storing forecast credentials in plain-text bundle output,
  - destructive git operations.

## 1) Mandatory Read Order

Read before code changes:

1. `AGENTS.md`
2. `docs/ARCHITECTURE/ARCHITECTURE.md`
3. `docs/ARCHITECTURE/CODING_RULES.md`
4. `docs/ARCHITECTURE/PIPELINE.md`
5. `docs/ARCHITECTURE/CODEBASE_CONTEXT_AND_INTENT.md`
6. `docs/ARCHITECTURE/CONTRIBUTING.md`
7. `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md`
8. `docs/ARCHITECTURE/AGENT.md`
9. `docs/PROFILES/PROFILE_STORAGE_AND_SETTINGS_SCOPE.md`
10. `docs/PROFILES/PROFILE_FILE_PORTABILITY_STRATEGY_2026-03-10.md`
11. `docs/PROFILES/PROFILE_PRODUCTION_GRADE_PHASED_IP_PROFILE_GAP_CLOSURE_2026-03-10.md`

## 2) Pre-Approved Policy Decisions

No extra approval required for these defaults:

1. Include in profile by default:
   - map style,
   - snail trail settings,
   - orientation extra fields (`autoResetEnabled`, `autoResetTimeoutSeconds`,
     `bearingSmoothingEnabled`),
   - waypoint file manifest state (names + enabled flags only),
   - airspace manifest state + selected classes (no binaries),
   - ADS-B default-medium-unknown rollout flags.
2. QNH is included with safety controls (metadata + recency guard).
3. Forecast credentials are excluded from default bundle export/import.
4. Raw waypoint/airspace file binaries remain out of profile JSON.

## 3) Non-Negotiable Architecture Rules

1. Preserve `UI -> domain/use-case -> data`.
2. Keep one SSOT owner per settings domain.
3. ViewModels consume use-cases only.
4. No hidden global mutable state.
5. No replay determinism regressions.
6. Any unavoidable exception requires a time-boxed
   `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md` entry (issue, owner, expiry).

## 4) Phase Order and Global Gates

Strict order: `0 -> 1 -> 2 -> 3 -> 4 -> 5 -> 6`.

Global phase gate (mandatory before next phase):

```bash
./gradlew assembleDebug
```

For non-trivial phase closures, also run:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
```

## 5) Phase Contract (0 To 6)

### Phase 0 - Contract Freeze

Implement:

1. Finalize new section IDs and settings-scope docs.
2. Lock include/exclude matrix for this gap set.

Exit gate:

1. Section IDs and policy documented with no ambiguity.

### Phase 1 - Repository Profile Scoping

Implement:

1. Add profile-scoped runtime ownership for:
   - map style,
   - snail trail,
   - QNH prefs (with metadata key),
   - waypoint manifest selection state,
   - airspace manifest selection/class state.
2. Ensure active profile switching updates these owners.

Exit gate:

1. Profile A/B isolation tests pass for each migrated owner.
2. No cross-profile bleed.

### Phase 2 - Snapshot/Restore Schema Expansion

Implement:

1. Add snapshot/restore sections:
   - `tier_a.map_style_preferences`
   - `tier_a.snail_trail_preferences`
   - `tier_a.qnh_preferences`
   - `tier_a.waypoint_file_preferences`
   - `tier_a.airspace_preferences`
2. Extend:
   - `tier_a.orientation_preferences` payload (missing fields),
   - `tier_a.adsb_traffic_preferences` payload (default-medium-unknown rollout).
3. Preserve backward compatibility for old bundles.

Exit gate:

1. Round-trip tests pass for all new/extended sections.
2. Legacy import compatibility tests pass.

### Phase 3 - Import Scope + Cleanup Integration

Implement:

1. Add new profile-scoped section IDs to scoped-import filtering.
2. Extend profile delete cleanup for new profile-scoped owners.
3. Ensure switch-time hydration path is complete.

Exit gate:

1. `PROFILE_SCOPED_SETTINGS` scope tests pass.
2. Profile delete cleanup tests pass.

### Phase 4 - QNH Safety Hardening

Implement:

1. Capture QNH metadata (`capturedAtWallMs`, source marker).
2. Enforce import recency guard.
3. Stale QNH must not auto-apply silently.

Exit gate:

1. Fresh/stale QNH behavior tests pass.
2. Import output explicitly reports staged/not-applied stale QNH.

### Phase 5 - Secure Credentials Portability (Optional Path)

Default required behavior:

1. Forecast credentials remain excluded from normal profile bundles.

Optional implementation (if product enables):

1. Explicit opt-in secure credential export/import path only.
2. Encryption required; no plain-text credential payloads.

Exit gate:

1. Default export contains no credentials.
2. If optional secure path is added, encryption failure tests pass.

### Phase 6 - Production Verification and Closure

Implement:

1. Final documentation sync and troubleshooting updates.
2. End-to-end verification for switch/export/import portability.

Required checks:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Targeted checks:

```bash
./gradlew :app:testDebugUnitTest --tests "com.example.xcpro.profiles.*"
./gradlew :feature:map:testDebugUnitTest --tests "*Profile*"
./gradlew :feature:profile:testDebugUnitTest
```

Exit gate:

1. All required checks pass.
2. New profile sections behave correctly across switch + export/import flows.

## 6) Evidence Requirements (Per Phase)

Must record:

1. Files changed.
2. SSOT ownership changes.
3. Tests added/updated.
4. Commands run and pass/fail.
5. Risks and mitigations.
6. Whether `KNOWN_DEVIATIONS.md` changed.

Recommended log target:

- `docs/PROFILES/EXECUTION_LOG_PROFILE_GAP_CLOSURE_PHASES_0_6_2026-03-10.md`

## 7) Failure and Rollback Protocol

1. If a phase gate fails, fix in-phase and rerun.
2. If root cause is unclear, rollback current phase delta only and re-slice.
3. Never hide failures with silent fallbacks.
4. Never use destructive reset commands.

## 8) Completion Criteria

Complete only when all are true:

1. Phases `0..6` executed in order.
2. Required verification is green.
3. No unresolved SSOT ambiguity for new sections.
4. No plain-text forecast credentials in default profile bundles.
5. Final quality rescore is documented with evidence and residual risks.

