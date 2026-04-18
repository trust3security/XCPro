
# CONTRIBUTING.md -- XC Pro (Android/Kotlin)

This repo uses **Kotlin + Jetpack Compose**, **MVVM + UDF**, **Hilt**, **Coroutines + Flow**, and a **multi-module** layout.
All code must follow **CODING_RULES.md** and must not violate **ARCHITECTURE.md**.

---

## 1) Required Reading

If you are not sure whether the change is trivial, start with
`PLAN_MODE_START_HERE.md`.
It explains when to use plan mode, what "non-trivial" means, and what minimum
plan output is required before coding.

### Map Display (Quick Notes)
- Map position must come from `FlightDataRepository` (SSOT); do not read sensor flows in ViewModels.
- Display smoothing is UI-only; do not write smoothed values back into repositories.
- Time base rules apply to display smoothing (live monotonic, replay IGC time). See `../../mapposition.md`.
- Never log location data in release builds (debug-only if needed).


Read these in order before making changes:
- `PLAN_MODE_START_HERE.md` - beginner planning entrypoint for non-trivial work
- `ARCHITECTURE.md` - system invariants (data flow, SSOT, threading, DI, lifecycle rules)
- `CODING_RULES.md` - day-to-day coding constraints that enforce the architecture
- `PIPELINE_INDEX.md` - quick routing into the authoritative pipeline doc
- `PIPELINE.md` - end-to-end data flow (see `PIPELINE.svg`)
- If touching map interaction or overlay runtime behavior, also read:
  - `../MAPSCREEN/01_MAPSCREEN_PRODUCTION_GRADE_PHASED_IP_2026-03-05.md`
  - `../MAPSCREEN/02_BASELINE_PROFILING_AND_SLO_MATRIX_2026-03-05.md`
  - `../MAPSCREEN/04_TEST_VALIDATION_AND_ROLLBACK_2026-03-05.md`
- If touching Levo vario or replay, also read:
  - `../LEVO/levo.md`
  - `../LEVO/levo-replay.md`
- `CONTRIBUTING.md` - workflow, branching, PR rules, testing expectations, and AI usage
AI/agents: read the planning entry doc plus the architecture files above in order before edits; include Levo docs when applicable.

---

## 2) How We Work
- **Default branch:** `main` (protected). Feature work happens on short-lived branches.
- **Branch names:** `feat/<scope>-<short>`, `fix/<scope>-<short>`, `chore/<scope>-<short>`, `docs/<scope>-<short>`.
- **Conventional Commits:**
  - `feat(map): add overlay hit registry`
  - `fix(variometer): reduce TE filter lag at low IAS`
  - `chore(ci): enable detekt baseline`
  - `docs(policy): clarify AI-NOTE usage`
- **PR size:** Aim <= 400 LOC changed. Split if larger.

---

## 3) Definition of Done
A change is ready when:
- [ ] Code adheres to **CODING_RULES.md** (SSOT, UDF, clean layering).
- [ ] `./gradlew enforceRules` passes (architecture/coding rule enforcement).
- [ ] Quality rescore included in PR description (see `docs/ARCHITECTURE/AGENT.md` -> Quality Rescore).
- [ ] **Rationale comments** are present for non-obvious decisions (`// AI-NOTE:` markers encouraged).
- [ ] ADR added or updated for non-trivial ownership, module-boundary, API-surface, or concurrency-policy decisions.
- [ ] New or changed authoritative/derived state has an explicit contract (owner, mutator, reset, persistence/time-base impact) in the plan or ADR.
- [ ] New long-lived `CoroutineScope(...)` owners have explicit lifetime/cancellation rationale in the plan.
- [ ] New compatibility shims/bridges declare owner, reason, removal trigger, and test coverage.
- [ ] New shared formulas/constants/policies name a canonical owner instead of creating undocumented duplicates.
- [ ] New Kotlin `object` or singleton-like holders are stateless or explicitly documented as non-authoritative infrastructure state.
- [ ] New IDs/timestamps are created at explicit owner boundaries, not hidden in model defaults without rationale.
- [ ] New `NoOp` or convenience-constructor paths are narrow, documented, and safe for production behavior.
- [ ] Levo pipeline changes are documented in `../LEVO/levo.md` and any
      related architecture/time-base rules are updated.
- [ ] Unit tests cover use cases; UI/instrumentation tests for gesture/event flow.
- [ ] Active quality gates pass; do not claim `detekt` / `ktlintCheck` unless they are actually configured in this repo.
- [ ] **Compose previews** compile.
- [ ] No deprecated APIs; no global mutable state.
- [ ] Performance budget respected in hot paths (TE->audio <= 50 ms typical).
- [ ] For map/overlay/replay interaction changes, impacted MapScreen SLOs
      (`MS-UX-*`, `MS-ENG-*`) pass with attached evidence.

