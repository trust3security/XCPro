# Quality Gates Release-Grade Rationalization Plan (Fast Local Gate + Full CI Gate)

## 0) Metadata
- Date: 2026-03-12
- Owner: Codex
- Status: In Progress
- Drivers:
  - Keep release-grade architecture enforcement without forcing the full heavy rule bucket into every local quick-check loop.
  - Remove duplicated static-scan ownership where possible.
  - Move toward standard, non-ad hoc enforcement over time.
- Non-goals:
  - No product/runtime behavior changes.
  - No module graph changes.
  - No Kotlin/Gradle compile optimization work in this plan.
- Progress note:
  - 2026-03-12: Phase 1 and Phase 2 implemented (`enforce_rules.ps1` rule-set split + `enforceArchitectureFast` task).
  - 2026-03-12: Phase 3 implemented (`check-quick.bat` and `auto-test.bat` now use `enforceArchitectureFast`; `preflight.bat` and CI unchanged).
  - 2026-03-12: Phase 4 implemented (`enforceRules` now aggregates `archGate` + repository rules; duplicate final-gate arch scans removed from CI and active QA harnesses).

## 1) Scope
This plan rationalizes local and CI quality-gate execution for the current repository tooling:
- [scripts/arch_gate.py](/C:/Users/Asus/AndroidStudioProjects/XCPro/scripts/arch_gate.py)
- [scripts/ci/enforce_rules.ps1](/C:/Users/Asus/AndroidStudioProjects/XCPro/scripts/ci/enforce_rules.ps1)
- [build.gradle.kts](/C:/Users/Asus/AndroidStudioProjects/XCPro/build.gradle.kts)
- [.github/workflows/quality-gates.yml](/C:/Users/Asus/AndroidStudioProjects/XCPro/.github/workflows/quality-gates.yml)
- [check-quick.bat](/C:/Users/Asus/AndroidStudioProjects/XCPro/check-quick.bat)
- [auto-test.bat](/C:/Users/Asus/AndroidStudioProjects/XCPro/auto-test.bat)
- [preflight.bat](/C:/Users/Asus/AndroidStudioProjects/XCPro/preflight.bat)
- developer workflow docs that describe the supported verification path

Why this change:
- `enforceRules` currently combines architecture boundaries, hygiene checks, line budgets, and product-specific regression grep rules in a single PowerShell script.
- CI currently pays for overlapping static scans by running both `python scripts/arch_gate.py` and `./gradlew enforceRules`.
- `check-quick.bat` and `auto-test.bat` currently run the full heavy gate, so "quick" local verification is slower than it should be.
- The repo already has a fast inner-loop entrypoint in `dev-fast.bat`; this plan aligns the surrounding wrappers with that intent.

Expected outcome:
- Faster local verification loops.
- Lower CI static-gate wall-clock time.
- No reduction in release-grade enforcement coverage during the transition.
- Clearer ownership for architecture rules versus product-behavior regression protection.

Important constraint:
- This plan improves verification speed, not Kotlin/Gradle compilation speed. `assembleDebug` and `compileDebugKotlin` are not expected to materially change from this work alone.

## 2) Architecture Contract

### 2.1 SSOT Ownership
| Concern | Current Primary Owner | Target Primary Owner |
| --- | --- | --- |
| Direct time API misuse in domain/fusion paths | `arch_gate.py` and `enforce_rules.ps1` (duplicated) | `arch_gate.py` in fast/static phase, then one explicit owner only |
| App identity contract (`applicationId`, debug suffix) | `arch_gate.py` | `arch_gate.py` |
| Repo-wide architecture/hard-boundary enforcement | `enforce_rules.ps1` | `enforce_rules.ps1` aggregate, with architecture subset exposed separately |
| Local quick verification contract | batch wrappers + ad hoc user choice | explicit `enforceArchitectureFast` + targeted tests/build |
| CI/release verification contract | workflow + `enforceRules` | workflow + full `enforceRules` |

Release-grade rule ownership must be explicit. The same invariant should not be maintained by two independent static scans unless there is a deliberate, documented fallback reason.

### 2.2 Dependency Direction / Boundary Moves
No product dependency direction changes are allowed in this plan.

Tooling contract changes:
- Local quick wrappers should depend on a fast architecture-focused gate, not the full aggregate release gate.
- CI/release should continue to depend on the full aggregate release gate until rule migration is complete.
- Product-specific behavior protection should move from grep-based static checks toward tests.

### 2.3 Bypass Removal
Current bypass-like friction:
- `check-quick.bat` and `auto-test.bat` route developers through the full heavy `enforceRules` task even when the intent is a quick local check.
- CI runs overlapping timebase/static scans in two separate commands.

