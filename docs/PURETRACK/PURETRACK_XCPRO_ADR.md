# PureTrack Integration for XCPro

**Status:** Draft ADR / implementation plan  
**Target location:** `docs/PURETRACK.md` or `docs/PURETRACK/README.md`  
**Last verified:** 2026-04-26  
**Verdict:** Ready for Phase 0/1 planning and API-core work, with corrections. Not ready for safety/proximity integration.  
**Phase 0 decision record:** `docs/ARCHITECTURE/ADR_PURETRACK_TRAFFIC_PROVIDER_2026-04-26.md`

Phase 0 locks provider ownership and Phase 1 boundaries. Phase 1.5 locks
production safety defaults without wiring runtime: the PureTrack app key is
confidential by XCPro/user decision because it is private to the paid
user/account, native XCPro traffic remains the default, PureTrack is opt-in,
over-wide production bboxes fail closed using XCPro's provisional Phase 2
threshold, and polling remains foreground/visible only once a later runtime
phase exists.

---

## 1. Executive verdict

PureTrack should be implemented as a **provider-owned traffic lane** inside `feature:traffic`.

Do **not** implement PureTrack as:

- an OGN replacement parser;
- an ADS-B source inside `AdsbTrafficRepository`;
- a map-layer direct data source;
- an emergency/proximity-alert provider in Phase 1.

Recommended decision:

```text
PureTrack = separate provider lane in feature:traffic
Map = consumes PureTrack only through existing traffic/map facades
Phase 1 = parser/client/session tests only, inside feature:traffic
Phase 4 = render-only map overlay
Phase 5 = dedupe/safety model before proximity/emergency alerts
```

This is the right long-term architecture because PureTrack is not just ADS-B, not just FLARM/OGN, and not just a phone tracker. It is an aggregator of many traffic/tracking sources, including OGN/FLARM, ADS-B networks, cellular trackers, satellite trackers, PureTrack/mobile trackers, and third-party app trackers.

---

## 2. Problem statement

A helicopter pilot with **no ADS-B** and **no FLARM** can open PureTrack while flying and become visible in the PureTrack app/map.

That same aircraft is not visible in Glide & Seek or XCPro because XCPro currently does not ingest PureTrack traffic. Existing traffic visibility depends on the data sources XCPro listens to. If XCPro only listens to native ADS-B, OGN, FLARM-derived feeds, local sensors, or existing NMEA-style providers, then a PureTrack-only phone/cellular/satellite/app marker will not appear.

The issue is not that the helicopter is impossible to track. The issue is that XCPro is not listening to the PureTrack provider.

---

## 3. Official PureTrack facts

### 3.1 Traffic API access model

From PureTrack's official Traffic API documentation:

- A PureTrack application API key is required.
- Users must be PureTrack Pro subscribers to access PureTrack traffic data through third-party applications.
- Login is performed with application key, user email, and user password.
- Login returns an API token and a `pro` flag.
- Tokens generally do not expire unless manually removed by the user.
- Traffic requests require Bearer authentication.
- Traffic is fetched by bounding box.

Source: <https://puretrack.io/help/api>

### 3.2 Login endpoint

```text
POST https://puretrack.io/api/login
```

Required parameters:

```text
key       application API key
email     PureTrack user email
password  PureTrack user password
```

Example response shape:

```json
{
  "access_token": "<token>",
  "pro": true
}
```

Implementation rule:

```text
Store token only.
Never store password.
Do not export token in profile backups.
Do not log token, password, or Authorization header.
```

Source: <https://puretrack.io/help/api>

### 3.3 Traffic endpoint

```text
POST https://puretrack.io/api/traffic
```

PureTrack says GET parameters also work, but implementation should prefer one style consistently. Use Bearer auth with the token returned by `/api/login`.

Required parameters:

```text
key    application API key
lat1   bounding box top-right latitude
long1  bounding box top-right longitude
lat2   bounding box bottom-left latitude
long2  bounding box bottom-left longitude
```

Optional parameters:

```text
cat  category filter: air, ground, other, water
o    comma-separated object type IDs
t    max age in minutes; defaults to 5; up to 24 hours
s    PureTrack map item keys to always try to include
i    isolate only the items listed by s; boolean 1/0; defaults to 0
```

Source: <https://puretrack.io/help/api>

### 3.4 Bounding-box warning

PureTrack documents a critical bbox behavior:

```text
When the bounding box size is too large, t is lowered to 5 minutes,
and data for the whole planet is returned.
```

This means XCPro must **not** blindly poll PureTrack at continent/global zoom levels. The runtime must clamp or reject large bounding boxes.

Source: <https://puretrack.io/help/api>

### 3.5 Traffic row format

Traffic response rows are compact comma-separated token strings. PureTrack states that only these fields are guaranteed:

```text
T  timestamp
L  latitude
G  longitude
K  PureTrack key
```

Most other values are optional and may be missing.

