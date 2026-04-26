# PureTrack XCPro Codex Implementation Handoff

Status: Codex-ready planning brief  
Date: 2026-04-26  
Repo: `C:\Users\Asus\AndroidStudioProjects\XCPro`  
Related local doc: `docs/PURETRACK/PURETRACK_XCPRO_ADR.md`  
Phase 0 decision record:
- `docs/ARCHITECTURE/ADR_PURETRACK_TRAFFIC_PROVIDER_2026-04-26.md`
External API docs:
- https://puretrack.io/help/api
- https://puretrack.io/help/api-insert
- https://puretrack.io/types.json

## 1. Short Verdict

PureTrack should be implemented as a separate provider-owned traffic lane in
`feature:traffic`.

Do not implement PureTrack as:

- an OGN parser replacement
- an ADS-B source inside `AdsbTrafficRepository`
- a direct data source from map/UI code
- a proximity, collision, or emergency-audio input in Phase 1

Recommended architecture:

```text
PureTrackProviderClient
  -> PureTrackSessionRepository / PureTrackTokenStore
  -> PureTrackTrafficRepository / runtime
  -> PureTrack map-facing facade
  -> MapScreenTrafficCoordinator
  -> render-only map overlay
```

Phase 1 should be parser/client/session tests only. It may define an injected
app-key provider seam and fake test provider, but must not add production
BuildConfig PureTrack key binding. Runtime polling and map integration should
come later after secure app-key strategy and provider-mode ownership are decided.

Phase 0 locked boundary:

```text
PureTrack is a separate feature:traffic provider lane.
The PureTrack application key is confidential because it is private to the paid user/account.
Do not embed the key in the Android APK, BuildConfig, resources, logs, profile backup, or client-extractable storage.
No production BuildConfig.PURETRACK_APP_KEY wiring under the current XCPro policy because the key is confidential and client-extractable.
Android runtime wiring remains blocked until a secure backend-mediated or equivalent non-client-secret strategy exists.
Fake/test app-key providers remain allowed for unit tests.
Native XCPro traffic remains default; PureTrack is user opt-in.
Hybrid remains experimental until provider-neutral dedupe exists.
PureTrack vendor bbox numeric limits remain unresolved.
XCPro provisional bbox threshold is width <= 200 km, height <= 200 km, diagonal <= 300 km.
PureTrack vendor polling cadence/rate limits remain unresolved.
XCPro provisional polling is 30s visible/flying, 60s idle, never faster than 15s, backoff 30/60/120/300, honor Retry-After.
No map runtime, OGN runtime, ADS-B emergency, or proximity changes in Phase 0/1.
```

## 2. Confirmed PureTrack API Facts

PureTrack Traffic API requires:

- application API key
- user email/password login
- PureTrack Pro subscription for third-party app access
- bearer token on traffic requests
- bounding-box based traffic requests

Login:

```text
POST https://puretrack.io/api/login
```

Required parameters:

```text
key
email
password
```

Response shape:

```json
{
  "access_token": "<token>",
  "pro": true
}
```

Traffic:

```text
POST https://puretrack.io/api/traffic
```

PureTrack says POST or GET parameters work. Choose one style consistently.

Required parameters:

```text
key
lat1   top-right latitude
long1  top-right longitude
lat2   bottom-left latitude
long2  bottom-left longitude
```

Optional parameters:

```text
cat  air | ground | other | water
o    comma-separated object type IDs
t    max point age in minutes, default 5, up to 24 hours
s    PureTrack map item keys to always include
i    isolate to s keys, 1 or 0
```

Critical bounding-box warning:

```text
When the bounding box is too large, PureTrack lowers t to 5 minutes and returns
data for the whole planet.
```

XCPro must reject or pause overly wide PureTrack bbox polling. Do not rely on
the provider silently doing something safe.

Traffic row format:

Rows are compact comma-separated token strings. Token key is the first
character, and the value is the rest of the token. Keys are case-sensitive.

Guaranteed fields:

```text
T  Unix epoch timestamp seconds
L  latitude
G  longitude
K  PureTrack key
```

Important optional fields:

```text
A  GPS altitude, meters
C  course, degrees 0..360
S  speed, m/s
V  vertical speed, m/s
O  object type ID
D  tracker_uid, e.g. FLARM ID or ADS-B ICAO hex
U  source_type_id
J  PureTrack target ID
B  label
N  name
E  registration
M  model
h  horizontal accuracy
z  vertical accuracy
m  callsign
t  standard pressure altitude, meters
8  on_ground
```