Target replacements:
- Introduce `enforceArchitectureFast` for local quick verification.
- Keep `enforceRules` as the release aggregate.
- Remove duplicated static ownership once parity is verified.

### 2.4 Time / Determinism Contract
This plan does not change replay/time semantics. Existing repo contracts remain:
- monotonic time for elapsed-duration calculations
- injected wall-clock sources where required
- deterministic replay behavior

Only the verification ownership changes:
- timebase misuse remains a hard gate
- the rule should have one explicit static owner in the final state

### 2.5 Coverage Risk Contract
The following are unacceptable:
- weakening release gates without an equivalent replacement
- losing rule coverage during migration
- leaving quick wrappers on the heavy full gate after the fast gate exists
- allowing product-behavior grep guards to disappear before tests replace them where needed

## 3) Data Flow (Before -> After)

### Before
1. Developer uses `check-quick.bat` or `auto-test.bat`.
2. Wrapper runs full `./gradlew enforceRules`.
3. CI runs `python scripts/arch_gate.py`.
4. CI also runs `./gradlew enforceRules`.
5. Timebase/static architecture checks are partially duplicated.
6. Product-specific regression semantics remain embedded in the same heavy grep gate.

### After
1. Developer inner loop remains `dev-fast.bat` or direct targeted Gradle commands.
2. Developer quick verification uses `./gradlew enforceArchitectureFast` plus targeted tests/build commands.
3. CI/release continues to use full `./gradlew enforceRules`.
4. Duplicate static ownership is removed so each invariant has one defined gate owner.
5. Product-specific regression semantics gradually migrate from grep rules to tests.
6. Syntax/AST-oriented rules gradually migrate to lint/detekt, shrinking custom PowerShell logic over time.

## 4) Implementation Phases

### Phase 0: Baseline Lock and Inventory
Goal:
- capture current behavior before any refactor

Actions:
- record current timings for:
  - `python scripts/arch_gate.py`
  - `./gradlew enforceRules`
  - `./gradlew testDebugUnitTest`
  - `./gradlew assembleDebug`
- inventory current rule groups in `scripts/ci/enforce_rules.ps1`
- inventory duplicate ownership between `arch_gate.py` and `enforce_rules.ps1`

Exit criteria:
- current failure surface is documented
- current timing baseline is documented
- current rule ownership is documented

### Phase 1: Internal Rule Grouping, No External Contract Change
Goal:
- make `enforce_rules.ps1` maintainable without changing any command contract yet

Actions:
- split the script into named internal groups:
  - architecture rules
  - hygiene rules
  - regression-contract rules
  - line-budget rules
- preserve `./gradlew enforceRules` behavior exactly

Exit criteria:
- `./gradlew enforceRules` still runs the full aggregate
- pass/fail behavior remains unchanged
- no workflow or wrapper changes yet

### Phase 2: Add a Fast Architecture Gate
Goal:
- introduce a fast, release-grade architecture/static gate for local use

Actions:
- add `enforceArchitectureFast` in [build.gradle.kts](/C:/Users/Asus/AndroidStudioProjects/XCPro/build.gradle.kts)
- wire it to:
  - `python scripts/arch_gate.py`
  - the architecture-only subset from `scripts/ci/enforce_rules.ps1`

Initial fast-gate scope:
- direct time API misuse
- app identity contract
- DI pipeline construction inside managers
- ViewModel purity boundaries
- `collectAsState`/lifecycle boundary misuse where still implemented in PowerShell
- Android/UI import bans in task/domain runtime layers
- runtime entrypoint lookup bans
- non-UI Compose runtime state bans

Exit criteria:
- `./gradlew enforceArchitectureFast` exists
- fast gate is materially smaller than `enforceRules`
- `enforceRules` remains unchanged as the release aggregate

### Phase 3: Rewire Local Quick Wrappers Only
Goal:
- speed local quick verification without weakening release verification

Actions:
- change [check-quick.bat](/C:/Users/Asus/AndroidStudioProjects/XCPro/check-quick.bat) to call `enforceArchitectureFast`
- change [auto-test.bat](/C:/Users/Asus/AndroidStudioProjects/XCPro/auto-test.bat) to call `enforceArchitectureFast`
- leave [preflight.bat](/C:/Users/Asus/AndroidStudioProjects/XCPro/preflight.bat) on full `enforceRules`
- leave CI workflow on full `enforceRules`

Exit criteria:
- quick wrappers are actually quick
- release/preflight bar is unchanged

### Phase 4: Remove Duplicate Static Ownership
Goal:
- stop paying twice for overlapping static scans

