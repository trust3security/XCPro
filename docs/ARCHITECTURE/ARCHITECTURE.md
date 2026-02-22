
# ARCHITECTURE.md

## Purpose
This document defines the **non-negotiable architecture rules** for this Android application.

These rules exist to ensure:
- Deterministic behaviour
- Testability
- Safe refactoring
- Simulator / replay capability
- Zero reliance on developer or AI memory

If something is not defined here, it must be discussed and added **before** implementation.

---

## Architecture Overview

This project uses:
- **MVVM (Model-View-ViewModel)**
- **UDF (Unidirectional Data Flow)**
- **SSOT (Single Source of Truth)**
- **Dependency Injection (Hilt)**
- **Coroutines + Flow** for concurrency

No deviations without explicit agreement.

---

## Compliance Guardrails

Architecture rules are enforced by CI and review.
Any deviation must be recorded in `KNOWN_DEVIATIONS.md` with issue ID, owner,
and expiry date before code is merged.

---

## Timebase and Clocks

All domain and fusion logic must use injected clocks.

Rules:
- Use a Clock or TimeSource interface with nowMonoMs() and nowWallMs().
- Domain and fusion code must not call System.currentTimeMillis,
  SystemClock, Date(), or Instant.now() directly.
- Fusion timing uses monotonic time. Replay uses IGC timestamps.
- Wall time is UI/output only (labels, persistence, manual input).
- Never compare or subtract across time bases.

---

## Dependency Direction

Allowed dependency flow:
UI -> domain -> data

Rules:
- UI must not depend on data repositories.
- Domain must not depend on Android or UI types.
- Domain defines repository/data-source interfaces (ports) for external I/O used by business logic.
- Data layer implements those ports via adapters.
- Engines/use-cases depend on ports, not concrete Android/data implementations.
- Core pipeline components are provided by DI, never constructed inside managers.

---

## ViewModel Contract

Rules:
- ViewModels depend on use-cases only.
- No SharedPreferences or persistence inside ViewModels.
- No UI framework types (androidx.compose.ui.*) inside ViewModels.

---

## Lifecycle Collection Standard

Rules:
- All UI flow collection must be lifecycle-aware.
- Compose uses collectAsStateWithLifecycle.
- Non-Compose uses repeatOnLifecycle or equivalent.
- Exceptions are only for previews/tests with explicit comments.

---

## Vendor Neutrality and Encoding

Rules:
- No vendor names in production strings or public APIs.
- Production Kotlin source must be ASCII only.

## 1. MVVM + UDF (State Flow Rules)

### Structure
```
UI (Compose)
  -> intents
ViewModel
  -> state
UI
```

### Rules
- UI renders state only
- UI never performs business logic
- UI never mutates state
- ViewModel never references UI types
- All state changes originate in ViewModel
- Transient interactions (one-off signals) are modeled as events (SharedFlow), not stored in UI state

### Forbidden
- Logic in Composables
- Two-way data binding
- ViewModel calling UI functions
- UI accessing repositories directly

---

## 2. SSOT - Single Source of Truth

### Definition
Each piece of data has **exactly one authoritative owner**.

### Ownership
- Raw sensor data -> Repository
- Derived domain values (TE, vario, filtering) -> UseCase
- UI-visible state -> ViewModel `StateFlow`

### Rules
- No duplicated state
- No cached mirrors across layers
- UI must not store derived values
- ViewModel does not persist domain data
- Settings/preferences are owned by repositories and exposed as Flow/StateFlow

Repositories may use `MutableStateFlow` internally but MUST expose only
`StateFlow` or `Flow` to consumers.

### Forbidden
- Same value stored in multiple layers
- UI recalculating business logic
- Temporary mirrors of SSOT data
- UI or ViewModel reading SharedPreferences directly

---

## 3. Dependency Injection (DI)

### Framework
- **Hilt is mandatory**

### Rules
- Classes do not create their own dependencies
- All dependencies are injected via constructors
- No logic singletons via `object`
- Replaceable implementations must be injectable

### Required
- Separate bindings for:
  - Real sensor sources
  - Simulator / replay sources
- ViewModels receive UseCases only
- UseCases receive Repositories only
- Domain engines/use-cases receive boundary interfaces (ports), not concrete adapters.
- Data adapters are bound to ports in DI modules.

### Boundary Adapter Rule (Ports + Adapters)
Required:
- Define boundary interfaces in domain for persistence/network/device/file I/O used by business logic.
- Implement those interfaces in data adapters.
- Inject interfaces into engines/use-cases via DI.
- Keep Android/framework types inside adapters and Android lifecycle components.