Source ID examples:

```text
0   flarm
12  adsb
16  puretrack
18  celltracker
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
50  Naviter Omni
51  Garmin Watch
```

Object type examples from `types.json`:

```text
0   other  Unknown
1   air    Glider
2   air    Tow
3   air    Helicopter
7   air    Paraglider
8   air    Plane
13  air    Drone
16  air    Gyrocopter
20  other  Person
```

Future request filtering may use `cat=air` as a recommended production filter
after runtime/settings ownership is approved. Phase 1 tests may cover request
serialization with `cat=air`, but Phase 0 does not lock production filtering.
Debugging a known marker should allow a small bbox with no `cat` filter or
`s=<PureTrack key>`.

PureTrack Insert API:

- sends live tracking points into PureTrack
- requires separate Insert API configuration
- is not route or turnpoint publishing
- is documented as a future XC/Pro-gated XCPro capability
- has no approved implementation in Phase 1

## 3. Verified XCPro Repo Anchors

Existing ADS-B shape:

- `feature/traffic/src/main/java/com/trust3/xcpro/adsb/AdsbTrafficRepository.kt`
- `feature/traffic/src/main/java/com/trust3/xcpro/adsb/AdsbTrafficRepositoryRuntime.kt`
- `feature/traffic/src/main/java/com/trust3/xcpro/adsb/AdsbProviderClient.kt`
- `feature/traffic/src/main/java/com/trust3/xcpro/adsb/OpenSkyProviderClient.kt`
- `feature/traffic/src/main/java/com/trust3/xcpro/adsb/OpenSkyTokenRepository.kt`

Existing OGN shape:

- `feature/traffic/src/main/java/com/trust3/xcpro/ogn/OgnTrafficRepository.kt`
- `feature/traffic/src/main/java/com/trust3/xcpro/ogn/OgnTrafficRepositoryRuntime.kt`
- `feature/traffic/src/main/java/com/trust3/xcpro/ogn/OgnAprsLineParser.kt`
- `feature/traffic/src/main/java/com/trust3/xcpro/ogn/OgnDdbRepository.kt`

Existing map traffic boundary:

- `feature/traffic/src/main/java/com/trust3/xcpro/map/TrafficMapApi.kt`
- `feature/traffic/src/main/java/com/trust3/xcpro/map/MapTrafficUseCases.kt`
- `feature/traffic/src/main/java/com/trust3/xcpro/map/MapScreenTrafficCoordinator.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapTrafficOverlayRuntimeBindings.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapTrafficOverlayRuntimeCollectors.kt`

Existing secret/config pattern:

- `app/build.gradle.kts`
- `app/src/main/java/com/trust3/xcpro/di/AppModule.kt`
- `build-logic/src/main/kotlin/com/example/xcpro/buildlogic/SecretPropertiesPlugin.kt`

Existing profile settings contributor pattern:

- `core/common/src/main/java/com/trust3/xcpro/core/common/profiles/ProfileSettingsSectionContract.kt`
- `feature/traffic/src/main/java/com/trust3/xcpro/adsb/AdsbTrafficProfileSettingsContributor.kt`
- `feature/traffic/src/main/java/com/trust3/xcpro/ogn/OgnTrafficProfileSettingsContributor.kt`

## 4. Architecture Rules That Matter

Repo rules require:

- preserve MVVM, UDF, SSOT
- keep business/runtime logic out of UI
- keep ViewModels dependent on use cases or narrow domain-facing seams
- use injected clocks/time sources
- do not introduce hidden global mutable state
- keep replay deterministic
- update `PIPELINE.md` when runtime wiring changes
- write a change plan or ADR for non-trivial feature/runtime work

PureTrack touches network I/O, auth state, persisted token state, settings,
runtime polling, traffic map rendering, and likely provider-mode policy.
Therefore it is non-trivial and needs phased planning before broad code.

## 5. Ownership Decision

Authoritative owners:

```text
PureTrack API HTTP calls:
  PureTrackProviderClient

PureTrack token:
  PureTrackTokenStore or PureTrackSessionRepository

PureTrack login/logout/pro state:
  PureTrackSessionRepository

PureTrack live target state:
  PureTrackTrafficRepository / runtime

PureTrack polling/backoff/bbox/stale policy:
  PureTrackTrafficRepositoryRuntime and focused policy helpers

PureTrack non-secret settings:
  PureTrackTrafficPreferencesRepository

Cross-provider mode:
  New traffic-owned repository or settings owner, not map UI

Map rendering:
  Map overlay layer only

Selection/details UI:
  Map traffic UI/facade layer
```