Important row fields:

```text
T  timestamp, Unix epoch seconds
L  latitude
G  longitude
K  PureTrack key
A  GPS altitude, metres
P  pressure
C  course, degrees 0-360
S  speed, m/s
V  vertical speed, m/s
O  object type, see types.json
D  tracker_uid, original tracker ID such as FLARM ID or ADS-B ICAO hex
U  source_type_id
J  PureTrack target ID
B  label
N  pilot/person name if provided
E  aircraft registration
M  aircraft model
h  horizontal accuracy
z  vertical accuracy
m  callsign, usually from ADS-B
8  on_ground
```

Parser rule:

```text
The first character is the token key.
The remaining substring is the token value.
Keys are case-sensitive.
Unknown keys must be ignored, not fatal.
Malformed rows must be dropped, not crash the app.
```

Source: <https://puretrack.io/help/api>

### 3.6 Source IDs

PureTrack rows may include `U`, the source type ID. Examples from the official source list include:

```text
0   flarm
1   spot
9   inreach
12  adsb
16  puretrack
18  celltracker
23  xcontest
24  skylines
26  livegliding
27  ADSBExchange
28  adsb.lol
29  adsb.fi
34  Tracker App
35  OGN ICAO
36  XC Guide
42  Gaggle
43  Wingman
45  airplanes.live
46  ADSB
47  XCglobe/FlyMe
50  Naviter Omni
51  Garmin Watch
```

Source: <https://puretrack.io/help/api>

### 3.7 Object types and helicopter handling

PureTrack object types are defined in `types.json`.

Relevant examples:

```text
id 0   cat other  name Unknown
id 1   cat air    name Glider
id 2   cat air    name Tow
id 3   cat air    name Helicopter
id 7   cat air    name Paraglider
id 8   cat air    name Plane
id 13  cat air    name Drone
id 16  cat air    name Gyrocopter
id 20  cat other  name Person
```

For the helicopter use case, `cat=air` should include a correctly configured helicopter marker with `O=3`. But if the pilot is using a phone tracker and the PureTrack marker is configured as `Person`, `Unknown`, or some other non-air type, `cat=air` may exclude it.

Therefore:

```text
Recommended future production filter: cat=air (not locked by Phase 0)
Debug/testing: use small bbox with no cat filter, or s=<known PureTrack key>
```

Source: <https://puretrack.io/types.json>

### 3.8 PureTrack is broader than aviation-only tracking

PureTrack's FAQ says it shows ADS-B traffic but also includes SPOT, InReach, cellular trackers, FLARM, OGN, and many paragliding apps/devices. It also says PureTrack is not limited to aircraft and can track vehicles, boats, and people.

This is why PureTrack cannot be treated as an ADS-B-only or OGN-only feed.

Source: <https://puretrack.io/help/faq>

### 3.9 PureTrack vs situational-awareness systems

PureTrack's FAQ says SafeSky is designed for situational awareness while flying, while PureTrack is primarily designed for general safety tracking.

This distinction matters for XCPro. PureTrack is valuable for display and tracking, but Phase 1 must not treat it as collision-grade traffic.

Source: <https://puretrack.io/help/faq>

### 3.10 Insert API is separate live tracking point publishing

PureTrack also provides an Insert API for sending application/user tracking data into PureTrack. It requires a separate insert key and is intended to let an app send its own users' safety-tracking points to PureTrack.

This is **not required** for XCPro to display existing PureTrack traffic.

For XCPro, the Insert API means live tracking point publishing only. It is not route or turnpoint publishing.

No Insert API implementation is approved in Phase 1. Phase 1 should use the Traffic API only.

Source: <https://puretrack.io/help/api-insert>

---

## 4. Repo-local architecture facts to verify

Codex previously identified these XCPro patterns:

```text
ADS-B:
  AdsbTrafficRepository.kt
  AdsbTrafficRepositoryRuntime.kt
  polling provider client
  token repository
  runtime store
  backoff
  network state

OGN:
  OgnTrafficRepository.kt
  socket stream
  OGN-specific DDB
  thermals/trails/SCIA

Map:
  TrafficMapApi.kt
  MapScreenTrafficCoordinator.kt
  map reads traffic through facades/coordinator, not direct data sources
```

These file names should be verified against the current repo before coding. The architecture recommendation assumes this pattern remains true:

```text
Provider repository owns data acquisition/runtime.
Map coordinator consumes provider output through traffic/map facade.
Map must not directly know API clients or auth repositories.
```

---

## 5. Architectural recommendation

### 5.1 Provider ownership

Implement PureTrack under `feature:traffic` as a separate provider-owned lane.

Suggested package:

```text
feature:traffic/.../puretrack/
```

Suggested files:

