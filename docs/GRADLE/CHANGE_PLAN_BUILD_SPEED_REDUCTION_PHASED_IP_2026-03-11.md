# CHANGE PLAN: Build Speed Reduction — Phased IP (2026-03-11)

## 0) Metadata
- Title: Build speed reduction and test-path strip-down for faster local feature iteration
- Owner: Codex / XCPro Team
- Date: 2026-03-11
- Issue/PR: Pending
- Status: In Progress
- Baseline Score: 81/100

## 1) Scope

### Problem statement
Local iteration time is dominated by rebuild breadth after edits in traffic/map paths, not by steady-state test suite runtime.

### Why now
Recent baseline confirms build flags are mostly tuned; the faster wins are now workflow and module-boundary control.

### In scope
- Remove non-deterministic helper retries from local build-speed tooling.
- Keep default local loops compile/assemble-first with explicit tests only.
- Reduce accidental invocation of heavy workflows from scripts/docs.
- Establish a safe phased execution order for any additional traffic/map trim work.

### Out of scope
- Production behavior changes (ADS-B rollout/shadow policy, emergency audio logic, map feature behavior).
- CI-wide retry policy changes for flaky tests outside local developer workflow.

### User-visible impact
- No visible runtime feature impact from this plan as shipped in this phase.

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| build-speed evidence | `docs/GRADLE/BASELINE_BUILD_MEASUREMENTS_2026-03-10.md` | markdown table | stale command references in helper docs |
| historical edit-impact benchmark path | retained docs/workflow notes | markdown tables and guidance text | stale helper-script references |
| workflow behavior | local wrapper scripts (`check-quick.bat`, `preflight.bat`, `auto-test.bat`) | explicit task args only | implicit retry wrappers in local path |

### 2.2 Dependency direction
No domain/architecture layer changes in this phase. No dependency rewiring.

### 2.3 Bypass removal (this phase)

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| retired edit-impact benchmark helper with `-RepairOnFailure` | automatic repair + retry | explicit one-pass measurement only | 1 |

### 2.4 Time Base
No product timebase changes. Scripts remain wall-time for measurement only.

### 2.5 Enforcement coverage

| Risk | Rule Reference | Guard Type | File/Test |
|---|---|---|---|
| Old helper option remains documented after parser removal | Docs/implementation mismatch | review + smoke run | retired benchmark helper notes, `scripts/dev/README.md`, `docs/GRADLE/BASELINE_BUILD_MEASUREMENTS_2026-03-10.md` |
| Hidden retry behavior remains in local loops | Build behavior regression | smoke test + diff check | `check-quick.bat`, `preflight.bat`, `auto-test.bat` |

## 3) Data flow

Before:

```
change -> edit-impact benchmark helper (retry-on-failure helper) -> edited synthetic markers -> repeat build unexpectedly
```

After:

```
change -> edit-impact benchmark helper (single-pass deterministic edit loops) -> edited synthetic markers -> single build per run
```

## 4) Implementation phases

### Phase 1 — Remove helper retry behavior from local benchmark path (current)

- Files:
  - retired edit-impact benchmark helper
  - `scripts/dev/README.md`
  - `docs/GRADLE/BASELINE_BUILD_MEASUREMENTS_2026-03-10.md`
- Scope:
  - Remove `-RepairOnFailure` parameter and automatic repair retry path.
  - Remove `-RepairOnFailure` from docs/examples.
- Exit criteria:
  - Parameter no longer accepted.
  - Scripts/docs consistent.
  - No extra hidden benchmark re-run on build failure.

### Phase 2 — Build loop clarity lock-in

- Files:
  - `docs/GRADLE/README.md`
  - `check-quick.bat`, `preflight.bat`, `auto-test.bat`
- Scope:
  - Add explicit notes that default local loops are compile/assemble only.
  - Keep explicit test invocation required for test runs.
- Exit criteria:
  - Developers must opt in to test tasks.

### Phase 3 — Compile-surface verification before further refactor

- Files:
  - map/traffic overlay boundary tests and build-surface docs
- Scope:
  - Verify `feature/traffic` traffic/debug runtime ownership remains consistent after Phase 1.
  - Confirm `MapTrafficDebugPanels.kt` shim remains API-only shim.
- Exit criteria:
  - No duplicate runtime behavior in feature/map and feature/traffic.

### Phase 4 — Optional performance hardening

- Files:
  - `gradle.properties`, `build.gradle.kts` (where required)
- Scope:
  - Evaluate one pass on `:app:compileDebugKotlin` after targeted map edits.
- Exit criteria:
  - No behavior/perf regression in required checks.

### Phase 5 — Governance / rollback

- Scope:
  - Add changelog note and residual risk log in AGENT docs if rollout-safe changes are later added.
- Exit criteria:
  - Change log complete, verification trace captured.

## 5) Test plan

- one-pass edit-impact benchmark invocation for `:app:compileDebugKotlin`
- one-pass edit-impact benchmark invocation for `:app:compileDebugKotlin`
  scoped to the `core-impl` scenario
- `Get-Content`/smoke checks for stale docs:
  - no `-RepairOnFailure` in `scripts/dev/README.md` and `docs/GRADLE/BASELINE...`
- Script option check:
  - call with unknown parameter should fail quickly with argument binding error (expected in PowerShell by design).

Required repo checks (from AGENTS):

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

## 6) Risks and mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| Removing repair path slows recovery from rare generated-state lock corruption | More manual reruns if state is dirty | Keep `repair-build.bat` and lock cleanup docs in `docs/GRADLE/README.md` for recovery | Codex |
| Docs still mention removed param | Confusion during handoff | CI-like docs sweep in Phase 2; update references in Phase 1 files now | Codex |
| Additional compile-surface work gets mixed into this phase | Increased risk from scope creep | Gate new work behind explicit phase transitions | Team |

## 7) Acceptance gates

- Default local loops are no longer implicitly retrying build work.
- No hidden test suite invocation in local speed scripts.
- Required checks in AGENTS pass after this round of changes.

## 8) Rollback plan

- Restore the retired edit-impact benchmark helper and docs from the previous
  revision if needed.
- Keep `scripts/dev/README.md` and baseline doc command snippets as-is until full rerun of evidence if required.

### Score
- Plan score (target): **88 / 100**
- Score basis:
  - + clarity of phase gates (24/25)
  - + low blast radius in this round (22/25)
  - + explicit verification steps (20/25)
  - + residual recovery/manual friction risk from removed auto-repair (12/25)
