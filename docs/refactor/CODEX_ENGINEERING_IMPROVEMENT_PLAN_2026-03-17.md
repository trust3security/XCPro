# XCPro Engineering Improvement Plan (Codex Execution Pack)

Date: 2026-03-17  
Owner: XCPro Team  
Scope: Repo-wide engineering design hardening with phased, Codex-executable slices.

---

## 1) Objective

Deliver a high-impact improvement program that:

1. Closes active architecture-risk deviations first (map SLO + logging drift).
2. Reduces change-risk concentration in orchestration hotspots.
3. Strengthens deterministic replay/timebase and verification confidence.
4. Improves maintainability through ownership-focused file splits.

This plan is designed as a **handoff pack** for Codex and follows:
- `docs/ARCHITECTURE/ARCHITECTURE.md`
- `docs/ARCHITECTURE/CODING_RULES.md`
- `docs/ARCHITECTURE/PIPELINE.md`
- `docs/ARCHITECTURE/AGENT.md`
- `docs/ARCHITECTURE/CHANGE_PLAN_TEMPLATE.md`

---

## 2) Why this plan first

Current repo signals indicate the highest ROI is to finish known, time-boxed debt before adding more feature scope:

- Active map visual SLO deviation (`MS-UX-01`, pkg-e1 lane).
- Active logging architecture/privacy drift deviation.

Both are already tracked in `KNOWN_DEVIATIONS.md` and are merge/readiness critical.

---

## 3) Plan topology

This plan is executed in 3 phases, each delivered as a separate Codex prompt file:

1. `docs/refactor/codex-pack/PHASE_1_STABILITY.md`
2. `docs/refactor/codex-pack/PHASE_2_OWNERSHIP_SPLIT.md`
3. `docs/refactor/codex-pack/PHASE_3_DETERMINISM_PERF.md`

Recommended merge strategy:
- One branch/PR per phase.
- Keep each PR focused and independently revertible.

---

## 4) Global acceptance gates (all phases)

Required checks per phase PR:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Run when relevant to touched runtime/device behavior:

```bash
./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"
```

For map/overlay/replay/task-gesture changes, attach SLO evidence from MAPSCREEN docs and comply with deviation policy if any mandatory threshold is missed.

---

## 5) Codex operating contract for this pack

For each phase prompt, Codex must:

1. Follow `docs/ARCHITECTURE/AGENT.md` phase model and pre-implementation ownership declarations.
2. Produce explicit file ownership summary (new/modified files and responsibilities).
3. Keep business logic out of UI and preserve MVVM + UDF + SSOT boundaries.
4. Keep replay deterministic and timebase explicit.
5. Update `PIPELINE.md` if wiring/ownership flow changes.
6. Add ADR only when ownership/module/API/concurrency policy becomes durable.

---

## 6) Exit definition for the full program

Program is complete when:

- Active deviations targeted by Phase 1 are resolved or re-scoped with approved, non-expired entries.
- Coordinator/screen hotspots targeted by Phase 2 are split into ownership-focused seams with equivalent/stronger tests.
- Replay/timebase/perf confidence in targeted areas is increased with deterministic and SLO-backed evidence (Phase 3).
- All phase PRs pass required verification gates.

