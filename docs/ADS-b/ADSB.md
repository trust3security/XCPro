# ADSB.md - Live ADS-B Internet Traffic in XCPro (OpenSky)
**v7 (2026-02-20): runtime contract and deep code-pass findings**

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

### 2.3 Ownship reference semantics
- Query center is used for provider fetch and horizontal radius filtering.
- Ownship origin is used for displayed distance and bearing when available.
- Ownship altitude is used for vertical filtering when available.
- If ownship altitude is unavailable:
  - vertical filtering is fail-open (targets are not dropped by vertical limits).
- If ownship position is unavailable:
  - center fallback is used for geometry
  - marker urgency coloring uses neutral color
  - emergency collision-risk classification is disabled

### 2.4 Proximity coloring
- Distance `> 5 km`: green
- Distance `2..5 km`: gradient green -> red
- Distance `<= 2 km`: red
- Emergency collision-risk styling has priority and stays red.

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
- Snapshot diagnostics (debug panel):
  - publishes `consecutiveFailureCount`, `nextRetryMonoMs`, and `lastFailureMonoMs`
  - reason labels include circuit-breaker and network-failure categories
- Token-auth behavior:
  - explicit credential rejection from token endpoint (`400/401/403`) enters `AuthFailed` mode
  - transient token-fetch network failures fall back to anonymous mode (not `AuthFailed`)
- HTTP bridge cancellation hardening:
  - coroutine/OkHttp bridge closes pending responses on cancel/resume races to avoid leaked sockets
- Network callback hardening:
  - `AdsbNetworkAvailabilityTracker` normalizes callback flaps and keeps fail-open behavior when callback registration is unavailable

### 2.6 Network exception expectations
- `UnknownHostException` is expected on unstable mobile DNS paths and maps to `AdsbNetworkFailureKind.DNS`.
- `SocketTimeoutException` is expected under latency/packet loss and maps to `AdsbNetworkFailureKind.TIMEOUT`.
- Both cases are treated as transient network failures, not fatal implementation bugs.
- Correct runtime behavior is: classify -> bounded backoff -> circuit-breaker protection -> recover when network/provider stabilizes.

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

Units contract:
- altitude, speed, vertical rate, and distance follow `General -> Units`
- if vertical speed unit is feet per minute, show `ft/min` suffix

3. Emitter category
- human-readable label + raw value when available

Metadata-not-ready contract:
- show metadata sync state ("downloading", "importing", "error", or last updated state)

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

## 5) Deep-pass status (updated 2026-02-21 +11:45)

Closed in current implementation:

1. Polling-health telemetry cross-thread visibility hardening.
2. Top-level loop recovery guard for unexpected non-cancellation failures.
3. Dedupe for unchanged center/origin/altitude updates to reduce avoidable reselection churn.
4. Stable target ordering tie-break for equal-priority display candidates.
5. Atomic metadata revision increment for on-demand hydration updates.
6. Token refresh single-flight dedupe under concurrent stale-token requests.
7. Metadata listing robustness guardrails (`NextMarker` fallback, token-loop guard, bounded page guard, dedicated tests).
8. Icon classification precedence hardening for non-fixed-wing categories with expanded helicopter prefix/conflict tests.

Still open:

1. Metadata import must continue supporting both direct CSV and complete snapshot format differences (`icaoAircraftClass` alias and quoted-field variations).

## 6) Improvement and implementation references

- `docs/ADS-b/README.md`
- `docs/ADS-b/ADSB_Improvement_Plan.md`
- `docs/ADS-b/ADSB_AircraftMetadata.md`
- `docs/ADS-b/ADSB_CategoryIconMapping.md`
