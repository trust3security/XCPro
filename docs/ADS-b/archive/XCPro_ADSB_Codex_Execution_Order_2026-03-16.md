# XCPro ADS-B Provider Refactor: Execution Order for Codex

Date: 2026-03-16  
Audience: Codex implementation agent  
Status: Execution plan  
Primary repo: `trust3security/XCPro`

---

## Mission
Refactor XCPro’s current ADS-B/OpenSky implementation into a provider-agnostic architecture with **backend relay as the default commercial path**, while preserving the current repository/store/map behavior and retaining direct OpenSky as a dev/advanced option.

---

## Non-negotiable constraints

1. Do **not** rip out the current ADS-B architecture.
2. Do **not** regress current map overlay behavior.
3. Do **not** move traffic logic into UI/ViewModel.
4. Do **not** delete existing OpenSky support.
5. Do **not** require end-user provider secrets for the standard release path.
6. Do **not** perform a giant rewrite.

---

## Assumed current strengths

Use these as anchors, not targets for demolition:
- OpenSky OAuth2 client-credentials token handling exists and works
- ADS-B repository/runtime/store architecture exists and works
- adaptive polling and credit-aware logic exists
- configurable distance and vertical filters exist
- tests already exist around repository behavior and connectivity handling

---

## Phase 0 — Recon and safety net

### Goal
Understand exactly what exists before touching architecture.

### Tasks
1. Locate all ADS-B/OpenSky classes, interfaces, DI bindings, settings screens, and tests.
2. Produce an internal implementation note summarizing:
   - provider boundary
   - repository boundary
   - settings boundary
   - map overlay path
3. Confirm which classes are provider-neutral vs OpenSky-specific.
4. Run or update tests before refactor.

### Deliverables
- small internal note or code comments
- green baseline tests

### Exit criteria
- Codex can name the current flow from provider client -> repository -> use case -> viewmodel -> overlay
- no behavior changes yet

---

## Phase 1 — Clarify boundaries in code

### Goal
Separate provider-neutral traffic domain from OpenSky-specific auth/config behavior.

### Tasks
1. Review package structure and move only if really needed.
2. Ensure the following separation is visible in code:

#### Provider-neutral
- traffic models used beyond provider edge
- repository runtime state
- polling policy
- filtering/store logic
- snapshot/debug state
- use cases and map integration

#### OpenSky-specific
- token repository
- credentials repository
- auth models
- OpenSky response parsing
- OpenSky header semantics

3. Rename ambiguous classes only if that reduces future confusion.

### Exit criteria
- a new backend provider can be added without hacking OpenSky code paths everywhere

---

## Phase 2 — Add provider mode as a first-class concept

### Goal
Stop assuming there is only one real ADS-B provider.

### Tasks
1. Add a provider mode enum/value object, for example:

```kotlin
enum class AdsbProviderMode {
    DISABLED,
    BACKEND_RELAY,
    OPENSKY_DIRECT
}
```

2. Add preference/config storage for provider mode.
3. Add DI/factory wiring so the selected provider mode resolves the provider client.
4. Keep current direct OpenSky as `OPENSKY_DIRECT`.

### Acceptance
- provider mode can be changed without invasive downstream changes
- repository/store/map pipeline remains intact

---

## Phase 3 — Define backend relay contract

### Goal
Design the backend contract before implementing the client.

### Tasks
1. Define an XCPro backend endpoint contract.
2. Keep the response normalized and practical.
3. Do not expose raw OpenSky array-index style payloads to the app from backend if avoidable.

### Recommended request shape

```json
{
  "bbox": {
    "minLat": -33.95,
    "minLon": 151.05,
    "maxLat": -33.75,
    "maxLon": 151.35
  },
  "ownship": {
    "lat": -33.8688,
    "lon": 151.2093,
    "altitudeMeters": 450.0
  },
  "filters": {
    "maxDistanceKm": 10,
    "verticalAboveMeters": 914.0,
    "verticalBelowMeters": 2000.0
  },
  "clientContext": {
    "appVersion": "x.y.z",
    "platform": "android"
  }
}
```

### Recommended response shape

