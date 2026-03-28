# Agent Contract: ADS-B Default Icon Phased Execution

Date: 2026-03-08  
Scope: `CHANGE_PLAN_ADSB_DEFAULT_ICON_MEDIUM_PHASED_IP_2026-03-08.md`

## 1) Execution Mode

1. Execute phases in strict order: `Phase 0 -> Phase 1 -> Phase 2 -> Phase 3 -> Phase 4`.
2. Do not skip phase gates.
3. After each phase, run a mandatory basic build gate:
   - `./gradlew assembleDebug --no-configuration-cache`
4. If a phase build fails:
   - fix within phase scope,
   - rerun basic build,
   - do not proceed to next phase until pass.
5. Proceed automatically to next phase when gate is green.

## 2) Architecture Contract

1. Preserve MVVM + UDF + SSOT layering.
2. Keep dependency direction `UI -> domain -> data`.
3. Keep domain/data truth unchanged for unknown classification semantics.
4. Keep replay deterministic for identical ordered inputs.
5. Use monotonic time for runtime icon projection/latency telemetry logic.

## 3) Phase Gates

### Phase 0

1. Lock baseline behavior and telemetry counters.
2. Verify unknown render and resolve latency counters.
3. Run basic build gate.

### Phase 1

1. Keep unknown semantic truth while default visual fallback is medium plane icon.
2. Verify projection tests.
3. Run basic build gate.

### Phase 2

1. Preserve bounded metadata hydration and fairness/no-starvation behavior.
2. Verify metadata repository tests.
3. Run basic build gate.

### Phase 3

1. Enable sticky icon projection cache with deterministic TTL behavior.
2. Authoritative non-fixed-wing categories override sticky hold immediately.
3. Run basic build gate.

### Phase 4

1. Add rollout flag and rollback/kill-switch path for default medium unknown icon behavior.
2. Add rollout observability fields/counters.
3. Ensure legacy fallback path remains available for rollback.
4. Run basic build gate.

## 4) Minimum Reporting After Each Phase

1. Files changed.
2. Build command run + pass/fail.
3. Residual risks or deductions.
4. Updated phase score `/100` with rubric split:
   - Architecture compliance: 30
   - UX outcome: 25
   - Performance/latency: 20
   - Test confidence: 15
   - Rollout safety/observability: 10

## 5) Stop Conditions

1. Architecture violation that cannot be fixed in-phase.
2. Replay determinism conflict.
3. Repeated build failure after two corrective attempts.
4. Scope drift outside ADS-B phase plan.
