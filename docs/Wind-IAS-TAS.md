## Wind / IAS / TAS Notes

### Current State (2025-11-13)
- **Wind pipeline**: `WindRepository` still mirrors XCSoar's circling estimator + EKF fusion.
- **Replay ingestion**: IGC points now feed synthetic GPS/Baro/Compass streams into `FlightDataCalculator`, so the same TAS proxy, QNH handling, and smoothing logic used in live flight also runs during replays.
- **IGC replay**: `IgcReplayController` now stops the live `VarioServiceManager`, streams B-records into `FlightDataRepository`, and restarts the sensors automatically when playback finishes or is cancelled.
- **Cards / Map**: Because the map screen reads `FlightDataRepository`, replay data renders exactly like inflight data once you return to the map.

### Using the Built-In Replay
1. Open **Menu > General > IGC Replay**.
2. Tap **Choose IGC File** and pick `docs/2025-11-11.igc` (or any SAF-visible log).
3. Press **Start Replay**. The screen navigates back; use the new replay HUD on the map to play/pause, scrub the timeline, or change speed while watching the cards update.
4. To stop early, hit **Stop** on the HUD (or reopen the replay screen) and sensors resume automatically.

### QA Checklist
- While replaying, confirm `WindRepository` publishes `WindSource.CIRCLING` in thermals and `WindSource.EKF` on the glides.
- Check that the status banner shows `Replaying at ...x. Return to the map to view data.` and flips to `Replay finished` at EOF.
- After finishing or cancelling, verify live sensors restart (GPS indicators come back and cards follow real time again).

### Known Gaps / Next Steps
- TAS proxy still mirrors ground speed; need XCSoar's polar/density TAS estimator to tighten EKF quality.
- G-load gating is still disabled until we expose accelerometer feeds.
- Replay currently auto-navigates back when it starts; future polish could offer a dedicated "View on Map" action instead.