```text
PureTrackModels.kt
PureTrackRowParser.kt
PureTrackTypeMapper.kt
PureTrackSourceMapper.kt
PureTrackProviderClient.kt
PureTrackAuthRepository.kt
PureTrackSessionRepository.kt
PureTrackTokenStore.kt
PureTrackTrafficRepository.kt
PureTrackTrafficRepositoryRuntime.kt
PureTrackBboxPolicy.kt
PureTrackBackoff.kt
PureTrackSnapshot.kt
PureTrackSettings.kt
```

Naming note:

Avoid `PureTrackCredentialsRepository` unless it never stores the password. Prefer names such as:

```text
PureTrackSessionRepository
PureTrackTokenStore
PureTrackAccountStateRepository
```

### 5.2 Why PureTrack must not be folded into OGN

PureTrack includes OGN/FLARM-derived data, but it also includes ADS-B, satellite trackers, cellular trackers, app trackers, PureTrack markers, and other sources.

Putting PureTrack into OGN would corrupt ownership because:

```text
OGN semantics are not PureTrack semantics.
PureTrack auth/pro state does not belong in OGN.
PureTrack source IDs include non-OGN sources.
PureTrack row format is not an OGN socket stream.
PureTrack may duplicate OGN targets.
OGN-specific thermal/trail/SCIA behavior should not be polluted by PureTrack traffic.
```

### 5.3 Why PureTrack must not be folded into ADS-B

PureTrack includes ADS-B-derived data, but it is not an ADS-B provider. It can include phones, app trackers, satellite trackers, FLARM/OGN, paragliding apps, Garmin Watch, Wingman, Gaggle, XC Guide, and more.

Putting PureTrack inside `AdsbTrafficRepository` would cause:

```text
wrong provider semantics
misleading source names
bad safety assumptions
duplicate ADS-B targets
wrong auth/account ownership
future dedupe problems
```

### 5.4 Map integration rule

The map must not call PureTrack APIs directly.

Correct flow:

```text
PureTrackProviderClient
  -> PureTrackTrafficRepository/runtime
  -> traffic facade / map API
  -> MapScreenTrafficCoordinator
  -> render-only overlay
```

Wrong flow:

```text
MapScreen -> PureTrackProviderClient
MapScreen -> PureTrackAuthRepository
MapScreen -> raw PureTrack row parser
```

---

## 6. Provider mode and dedupe policy

### 6.1 The duplicate-provider problem

PureTrack may include ADS-B, OGN/FLARM, and other targets already visible through XCPro's native providers.

If XCPro runs these at the same time:

```text
native OGN
native ADS-B
PureTrack cat=air
```

then the app can show duplicates, conflicting labels, conflicting stale states, and conflicting identities.

### 6.2 Server-side source filtering is not documented

The public Traffic API documents filters for:

```text
cat
o
t
s
i
```

It does **not** document a server-side filter by `source_type_id`.

Therefore source filtering, if needed, must be done client-side after parsing `U`.

Source: <https://puretrack.io/help/api>

### 6.3 Provider modes

Phase 1.5 locks production defaults for future runtime/map work. Wiring and UI
remain deferred, but the defaults below are no longer optional recommendations.

Phase 1/4 should not enable three overlapping traffic systems by default.

Provider modes:

```text
Native mode, production default:
  Existing XCPro OGN/ADS-B behavior.
  PureTrack disabled.

PureTrack mode, user opt-in:
  PureTrack overlay enabled.
  Native OGN/ADS-B traffic can be disabled or visually separated to prevent duplicates.

Hybrid mode, experimental only:
  Native OGN/ADS-B + PureTrack.
  Requires provider-neutral dedupe before general release.
```

Possible future client-side source filtering:

```text
Exclude from PureTrack when native providers are enabled:
  U=0   flarm
  U=12  adsb
  U=27  ADSBExchange
  U=28  adsb.lol
  U=29  adsb.fi
  U=35  OGN ICAO
  U=45  airplanes.live
  U=46  ADSB

Keep likely non-native/mobile/satellite/app sources:
  U=1   spot
  U=9   inreach
  U=16  puretrack
  U=18  celltracker
  U=23  xcontest
  U=24  skylines
  U=34  Tracker App
  U=36  XC Guide
  U=42  Gaggle
  U=43  Wingman
  U=47  XCglobe/FlyMe
  U=50  Naviter Omni
  U=51  Garmin Watch
```

This must be configurable and tested. Do not ship it as silent magic without a visible provider/debug state.

---

## 7. Safety scope

### 7.1 Phase 1 rule

PureTrack must be **render/display only** in the first production scope.

Do not feed PureTrack into:

```text
emergency audio
collision/proximity alerts
existing ADS-B emergency logic
existing OGN proximity logic
flight-critical warnings
```

### 7.2 Why not safety alerts yet

Reasons:

```text
PureTrack is a general safety-tracking aggregator, not a collision-grade feed.
Latency can vary by source.
Cell coverage can drop.
Some targets may be stale.
Some markers may be phones, people, cars, boats, or app trackers.
Object type can be missing or wrong.
Source ID can vary.
PureTrack may merge multiple trackers into one marker.
PureTrack may duplicate native OGN/ADS-B targets.
Accuracy fields are optional.
```