Forbidden:
- Engines/use-cases depending directly on `Context`, `SharedPreferences`, Room DAOs, HTTP clients, file APIs, or sensor managers.
- Passing concrete data adapters through ViewModels/UI to business logic.

### Forbidden
- `new` inside ViewModels
- Static/global access to repositories
- Hardwired implementations

---

## 4. Threading Rules

### Thread Ownership

| Responsibility | Dispatcher |
|---|---|
| UI rendering | `Dispatchers.Main` |
| Business logic / math | `Dispatchers.Default` |
| I/O (files, replay, logging) | `Dispatchers.IO` |

### Rules
- No blocking calls on Main
- Sensor callbacks must offload immediately
- Long-running flows must be cancellable
- Shared state must use Flow or immutability

### Forbidden
- `runBlocking`
- Blocking I/O anywhere
- Manual thread management
- Mutable shared state without Flow

---

## 4A. Time Base Rules

### Live vs Replay
- Live fusion uses monotonic time for delta/validity windows.
- Replay uses IGC timestamps as the simulation clock.
- Wall time is used only for storage/output/UX (e.g., manual wind timestamps, UI labels).

### Rules
- Never compare monotonic timestamps to wall time.
- All live SensorDataSource implementations must populate monotonic timestamps.
- If a source cannot provide monotonic time, use `0` and ensure all related inputs fall back to wall time consistently.
- Time sources must be injected (Clock interface). Domain logic must not call SystemClock/System.currentTimeMillis directly.

---

## 4B. Unit System Rules (SI)

Internal calculations are SI-only. This is non-negotiable.

Canonical internal units:
- Distance: meters.
- Altitude: meters.
- Horizontal/airspeed/wind speed: m/s.
- Vertical speed (vario/STF): m/s.
- Acceleration: m/s^2.
- Pressure: Pa or hPa (explicitly labeled).

Rules:
- Domain/fusion/task/scoring/replay logic must not mix km/h, knots, feet, NM, miles with SI internals.
- Non-SI units are allowed only at explicit input/output boundaries (UI formatting, protocol/file adapters).
- Convert once at ingress/egress and keep SI values throughout the internal flow.
- Method and field names must encode units (`*Meters`, `*Ms`, `*Hpa`, `*Pa`, `*Deg`).
- Unit-crossing comparisons are forbidden (example: km value compared against meter threshold).

Why this is required in XCPro:
- FAI task geometry and scoring rely on precise distance semantics (start lines, turnpoint cylinders, finish zones).
- Typical competition constraints include 500 m turnpoint cylinders and finish rings >= 3 km.
- Core sensors already provide SI-aligned data paths (GPS meters, accelerometer m/s^2, baro -> altitude in meters, vario in m/s).
- Mixed units create subtle near-correct failures in start/finish crossing detection, zone intersection, STF, polar interpolation, conflict/near-miss logic, and scoring.

---

## 5. Data Flow Contract

### Allowed Flow
```
Sensors / Data Sources
  -> Repository (SSOT)
    -> UseCase (derive / filter)
      -> ViewModel (UI State)
        -> UI (render only)
```

### Simulator / Replay Mode
- Implemented by swapping repositories via DI
- No conditional logic in ViewModel or UI
- Architecture must remain unchanged

---


## 5A. Map Display Pipeline (UI Only)

The map is a UI consumer of SSOT. It must not bypass repositories or
write back smoothed values.

### Rules
- Map position comes from `FlightDataRepository` only.
- ViewModels never read raw sensor flows for map position.
- Display smoothing is visual-only and must not alter SSOT data.
- MapLibre types are owned by UI/runtime controllers, not ViewModels.
- Display time must match the fix time base:
  - Live: monotonic if present, otherwise wall time.
  - Replay: IGC timestamps as the simulation clock.
- Camera updates are gated and short-animated to avoid jumps.

See `../../mapposition.md` for the concrete flow and component list.

---

## 5B. Task Pipeline Contract (Racing and AAT)

Task management follows the same MVVM + UDF + SSOT model as flight data.

### Allowed Flow
```
Task UI (Compose)
  -> TaskSheetViewModel intents
    -> Task use-cases/coordinator use-cases
      -> Task repository/coordinator (authoritative state)
        -> ViewModel StateFlow
          -> Task UI render
```