Actions:
- choose one explicit owner for timebase misuse enforcement
- keep app identity ownership in `arch_gate.py`
- remove overlapping timebase logic from the non-owning gate after parity verification
- update workflow/docs so command responsibilities are explicit

Preferred low-churn option:
- keep timebase and app identity in `arch_gate.py`
- remove the overlapping timebase block from `scripts/ci/enforce_rules.ps1`

Exit criteria:
- no duplicated static rule ownership remains for timebase checks
- CI still enforces the same invariant set

### Phase 5: Migrate Toward Standard Enforcement
Goal:
- reduce ad hoc grep enforcement over time without losing coverage

Move to lint/detekt over time:
- lifecycle-collection misuse
- ViewModel forbidden imports / persistence shortcuts
- Android/UI imports in domain/task layers
- vendor string checks
- non-ASCII production-code checks
- file line-budget enforcement, if retained

Move to tests over time:
- product-specific racing / AAT / task regression rules
- exact-expression guards
- exact-helper-name / wrapper reintroduction guards
- other behavior-level rules currently implemented as grep checks

Exit criteria:
- `enforceRules` becomes narrower and more stable
- syntax/AST rules live in standard Android tooling
- behavior contracts live in tests

## 5) Test Plan

### 5.1 Baseline Verification
Before refactor:
- run `python scripts/arch_gate.py`
- run `./gradlew enforceRules`
- run `./gradlew testDebugUnitTest`
- run `./gradlew assembleDebug`

### 5.2 Phase Verification
After Phase 1:
- confirm `./gradlew enforceRules` has identical pass/fail behavior

After Phase 2:
- run `./gradlew enforceArchitectureFast`
- confirm it fails on architecture-rule violations and does not include the full heavy rule set

After Phase 3:
- run [check-quick.bat](/C:/Users/Asus/AndroidStudioProjects/XCPro/check-quick.bat)
- run [auto-test.bat](/C:/Users/Asus/AndroidStudioProjects/XCPro/auto-test.bat)
- confirm both now use the fast gate path

After Phase 4:
- verify only one static owner remains for timebase misuse enforcement
- confirm CI workflow command list matches intended ownership

After each non-trivial change:
- run `./gradlew enforceRules`
- run `./gradlew testDebugUnitTest`
- run `./gradlew assembleDebug`

### 5.3 Acceptance Signals
- local quick verification wall-clock time decreases
- CI static verification wall-clock time decreases
- no product/runtime behavior changes are observed
- no architecture boundary regressions are introduced

## 6) Risks and Mitigations

| Risk | Impact | Mitigation |
| --- | --- | --- |
| Release gate accidentally weakened during split | High | Keep `enforceRules` as the unchanged aggregate until replacements are proven |
| Fast gate grows into another heavy ad hoc bucket | High | Restrict fast gate to architecture-critical invariants only |
| Duplicate timebase enforcement remains and drifts | Medium | Assign one explicit owner and remove the duplicate only after parity verification |
| Product-specific regex guards are deleted without test replacement | High | Migrate those rules only alongside focused tests |
| Wrapper/doc drift confuses developers | Medium | Update wrapper scripts and verification docs in the same change |
| Line-budget failures obscure plan rollout | Medium | Treat existing unrelated line-budget failures as baseline noise, not as blockers to the plan design |

## 7) Acceptance Gates
- `enforceRules` remains the full release/CI aggregate until rule migrations are complete.
- `enforceArchitectureFast` exists and is materially smaller than the full aggregate.
- `check-quick.bat` and `auto-test.bat` use the fast gate.
- `preflight.bat` and CI still use the full gate.
- Duplicate timebase ownership is eliminated in the final static-gate ownership model.
- No product/runtime behavior changes are introduced.
- Required repo verification still passes, subject to unrelated pre-existing failures:
  - `./gradlew enforceRules`
  - `./gradlew testDebugUnitTest`
  - `./gradlew assembleDebug`

## 8) Rollback Plan
- If the fast gate causes unexpected misses or workflow confusion:
  - revert wrapper changes first
  - keep `enforceRules` as the single user-facing gate temporarily
- If the internal `enforce_rules.ps1` split causes parity regressions:
  - revert the script refactor without changing external tasks
- If duplicate-scan removal loses coverage:
  - restore the removed rule block and re-run parity verification before retrying

Rollback is low risk because this plan changes verification wiring, not product code paths.

## 9) Recommended Next Step
Implement Phase 1 and Phase 2 first in one bounded change:
- internal grouping in `scripts/ci/enforce_rules.ps1`
- new `enforceArchitectureFast` task in `build.gradle.kts`
- no wrapper or CI rewiring yet

That gives a safe checkpoint with minimal churn and a clean base for the wrapper and CI follow-up.