Forbidden duplicate owners:

- MapScreen must not own API/auth/parser/polling.
- ViewModel must not store or mutate PureTrack tokens directly.
- ADS-B repository must not own PureTrack data.
- OGN repository must not own PureTrack data.
- Profile export must not own or copy PureTrack secrets.

## 6. Unresolved Blockers

Resolve before production runtime or map integration:

1. Define the secure production app-key strategy.
   - App-key confidentiality is resolved by XCPro/user decision because it is
     private to the paid user/account.
   - Android runtime wiring remains blocked until a secure backend-mediated or
     equivalent non-client-secret strategy exists.
   - Do not embed it in Android APK, BuildConfig, resources, logs, profile
     backup, or client-extractable storage.

2. What production poll cadence/rate limits does PureTrack allow?
   - XCPro provisional Phase 2 policy is `30s` visible/flying, `60s` idle,
     never faster than `15s`, backoff `30/60/120/300`, honor `Retry-After`,
     and no background polling.
   - Do not imply PureTrack confirmed this cadence.

3. What exact bbox size should XCPro reject?
   - PureTrack warns about large bboxes but does not publish a threshold.
   - XCPro provisional Phase 2 policy fails closed when width is greater than
     `200 km`, height is greater than `200 km`, or diagonal is greater than
     `300 km`.
   - Do not imply PureTrack confirmed these limits.

4. Where should provider-mode UI/settings live?
   - Native XCPro traffic remains default.
   - PureTrack mode is user opt-in.
   - Hybrid mode: experimental only until provider-neutral dedupe exists.

5. What PureTrack fields may be shown in production UI?
   - Phone number must not be shown.
   - Name/label display should be explicit and privacy-reviewed.

6. What additional provider prerequisites must be verified before implementing
   PureTrack Insert API live point publishing?
   - Documentation currently limits this to live tracking points only.
   - No Insert API code is approved in Phase 1.

## 7. App Key and Token Policy

Rules:

- Store token only.
- Never store password.
- Never log password, token, Authorization header, or raw traffic rows.
- `logout()` and `markTokenInvalid()` are local-authoritative in-process.
  If secure storage clear fails, they expose `PERSISTENCE_UNAVAILABLE`,
  suppress bearer access for the current process, and do not claim durable
  deletion until storage recovery or a retry succeeds.
- 401 or invalid-token response moves session/runtime to `NeedsLogin`.
- Profile backup/export must never include token, password, email, app key,
  watched keys, selected target, cached rows, raw API data, names, phone, or
  identity-bearing traffic data.
- `AndroidPureTrackTokenStore` now exists as an unwired production adapter
  behind `PureTrackTokenStore`. Production DI/runtime wiring remains deferred.
  The adapter uses Android Keystore-backed encrypted app-private storage, with
  no plaintext fallback and backup exclusion. Do not store email or password.

BuildConfig decision:

```text
Phase 1/1.5:
  app-key confidentiality is resolved by XCPro/user decision.
  injected app-key provider seam and fake test provider only.
  no production BuildConfig.PURETRACK_APP_KEY wiring under the current XCPro policy because the key is confidential and client-extractable.

Later:
  define a secure backend-mediated or equivalent non-client-secret strategy before Android runtime wiring.
```

## 8. Timebase Policy

Use both provider source time and injected monotonic receipt time.

Recommended target model fields:

```kotlin
val sourceTimestampEpochSec: Long
val receivedMonoMs: Long
```

Freshness, stale, expiry, backoff, and retry timing:

```text
Use injected monotonic clock, e.g. Clock.nowMonoMs().
```

Display age:

```text
May derive from source timestamp and response/receipt context.
Do not compare wall time to monotonic time.
```

Avoid `Instant` as the primary runtime contract in Phase 1. If a wall/source
timestamp is needed for display or persistence, keep it explicitly labeled as
epoch seconds or wall millis.

Replay:

```text
PureTrack is live-only in Phase 1-4.
Replay must not poll PureTrack or depend on network/device state.
```

## 9. Parser Contract

Valid row requires:

```text
T
L
G
K
```

Drop row if:

- missing required field
- invalid timestamp
- invalid latitude/longitude
- blank key
- malformed required token value

Optional fields are nullable. Invalid optional values should not drop an
otherwise valid row unless the value is required for the selected behavior.

Token rules:

