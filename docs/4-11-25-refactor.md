## XCPro Map Telemetry Refactor – 4 Nov 2025

### Findings
- **Duplicate live-data fan-out** – `MapMainLayers` pushes every `RealTimeFlightData` sample directly into both `FlightDataManager` and `FlightDataViewModel`, while `MapComposeEffects` mirrors the same data via `snapshotFlow`. The redundant path bypasses the reactive chain, risks race conditions, and makes hydration behaviour inconsistent on cold start.
- **Orientation split-brain** – `OrientationDataSource` still owns its own sensor listeners instead of reusing `UnifiedSensorManager`. This duplicates work, increases battery drain, and lets heading validity diverge from the unified sensor stack.
- **Inconsistent vario smoothing** – `FlightDataManager` applies an extra EMA on vertical speed before exposing `liveFlightData`, while cards render the raw stream from the repository. Users can see conflicting numbers on the map HUD versus cards.
- **Regression test gap** – No automated coverage ensures map-first launch hydrates cards, so the “blank cards until visiting Flight Data” bug can sneak back in.

### Refactor Plan
1. **Consolidate live-data propagation**
   - Remove the imperative `flightViewModel.updateCardsWithLiveData` call in `MapMainLayers`.
   - Let the `snapshotFlow` in `MapComposeEffects` remain the single path from `FlightDataManager.liveFlightData` to the SSOT view model, after pushing updates into `FlightDataManager`.
   - Confirm cards still hydrate on launch and adjust throttling if required.
2. **Unify orientation updates**
   - Route flight telemetry exclusively through `OrientationDataSource.updateFromFlightData` via the existing snapshot flow (already done once per update).
   - Strip the extra orientation call in `MapMainLayers` to avoid double updates and ensure one timing source.
   - Schedule follow-up work to move `OrientationDataSource` onto `UnifiedSensorManager`; current scope removes redundant calls, next iteration will merge listeners.
3. **Clarify vertical speed smoothing**
   - Keep `FlightDataManager` responsible for the smoothed UI value but also surface the raw value so downstream consumers can intentionally choose.
   - Ensure the repository receives the same smoothed data used in the HUD to eliminate visible discrepancies.
4. **Regression coverage**
   - Add an instrumentation test that launches directly into the map screen, waits for sensor stubs to emit a sample, and asserts non-placeholder card content.
   - This test protects the startup path from future regressions.

### Outcome Goals
- Single-source data flow from sensors ➜ calculator ➜ manager ➜ view model ➜ cards.
- Orientation updates driven from one stream, respecting the chosen mode without duplicate sensor listeners.
- Consistent vertical speed values between HUD overlays and cards.
- Automated guardrail preventing blank-card regressions.
