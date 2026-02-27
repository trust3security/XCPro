# TASK_OGN_50KM.md (Legacy Filename)

Status: implemented and superseded.
The file name is historical. Current runtime behavior is not 50 km.

Current contract:
- Receive policy: 300 km diameter around ownship GPS (150 km radius).
- Center source: ownship/user GPS (`mapLocation`), not camera center.
- Stream gate: `allowSensorStart && mapVisible && ognOverlayEnabled`.
- Display: glider icon + label + track rotation + stale fade.
- Stale/eviction: 60s visual stale, 120s eviction.
- OGN overlay renders no targets when disabled.
- OGN data is informational only and excluded from navigation/task/scoring logic.

Authoritative docs:
- `docs/ARCHITECTURE/PIPELINE.md`
- `docs/OGN/OGN_PROTOCOL_NOTES.md`
- `docs/OGN/OGN.md`