- first character is key
- rest of token is value
- keys are case-sensitive
- unknown keys ignored
- duplicate-key policy: last token wins
- duplicate count should be available in parser diagnostics

Parser result should represent:

```text
Parsed valid row
Dropped missing required field
Dropped invalid coordinate
Dropped invalid timestamp
Dropped malformed row
```

Do not log raw rows in production.

## 10. Runtime Snapshot States

Suggested states:

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

Runtime owns:

- current target list
- connection/session projection
- network availability projection
- bbox policy result
- last poll/last success mono times
- last HTTP status
- last error category, redacted
- dropped malformed row count
- filtered source/type count
- stale/expired target counts

## 11. Provider Mode and Dedupe

PureTrack may duplicate targets already available through native OGN or ADS-B.
Do not release blind concurrent native OGN + native ADS-B + PureTrack without a
dedupe story.

Phase 1.5 locks provider-mode defaults for later runtime/map work. Wiring and
UI remain deferred.

Provider mode policy:

```text
Native:
  Existing OGN/ADS-B behavior. Production default.

PureTrack:
  User opt-in. Native providers disabled or visually separated.

Hybrid:
  Experimental only until provider-neutral dedupe exists.
```

Potential client-side source filtering:

```text
Exclude when native providers are enabled:
  U=0, U=12, U=27, U=28, U=29, U=35, U=45, U=46

Keep likely non-native/mobile/satellite/app sources:
  U=1, U=9, U=16, U=18, U=23, U=24, U=34, U=36,
  U=42, U=43, U=47, U=50, U=51
```

Do not ship source filtering as hidden magic. It must be surfaced in debug or
settings state and covered by tests.

## 12. Safety Scope

Phase 1-4 are display/render-only.

PureTrack must not feed:

- ADS-B emergency audio
- ADS-B collision/proximity logic
- OGN proximity logic
- flight-critical alerts
- navigation/scoring logic

Reasons:

- PureTrack is a broad safety-tracking aggregator, not a collision-grade feed.
- Source latency varies.
- Mobile/satellite/cellular sources can be stale.
- Some targets may be persons, cars, boats, or incorrectly typed objects.
- Accuracy fields are optional.
- Duplicates can conflict with native OGN/ADS-B targets.

Only Phase 5 may propose proximity or emergency integration, after dedupe and
source-trust policy exist.

## 13. Minimal Phase 0 Plan

Goal:

```text
Lock architecture and security decisions before implementation.
```

Create or update:

- `docs/PURETRACK/PURETRACK_XCPRO_ADR.md`
- `docs/ARCHITECTURE/ADR_PURETRACK_TRAFFIC_PROVIDER_2026-04-26.md`
- `docs/PURETRACK/CODEX_PURETRACK_IMPLEMENTATION_HANDOFF_2026-04-26.md`
- no `PIPELINE.md` update until runtime wiring is added

Decisions to record:

- PureTrack is a separate `feature:traffic` provider lane.
- Traffic API only in Phase 1 code.
- Insert API live point publishing is documented as a future XC/Pro-gated capability, with no implementation in Phase 1.
- App-key confidentiality is resolved by XCPro/user decision because it is private to the paid user/account.
- Do not embed the key in the Android APK, BuildConfig, resources, logs, profile backup, or client-extractable storage.
- Phase 1 may use an injected app-key provider seam and fake test provider only.
- No production BuildConfig.PURETRACK_APP_KEY wiring under the current XCPro policy because the key is confidential and client-extractable.
- Android runtime wiring remains blocked until a secure backend-mediated or equivalent non-client-secret strategy exists.
- Production token storage is owned by `PureTrackTokenStore`. The Android
  production adapter may exist behind that seam only; it remains unwired in DI
  and runtime until a later phase. It must be Android Keystore-backed encrypted
  app-private storage, excluded from backup, with no plaintext fallback and no
  email/password storage.
- Profile export exclusions.
- Native XCPro traffic remains default; PureTrack is user opt-in; Hybrid remains
  experimental until dedupe exists.
- No safety/proximity scope before Phase 5.
- PureTrack vendor bbox numeric limits remain unresolved; fail closed above the
  XCPro provisional threshold: width > 200 km, height > 200 km, or diagonal
  > 300 km.
- Production polling cadence/rate limits remain unresolved with PureTrack;
  XCPro provisional Phase 2 policy is 30s visible/flying, 60s idle, never
  faster than 15s, backoff 30/60/120/300, honor Retry-After, and no background
  polling.