### Ownership Rules
- Task definitions, active leg, and persistence are authoritative in task repository/coordinator owners.
- Boundary policy (zone entry, distance/radius policy, auto-advance criteria) belongs to domain/use-case logic.
- ViewModels transform task domain state into UI state and dispatch intents.
- Composables render state and emit intents only.

### Forbidden
- Composables calling task managers/coordinators directly for mutation or business queries.
- Composables reading manager internals as state (for example currentTask/currentLeg/currentAATTask).
- ViewModels exposing raw manager/controller handles as public API.
- Non-UI manager/domain classes using Compose runtime state primitives.

---

## 6. Foreground Service (Lifecycle Host, NOT State Owner)

A foreground service is used **only** to satisfy Android OS lifecycle and
background execution requirements.

### Responsibilities
- Keep the data pipeline alive when the UI is backgrounded
- Start / stop sensor collection based on lifecycle and commands
- Host injected components required for continuous operation

### Explicit Non-Responsibilities
- The service does NOT own application state
- The service does NOT act as SSOT
- The service does NOT contain business logic

### Architecture Rule
All authoritative state is owned by repositories.

The foreground service:
- Injects repositories and use-cases
- Triggers start/stop actions
- Observes flows if required
- Never stores or mutates domain state

SSOT remains intact regardless of UI or service lifecycle.

---

## 7. Service and Singleton Rules

### Allowed
- Android framework Services (including ForegroundService)
- Services instantiated and managed by the OS
- Dependencies injected into services via Hilt

### Forbidden
- Kotlin `object` services
- Global/static service managers
- Hidden singletons holding mutable state
- Manually constructed long-lived service instances

Rule of thumb:
Android components may exist.  
Global mutable singletons may not.

---

## 8. ViewModel Rules

### Responsibilities
- Own UI state
- Transform domain outputs into UI models
- Handle user intents
- Expose immutable `StateFlow`

### Forbidden
- File access
- Sensor access
- Heavy computation
- Platform APIs

---

## 9. UI (Compose) Rules

### Responsibilities
- Render state
- Frame-ticker loops for visual-only smoothing when scoped to `LaunchedEffect`
- Collect flows with lifecycle-aware APIs (`collectAsStateWithLifecycle` in Compose; `repeatOnLifecycle` elsewhere)
- Emit user intents
- Handle visuals and animations only

### Forbidden
- Business logic
- State derivation
- Long-running coroutines outside the frame-ticker display exception
- Side effects outside `LaunchedEffect`

---

## 10. Error Handling

### Rules
- Errors are data
- No silent failures
- No swallowed exceptions
- Errors flow upward via domain models

### UI
- Renders error state
- Never decides error semantics

---

## 11. XCSoar Reference Policy

### Production Code Rule
The literal string `"xcsoar"` MUST NOT appear in:
- Production Kotlin source code
- Package names
- Class names
- Runtime string constants
- Public APIs

### Allowed Locations
References are permitted in:
- Documentation
- Test code
- Research notes
- Commit messages
- Comments explaining algorithm parity

### Rationale
The application must remain implementation-original and vendor-neutral
while allowing accurate technical documentation.

---

## 12. Testing Expectations

The following must be testable without Android framework:
- Repositories (with fake sources)
- UseCases (pure logic)
- ViewModels (state transitions)

If a component cannot be tested in isolation, the design is incorrect.

---

## 13. AI / Tooling Assumptions

### Assumptions
- AI sessions are stateless
- Context will be lost
- Developers will change

### Therefore
- Architecture rules live here
- Feature intent must be documented
- Code must be self-describing
- No reliance on remembered context

---

## 14. Change Safety Requirements

These rules prevent regressions and future rewrites.

Rules:
- Non-trivial refactors must have a written plan doc with phases, ownership, and tests.
- Use `CHANGE_PLAN_TEMPLATE.md` as the default starting point for non-trivial feature/refactor plans.
- SSOT ownership must be explicit with a simple flow diagram or bullet flow.
- Time base must be specified and enforced in code and tests (monotonic or replay only).
- State machines must be explicit: list states and transitions in docs.
- Regression tests are mandatory for new behavior (unit tests first, replay tests when applicable).

Red flags in review (reject changes):
- Business logic in UI code.
- Shared utilities across task types.
- Wall time used in navigation or scoring logic.
- Duplicate state owners for the same data.

---

## Related Docs
- `CHANGE_PLAN_TEMPLATE.md`: neutral plan template for new features and refactors.
- `../../mapposition.md`: map display update flow and time-base rules.

## Final Rule

Architecture is not optional.  
If it is not enforced, it does not exist.

