# XCPro Vision & Flight Safety

## Mission Statement
XCPro delivers competition-ready navigation for glider pilots who depend on precise, real-time insights while racing. The app must guide pilots from launch to finish without ambiguity, stay aligned with FAI scoring, and remain usable in turbulent, glove-on environments. Every feature, from SkySight overlays to task planners, exists to keep pilots informed, compliant, and confident in the cockpit.

## Flight Safety Principles
- **No Surprises:** Visual cues, alerts, and calculations must stay synchronized. If the map shows a line or radius, scoring logic uses the same geometry—no exceptions.
- **Pilot Authority:** Auto-camera returns, gesture overrides, or background recalculations that wrest control from pilots are prohibited. Inputs must be intentional and reversible.
- **Data Preservation:** On-device data (profiles, SkySight credentials, task plans) survives routine builds and app restarts. Destructive commands such as `./gradlew clean` stay banned unless the pilot-owner explicitly opts in.
- **Fail Fast:** Sensor faults, validation gaps, or distance mismatches surface immediately, with clear remediation steps.

## Single Source of Truth (SSOT)
Competition integrity depends on a single authoritative dataset for each domain:
1. **Task Geometry:** `AATRadiusAuthority`, `RacingTaskCalculator`, and related managers own the math used for rendering and scoring. Never fork or cache those calculations elsewhere.
2. **Map Orientation:** `MapOrientationManager` dictates north-up, track-up, and heading-up behaviour for all consumers. UI layers subscribe; they do not re-implement filters.
3. **Flight Data Cards:** `dfcards-library` receives normalized sensor data from the unified flight data pipeline. Cards render what the pipeline emits—no duplication in Compose.

Before coding, confirm the SSOT owner, write updates through its API, and rely on reactive flows for UI refresh. Manual syncs or ad-hoc caches are flight-safety violations.

## Operational Rules for Contributors
- **Gesture Discipline:** Custom gesture handlers remain enabled; standard MapLibre gestures stay disabled. See `CLAUDE.md` and `Quick_Reference.md` for the non-negotiable gesture matrix.
- **Task Separation:** Racing, AAT, and other task types never import one another’s calculators, models, or utils. Cross-contamination invalidates competition results.
- **Documentation First:** When touching safety-critical code, update the relevant spec (`Task_Type_Separation.md`, `AAT_Tasks.md`, etc.) before merging.
- **Testing:** Provide JVM/unit coverage for geometry, validation, and calculations. For UI or gesture changes, include Compose/espresso instrumentation notes in PRs.

## How to Use This Guide
Share this vision document with new collaborators before they open Android Studio. Pair it with `AGENTS.md` for day-to-day workflows and `CLAUDE.md` for detailed enforcement checklists. The pilot stays safe when everyone aligns on these principles.