### 7.3 Phase 5 requirement

Only after a provider-neutral dedupe and safety model exists should PureTrack be considered for proximity/emergency integration.

Phase 5 owner must define:

```text
dedupe key hierarchy
stale rules
latency assumptions
source trust model
minimum update frequency
accuracy requirements
altitude semantics
on-ground rules
provider conflict handling
user-visible warnings/disclaimers
```

---

## 8. Security and privacy requirements

### 8.1 App key handling

Phase 2 XCPro policy:

```text
The PureTrack application key is confidential because it is private to the paid
user/account.

No production BuildConfig.PURETRACK_APP_KEY wiring is allowed under the current
XCPro policy because the key is confidential and client-extractable.

Do not embed the key in the Android APK, BuildConfig, resources, logs, profile
backup, or any client-extractable storage.

Android runtime wiring remains blocked until a secure backend-mediated or
equivalent non-client-secret strategy exists.

Fake/test app-key providers remain allowed for unit tests.
```

### 8.2 Token handling

Rules:

```text
Store only the API token, not the password.
Never store the PureTrack password.
Never log token/password/Auth header.
Never export token in profile backups.
Logout must remove token.
401/invalid token must move runtime to NeedsLogin.
```

Production token persistence is owned by `PureTrackTokenStore`. The production
Android adapter is `AndroidPureTrackTokenStore`; it remains unwired in DI and
unused by runtime until a later phase. It uses Android Keystore-backed AES-GCM,
stores only version, IV, and ciphertext in app-private preferences, has no
plaintext fallback, stores no email/password, and is excluded from backup. If
local clear fails, the current process must suppress bearer access and expose
`PERSISTENCE_UNAVAILABLE`; it must not claim durable logout succeeded while
ciphertext may remain on disk.

### 8.3 Profile/export handling

Profile contributor may include non-secret preferences only:

```text
overlay enabled
provider mode
type/category filters
source filters
display preferences that are not tied to a selected identity
```

Profile contributor must not include:

```text
access_token
password
email
Authorization header
app key
watched keys
selected target
cached rows
raw API data
names
phone
identity-bearing traffic data
```

### 8.4 PII handling

PureTrack rows can include fields such as:

```text
N  name
B  label
p  phone
```

Map display and logs must be careful:

```text
Do not display phone numbers.
Do not log raw rows in production.
Debug logs should redact names/labels/phone/token.
Selection/details can show label/rego/model where appropriate.
```

---

## 9. Runtime contract

PureTrack runtime must be a single-writer state owner.

Suggested snapshot states:

```text
Disabled
NeedsLogin
NotPro
WaitingForNetwork
WaitingForMapBounds
BoundsTooWide
Polling
Active
Degraded
Error
```

Repository/runtime owns:

```text
live PureTrack targets
auth/pro state
network state
polling lifecycle
bbox policy
backoff policy
stale/expiry policy
provider/source filtering
snapshot state
```

Settings/use-case layer owns:

```text
login/logout
Pro status display
overlay enable/disable
provider mode
category/type/source filters
non-secret profile preferences
```

Map owns:

```text
rendering
selection/details UI
connection indicator
facade/coordinator integration
```

Map does not own:

```text
auth
API client
row parsing
polling
backoff
token storage
```

---

## 10. Bounding-box and polling policy

### 10.1 Bbox rules

The runtime must:

```text
Poll only when overlay is enabled.
Poll only when authenticated and Pro.
Poll only when network is available.
Poll only when map bounds are known.
Reject polling when bbox is too wide.
Debounce rapid map moves.
Cancel or ignore stale poll responses after bbox changes.
Use visible map bbox, not global bounds.
```

Because PureTrack warns that large bboxes can return whole-planet data, the repo
must have a named `PureTrackBboxPolicy` with tests before production bbox calls.

PureTrack vendor numeric limits remain unresolved. XCPro-approved provisional
Phase 2 policy is fail closed when any of these are exceeded:

```text
width > 200 km
height > 200 km
diagonal > 300 km
```

Do not write code or docs that imply PureTrack confirmed those numbers. Do not
widen the provisional threshold unless vendor limits are confirmed safe or XCPro
explicitly revises this policy.

### 10.2 Poll cadence

Do not hardcode a production poll interval until PureTrack confirms rate limits or acceptable cadence.

Phase 2 XCPro provisional policy for future runtime work:

```text
Phase 1/2 tests:
  fake client / no real network polling

Production:
  visible/flying cadence: 30 seconds
  idle cadence: 60 seconds
  never faster than 15 seconds
  backoff sequence: 30/60/120/300 seconds
  honor Retry-After and 429
  no background polling
```

PureTrack vendor rate limits, burst behavior, and official cadence remain
unresolved. Do not write code or docs that imply PureTrack confirmed the
provisional XCPro cadence.

