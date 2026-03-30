# PIPELINE_INDEX.md

## Purpose

This file is a quick navigation guide for `PIPELINE.md`.

`PIPELINE.md` remains the authoritative runtime wiring document.
Use this index to find the right section quickly before reading the full detail.

---

## Fast Start

Read these in order:

1. `PIPELINE.md` -> `Quick Map (Live)`
2. the feature-specific section below
3. `PIPELINE.md` -> `Time Base Rules (Enforced by Design)` for any runtime or
   replay-sensitive change
4. `PIPELINE.md` -> `Primary Files Index` if you need owner/file anchors

---

## Feature Routing

Use these sections when your change touches:

- sensors, GPS, baro, IMU, or live ingestion:
  `1) Sensor Ingestion (Live)`, `2) Fusion + Metrics (Live)`,
  `3) SSOT Repository + Source Gating`, `9) Time Base Rules`,
  `10) Primary Files Index`
- live flight metrics, fusion outputs, or flight-data SSOT:
  `2) Fusion + Metrics (Live)`, `3) SSOT Repository + Source Gating`,
  `7) Parallel Pipelines (Wind + Flight State)`, `9) Time Base Rules`
- LiveFollow pilot or watch runtime:
  `3A) LiveFollow Pilot + Watch Runtime`
  Supporting detail only: `PIPELINE_LiveFollow_Addendum.md`
- use-case to ViewModel wiring:
  `4) Use Case -> ViewModel`
- map screen UI, overlays, or map-side rendering:
  `5) ViewModel -> UI (Map + Cards)`
- cards and `dfcards-library`:
  `5A) Cards (dfcards-library) sub-pipeline`
- tasks, racing, or AAT:
  `5B) Task Management Pipeline (Racing + AAT)`
- audio or variometer audio path:
  `6) Audio Pipeline`
- wind fusion or flight-state parallel runtime:
  `7) Parallel Pipelines (Wind + Flight State)`
- IGC replay, replay controllers, or replay sensor feed:
  `8) Replay Pipeline (High-Level)`, `9) Time Base Rules`

---

## Section Map

| `PIPELINE.md` section | Read it when you need |
|---|---|
| `Automated Quality Gates` | verification and enforcement context |
| `Quick Map (Live)` | a short end-to-end orientation before detail |
| `1) Sensor Ingestion (Live)` | live sensor entry points and device sources |
| `2) Fusion + Metrics (Live)` | fusion loops, metrics, and flight calculations |
| `3) SSOT Repository + Source Gating` | authoritative flight-data ownership and source switching |
| `3A) LiveFollow Pilot + Watch Runtime` | LiveFollow ownership and runtime flow |
| `4) Use Case -> ViewModel` | domain/use-case to ViewModel boundaries |
| `5) ViewModel -> UI (Map + Cards)` | map UI and render-side consumers |
| `5A) Cards (dfcards-library) sub-pipeline` | card data path and adapters |
| `5B) Task Management Pipeline (Racing + AAT)` | task SSOT, task runtime, and task UI flow |
| `6) Audio Pipeline` | audio control and tone generation path |
| `7) Parallel Pipelines (Wind + Flight State)` | wind and flight-state side pipelines |
| `8) Replay Pipeline (High-Level)` | replay source, controller, and repository flow |
| `9) Time Base Rules (Enforced by Design)` | monotonic vs replay vs wall-time rules |
| `10) Primary Files Index` | fast owner/file lookup |

---

## Notes

- Do not treat this file as a second source of truth for runtime wiring.
- If runtime wiring changes, update `PIPELINE.md` first and then update this
  index only if navigation guidance changed.
