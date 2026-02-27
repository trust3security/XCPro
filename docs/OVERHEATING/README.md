# OVERHEATING Docs

Purpose:
- Provide a persistent, code-linked overheating and battery-drain knowledge base for XCPro.
- Capture current hypotheses, evidence, profiling method, and mitigation backlog.
- Let future agents continue work without relying on chat history.

Scope:
- Live flying runtime, with emphasis on Levo vario pipeline and map overlays.
- CPU, GPU, sensor, audio, and network contributors.

Read Order:
1. `Levo-Overlay-Overheating-Baseline-2026-02-24.md`
2. `Profiling-Playbook.md`
3. `Mitigation-Backlog.md`
4. `Session-Log-Template.md`

Current Baseline Summary (2026-02-24):
- Primary suspected contributor: per-frame map render/update path (camera + pose + overlay source updates).
- Secondary contributors: high-rate sensor ingestion + fusion path and continuous vario audio generation.
- Overlay-heavy modes (OGN real-time, ADS-B interpolation loop, weather animation) can materially increase thermal load.

How To Extend This Folder:
- Add dated findings files (for example `Findings-2026-03-03.md`).
- Keep hypotheses linked to concrete code paths and line references.
- Update `Mitigation-Backlog.md` when priority changes.
- Record every profiling session using `Session-Log-Template.md`.