```json
{
  "provider": "opensky",
  "fetchedAtEpochSec": 1710000000,
  "remainingCredits": 123,
  "targets": [
    {
      "icao24": "abc123",
      "callsign": "QFA12",
      "latitude": -33.86,
      "longitude": 151.21,
      "altitudeMeters": 500.0,
      "groundSpeedMps": 55.0,
      "trackDegrees": 270.0,
      "verticalRateMps": -1.5,
      "category": "aircraft"
    }
  ]
}
```

### Exit criteria
- contract is simple enough for app and backend to version independently

---

## Phase 4 — Implement BackendRelayProviderClient

### Goal
Add the new provider without destabilizing downstream logic.

### Tasks
1. Create backend DTOs.
2. Create mapper from backend DTOs to the existing provider-neutral traffic domain input.
3. Implement `BackendRelayProviderClient : AdsbProviderClient`.
4. Reuse the repository/store exactly as far downstream as possible.
5. Add error mapping consistent with current connection state handling.

### Acceptance
- app can fetch ADS-B traffic through backend relay
- repository/store/map code requires little or no behavioral change

---

## Phase 5 — Release-mode defaults

### Goal
Make the safe path the normal path.

### Tasks
1. Set `BACKEND_RELAY` as the release default.
2. Keep `OPENSKY_DIRECT` available only for:
   - debug builds
   - internal builds
   - advanced-user opt-in mode if product wants it
3. Rework settings UI accordingly.

### Suggested UI stance
- Normal user: sees ADS-B toggle and optional debug/provider status, but not provider secrets
- Developer/advanced mode: can enter direct OpenSky credentials if explicitly enabled

### Acceptance
- standard release build works without user entering OpenSky client credentials

---

## Phase 6 — Tests

### Must-have tests
1. Provider mode selection resolves the correct client.
2. Direct OpenSky mode still behaves as before.
3. Backend relay mode produces equivalent downstream target behavior.
4. Existing filters still apply.
5. Existing snapshot/connection state behavior remains sane under backend errors.
6. No regression in stale target handling.
7. No regression in reconnect/retry behavior where still relevant.

### Nice-to-have tests
- settings visibility by build type/provider mode
- backend DTO malformed response handling
- provider switching at runtime if supported

---

## Phase 7 — Cleanup and polish

### Tasks
1. Remove duplicated provider-specific wording from generic traffic UI.
2. Add debug fields for:
   - provider mode
   - last provider status
   - next poll delay
   - remaining credits if present
3. Update docs in `docs/ADS-b/`.
4. Leave concise migration notes for future providers.

---

## Files likely to touch

This is guidance, not a hard limit.

### Likely existing files/modules
- `feature/traffic/.../adsb/OpenSkyTokenRepository.kt`
- `feature/traffic/.../adsb/AdsbProviderClient.kt`
- `feature/traffic/.../adsb/OpenSkyProviderClient.kt`
- `feature/traffic/.../adsb/AdsbTrafficRepository*.kt`
- `feature/traffic/.../adsb/AdsbTrafficStore*.kt`
- ADS-B settings/use case/viewmodel files
- DI modules for traffic/network bindings
- tests under ADS-B traffic package

### Likely new files
- `BackendRelayProviderClient.kt`
- `BackendRelayDtos.kt`
- provider mode/config model file
- provider factory/binding helper

---

## Refactor rules

### Allowed
- extract interfaces
- add provider mode config
- add a new provider implementation
- refactor DI
- add tests
- lightly clean naming

### Not allowed unless required
- rewrite all traffic models
- rebuild map overlay pipeline
- delete OpenSky direct support
- redesign settings screen from scratch
- invent a huge plugin system

---

## Definition of done

The work is done when all of the following are true:

1. XCPro still shows ADS-B traffic correctly on the map.
2. Current OpenSky direct mode still works for development/advanced use.
3. Backend relay mode works and is the default intended commercial path.
4. Existing repository/store/filtering logic is mostly preserved.
5. No provider secrets are required for standard end-user release operation.
6. Tests cover provider selection and no major regressions are introduced.

---

## Final instruction

Treat the current OpenSky implementation as a **valuable base layer**.

Do not waste time rewriting what already works.
The job is to **decouple, add backend relay, preserve behavior, and strengthen release architecture**.