Exit criteria:

- ADR accepted.
- Unresolved decisions are explicitly listed, not silently decided.
- Phase 1 code scope limited to parser/client/session tests.
- No production code has been implemented.

## 14. Minimal Phase 1 Code Plan

Goal:

```text
PureTrack API core without runtime map integration.
```

Create under `feature/traffic/src/main/java/com/trust3/xcpro/puretrack/`:

- `PureTrackModels.kt`
- `PureTrackRowParser.kt`
- `PureTrackTypeMapper.kt`
- `PureTrackSourceMapper.kt`
- `PureTrackApiConfig.kt`
- `PureTrackProviderClient.kt`
- `PureTrackSessionRepository.kt`
- `PureTrackTokenStore.kt`

Tests under `feature/traffic/src/test/java/com/trust3/xcpro/puretrack/`:

- `PureTrackRowParserTest.kt`
- `PureTrackTypeMapperTest.kt`
- `PureTrackSourceMapperTest.kt`
- `PureTrackProviderClientTest.kt`
- `PureTrackSessionRepositoryTest.kt`

Phase 1 must not:

- add production BuildConfig PureTrack key binding
- add production app DI key binding
- add runtime polling
- touch map overlay
- touch `AdsbTrafficRepository`
- touch `OgnTrafficRepository`
- add proximity or emergency alerts
- store password
- export token in profiles

Phase 1 parser tests:

- required-only valid row
- full example row
- missing timestamp
- missing latitude
- missing longitude
- missing key
- invalid timestamp
- latitude outside range
- longitude outside range
- invalid optional numeric field
- unknown token key ignored
- case-sensitive `S` vs `s`
- uppercase `T` vs lowercase `t`
- source type mapping
- object type mapping
- on-ground key `8`
- malformed empty tokens
- duplicate-key last-wins policy

Phase 1 auth/client tests:

- login success, pro true
- login success, pro false
- login rejected
- transient login failure
- token stored, password not stored
- logout/markTokenInvalid clear local token, or expose persistence unavailable
  without claiming durable deletion when secure storage clear fails
- stale login completing after logout does not save token
- stale login completing after markTokenInvalid does not save token
- older concurrent login cannot overwrite a newer login result
- traffic request includes bearer token
- traffic request includes app key and bbox params
- malformed traffic JSON mapped to typed error
- HTTP 401 maps to token invalid / needs login

## 15. Phase 2 Runtime Plan

Create:

- `PureTrackTrafficRepository.kt`
- `PureTrackTrafficRepositoryRuntime.kt`
- `PureTrackTrafficRepositoryRuntimePolicy.kt`
- `PureTrackBboxPolicy.kt`
- `PureTrackBackoffPolicy.kt`
- `PureTrackSnapshot.kt`
- `PureTrackNetworkAvailabilityPort.kt` if not reusing a generic traffic network port

XCPro-approved Phase 2 policy defaults:

- app-key confidentiality is resolved by XCPro/user decision because it is private to the paid user/account
- no production BuildConfig.PURETRACK_APP_KEY wiring under the current XCPro policy because the key is confidential and client-extractable
- Android runtime wiring remains blocked until a secure backend-mediated or equivalent non-client-secret strategy exists
- fake/test app-key providers remain allowed for unit tests
- Keystore-backed encrypted token storage adapter behind `PureTrackTokenStore`, no plaintext fallback, excluded from backup, no email/password, no DI/runtime wiring yet
- Native default, PureTrack opt-in, Hybrid experimental only
- provisional bbox fail-closed threshold: width <= 200 km, height <= 200 km, diagonal <= 300 km
- provisional polling: 30s visible/flying, 60s idle, never faster than 15s, backoff 30/60/120/300, honor Retry-After
- profile backup exports non-secret display/filter preferences only

Vendor facts still unresolved:

- bbox numeric limits
- polling/rate limits and burst behavior

Runtime tests:

- disabled overlay stops polling
- missing token -> NeedsLogin
- pro false -> NotPro
- network unavailable -> WaitingForNetwork
- missing map bounds -> WaitingForMapBounds
- too-wide bbox -> BoundsTooWide and no API call
- successful poll -> Active
- malformed rows dropped without crashing snapshot
- 401 -> NeedsLogin
- 429/5xx -> backoff
- stale targets expire
- source filter removes configured source IDs
- overlay disabled clears runtime state

## 16. Phase 3 Settings and Profile Plan

Create or update:

