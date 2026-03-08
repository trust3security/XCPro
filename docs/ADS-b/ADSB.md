# ADSB.md - Live ADS-B Internet Traffic in XCPro (OpenSky)
**v8 (2026-03-08): runtime contract, maintainability hardening, and deep code-pass findings**

This document is the runtime contract for ADS-B behavior in XCPro.

## 0) Definitions

There are two different "type" concepts:

1. Aircraft identification (what pilots mean by "what aircraft is that?")
- Registration (tail number), for example `VH-DFV`
- Typecode (ICAO aircraft type designator), for example `C208`
- Model, for example `CESSNA 208 Caravan`
- Optional ICAO aircraft class/description metadata when available

2. Emitter category (OpenSky state-vector category)
- ADS-B emitter category bucket from `/states/all?extended=1`
- This is not the same as aircraft registration/model/typecode metadata

UI rule:
- Do not label emitter category as generic "Aircraft Type".
- Use "Emitter category" wording in UI/details.

Reference:
- https://openskynetwork.github.io/opensky-api/rest.html

## 1) Data sources

1. Live traffic state:
- `GET https://opensky-network.org/api/states/all?lamin=...&lomin=...&lamax=...&lomax=...&extended=1`

2. Aircraft metadata (ICAO24 keyed):
- `https://opensky-network.org/datasets/metadata/aircraftDatabase.csv`
- imported locally into Room and joined by ICAO24

Reference:
- https://opensky-network.org/data
- https://opensky-network.org/data/aircraft

## 2) Runtime contract (validated as of 2026-02-20)

### 2.1 Identity and mapping
- Parse `icao24` from state index `0`.
- Normalize lowercase for storage and lookup.
- Use ICAO24 as stable feature identity for map updates.

### 2.2 Display envelope and filtering
- Horizontal display distance:
  - user configurable
  - min `1 km`, max `100 km`, default `10 km`
- Vertical filtering:
  - separate `Above ownship` and `Below ownship` limits
  - values stored in meters
  - UI labels follow `General -> Units` altitude unit
- Display cap:
  - maximum `30` displayed targets
- Airborne gate:
  - altitude must be `> 100 ft`
  - speed must be `> 40 kt`
- Position source filter:
  - FLARM source rows (`position_source=3`) are ignored

### 2.2A Icon fallback semantics
- Aircraft class `Unknown` remains an explicit semantic outcome in ADS-B state/details.
- Map rendering rollout default uses a neutral fixed-wing fallback for unknown class:
  - style image id: `adsb_icon_unknown`
  - drawable asset: `ic_adsb_plane_medium.png`
- Rollback path remains available via rollout flag:
  - style image id: `adsb_icon_unknown_legacy`
  - drawable asset: `ic_adsb_unknown.png`
- Non-fixed-wing authoritative categories (`8`, `9`, `10`, `11`, `12`, `14`) are unchanged.

### 2.3 Ownship reference semantics
- Query center is used for provider fetch and horizontal radius filtering.
- Ownship origin is used for displayed distance and bearing when available.
- Ownship altitude is used for vertical filtering when available.
- ADS-B proximity semantics are ownship-relative only (phone/glider current position).
- OGN targets do not participate in ADS-B distance/bearing, color-tier, or details-distance calculations.
- If ownship altitude is unavailable:
  - vertical filtering is fail-open (targets are not dropped by vertical limits).
- If ownship position is unavailable:
  - center fallback is used for geometry
  - marker urgency coloring uses neutral color
  - emergency collision-risk classification is disabled
- Same-coordinate ownship-origin refresh updates republish snapshot/store state immediately,
  restoring ownship-relative semantics without waiting for the next provider poll.

### 2.4 Proximity coloring
- Policy is trend-aware, not distance-only.
- Distance tier base:
  - Distance `> 5 km`: green
  - Distance `2..5 km`: amber
  - Distance `<= 2 km`: red
- Alert colors (`amber`/`red`) are shown only while closing trend is active.
- If a target is no longer closing (steady or diverging), color de-escalates to green
  after a short recovery dwell (`4 s`) to avoid flicker.
- Non-fresh trend evaluations hold the last resolved non-emergency tier to prevent
  `green <-> amber` rebound flicker between fresh and stale reevaluations.
- First in-range sample is treated as alert-eligible until trend is established.
- Emergency collision-risk styling has highest priority and requires:
  - emergency geometry match,
  - active closing trend,
  - fresh sample age (`<= 20 s`).

### 2.5 Polling behavior
- Hot interval baseline is `10s`.
- Runtime can back off based on empty scans, movement, auth mode, and quota budget floors.
- Radius changes reconnect immediately from settings flow.
- Transient network failures are classified (`DNS`, `timeout`, `connect`, `no route`, `TLS`, malformed payload, `unknown`)
  and use bounded retry floors before exponential backoff:
  - DNS/no-route: minimum `15s`
  - timeout: minimum `8s`
  - connect: minimum `10s`
  - TLS/malformed payload: minimum `20s`
  - unknown: minimum `2s`
