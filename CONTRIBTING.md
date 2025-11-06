# CONTRIBUTING.md — XC Pro (Android/Kotlin)

This repo uses **Kotlin + Jetpack Compose**, **MVVM + UDF**, **Hilt**, **Coroutines + Flow**, and a **multi‑module** layout. All code must follow **CODING_POLICY.md**.

---

## 1) How We Work
- **Default branch:** `main` (protected). Feature work happens on short‑lived branches.
- **Branch names:** `feat/<scope>-<short>`, `fix/<scope>-<short>`, `chore/<scope>-<short>`, `docs/<scope>-<short>`.
- **Conventional Commits:**
  - `feat(map): add overlay hit registry`
  - `fix(variometer): reduce TE filter lag at low IAS`
  - `chore(ci): enable detekt baseline`
  - `docs(policy): clarify AI-NOTE usage`
- **PR size:** Aim ≤ 400 LOC changed. Split if larger.

---

## 2) Definition of Done
A change is ready when:
- [ ] Code adheres to **CODING_POLICY.md** (SSOT, UDF, clean layering).
- [ ] **Rationale comments** are present for non‑obvious decisions (`// AI-NOTE:` markers encouraged).
- [ ] Unit tests cover use cases; UI/instrumentation tests for gesture/event flow.
- [ ] **Lint/detekt** pass; **Compose previews** compile.
- [ ] No deprecated APIs; no global mutable state.
- [ ] Performance budget respected in hot paths (TE→audio ≤ 50 ms typical).

---

## 3) Local Dev Setup
```bash
# sync dependencies
./gradlew help

# static analysis
./gradlew detekt ktlintCheck

# unit + instrumentation tests
./gradlew testDebugUnitTest connectedDebugAndroidTest

# assemble + verify previews
./gradlew :app:assembleDebug
```

**Android Studio:** Latest stable + Kotlin plugin. Enable `Preview` and `Layout Inspector` for Compose. Use `Analyze > Inspect Code` before PR.

---

## 4) Reviews & CI
- **Required:** 1 reviewer approval + all CI checks green.
- Discuss design in PR description with diagrams or short notes when touching fusion/filters or gesture routing.
- Keep commits clean; rebase onto `main` before merge.

---

## 5) Testing Guidance
- **Domain**: Pure JVM tests for TE math, filters, unit conversions.
- **Integration**: Stream replay (IGC → repository → use case → ViewModel) with deterministic seeds.
- **UI**: Compose tests for state rendering; gesture tests for hamburger/variometer (tap vs long‑press); regression for map event consumption.
- **Golden**: Snapshot important UI states.

---

## 6) Documentation Rules
- Add/update **KDoc** for public APIs.
- Add top‑of‑file header describing role and invariants.
- Use `// AI-NOTE:` before intent‑critical rationale so future AI tools preserve design.

Example:
```kotlin
// AI-NOTE: Consume DOWN in map gesture layer without returning early to allow overlay composables to handle clicks (UDF, SSOT).
```

---

## 7) AI & Automation Policy
- AI may generate code **only** within this policy.
- AI commits must include: a short “why” paragraph in the PR description and inline `AI-NOTE` comments where intent matters.
- No committing secret keys or personal data. Redact GPS traces unless explicitly enabled in debug config.

---

## 8) Versioning & Releases
- **SemVer**: bump `minor` for new features, `patch` for fixes, `major` for breaking changes.
- Tag releases: `vX.Y.Z` with brief notes (features, fixes, migrations).

---

## 9) Issue Hygiene
- Use labels: `bug`, `feature`, `tech-debt`, `performance`, `docs`.
- For bugs include: steps to reproduce, expected vs actual, logs (redacted), device model (e.g., S22 Ultra), and build variant.

---

## 10) Security & Privacy
- Request only necessary permissions. Avoid storing raw tracks unless user opts in.
- Do not log location data in release builds.

---

## 11) Merge Strategy
- Prefer **squash merge** with a clean, conventional subject.
- If multiple logical changes exist, split into multiple PRs.

---

## 12) New Contributor Quickstart
1. Fork/clone, create branch `feat/<scope>-<short>`.
2. Implement per **CODING_POLICY.md**.
3. Add tests + `AI-NOTE` comments.
4. Run `detekt`, `ktlintCheck`, `test`, `connectedAndroidTest`.
5. Open PR with a crisp description + screenshots/notes.
6. Address review feedback and merge once green.

---

**Thanks!** Consistency beats cleverness. Preserve SSOT, keep latency low, document the *why*. 