- PureTrack settings UI/use case/view model
- PureTrack preferences repository for non-secret settings
- profile settings contributor for non-secret settings only
- DI bindings
- settings route/subsheet entries

Profile export may include:

- overlay enabled
- provider mode
- category/type/source filters
- display/filter preferences that are not tied to a selected identity

Profile export must not include:

- token
- password
- email
- Authorization header
- app key
- watched keys
- selected target
- cached rows
- raw traffic rows
- raw API data
- names
- phone field
- identity-bearing traffic data

Tests:

- login stores token only
- logout/markTokenInvalid clear token, or expose persistence unavailable without
  claiming durable deletion when secure storage clear fails
- non-Pro state displayed
- profile export includes non-secret prefs only
- profile restore does not restore token

## 17. Phase 4 Map Integration Plan

Create or update:

- `TrafficMapApi.kt` for PureTrack facade, or a provider-neutral traffic facade if planned
- `MapTrafficUseCases.kt`
- `MapScreenTrafficCoordinator.kt`
- map overlay runtime binding/collector files
- map overlay handle/factory files
- selection/details UI
- provider connection indicator

Display only:

- target marker
- selection/details panel
- source/type/age/debug fields

Do not:

- feed PureTrack targets into ADS-B emergency audio
- feed PureTrack targets into collision/proximity logic
- alter OGN thermal/trail/SCIA behavior

Because map overlay/runtime changes are involved, run MapScreen SLO evidence.

## 18. Phase 5 Dedupe and Safety Plan

Do not start Phase 5 until Phase 1-4 are stable.

Needed owners:

- provider-neutral identity model
- dedupe owner
- source trust policy
- stale/latency safety policy
- conflict resolution policy

Dedupe candidates:

- registration `E`
- tracker UID `D`
- ADS-B ICAO
- FLARM ID
- PureTrack key `K`
- callsign `m`
- position/time similarity
- altitude/speed/course similarity
- source priority

Safety gates before any alert use:

- object category must be air
- source age below threshold
- target update frequency above threshold
- optional accuracy fields meet threshold when present
- on-ground handling defined
- duplicate conflict policy defined
- user-visible disclaimer defined
- tests cover mixed-provider conflicts

## 19. Verification Commands

Targeted Phase 1:

```bash
./gradlew :feature:traffic:testDebugUnitTest
./gradlew enforceRules
```

Runtime/map phases:

```bash
./gradlew :feature:traffic:testDebugUnitTest
./gradlew :feature:map:testDebugUnitTest
./gradlew enforceRules
```

Merge-ready:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Map overlay/runtime changes:

```text
Include impacted MapScreen SLO IDs and evidence per docs/MAPSCREEN rules.
```

## 20. Suggested Codex Prompt

Use this prompt with Codex when starting implementation:

```text
Read:
- AGENTS.md
- docs/ARCHITECTURE/PLAN_MODE_START_HERE.md
- docs/ARCHITECTURE/ARCHITECTURE.md
- docs/ARCHITECTURE/CODING_RULES.md
- docs/ARCHITECTURE/PIPELINE_INDEX.md
- docs/ARCHITECTURE/PIPELINE.md
- docs/ARCHITECTURE/CODEBASE_CONTEXT_AND_INTENT.md
- docs/ARCHITECTURE/CONTRIBUTING.md
- docs/ARCHITECTURE/KNOWN_DEVIATIONS.md
- docs/PURETRACK/PURETRACK_XCPRO_ADR.md
- docs/PURETRACK/CODEX_PURETRACK_IMPLEMENTATION_HANDOFF_2026-04-26.md

Do not code yet.

Produce the smallest safe Phase 0 and Phase 1 plan for PureTrack:
- verified facts
- explicit decisions
- unresolved blockers
- SSOT owners
- file ownership plan
- timebase/determinism contract
- tests
- docs to update
- exact verification commands

Keep Phase 1 limited to PureTrack API/auth/parser/session code in feature:traffic.
Do not touch map runtime, ADS-B emergency logic, OGN runtime, or proximity alerts.
```

## 21. Bottom Line

Correct first move:

```text
Phase 0 ADR and app-key/provider-mode decisions.
```

Correct first code:

```text
PureTrack parser/client/session tests in feature:traffic.
```

Incorrect first code:

```text
Map UI first
PureTrack inside ADS-B repository
PureTrack inside OGN repository
PureTrack proximity/emergency alerts
Stored PureTrack password
Blind concurrent native OGN + native ADS-B + PureTrack
Embedded confidential PureTrack app key
```