- Polling is connectivity-aware:
  - when device network is offline, ADS-B polling pauses (no request loop churn)
  - polling resumes immediately when network returns
  - if connectivity drops during an active retry/poll delay window, the timer is interrupted and
    polling waits for reconnect instead of burning the remaining delay budget
- Failure circuit breaker:
  - after `3` consecutive fetch failures, polling enters cooldown for `30s`
  - after cooldown, one half-open probe fetch is attempted
  - if probe fails, cooldown reopens; if probe succeeds, normal cadence resumes
- Retry/circuit policy ownership:
  - `AdsbPollingHealthPolicy` owns consecutive-failure counters, probe scheduling, and circuit state transitions
- Runtime mutation serialization:
  - repository runtime mutators are single-writer serialized on one dispatcher lane;
    center/ownship/filter/reconnect/enable updates enqueue onto the runtime scope
    before any store/FSM mutation.
- Snapshot diagnostics (debug panel):
  - publishes `consecutiveFailureCount`, `nextRetryMonoMs`, and `lastFailureMonoMs`
  - reason labels include circuit-breaker and network-failure categories
  - issue-state UI policy treats both `Error` and `BackingOff` as degraded connectivity episodes
- Token-auth behavior:
  - explicit credential rejection from token endpoint (`400/401/403`) enters `AuthFailed` mode
  - transient token-fetch network failures fall back to anonymous mode (not `AuthFailed`)
- HTTP bridge cancellation hardening:
  - coroutine/OkHttp bridge closes pending responses on cancel/resume races to avoid leaked sockets
- Metadata transport hardening:
  - metadata CSV download and DB import are decoupled via temp-file staging; import runs after HTTP response closure
  - ADS-B polling HTTP client and ADS-B metadata HTTP client use separate timeout profiles
- Network callback hardening:
  - `AdsbNetworkAvailabilityTracker` normalizes callback flaps and keeps fail-open behavior when callback registration is unavailable

### 2.6 Network exception expectations
- `UnknownHostException` is expected on unstable mobile DNS paths and maps to `AdsbNetworkFailureKind.DNS`.
- `SocketTimeoutException` is expected under latency/packet loss and maps to `AdsbNetworkFailureKind.TIMEOUT`.
- Both cases are treated as transient network failures, not fatal implementation bugs.
- Correct runtime behavior is: classify -> bounded backoff -> circuit-breaker protection -> recover when network/provider stabilizes.

### 2.7 Maintainability and observability hardening (2026-03-08)
- Runtime policies are centralized in one contract file:
  - `AdsbTrafficRepositoryRuntimePolicy.kt`
- Runtime orchestration is split into focused helpers with unchanged behavior contract:
  - `AdsbTrafficRepositoryRuntimeLoopTransitions.kt` (poll-cycle/error/backoff transitions)
  - `AdsbTrafficRepositoryRuntimeNetworkWait.kt` (center/network wait + housekeeping driver)
  - `AdsbTrafficRepositoryRuntimeSnapshot.kt` (snapshot projection decomposition)
- ADS-B map overlay/runtime delegate internals are split for safer change boundaries:
  - `AdsbOverlayFrameLoopController.kt` (frame scheduling + interval gating)
  - `AdsbTrafficOverlayFeatureProjection.kt` (feature projection path)
  - `MapOverlayManagerRuntimeTrafficDelegate.kt` projection helper reuse for init/render parity
- Diagnostics surfaces now expose counters consistently:
  - Repository snapshot transition telemetry:
    `networkOnline`, `networkOfflineTransitionCount`, `networkOnlineTransitionCount`,
    `lastNetworkTransitionMonoMs`, `currentOfflineDwellMs`,
    `consecutiveFailureCount`, `nextRetryMonoMs`, `lastFailureMonoMs`
  - Map overlay runtime counters:
    unknown/legacy unknown icon render counts, icon resolve-latency stats,
    default-medium-unknown rollout effective flag, overlay front-order apply/skip counts
    (surfaced via `MapOverlayManagerRuntime.runtimeCounters()` and `getOverlayStatus()`).

## 3) Details sheet requirements

Show sections:

1. Aircraft identification
- registration
- typecode
- model
- optional ICAO class/description field (when present)

2. Live state
- ICAO24
- callsign
- altitude
- speed
- track
- vertical rate
- distance
- ownship reference status
- proximity tier
- trend state (closing / recovery dwell / not closing)
- range rate (+ closing)
- proximity reason

Units contract:
- altitude, speed, vertical rate, and distance follow `General -> Units`
- if vertical speed unit is feet per minute, show `ft/min` suffix

3. Emitter category
- human-readable label + raw value when available

Metadata-not-ready contract:
- show metadata sync state ("downloading", "importing", "error", or last updated state)