### 10.3 Max age and stale expiry

The API defaults `t` to 5 minutes. Phase 1 should start with:

```text
t=5
```

This is a request-serialization test default, not a locked production polling
or stale-expiry decision.

Runtime should expire targets locally after a defined stale threshold. Suggested policy:

```text
fresh:       age <= 2 minutes
stale:       2 minutes < age <= 5 minutes
expired:     age > 5 minutes + slack
```

Exact values should be aligned with existing traffic UI semantics.

---

## 11. Data model recommendation

Suggested domain model:

```kotlin
data class PureTrackTarget(
    val key: PureTrackKey,
    val timestamp: Instant,
    val position: LatLng,
    val altitudeMetersGps: Double?,
    val altitudeMetersStandard: Double?,
    val pressure: Double?,
    val courseDegrees: Double?,
    val speedMetersPerSecond: Double?,
    val verticalSpeedMetersPerSecond: Double?,
    val objectTypeId: Int?,
    val objectTypeName: String?,
    val objectCategory: PureTrackCategory?,
    val sourceTypeId: Int?,
    val sourceName: String?,
    val trackerUid: String?,
    val targetId: String?,
    val label: String?,
    val name: String?,
    val registration: String?,
    val model: String?,
    val callsign: String?,
    val horizontalAccuracyMeters: Double?,
    val verticalAccuracyMeters: Double?,
    val onGround: Boolean?,
    val rawFieldKeys: Set<Char>
)
```

Parser output should distinguish:

```text
Parsed valid row
Dropped malformed row
Dropped missing required field
Dropped invalid lat/lon
Dropped invalid timestamp
```

Do not leak raw row values in production logs.

---

## 12. Parser requirements

### 12.1 Required field validation

A row is valid only if it has all of:

```text
T timestamp
L latitude
G longitude
K key
```

Drop the row if:

```text
T missing or not integer epoch seconds
L/G missing or not decimal
L outside -90..90
G outside -180..180
K missing or blank
```

### 12.2 Optional field handling

All optional values must be nullable.

Do not require:

```text
altitude
course
speed
vertical speed
object type
source ID
tracker UID
registration
label
name
accuracy
on_ground
```

### 12.3 Key semantics

Rules:

```text
Keys are case-sensitive.
Uppercase S is speed.
Lowercase s is speed_calc, currently not useful.
Lowercase t is standard altitude.
Uppercase T is timestamp.
Digit key 8 is on_ground.
Unknown keys are ignored.
Duplicate keys must have a deterministic policy and test.
```

Recommended duplicate-key policy:

```text
Last token wins.
Count duplicate in parser diagnostics.
```

### 12.4 Unit test rows

Include tests for:

```text
required-only valid row
full example row
missing timestamp
missing latitude
missing longitude
missing key
invalid timestamp
invalid latitude range
invalid longitude range
invalid numeric optional field
unknown token key
case-sensitive S vs s
uppercase T vs lowercase t
source type mapping
object type mapping
on_ground key 8
malformed empty tokens
duplicate token policy
```

---

## 13. Authentication repository requirements

`PureTrackAuthRepository` / `PureTrackSessionRepository` should support:

```text
login(email, password)
logout()
observeSessionState()
refreshProStateIfNeeded()
getBearerTokenOrNull()
```

Session states:

```text
LoggedOut
LoggedInPro
LoggedInNotPro
TokenInvalid
Error
```

Login behavior:

```text
Call /api/login with key/email/password.
If response pro=true, store token and expose LoggedInPro.
If response pro=false, store token only if useful for logout/account state, but traffic remains NotPro.
Never store password.
```

Logout behavior:

```text
Clear token locally.
Do not call a server logout endpoint unless an official PureTrack logout
endpoint is verified from official docs.
```

---

## 14. API client requirements

`PureTrackProviderClient` owns raw HTTP calls.

Responsibilities:

```text
login request
traffic request
JSON decoding
HTTP status mapping
Bearer header injection
Accept: application/json header
redaction-safe errors
```

Client must not own:

```text
polling loop
map bbox lifecycle
token persistence
settings UI state
map rendering
```

Traffic request input:

```kotlin
data class PureTrackTrafficRequest(
    val bounds: PureTrackBounds,
    val category: PureTrackCategory?,
    val objectTypeIds: Set<Int>,
    val maxAgeMinutes: Int,
    val alwaysIncludeKeys: Set<String>,
    val isolateAlwaysIncludeKeys: Boolean
)
```

---

## 15. Helicopter debugging checklist

Use this when validating the original use case: friend is visible on PureTrack, not visible in XCPro.

### 15.1 First confirm the marker identity

Ask for one of:

```text
PureTrack key K
registration E
label B
name N
tracker UID D
screenshot from PureTrack marker details
```

Best is `K`, because the API supports `s=<PureTrack key>`.