---

## 3A) Refactor Guard Checklist (Task and Map)

Use this checklist for any task/map refactor before opening a PR.

- [ ] Architecture drift checklist completed (see `.github/pull_request_template.md`).

- [ ] UI emits intents to ViewModel only; no direct `TaskManagerCoordinator` mutations from Composables.
- [ ] UI reads ViewModel state only; no direct manager internals in Composables (`currentTask`, `currentLeg`, `currentAATTask`).
- [ ] ViewModels contain no business geospatial policy (distance/radius/zone-entry/auto-advance math).
- [ ] Non-UI managers/domain classes do not use Compose runtime state (`mutableStateOf`, `derivedStateOf`, `remember`).
- [ ] Core collaborators are injected; no manager/persistence construction inside coordinators.
- [ ] New direct `Log.*` calls are justified; prefer `AppLogger` for production Kotlin logging.
- [ ] New `CoroutineScope(...)` creation has explicit owner and teardown.
- [ ] Temporary compatibility shims/bridges are tagged with removal conditions.
- [ ] New `object` usage is not acting as a hidden state owner or service locator.
- [ ] Duplicate formulas/constants name a canonical owner or a documented temporary divergence reason.
- [ ] If pipeline wiring changed, `PIPELINE.md` is updated in the same PR.
- [ ] If map interaction/overlay behavior changed, SLO evidence from
      `docs/MAPSCREEN/02...` + `docs/MAPSCREEN/04...` is attached.
- [ ] If any rule is knowingly violated, add an entry in `KNOWN_DEVIATIONS.md` with issue ID, owner, and expiry.

---

## 4) Local Dev Setup
```bash
# sync dependencies
./gradlew help

# architecture/static analysis
./gradlew enforceRules

# optional future static-analysis tasks if configured in this branch/repo
# ./gradlew detekt ktlintCheck

# unit + fast local instrumentation (app module only, keep debug app installed)
./gradlew testDebugUnitTest :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"

# full release/CI instrumentation (all modules)
./gradlew connectedDebugAndroidTest --no-parallel

# assemble + verify previews
./gradlew :app:assembleDebug
```

### 4A) Role-Based Command Sets

Fast loop (before push):
```bash
./gradlew enforceArchitectureFast
./gradlew :feature:map:compileDebugKotlin
```

PR loop (matches CI quality gates):
```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Release loop:
```bash
preflight.bat
./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest --no-parallel
```

Fast local loop for feature/debug work:
```bat
dev-fast.bat feature:map compile
dev-fast.bat feature:map assemble
dev-fast.bat app install
```

Repo-owned verification bundles:
```bat
scripts\qa\run_change_verification.bat -Profile fast-loop
scripts\qa\run_change_verification.bat -Profile slice-terrain
scripts\qa\run_change_verification.bat -Profile pr-ready
```

Reliable root unit-test wrapper:
```bat
scripts\qa\run_root_unit_tests_reliable.bat
```

`dev-fast.bat` is optimized for compile/install loops; run tests with
`gradlew` directly (or with explicit arguments via `check-quick.bat`).

Use the verification bundles as the default local developer/agent workflow for
common lanes. Keep root `testDebugUnitTest` as the PR-ready unit-test gate; the
reliable wrapper only hardens Windows lock recovery and does not narrow the
meaning of that gate.

Use `preflight.bat` before PR/release validation.
For fast daily local verification, use `check-quick.bat`:
```bat
check-quick.bat
check-quick.bat :feature:map:testDebugUnitTest --tests "com.trust3.xcpro.map.MapScreenViewModelTest"
```
For Windows test file-lock resilience (`output.bin`/`.lck`), use
`gradlew` directly:
```bat
gradlew testDebugUnitTest
gradlew :feature:map:testDebugUnitTest --tests "com.trust3.xcpro.sensors.domain.CalculateFlightMetricsUseCase*"
```

Preferred root-gate retry path:
```bat
scripts\qa\run_root_unit_tests_reliable.bat
```

For lock resilience on a failing local test pass, run manual recovery:
```bat
gradlew --stop
powershell -NoProfile -Command "Get-ChildItem .\feature\map\build\test-results\testDebugUnitTest\binary, .\app\build\test-results\testDebugUnitTest\binary, . -Recurse -Filter output.bin -ErrorAction SilentlyContinue | Remove-Item -Force -Recurse -ErrorAction SilentlyContinue"
```

For Windows KSP/generated-state corruption recovery, use `repair-build.bat`
before falling back to broad `clean` runs:
```bat
repair-build.bat
repair-build.bat all assemble
```
`repair-build.bat` stops repo-local Gradle daemons, removes module-local
`build/kspCaches` plus generated KSP output, clears wrapper `.lck` files, and
re-runs a narrow Gradle task in compile/assemble mode.

Unit-test hang protection policy:
- Default per-test timeout is `60s` (override via `-Pxcpro.test.timeout.seconds=<10..120>`).
- Known flaky Robolectric retries are CI-only (`CI=true`) with `maxRetries=1`,
  scoped to `config/test/flaky-robolectric-allowlist.txt`.

**Android Studio:** Latest stable + Kotlin plugin. Enable `Preview` and `Layout Inspector` for Compose. Use `Analyze > Inspect Code` before PR.

### XCSoar Reference (Docs Only)
- Source location (shared): `C:\Users\Asus\AndroidStudioProjects\XCSoar`
- Use this repo as a read-only reference for behavior and structure.
- Do not copy code; production code must not include the literal string "xcsoar" (see ARCHITECTURE.md).

---

## 5) Reviews & CI
- **Required:** 1 reviewer approval + all CI checks green.
- Discuss design in PR description with diagrams or short notes when touching fusion/filters or gesture routing.
- Keep commits clean; rebase onto `main` before merge.

---

## 6) Testing Guidance
- **Domain**: Pure JVM tests for TE math, filters, unit conversions.
- **Integration**: Stream replay (IGC -> repository -> use case -> ViewModel) with deterministic seeds.
- **UI**: Compose tests for state rendering; gesture tests for hamburger/variometer (tap vs long-press); regression for map event consumption.
- **Golden**: Snapshot important UI states.

---

## 7) Documentation Rules
- Keep files ASCII or UTF-8 only; see CODING_RULES.md -> File Encoding Rules.
- Add/update **KDoc** for public APIs.
- Add top-of-file header describing role and invariants.
- Use `// AI-NOTE:` before intent-critical rationale so future AI tools preserve design.
- Do not duplicate architecture rules in multiple docs with different wording; `ARCHITECTURE.md` owns invariants and `CODING_RULES.md` owns implementation defaults.
- Use `PLAN_MODE_START_HERE.md` to decide whether work is non-trivial, then use `CHANGE_PLAN_TEMPLATE.md` for non-trivial work and `ADR_TEMPLATE.md` for non-trivial architecture decisions.
- Keep global docs durable; task-specific "active plan" pointers belong in the change plan, not the global contract docs.

