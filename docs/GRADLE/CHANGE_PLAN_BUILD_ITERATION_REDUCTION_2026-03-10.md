# CHANGE PLAN: Build Iteration Reduction 2026-03-10

## 0) Metadata

- Title: Build iteration reduction after edit-sensitive baseline
- Owner: Codex / XCPro Team
- Date: 2026-03-10
- Issue/PR: Pending
- Status: Draft

## 1) Scope

- Problem statement:
  - Warm no-edit tasks are already fast, but real source edits in
    `feature:map` and shared ABI edits in `core:common` cause broad rebuilds.
- Why now:
  - Measured edit-sensitive rebuild cost is now the clearest blocker to local
    feature velocity.
- In scope:
  - module-boundary and compile-surface reduction for high-churn traffic/map
    runtime code
  - narrow API design to reduce ABI churn
  - preserving current runtime behavior while lowering recompilation breadth
- Out of scope:
  - CI/distributed cache infrastructure
  - release packaging changes
  - production behavior changes unrelated to build iteration
- User-visible impact:
  - none intended; this is a maintainability/build-speed refactor

## 2) Architecture Contract

### 2.1 SSOT Ownership

No product SSOT changes are intended.

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| traffic targets | existing traffic repositories | existing use-case flows | duplicate repository mirrors in UI/runtime |
| map overlay state | existing map runtime owners | existing runtime/controller APIs | new parallel map state owners |
| build benchmark evidence | `docs/GRADLE` + `scripts/dev` | markdown docs + scripts | ad-hoc undocumented measurements |

### 2.2 Dependency Direction

Dependency flow remains:

`UI -> domain -> data`

- Modules/files touched:
  - `feature:map`
  - `feature:traffic`
  - `app` only if dependency wiring changes
  - `docs/GRADLE`
  - `scripts/dev`
- Any boundary risk:
  - introducing cyclic dependency between `feature:map` and `feature:traffic`
  - leaking MapLibre/UI runtime types into data/domain layers

### 2.2A Boundary Moves (Mandatory)

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| traffic render implementation details | `feature:map` | `feature:traffic` or a narrow runtime sub-slice | reduce `feature:map` compile breadth | targeted compile benchmarks |
| map traffic debug panel implementation | `feature:map` | traffic-owned implementation behind map facade | reduce high-churn UI/runtime recompiles | targeted compile benchmarks |

### 2.2B Bypass Removal Plan (Mandatory)

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| map runtime directly owning all traffic render details | broad direct ownership in `feature:map` | narrow facade + implementation sub-slice | Phase 1 |

### 2.3 Time Base

No production time-base change is intended.

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| build timing evidence in scripts | Wall | local developer measurement only |
| production overlay timing | existing monotonic/replay rules | must remain unchanged |

Explicitly forbidden comparisons:

- Monotonic vs wall
- Replay vs wall

### 2.4 Threading and Cadence

- Dispatcher ownership: unchanged in production code
- Primary cadence/gating sensor: unchanged
- Hot-path latency budget: build-iteration target, not runtime latency, is the
  focus here

### 2.5 Replay Determinism

- Deterministic for same input: Yes, unchanged target
- Randomness used: No new randomness
- Replay/live divergence rules: unchanged

### 2.6 Enforcement Coverage (Mandatory)

| Risk | Rule Reference | Guard Type (lint/enforceRules/test/review) | File/Test |
|---|---|---|---|
| cyclic module dependency | architecture dependency direction | review + compile verification | `settings.gradle.kts`, module build files |
| Hilt/KSP drift during split | DI rules | compile verification + targeted tests | `:app:compileDebugKotlin`, `:app:assembleDebug` |
| map behavior regression | map runtime contract | unit/instrumentation where relevant | existing map overlay tests |
| undocumented benchmark claims | AGENT evidence requirement | docs review | `docs/GRADLE/*` |

## 3) Data Flow (Before -> After)

Before:

```text
Traffic repositories/use-cases -> feature:map runtime/render implementations -> app compile
```

After:

```text
Traffic repositories/use-cases -> narrow traffic render facade -> feature:map runtime owner -> app compile
```

Goal: keep runtime ownership and SSOT behavior stable while reducing how much
`feature:map` recompiles after traffic-render edits.

