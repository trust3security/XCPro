# AGENT_EXECUTION_CONTRACT_PROXIMITY_PHASED_FAST_THEN_FULL_2026-03-04

## 0) Metadata

- Scope: ADS-B proximity production hardening phases
- Applies to: `docs/PROXIMITY/CHANGE_PLAN_ADSB_PROXIMITY_PRODUCTION_GRADE_2026-03-03.md`
- Date: 2026-03-04
- Mode: Autonomous execution
- Verification cadence: Fast/basic after each phase, full suite once after final phase

## 0A) Automation Entrypoint

Primary runner:

```bash
powershell -ExecutionPolicy Bypass -File scripts/qa/run_proximity_phase_gates.ps1
```

Windows wrapper:

```bat
scripts\qa\run_proximity_phase_gates.bat
```

Useful options:

```bash
powershell -ExecutionPolicy Bypass -File scripts/qa/run_proximity_phase_gates.ps1 -Resume
powershell -ExecutionPolicy Bypass -File scripts/qa/run_proximity_phase_gates.ps1 -FromPhase 3 -ToPhase 5
powershell -ExecutionPolicy Bypass -File scripts/qa/run_proximity_phase_gates.ps1 -DryRun
```

## 1) Execution Authority

The agent must:

1. Execute phases end-to-end without pausing for confirmation unless blocked by missing local context.
2. Follow `AGENTS.md` and architecture docs as non-negotiable.
3. Preserve MVVM + UDF + SSOT, dependency direction, and deterministic replay.
4. Keep touched production Kotlin files `< 500` lines.
5. Fix rule violations immediately, or record a time-boxed deviation in `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md`.

## 2) Phase Set

This contract runs all phases in order:

1. Phase 0: Baseline lock and replay corpus
2. Phase 1: Predictive/policy correctness hardening
3. Phase 2: Freshness/quality gating and first-sample behavior
4. Phase 3: Tier semantics + UI truthfulness hardening
5. Phase 4: Runtime wiring + rollout control hardening
6. Phase 5: KPI accumulation and threshold observability
7. Phase 6: Operational lifecycle/audio resilience hardening
8. Phase 7: Controlled rollout/rollback drill readiness
9. Phase 8: Production signoff package

No phase skipping. No parallel phase execution.

## 3) Per-Phase Fast/Basic Gate (Mandatory)

After each phase, run only fast/basic verification:

```bash
./gradlew enforceRules
./gradlew :feature:map:testDebugUnitTest --tests "com.trust3.xcpro.adsb.*" --tests "com.trust3.xcpro.map.*Adsb*"
./gradlew :feature:map:assembleDebug
```

Windows:

```bat
.\gradlew.bat enforceRules
.\gradlew.bat :feature:map:testDebugUnitTest --tests "com.trust3.xcpro.adsb.*" --tests "com.trust3.xcpro.map.*Adsb*"
.\gradlew.bat :feature:map:assembleDebug
```

If a phase touches non-ADS-B map/task logic, add targeted test patterns for touched packages in the same fast gate.

Phase transition rule:

1. If fast gate fails, do not start next phase.
2. Fix failures and rerun fast gate until green.
3. Record pass/fail and changed files before moving on.

## 4) Final Full Gate (Run Once, After Last Phase)

Run full verification only after Phase 8 is complete:

```bash
python scripts/arch_gate.py
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

When device/emulator is available:

```bash
./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"
./gradlew connectedDebugAndroidTest --no-parallel
```

Windows full gate:

```bat
python scripts/arch_gate.py
.\gradlew.bat enforceRules
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

## 5) Required Phase Report Template

Each phase must output:

1. What changed (policy/wiring/tests/docs)
2. Files touched
3. Fast/basic commands run
4. Pass/fail result for each command
5. Open risks and next phase entry condition

Final report (after full gate) must include:

1. Full gate command results
2. Architecture drift self-audit
3. Residual risks
4. Production readiness score and rationale

## 6) Safety/Determinism Constraints

Mandatory during every phase:

1. No business logic in UI.
2. No direct system time calls in domain/fusion paths.
3. No hidden mutable global state.
4. Replay must remain deterministic for identical input.
5. No bypass of use-case/repository boundaries.

## 7) Completion Criteria

This contract is complete only when:

1. Phases 0-8 are implemented in order.
2. Fast/basic gate passed for every phase.
3. Final full gate passed after Phase 8.
4. No unresolved architecture-rule violations remain.
5. Any unavoidable deviation is documented with issue, owner, and expiry.