### 15.2 Debug API request strategy

Do not start with `cat=air` only.

Use:

```text
small bbox around known area
short t, e.g. t=5
no cat filter initially
s=<known PureTrack key> if available
optionally i=1 to isolate only s key
```

Then inspect parsed fields:

```text
K  key
O  object type
U  source type
D  tracker UID
E  registration
B  label
N  name
age seconds
```

### 15.3 Why `cat=air` may miss the helicopter

If the PureTrack marker has:

```text
O=3   Helicopter, cat=air
```

then `cat=air` should include it.

But if the marker has:

```text
O=20  Person, cat=other
O=0   Unknown, cat=other
O missing
```

then `cat=air` may exclude it.

Fix options:

```text
Correct marker type in PureTrack dashboard if possible.
Use debug s=<key> to force include.
Consider carefully whether production should include selected non-air types.
```

### 15.4 Source ID interpretation

If the friend has no ADS-B and no FLARM, expect source IDs such as:

```text
16  puretrack
18  celltracker
34  Tracker App
51  Garmin Watch
```

Actual `U` depends on how the pilot is publishing to PureTrack.

### 15.5 Other possible causes of invisibility

Check:

```text
PureTrack Pro access for the XCPro user
API key validity
token present and Bearer auth used
bbox covers the helicopter
bbox is not too wide
map zoom gate is not blocking
cat/type filter is not excluding marker
source filter is not excluding marker
age/stale policy is not expiring marker
marker privacy/API availability behavior, confirm with PureTrack if needed
```

Privacy note: PureTrack says non-registered aircraft/person/vehicle markers can have public/private last-known-location settings. Whether and how that affects third-party Traffic API visibility should be confirmed with PureTrack if a known marker is visible in the web/app but missing from API response.

Source: <https://puretrack.io/help/faq>

---

## 16. Phased implementation plan

## Phase 0 — Change plan and ADR

Deliverables:

```text
docs/ARCHITECTURE/ADR_PURETRACK_TRAFFIC_PROVIDER_2026-04-26.md
Minimal updates to this PureTrack planning doc
Minimal updates to CODEX_PURETRACK_IMPLEMENTATION_HANDOFF_2026-04-26.md
```

Decisions to record:

```text
PureTrack is a separate traffic provider.
Phase 1 uses Traffic API only.
Insert API live point publishing is documented as a future XC/Pro-gated capability, with no implementation in Phase 1.
App-key confidentiality is resolved by XCPro/user decision because it is private to the paid user/account.
No production BuildConfig.PURETRACK_APP_KEY wiring under the current XCPro policy because the key is confidential and client-extractable.
Android runtime wiring remains blocked until a secure backend-mediated or equivalent non-client-secret strategy exists.
Phase 1 may define an injected app-key provider seam and fake test provider only.
Production token storage is implemented as an unwired Android Keystore-backed
encrypted adapter behind `PureTrackTokenStore`, excluded from backup, with no
plaintext fallback.
Store token only, never email or password.
Do not export token, password, email, app key, watched keys, selected target,
cached rows, raw API data, names, phone, or identity-bearing traffic data.
Native XCPro traffic remains default; PureTrack is user opt-in; Hybrid remains
experimental until dedupe exists.
No emergency/proximity use until Phase 5.
No hybrid native+PureTrack release until dedupe exists.
Vendor bbox numeric limits remain unresolved. XCPro provisional Phase 2 policy
fails closed when width > 200 km, height > 200 km, or diagonal > 300 km.
Vendor polling/rate limits remain unresolved. XCPro provisional Phase 2 policy
uses 30s visible/flying, 60s idle, never faster than 15s, backoff
30/60/120/300, honors Retry-After, and never runs in background.
```

Exit criteria:

```text
ADR merged.
Phase 1 code scope limited to parser/client/session tests.
Unresolved decisions are explicitly listed, not silently decided.
No production BuildConfig.PURETRACK_APP_KEY wiring under the current XCPro policy.
```

---

## Phase 1 — PureTrack API core

Deliverables:

```text
PureTrackModels.kt
PureTrackRowParser.kt
PureTrackTypeMapper.kt
PureTrackSourceMapper.kt
PureTrackProviderClient.kt
PureTrackSessionRepository.kt
PureTrackTokenStore.kt
unit tests
fake client responses
```

Scope:

```text
Login/logout API support.
Traffic API support.
Parser for compact row strings.
Source/type mapping.
Token/pro state model.
Injected app-key provider seam with fake test provider only.
No map UI.
No emergency/proximity integration.
No Insert API implementation.
No production BuildConfig.PURETRACK_APP_KEY wiring under the current XCPro policy.
No runtime polling.
```

Tests:

```text
login success, pro=true
login success, pro=false
login failure
traffic request includes Bearer token
traffic request includes bbox/key/t/cat params
malformed rows dropped
required-only rows accepted
optional missing fields accepted
source/type mapping works
unknown source/type tolerated
```

