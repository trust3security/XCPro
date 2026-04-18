# XCPro OGN 50km Integration Plan

## Objective
Implement live OGN traffic ingestion and display nearby gliders/aircraft within **50 km** of the user's current location on the map screen.

---

## 1) Architecture fit (XCPro)

Use existing XCPro map patterns:

- **Repository + UseCase + StateFlow** for data access and screen consumption.
- **MapLibre GeoJSON overlays** for marker rendering.
- **MapScreenViewModel as orchestration point** for flow combination, UI state, and toggles.

### Proposed modules/classes

- `feature/map/.../ogn/data/OgnTrafficRepository.kt`
- `feature/map/.../ogn/data/OgnTrafficClient.kt` (TCP/WebSocket/HTTP polling adapter)
- `feature/map/.../ogn/domain/OgnTrafficUseCase.kt`
- `feature/map/.../ogn/model/OgnTarget.kt`
- `feature/map/.../ogn/map/OgnTrafficOverlay.kt`
- `feature/map/.../ogn/ui/OgnTrafficUiModel.kt`

---

## 2) Data model contract

Define a minimal target model:

```kotlin
data class OgnTarget(
    val id: String,
    val callsign: String?,
    val lat: Double,
    val lon: Double,
    val altitudeMslM: Double?,
    val groundSpeedMs: Double?,
    val trackDeg: Double?,
    val climbMs: Double?,
    val timestampMs: Long,
    val aircraftType: String?,
    val source: String = "OGN"
)
```

Add computed metadata in UI mapping layer:

- `distanceMeters`
- `bearingDegFromUser`
- `ageSeconds`
- `isStale`

---

## 3) 50km filter logic

Implement in `OgnTrafficUseCase`:

1. Read user location flow from map state (`currentUserLocation`) and/or `mapLocation`.
2. Apply two-stage filter for performance:
   - fast bounding box pre-filter around center
   - precise haversine filter: `distanceMeters <= 50_000`
3. Drop stale traffic (recommended: > 45 seconds old).
4. Sort by distance ascending.

### Tunables

- `radiusKm` default `50.0` (future settings surface)
- `staleTimeoutSec` default `45`
- `maxTargetsRendered` default `200`

---

## 4) Networking and ingestion

`feature/map` already includes Retrofit + OkHttp + Gson, so reuse that stack.

### Implementation requirements

- Reconnecting client with exponential backoff.
- Parse feed payload -> `OgnTarget` updates.
- Upsert by `id` in memory store.
- Cleanup task to evict stale targets periodically.
- Expose connection state:
  - `Disconnected`
  - `Connecting`
  - `Connected`
  - `Error(message)`

---

## 5) Map rendering

Create `OgnTrafficOverlay` using GeoJSON sources/layers:

- `traffic-source`
- symbol layer for aircraft icon
- optional text layer for callsign/altitude

### Rendering rules

- Render only filtered (<=50km) set.
- Rotate icon by `trackDeg` when available.
- Use style expressions for icon/image by `aircraftType` when possible.
- Keep traffic layer **below ownship icon** and above static map features.
- Throttle render updates to ~1 Hz to avoid style churn.

---

## 6) Wiring into map lifecycle

Integrate with map style/lifecycle paths:

1. Create/init overlay during map style load.
2. Re-initialize on style changes.
3. Update source whenever filtered flow changes.
4. Clear source on teardown.

Suggested integration points:

- `MapInitializer.setupOverlays(...)`
- `MapOverlayManager.onMapStyleChanged(...)`
- map lifecycle cleanup/dispose paths

---

## 7) ViewModel and UI state

In `MapScreenViewModel` add:

- `ognTraffic: StateFlow<List<OgnTrafficUiModel>>`
- `ognConnectionState: StateFlow<OgnConnectionState>`
- `showOgnTraffic: StateFlow<Boolean>` (feature flag / user pref)

Behavior:

- Auto-start traffic when map is active and location permission granted.
- Suspend ingestion when map screen is not visible.
- Keep replay/live modes isolated (OGN optional during replay, default off if noisy).

---

## 8) User-facing UX (MVP)

- Toggle in map overlays: **"OGN Traffic"**.
- Optional chip/status: `OGN Connected / Connecting / No data`.
- Empty state text:
  - "No traffic within 50 km"
- Marker tap bottom sheet:
  - callsign/reg
  - distance + relative bearing
  - altitude and climb
  - age

---

## 9) Privacy, safety, and compliance

- Respect OGN terms and privacy flags from DDB.
- Do not persist unrestricted traffic history.
- In-app disclaimer: informational only, **not collision avoidance**.
- Keep traffic out of final glide / task computations.

---

## 10) Incremental delivery plan

### Phase 1 — Data pipeline
- OGN client + parser
- repository store + stale cleanup
- connection state flow

### Phase 2 — Radius filtering
- combine with user location
- 50km filter + sorting
- unit tests for edge distances

### Phase 3 — Overlay rendering
- map source/layers init
- marker rendering and updates
- style reload resilience

### Phase 4 — UX and controls
- toggle + status
- tap details
- empty states

### Phase 5 — Hardening
- reconnect/backoff
- performance profiling on dense traffic
- battery/network behavior checks

---

## 11) Test plan

### Unit tests

- Haversine distance accuracy and threshold behavior (49.9km, 50.0km, 50.1km).
- Stale eviction behavior.
- Upsert/replace logic by target id.
- Sorting by distance.

### Integration tests (where practical)

- Overlay initialization and re-initialization after style change.
- Source updates with synthetic traffic list.
- Toggle hides/shows overlay and halts updates.

### Manual QA scenarios

1. GPS fixed, no traffic -> clear empty state.
2. Several nearby traffic targets -> markers + sorted list.
3. Network drop -> reconnect and status transitions.
4. Style switch -> traffic layer restored correctly.
5. Screen pause/resume -> no leaks, resumes stream.

---

## 12) Implementation checklist

- [ ] Add OGN domain/data model classes.
- [ ] Build OGN network client + parser.
- [ ] Add repository with in-memory traffic store.
- [ ] Add use case with 50km filtering.
- [ ] Wire into ViewModel state flows.
- [ ] Implement MapLibre traffic overlay.
- [ ] Add overlay lifecycle hooks on style load/change.
- [ ] Add UI toggle and connection indicator.
- [ ] Add tests (unit + map integration where possible).
- [ ] Add disclaimer/compliance review.

---

## 13) Suggested initial defaults

- Radius: `50 km`
- Stale timeout: `45 s`
- UI refresh interval: `1000 ms`
- Reconnect initial delay: `2 s`
- Reconnect max delay: `60 s`

These values provide a practical balance between responsiveness, stability, and battery usage.
