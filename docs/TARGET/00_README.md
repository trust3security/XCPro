# TARGET Docs

Purpose
- This folder captures the implementation investigation and rollout plan for the OGN Target feature.
- Feature intent: user taps an OGN glider marker, opens the glider bottom sheet, enables a Target toggle, then sees:
  - a target icon/ring around that glider marker
  - a direct line from ownship to the targeted glider

Status
- Focused code pass complete as of 2026-03-06.
- Production-grade phased IP and validation plan are documented.
- Production implementation is now in progress in the map/OGN runtime and UI stacks.
- Validation checks currently passing: `arch_gate`, `enforceRules`, `testDebugUnitTest`, `assembleDebug`.

Read order
1. `01_SYSTEM_MAP_AND_CURRENT_BEHAVIOR.md`
2. `04_FOCUSED_CODE_PASS_FINDINGS_2026-03-06.md`
3. `02_IMPLEMENTATION_PLAN_OGN_TARGET_2026-03-06.md`
4. `03_TEST_VALIDATION_PLAN_OGN_TARGET_2026-03-06.md`

Related docs
- `docs/ARCHITECTURE/ARCHITECTURE.md`
- `docs/ARCHITECTURE/CODING_RULES.md`
- `docs/ARCHITECTURE/PIPELINE.md`
- `docs/OGN/OGN.md`
- `docs/MAPSCREEN/02_BASELINE_PROFILING_AND_SLO_MATRIX_2026-03-05.md`
- `docs/MAPSCREEN/04_TEST_VALIDATION_AND_ROLLBACK_2026-03-05.md`

Non-negotiables
- Preserve MVVM + UDF + SSOT ownership.
- Keep business logic out of Compose UI.
- Keep map runtime overlays UI-owned (no ViewModel MapLibre types).
- Keep deterministic behavior and injected time usage in non-UI logic.