Exit criteria:

```text
:feature:traffic:testDebugUnitTest passes
No password storage
No token/profile export
No production raw row logs
```

---

## Phase 2 — Runtime repository

Deliverables:

```text
PureTrackTrafficRepository.kt
PureTrackTrafficRepositoryRuntime.kt
PureTrackBboxPolicy.kt
PureTrackBackoff.kt
PureTrackSnapshot.kt
runtime tests
```

Scope:

```text
single-writer runtime
network wait
auth/pro state
polling lifecycle
bbox clamp/zoom gate
backoff with jitter
stale/expiry policy
provider/source filtering
explicit snapshot states
```

Phase 2 XCPro policy defaults:

```text
PureTrack app key is confidential by XCPro/user decision.
No production BuildConfig.PURETRACK_APP_KEY wiring under the current XCPro policy because the key is confidential and client-extractable.
Android runtime wiring is blocked until a secure backend-mediated or equivalent non-client-secret strategy exists.
Fake/test app-key providers remain allowed for unit tests.
Native traffic remains default.
PureTrack is user opt-in.
Hybrid remains experimental only.
Bbox fail-closed threshold: width <= 200 km, height <= 200 km, diagonal <= 300 km.
Polling: 30s visible/flying, 60s idle, never faster than 15s.
Backoff: 30/60/120/300 seconds.
Honor Retry-After.
Export non-secret display/filter preferences only.
```

Vendor facts still unresolved:

```text
PureTrack bbox numeric limits.
PureTrack polling/rate limits and burst behavior.
```

Runtime states:

```text
Disabled
NeedsLogin
NotPro
WaitingForNetwork
WaitingForMapBounds
BoundsTooWide
Polling
Active
Degraded
Error
```

Tests:

```text
disabled overlay stops polling
no token -> NeedsLogin
pro=false -> NotPro
network unavailable -> WaitingForNetwork
bbox missing -> WaitingForMapBounds
bbox too wide -> BoundsTooWide, no API call
successful poll -> Active snapshot
malformed rows dropped, snapshot still valid
401 -> NeedsLogin
429/5xx -> backoff
stale targets expire
overlay disabled clears runtime state
source filter removes configured source IDs
```

Exit criteria:

```text
runtime tests pass
no map dependency in runtime
no direct safety/proximity output
```

---

## Phase 3 — Settings and profile

Deliverables:

```text
PureTrack settings sheet/screen
login/logout UI/use case
Pro status display
overlay enable setting
provider mode setting
category/type/source filters
profile contributor for non-secret prefs only
DI bindings
```

Scope:

```text
User can login/logout.
User can see Pro/non-Pro state.
User can enable PureTrack overlay.
User can choose provider mode.
User can configure basic filters.
Profile export excludes token/password.
```

Tests:

```text
login stores token only
logout clears token
non-Pro state displayed
profile export includes prefs only
profile export excludes token
settings restore does not restore token
```

Exit criteria:

```text
settings tests pass
profile backup safe
no password persistence
```

---

## Phase 4 — Map integration

Deliverables:

```text
PureTrack traffic facade
MapScreenTrafficCoordinator wiring
render-only overlay
selection/details panel
connection/provider indicator
debug marker details
map SLO evidence
```

Scope:

```text
Display PureTrack targets on map.
Allow target selection/details.
Show provider/source/type/age in debug details.
Do not use PureTrack for emergency/proximity/audio.
```

Map display fields:

```text
registration E, if present
label B, if present
name N, if appropriate
model M, if present
object type O/name
source U/name
age
altitude/speed/course if present
```

Do not display:

```text
phone number p
raw token
Authorization header
raw row in production UI
```

Tests:

```text
facade exposes snapshot to map
map renders PureTrack targets
selection/details displays safe fields
provider indicator reflects state
no emergency/proximity pipeline receives PureTrack
map SLO evidence captured
```

Exit criteria:

```text
:feature:map:testDebugUnitTest passes
map SLO evidence included
PureTrack is render-only
```

---

## Phase 5 — Dedupe and safety model

Deliverables:

```text
provider-neutral traffic identity model
provider-neutral dedupe owner
source trust model
stale/latency safety policy
optional proximity/emergency integration proposal
```

Dedupe candidates:

```text
registration E
tracker UID D
ADS-B ICAO
FLARM ID
PureTrack key K
callsign m
position/time similarity
altitude/speed/course similarity
source priority
```

Safety gates before any alert use:

```text
minimum update age
maximum stale age
minimum accuracy when available
object category must be air
source trust policy
on-ground handling
duplicate conflict policy
user-visible disclaimer
alert tests with mixed providers
```

Exit criteria:

```text
dedupe model reviewed
safety policy reviewed
alerts remain disabled until explicitly approved
```

---

## 17. Verification commands

Minimum targeted loop:

