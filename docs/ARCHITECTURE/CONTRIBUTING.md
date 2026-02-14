
# CONTRIBUTING.md -- XC Pro (Android/Kotlin)

This repo uses **Kotlin + Jetpack Compose**, **MVVM + UDF**, **Hilt**, **Coroutines + Flow**, and a **multi-module** layout.
All code must follow **CODING_RULES.md** and must not violate **ARCHITECTURE.md**.

---

## 1) Required Reading

### Map Display (Quick Notes)
- Map position must come from `FlightDataRepository` (SSOT); do not read sensor flows in ViewModels.
- Display smoothing is UI-only; do not write smoothed values back into repositories.
- Time base rules apply to display smoothing (live monotonic, replay IGC time). See `../../mapposition.md`.
- Never log location data in release builds (debug-only if needed).


Read these in order before making changes:
- `ARCHITECTURE.md` - system invariants (data flow, SSOT, threading, DI, lifecycle rules)
- `CODING_RULES.md` - day-to-day coding constraints that enforce the architecture
- `PIPELINE.md` - end-to-end data flow (see `PIPELINE.svg`)
- If touching Levo vario or replay, also read `../LevoVario/levo.md`
- `CONTRIBUTING.md` - workflow, branching, PR rules, testing expectations, and AI usage
AI/agents: read the first three files in order before edits; include Levo docs when applicable.

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
- [ ] Levo pipeline changes are documented in `../LevoVario/levo.md` and any
      related architecture/time-base rules are updated.
- [ ] Unit tests cover use cases; UI/instrumentation tests for gesture/event flow.
- [ ] **Lint/detekt** pass; **Compose previews** compile.
- [ ] No deprecated APIs; no global mutable state.
- [ ] Performance budget respected in hot paths (TE->audio <= 50 ms typical).

---

## 3A) Refactor Guard Checklist (Task and Map)

Use this checklist for any task/map refactor before opening a PR.

- [ ] Architecture drift checklist completed (see `.github/pull_request_template.md`).

- [ ] UI emits intents to ViewModel only; no direct `TaskManagerCoordinator` mutations from Composables.
- [ ] UI reads ViewModel state only; no direct manager internals in Composables (`currentTask`, `currentLeg`, `currentAATTask`).
- [ ] ViewModels contain no business geospatial policy (distance/radius/zone-entry/auto-advance math).
- [ ] Non-UI managers/domain classes do not use Compose runtime state (`mutableStateOf`, `derivedStateOf`, `remember`).
- [ ] Core collaborators are injected; no manager/persistence construction inside coordinators.
- [ ] If pipeline wiring changed, `PIPELINE.md` is updated in the same PR.
- [ ] If any rule is knowingly violated, add an entry in `KNOWN_DEVIATIONS.md` with issue ID, owner, and expiry.

---

## 4) Local Dev Setup
```bash
# sync dependencies
./gradlew help

# static analysis
./gradlew detekt ktlintCheck

# unit + fast local instrumentation (app module only, keep debug app installed)
./gradlew testDebugUnitTest :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"

# full release/CI instrumentation (all modules)
./gradlew connectedDebugAndroidTest --no-parallel

# assemble + verify previews
./gradlew :app:assembleDebug
```

Fast local loop for feature/debug work:
```bat
dev-fast.bat feature:map compile
dev-fast.bat feature:map assemble
dev-fast.bat app install
```

Use `preflight.bat` before PR/release validation.

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
2. Implement per **ARCHITECTURE.md** + **CODING_RULES.md**.
3. Add tests + `AI-NOTE` comments.
4. Run `detekt`, `ktlintCheck`, `testDebugUnitTest`, and `:app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"`.
5. Run full `connectedDebugAndroidTest --no-parallel` before release/merge.
6. Open PR with a crisp description + screenshots/notes.
7. Address review feedback and merge once green.

---

**Thanks!** Consistency beats cleverness. Preserve SSOT, keep latency low, document the *why*.