Example:
```kotlin
// AI-NOTE: Consume DOWN in map gesture layer without returning early to allow overlay composables to handle clicks (UDF, SSOT).
```

---

## 8) AI & Automation Policy
- AI may generate code **only** within this policy.
- AI commits must include: a short "why" paragraph in the PR description and inline `AI-NOTE` comments where intent matters.
- AI must not choose the fastest shortcut if it violates **CODING_RULES.md** or **ARCHITECTURE.md**.
  Prefer correct layering and SSOT compliance over minimal plumbing.
- AI must not introduce undocumented ownership moves, public APIs, or long-lived compatibility shims; those belong in the plan and ADR.
- AI must not introduce new hidden scope owners, hidden ID/time generation, or silent `NoOp` production fallbacks without documenting them in the plan.
- No committing secret keys or personal data. Redact GPS traces unless explicitly enabled in debug config.

---

## 9) Versioning & Releases
- **SemVer**: bump `minor` for new features, `patch` for fixes, `major` for breaking changes.
- Tag releases: `vX.Y.Z` with brief notes (features, fixes, migrations).

---

## 10) Issue Hygiene
- Use labels: `bug`, `feature`, `tech-debt`, `performance`, `docs`.
- For bugs include: steps to reproduce, expected vs actual, logs (redacted), device model (e.g., S22 Ultra), and build variant.

---

## 11) Security & Privacy
- Request only necessary permissions. Avoid storing raw tracks unless user opts in.
- Do not log location data in release builds.

---

## 12) Merge Strategy
- Prefer **squash merge** with a clean, conventional subject.
- If multiple logical changes exist, split into multiple PRs.

---

## 13) New Contributor Quickstart
1. Fork/clone, create branch `feat/<scope>-<short>`.
2. For non-trivial work, start with `PLAN_MODE_START_HERE.md` and `CHANGE_PLAN_TEMPLATE.md`; then implement per **ARCHITECTURE.md** + **CODING_RULES.md**.
3. Add tests + `AI-NOTE` comments.
4. Run the configured quality gates plus `testDebugUnitTest`, and run `:app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"` when relevant.
5. Run full `connectedDebugAndroidTest --no-parallel` before release/merge.
6. Open PR with a crisp description + screenshots/notes.
7. Address review feedback and merge once green.

---

**Thanks!** Consistency beats cleverness. Preserve SSOT, keep latency low, document the *why*.