Details semantics:
- "Distance from ownship" is always ownship-relative and is independent from OGN overlay data.
- When ownship reference is unavailable, details explicitly show query-center fallback semantics.

## 4) Parser indexes (/states/all)

```kotlin
private const val IDX_ICAO24 = 0
private const val IDX_CALLSIGN = 1
private const val IDX_LON = 5
private const val IDX_LAT = 6
private const val IDX_BARO_ALT_M = 7
private const val IDX_ON_GROUND = 8
private const val IDX_VELOCITY_MPS = 9
private const val IDX_TRUE_TRACK_DEG = 10
private const val IDX_VERT_RATE_MPS = 11
private const val IDX_GEO_ALT_M = 13
private const val IDX_POSITION_SOURCE = 16
private const val IDX_CATEGORY = 17 // when extended=1
```

## 5) Deep-pass status (updated 2026-03-08)

Closed in current implementation:

1. Polling-health telemetry cross-thread visibility hardening.
2. Top-level loop recovery guard for unexpected non-cancellation failures.
3. Dedupe for unchanged center/origin/altitude updates to reduce avoidable reselection churn.
4. Stable target ordering tie-break for equal-priority display candidates.
5. Atomic metadata revision increment for on-demand hydration updates.
6. Token refresh single-flight dedupe under concurrent stale-token requests.
7. Metadata listing robustness guardrails (`NextMarker` fallback, token-loop guard, bounded page guard, dedicated tests).
8. Icon classification precedence hardening for non-fixed-wing categories with expanded helicopter prefix/conflict tests.
9. Offline wait stale/expiry progression:
   - repository now advances housekeeping while waiting for network restoration, so stale dim and expiry purge continue during long offline periods.
10. Cache reselection expiry safety:
   - `publishFromStore(...)` now purges expired targets before select/publish.
   - regression coverage added for offline stale->expiry progression and center-update reselection while offline.
11. Reconnect monotonic timestamp freshness:
   - success path now stamps `lastPollMonoMs`/`lastSuccessMonoMs` using current post-wait monotonic time.
   - fixes stale pre-wait timestamp carryover after long offline/circuit waits.
   - regression coverage added for offline-wait reconnect success timestamp correctness.
12. Runtime policy and orchestration maintainability hardening:
   - loop/poll/network-wait/snapshot responsibilities split into focused helper files.
   - direct runtime transition tests added (offline->online, circuit probe transition, reconnect timestamp, degraded snapshot fields).
13. ADS-B overlay/delegate maintainability hardening:
   - overlay frame loop and feature projection split into dedicated helper units.
   - traffic delegate style projection path unified for init/render parity.
   - direct delegate tests added for throttling/deferred flush, sticky projection, rollout switch, runtime counters.
14. Overlay status diagnostics consistency:
   - ADS-B icon telemetry + overlay front-order counters are included in `MapOverlayManager` status output.

Still open:

1. Metadata import must continue supporting both direct CSV and complete snapshot format differences (`icaoAircraftClass` alias and quoted-field variations).
2. ADS-B degraded-state UX and transition observability were implemented in the connectivity score-lift pass:
   - persistent release-safe degraded status surface in map UI
   - typed transition telemetry in snapshot (`networkOnline`, transition counters, transition timestamp, offline dwell)
   Remaining gap: stronger release/e2e network-transition coverage is tracked in:
   - `docs/ADS-b/CHANGE_PLAN_ADSB_CONNECTIVITY_SCORE_LIFT_2026-03-01.md`
   - `docs/ADS-b/CHANGE_PLAN_ADSB_NETWORK_TRANSITION_E2E_COVERAGE_2026-03-01.md`
   - `docs/ADS-b/CHANGE_PLAN_ADSB_CONNECTIVITY_UX_STALE_HOUSEKEEPING_OBSERVABILITY_2026-03-01.md`

## 6) Improvement and implementation references

- `docs/ADS-b/README.md`
- `docs/ADS-b/CHANGE_PLAN_ADSB_CONNECTIVITY_SCORE_LIFT_2026-03-01.md`
- `docs/ADS-b/CHANGE_PLAN_ADSB_NETWORK_TRANSITION_E2E_COVERAGE_2026-03-01.md`
- `docs/ADS-b/CHANGE_PLAN_ADSB_CONNECTIVITY_UX_STALE_HOUSEKEEPING_OBSERVABILITY_2026-03-01.md`
- `docs/ADS-b/CHANGE_PLAN_ADSB_SOCKET_ERROR_HARDENING_2026-03-01.md`
- `docs/ADS-b/CHANGE_PLAN_ADSB_MAINTAINABILITY_SCORE_LIFT_2026-03-08.md`
- `docs/ADS-b/ADSB_Improvement_Plan.md`
- `docs/ADS-b/ADSB_AircraftMetadata.md`
- `docs/ADS-b/ADSB_CategoryIconMapping.md`
