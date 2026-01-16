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
- **MVVM (Model–View–ViewModel)**
- **UDF (Unidirectional Data Flow)**
- **SSOT (Single Source of Truth)**
- **Dependency Injection (Hilt)**
- **Coroutines + Flow** for concurrency

No deviations without explicit agreement.

---

## 1. MVVM + UDF (State Flow Rules)

### Structure
```
UI (Compose)
  ↓ intents
ViewModel
  ↓ state
UI
```

### Rules
- UI renders state only
- UI never performs business logic
- UI never mutates state
- ViewModel never references UI types
- All state changes originate in ViewModel

### Forbidden
- Logic in Composables
- Two-way data binding
- ViewModel calling UI functions
- UI accessing repositories directly

---

## 2. SSOT – Single Source of Truth

### Definition
Each piece of data has **exactly one authoritative owner**.

### Ownership
- Raw sensor data → Repository
- Derived domain values (TE, vario, filtering) → UseCase
- UI-visible state → ViewModel `StateFlow`

### Rules
- No duplicated state
- No cached mirrors across layers
- UI must not store derived values
- ViewModel does not persist domain data

Repositories may use `MutableStateFlow` internally but MUST expose only
`StateFlow` or `Flow` to consumers.

### Forbidden
- Same value stored in multiple layers
- UI recalculating business logic
- Temporary mirrors of SSOT data

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

---

## 5. Data Flow Contract

### Allowed Flow
```
Sensors / Data Sources
  → Repository (SSOT)
    → UseCase (derive / filter)
      → ViewModel (UI State)
        → UI (render only)
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

See `mapposition.md` for the concrete flow and component list.

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
- ARCHITECTURE_DECISIONS.md: rationale for major architecture refactors and why they were made.
- REFACTOR.md: active UDF/SSOT map refactor plan and progress.
- REFACTOR_GEOPOINT.md: map-agnostic GPS refactor plan.
- mapposition.md: map display update flow and time-base rules.

## Final Rule

Architecture is not optional.  
If it is not enforced, it does not exist.
