# Gliding Racing Task Rules for an Android Task Builder (XCPro)

These notes are a developer-oriented extraction of **FAI gliding championship task rules** relevant to building and validating a **Racing Task (RT)** in a mobile app.

They are intended to be fed to an LLM coding agent (Codex) as implementation guidance. They are **not** the official rules; always check the original FAI documents and any local competition rules.

## Primary sources

- FAI Sporting Code **Section 3 – Annex A** (Gliding Championships), **2025 edition**: https://www.fai.org/sites/default/files/sc3a_2025.pdf
- Historical reference: FAI SC3 **Annex A 2013**: https://www.fai.org/sites/default/files/documents/sc3a_2013_1.pdf
- (Context) FAI Sporting Code **Section 3 – Gliding (2024)**: https://www.fai.org/sites/default/files/sc3_2024.pdf
- FAI documents index page (useful to find newer editions): https://www.fai.org/page/documents-0

## Scope

This package focuses on what an app needs to let a user **create a Racing Task** and **validate task progress/completion** from a GPS track:

- Task structure: Start → (≥2) Turn Points → Finish (+ optional Steering Point).
- Zone geometry: start line / start ring / start cylinder; turnpoint cylinders; finish ring / finish line.
- Validation logic: enter/leave/cross, sequencing, time interpolation, and “near miss” tolerances.
- Parameters that are typically configured on a “Task Sheet”: zone sizes, times, and optional start procedures (e.g., PEV start option).

Scoring formulas and penalty point calculations are *mostly out of scope*, but some penalty-related parameters are included because they affect how a task is defined (e.g., min finish altitude).

## File index

1. `sources.md` — authoritative documents and section pointers.
2. `racing_task_definition.md` — what an RT is and what data it needs.
3. `task_elements_and_geometry.md` — how Start/TP/Finish are represented (zones, bearings).
4. `start_procedure.md` — start opening/closing, line start, PEV option, cylinder start, tolerances.
5. `turnpoints_and_observation_zones.md` — TP cylinder rules and achievement detection.
6. `finish_procedure.md` — finish ring/line rules, closure, and validation.
7. `validation_algorithms.md` — concrete pseudocode for “did we start/turn/finish?”
8. `task_creation_ui_spec.md` — suggested UI fields + validation checks for a task editor.
9. `task_json_schema_example.md` — a JSON task format proposal (schema + example).

## Conventions

- **FAI rule** = a paraphrase of SC3 Annex A text, with section numbers so you can verify it.
- **App guidance** = recommended behavior for an Android task builder; adjust to your needs.
- All bearings are **true** bearings on WGS‑84. Distances are geodesic unless otherwise stated.

