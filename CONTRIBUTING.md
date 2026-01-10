# CONTRIBUTING.md — XC Pro (Android/Kotlin)

This repo uses **Kotlin + Jetpack Compose**, **MVVM + UDF**, **Hilt**, **Coroutines + Flow**, and a **multi‑module** layout.
All code must follow **CODING_RULES.md** and must not violate **ARCHITECTURE.md**.

---

## 1) Required Reading
Read these in order before making changes:
- `ARCHITECTURE.md` - system invariants (data flow, SSOT, threading, DI, lifecycle rules)
- `CODING_RULES.md` - day-to-day coding constraints that enforce the architecture
- `CONTRIBUTING.md` - workflow, branching, PR rules, testing expectations, and AI usage
AI/agents: read the three files in order before edits.

---

## 2) How We Work
- **Default branch:** `main` (protected). Feature work happens on short‑lived branches.
- **Branch names:** `feat/<scope>-<short>`, `fix/<scope>-<short>`, `chore/<scope>-<short>`, `docs/<scope>-<short>`.
- **Conventional Commits:**
  - `feat(map): add overlay hit registry`
  - `fix(variometer): reduce TE filter lag at low IAS`
  - `chore(ci): enable detekt baseline`
  - `docs(policy): clarify AI-NOTE usage`
- **PR size:** Aim ≤ 400 LOC changed. Split if larger.

---

## 3) Definition of Done
A change is ready when:
- [ ] Code adheres to **CODING_RULES.md** (SSOT, UDF, clean layering).
- [ ] **Rationale comments** are present for non‑obvious decisions (`// AI-NOTE:` markers encouraged).
- [ ] Unit tests cover use cases; UI/instrumentation tests for gesture/event flow.
- [ ] **Lint/detekt** pass; **Compose previews** compile.
- [ ] No deprecated APIs; no global mutable state.
- [ ] Performance budget respected in hot paths (TE→audio ≤ 50 ms typical).

---

## 4) Local Dev Setup
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
- **Integration**: Stream replay (IGC → repository → use case → ViewModel) with deterministic seeds.
- **UI**: Compose tests for state rendering; gesture tests for hamburger/variometer (tap vs long‑press); regression for map event consumption.
- **Golden**: Snapshot important UI states.

---

## 7) Documentation Rules
- Keep files ASCII or UTF-8 only; see CODING_RULES.md -> File Encoding Rules.
- Add/update **KDoc** for public APIs.
- Add top‑of‑file header describing role and invariants.
- Use `// AI-NOTE:` before intent‑critical rationale so future AI tools preserve design.

Example:
```kotlin
// AI-NOTE: Consume DOWN in map gesture layer without returning early to allow overlay composables to handle clicks (UDF, SSOT).
```

---

## 8) AI & Automation Policy
- AI may generate code **only** within this policy.
- AI commits must include: a short “why” paragraph in the PR description and inline `AI-NOTE` comments where intent matters.
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
4. Run `detekt`, `ktlintCheck`, `test`, `connectedAndroidTest`.
5. Open PR with a crisp description + screenshots/notes.
6. Address review feedback and merge once green.

---

**Thanks!** Consistency beats cleverness. Preserve SSOT, keep latency low, document the *why*.
