# XCPro phased IP: Condor TCP listen mode with XCSoar-equivalent semantics

## Objective

Implement an **architecture-correct Condor simulator mode** in XCPro so the app can be connected to Condor on a PC the same way XCSoar is connected today:

```text
Condor -> COM -> external bridge -> TCP -> Android phone -> XCPro
```

The implementation target is **XCSoar-equivalent intake**, not merely “some socket support”.

## Scope

### In scope
- Android-side TCP listen mode for Condor
- configurable listen port, default `4353`
- simulator-owned incoming stream lifecycle
- line-based NMEA ingestion
- Condor-aware parsing / normalization
- explicit runtime source selection for Condor mode
- suppression of phone-sensor ownship authority while Condor mode is active
- connection status UX
- tests and golden-capture validation against the known-working XCSoar path

### Out of scope
- bundling or controlling HW VSP3 from XCPro
- Windows-side COM bridge management
- replacing all future simulator transports in one PR
- broad transport abstraction for every external device under the sun

Build the narrow, correct Condor TCP path first.

---

## Architecture rules

## 1) Ownership

### `feature:profile`
Owns:
- persisted user setting enabling Condor TCP mode
- listen port configuration
- any user-facing “show phone IP / status” settings surface

### `feature:simulator` (recommended new module if not already present)
Owns:
- Condor TCP listener transport
- connection/session state
- line buffering / framing
- checksum validation
- Condor-specific sentence parsing
- Condor-specific normalization
- stale timeout / reconnect behavior
- simulator health reporting

### `feature:flight-runtime`
Owns:
- consuming normalized simulator ownship data through a narrow input port
- source arbitration through an explicit resolver / mode policy
- providing fused flight truth to downstream consumers

### `app`
Owns:
- DI wiring
- lifecycle actuation
- start / stop orchestration only

### `feature:map`
Owns:
- nothing simulator-specific
- only consumes fused runtime truth

## 2) Source authority

When Condor TCP mode is active and healthy:
- Condor ownship data is the active ownship authority
- phone GPS must not silently override it
- phone compass / heading must not silently override Condor heading policy
- fallback, if any, must be explicit, not hidden

## 3) Boundary lock

Do not allow:
- map feature opening sockets
- ViewModels parsing NMEA directly
- raw Condor transport objects leaking into UI
- simulator transport bypassing `flight-runtime`

---

## Recommended types / seams

These names are recommendations, not hard requirements, but keep the ownership model.

### Settings / policy
- `DesiredLiveMode`
- `SimulatorConnectionConfig`
- `CondorTcpConfig`
- `LiveSourceResolver`

### Simulator transport / parsing
- `SimulatorTransport`
- `CondorTcpListenTransport`
- `SimulatorConnectionState`
- `CondorSentenceParser`
- `CondorNormalizer`
- `SimulatorOwnshipFrame`
- `SimulatorHealthSnapshot`

### Runtime integration
- `SimulatorOwnshipRepository`
- `SimulatorOwnshipReadPort`
- `FlightOwnshipIngestPort`
- `ActiveLiveSource`

---

## Phases

## Phase 0 — Contract lock and docs
Create / update docs first.

### Required outputs
- ADR or architecture note stating that Condor intake is simulator-owned
- pipeline update showing external bridge -> simulator module -> flight-runtime -> map
- explicit no-fallback rule for ownship authority in Condor mode
- exact semantics list for the Condor TCP path

### Acceptance
- no code yet or only scaffolding
- ownership boundaries are written down before implementation churn starts

---

## Phase 1 — Persisted mode and configuration
Add the user-facing and persisted configuration required to activate the mode.

### Required behavior
- user can enable `Condor TCP`
- user can set listen port
- default port is `4353`
- app can expose current local IP for same-LAN setup
- configuration is persisted

### Acceptance
- settings persist across restart
- no transport created in map/UI layer
- no hidden activation path

---

## Phase 2 — Live source resolution
Introduce or extend the runtime policy that decides which live source is authoritative.

### Required behavior
- `Condor TCP` is an explicit source mode
- runtime selects Condor ownship when mode is enabled and stream is healthy
- runtime does not merge phone ownship with Condor ownship by accident
- disconnect / stale transitions are explicit

### Acceptance
- resolver tests prove single authority
- no "last writer wins" behavior
- no silent fallback to phone ownship while Condor is expected to be authoritative

---

## Phase 3 — Simulator-owned transport and parser
Implement the actual incoming transport and sentence handling.

### Required behavior
- background thread or coroutine-based server socket
- binds on configured port
- accepts incoming TCP client
- reads NMEA lines continuously
- validates checksums where applicable
- parses the incoming Condor feed
- normalizes Condor semantics before handing data upstream

### Must support
- full line-stream intake
- `LXWP0` Condor handling
- Condor 2 wind reciprocal correction
- TAS semantics from `LXWP0`
- Condor-specific altitude handling

### Acceptance
- parser tests for valid / invalid `LXWP0`
- transport tests for connect / disconnect / stale timeout
- no UI-thread socket operations

---

## Phase 4 — Flight-runtime ingestion
Feed the normalized simulator data into the authoritative runtime seam.

### Required behavior
- simulator ownship frames enter through one narrow port
- downstream runtime consumers see simulator-fed truth
- existing downstream consumers do not need to know whether ownship came from phone sensors or Condor

### Acceptance
- map does not know about TCP or NMEA
- no simulator-specific branches scattered through UI features
- flight truth remains SSOT

---

## Phase 5 — UX and observability
Expose enough state to make setup debuggable.

### XCPro should show
- mode enabled / disabled
- current phone IP
- listen port
- listening state
- connected state
- last valid sentence time
- stale / timeout state
- error message when bind or socket fails

### Acceptance
A user should be able to tell whether failure is:
- wrong IP
- wrong port
- no incoming client
- no valid sentences
- stale stream
- permission / bind failure

---

## Phase 6 — Validation and proof
Prove parity against the working XCSoar setup.

### Required proof
- side-by-side test with Condor -> HW VSP3 -> XCSoar and XCPro
- captured real stream fixtures committed for tests
- compare ownship movement, altitude, speed, wind, and vario behavior
- disconnect/reconnect proof
- boundary-lock proof that simulator ownership did not leak into map/UI

### Acceptance
Overall result is only a PASS if:
- the path works end-to-end
- Condor semantics are preserved
- architecture remains intact

---

## Implementation notes that matter

## 1) Keep the transport generic, but the parser Condor-specific
Good:
```text
generic TCP listener + Condor parser/normalizer
```

Bad:
```text
HW VSP3-specific code path inside XCPro
```

## 2) Prefer a single incoming connection
For the first pass, accept one client at a time. Keep it simple and deterministic.

## 3) No socket work on the main thread
Required on Android anyway, and it keeps the design sane.

## 4) Keep simulator replay compatibility in mind
If XCPro already has or plans replay pathways, make the normalized simulator frame suitable for replay fixtures too.

## 5) Make capture-driven tests mandatory
Because “it connected once on my desk” is not proof.

---

## No-go patterns

Fail the change if any of these appear:

- map feature directly creates or owns the TCP listener
- UI/ViewModel parses raw NMEA
- generic NMEA path is used without Condor normalization
- phone GPS/heading silently overrides Condor while Condor mode is active
- transport and parser are split across random modules
- connection state is invisible to the user
- implementation depends on HW VSP3 specifically instead of generic incoming TCP

---

## Deliverables expected from implementation

- settings and persisted mode
- simulator-owned TCP listener
- Condor parser / normalizer
- runtime source selection
- connection state UX
- tests
- docs / ADR / pipeline updates