## 4) Implementation Phases

### Phase 0 - Baseline lock

- Goal:
  - keep current measurement evidence and benchmark scripts stable
- Files to change:
  - `docs/GRADLE/*`
  - `scripts/dev/*`
- Tests to add/update:
  - none required beyond script verification
- Exit criteria:
  - current baseline is documented and reproducible

### Phase 1 - First compile-surface reduction

- Goal:
  - extract the implementation-only traffic overlay/debug slice out of
    `feature:map` behind a narrow facade
- Files to change:
  - likely start with:
    - `feature/map/src/main/java/com/example/xcpro/map/AdsbTrafficOverlay.kt`
    - `feature/map/src/main/java/com/example/xcpro/map/OgnTrafficOverlay.kt`
    - `feature/map/src/main/java/com/example/xcpro/map/ui/MapTrafficDebugPanelsAdsb.kt`
    - `feature/map/src/main/java/com/example/xcpro/map/ui/MapTrafficDebugPanelsOgn.kt`
    - supporting config/helper classes
- Tests to add/update:
  - targeted map overlay/render tests
  - compile benchmark rerun
- Exit criteria:
  - editing the extracted slice no longer recompiles the full `feature:map`
    implementation surface to the same extent

### Phase 2 - Runtime facade hardening

- Goal:
  - keep `MapOverlayManagerRuntime` as a facade while reducing direct ownership
    of traffic-specific render details
- Files to change:
  - `MapOverlayManagerRuntime*`
  - traffic facade types
- Tests to add/update:
  - runtime lifecycle and overlay interaction tests
- Exit criteria:
  - traffic runtime implementation is isolated behind narrow APIs

### Phase 3 - ABI tightening

- Goal:
  - reduce public/shared ABI churn in the extracted slice
- Files to change:
  - public constants, exposed models, helper APIs
- Tests to add/update:
  - compile benchmark rerun for ABI-edit scenarios
- Exit criteria:
  - ABI edits have a smaller downstream footprint or are rarer by design

### Phase 4 - Verification

- Goal:
  - confirm build wins and no behavior regression
- Files to change:
  - docs and any final cleanup
- Tests to add/update:
  - rerun benchmark scripts and required repo checks
- Exit criteria:
  - evidence shows improvement and required checks still pass

## 5) Test Plan

- Unit tests:
  - existing overlay and runtime support tests that cover extracted behavior
- Replay/regression tests:
  - unchanged unless traffic replay wiring is affected
- UI/instrumentation tests:
  - only where moved debug panel/render ownership changes behavior
- Degraded/failure-mode tests:
  - build benchmark reruns after implementation-only and ABI edits
- Boundary tests for removed bypasses:
  - ensure `feature:map` talks through the new facade only

Required checks:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Additional local build-speed evidence:

```bash
powershell -NoProfile -File .\scripts\dev\measure_map_build.ps1 -Tasks ':app:compileDebugKotlin'
powershell -NoProfile -File .\scripts\dev\measure_edit_impact.ps1 -Tasks ':app:compileDebugKotlin'
```

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| module cycle between `feature:map` and `feature:traffic` | build break | keep a narrow one-way facade and verify Gradle graph early | Codex |
| moving Hilt/KSP-heavy code too early | compile churn or KSP failures | start with implementation-only render/debug code first | Codex |
| runtime regression in overlay behavior | user-visible map regression | keep `MapOverlayManagerRuntime` facade stable and rerun overlay tests | XCPro Team |
| misleading benchmark due to stale generated state | false conclusions | use `repair-build.bat` when KSP/generated state fails | Codex |

## 7) Acceptance Gates

- No rule violations from `ARCHITECTURE.md` and `CODING_RULES.md`
- No duplicate SSOT ownership introduced
- Time base handling remains explicit and unchanged
- Replay behavior remains deterministic
- `KNOWN_DEVIATIONS.md` updated only if explicitly approved
- New benchmark evidence shows lower edit-sensitive rebuild cost for the
  targeted slice

## 8) Rollback Plan

- What can be reverted independently:
  - facade introduction
  - extracted implementation slice
  - benchmark docs/scripts
- Recovery steps if regression is detected:
  - restore traffic render implementation ownership to `feature:map`
  - keep benchmark docs for post-mortem comparison