```bash
./gradlew :feature:traffic:testDebugUnitTest
./gradlew :feature:map:testDebugUnitTest
./gradlew enforceRules
```

Merge-ready loop:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Because map overlay/runtime changes are involved, include impacted MapScreen SLO evidence per repo rules.

---

## 18. Acceptance criteria summary

### Phase 1 acceptance

```text
PureTrack API client exists.
Login/logout supported.
Token/pro state represented.
Password never stored.
Traffic rows parsed safely.
Malformed rows dropped.
Source/type mapping present.
No map UI yet.
No safety/proximity integration.
```

### Phase 2 acceptance

```text
Runtime repository owns polling.
Network/auth/pro/bbox/backoff/stale states are explicit.
Large bbox does not poll.
Source filtering is client-side and tested.
No map dependency.
```

### Phase 3 acceptance

```text
User can login/logout.
User can see Pro status.
User can enable overlay.
Profile export excludes secrets.
```

### Phase 4 acceptance

```text
PureTrack targets render on map.
Selection/details work.
Provider/source/type/age visible in debug.
No emergency/proximity/audio integration.
Map SLO evidence included.
```

### Phase 5 acceptance

```text
Provider-neutral dedupe exists.
Safety model exists.
Only then consider PureTrack proximity/emergency alerts.
```

---

## 19. Critical open questions

Resolve before production release:

```text
1. What poll cadence and rate limits does PureTrack allow?
   XCPro provisional Phase 2 policy is 30s visible/flying, 60s idle, never
   faster than 15s, backoff 30/60/120/300, and honor Retry-After. This is not
   PureTrack-confirmed.

2. Does PureTrack offer undocumented server-side source filtering?
   Public docs do not show it.

3. How do private/non-registered markers behave in third-party Traffic API responses?
   Confirm with PureTrack if a known marker appears on web/app but not API.

4. What UI/settings surface should expose the opt-in PureTrack provider mode?
   Native XCPro traffic remains default; Hybrid is experimental until dedupe
   exists.

5. What exact bbox size should XCPro reject?
   XCPro provisional Phase 2 policy fails closed above width 200 km, height
   200 km, or diagonal 300 km. PureTrack documents the large-bbox behavior but
   does not confirm numeric limits.

6. What target fields may be shown in production UI without privacy risk?
   Especially name, label, and phone fields.

7. What additional provider prerequisites must be verified before implementing
   PureTrack Insert API live point publishing?
   Documentation currently limits this to live tracking points only, with no
   route or turnpoint publishing.
```

---

## 20. Recommended Codex review prompt

Use this prompt when asking Codex to review the plan:

```text
Review docs/PURETRACK.md against the current XCPro repo.

Please verify:
1. Whether the named existing files/patterns are current:
   - AdsbTrafficRepository.kt
   - AdsbTrafficRepositoryRuntime.kt
   - OgnTrafficRepository.kt
   - TrafficMapApi.kt
   - MapScreenTrafficCoordinator.kt

2. Whether feature:traffic is the correct module/package for a provider-owned PureTrack lane.

3. Whether the proposed PureTrack files fit current module boundaries and naming conventions.

4. Whether token storage should mirror existing ADS-B/OpenSky token handling or use a different repository.

5. Whether the secure backend-mediated or equivalent non-client-secret app-key strategy is defined before Android runtime wiring.

6. Whether profile backup/export currently has a mechanism to exclude secret provider tokens.

7. Whether map integration can be done through the existing traffic facade only, without MapScreen depending on PureTrack client/auth/parser classes.

8. Whether any existing emergency/proximity pipeline would accidentally consume PureTrack targets once they enter the traffic facade.

9. Whether provider mode should be Native, PureTrack, or Hybrid, and where that setting should live.

10. What exact tests should be added per phase, and which existing test fixtures/helpers should be reused.

Return:
- corrections to this ADR;
- a minimal Phase 0 PR plan;
- a minimal Phase 1 PR plan;
- risks/blockers;
- exact files to create/change;
- commands to run.
```

---

## 21. Bottom line

The implementation should proceed, but only as a clean provider-owned lane.

Correct first move:

```text
Phase 0 ADR + PureTrack API/parser/auth plan
```

Correct first code:

```text
PureTrack parser/client/session tests in feature:traffic
```

Incorrect first code:

```text
Map UI first
PureTrack inside ADS-B repo
PureTrack inside OGN repo
PureTrack proximity/emergency alerts
Stored PureTrack password
Unreviewed Android-embedded confidential key
Blind concurrent OGN + ADS-B + PureTrack streams
```

For the original helicopter case, XCPro should be able to display the friend once:

```text
PureTrack API access is authenticated and Pro.
The marker is within bbox.
The marker is not excluded by cat/type/source filters.
The runtime stale policy does not expire it.
The map consumes PureTrack through the facade.
```

If `cat=air` misses the marker, inspect `O` and `U`. The marker may not be typed as `Helicopter` even if it is being used by a helicopter pilot.
