
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

Reference visuals:
- `APPLICATION_WIRING.svg` for the app shell, module relationships, and major runtime ownership seams.
- `PIPELINE.svg` for the detailed live/replay flight-data pipeline.

No deviations without explicit agreement.

---

## Compliance Guardrails

Architecture rules are enforced by CI and review.
Any deviation must be recorded in `KNOWN_DEVIATIONS.md` with issue ID, owner,
and expiry date before code is merged.

---

## Rule Classification

This repo uses three rule classes:

| Class | Meaning | Merge Expectation |
|---|---|---|
| Invariant | Non-negotiable architecture, determinism, safety, or boundary rule | Must hold, be enforced, or have an approved time-boxed deviation |
| Default | Expected baseline for new work | May vary only with explicit rationale and equivalent safety/test coverage |
| Guideline | Strong recommendation that improves consistency and reviewability | May vary with local rationale |

Rules:
- Everything in `ARCHITECTURE.md` is an `Invariant` unless a section explicitly says `Default`.
- `CODING_RULES.md` may define `Invariant`, `Default`, or `Guideline` rules, but CI-backed rules must be identified there.
- Workflow docs (`CONTRIBUTING.md`, plan docs, PR notes) do not override architecture invariants.

---

## Documentation Source of Truth

Each architecture document has a single job.

| Document | Owns | Must Not Become |
|---|---|---|
| `AGENTS.md` | repository entry contract for agents | a duplicate architecture rulebook |
| `ARCHITECTURE.md` | non-negotiable system invariants and governance model | task-specific execution log |
| `CODING_RULES.md` | day-to-day implementation rules and enforceable coding defaults | a second pipeline map |
| `PIPELINE.md` | current end-to-end wiring and runtime ownership flow | a backlog or design debate log |
| `CODEBASE_CONTEXT_AND_INTENT.md` | durable product/behavior intent that must survive refactors | a pointer to one temporary active plan |
| `CONTRIBUTING.md` | contributor workflow, review, and verification expectations | a second source of architectural truth |
| `CHANGE_PLAN_TEMPLATE.md` | required planning structure for non-trivial work | a permanent architecture policy doc |
| `KNOWN_DEVIATIONS.md` | the only authoritative ledger of approved temporary rule exceptions | a general risk register |
| `ADR_TEMPLATE.md` | template for durable architecture decisions and tradeoff capture | a task checklist |

Rules:
- Global docs must stay durable. Temporary status, active-plan pointers, and one-off task instructions belong in change plans or ADRs, not here.
- If the same rule appears in multiple docs, one file must be named authoritative and the others should link to it rather than restate it differently.

---

## Architecture Decision Records (ADR)

Non-trivial architecture decisions must be captured in a durable decision record.

An ADR is required when work changes any of the following:
- ownership or dependency boundaries across layers/modules
- public or cross-module API surface
- concurrency, buffering, cadence, or determinism policy
- exception classes that are expected to recur
- performance/SLO budgets that will influence future design tradeoffs

Rules:
- Use `ADR_TEMPLATE.md` for new ADRs.
- ADRs record context, decision, alternatives, consequences, validation, and rollback.
- Change plans describe execution; ADRs describe durable decisions. Do not use one as a substitute for the other.
- Superseded ADRs remain in-repo with an explicit replacement reference.

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

## Module and API Surface Governance

Rules:
- New modules require a documented reason in the change plan and, for durable boundary moves, an ADR. Valid reasons include boundary enforcement, ownership isolation, compile-speed improvements, or testability.
- Visibility must be as narrow as possible. Default to `private`, then `internal`, and use wider visibility only for named consumers.
- New public or cross-module contracts must declare an owner, expected consumers, and stability expectations.
- Compatibility shims/adapters introduced during refactors must have a removal condition or expiry path; permanent hidden compatibility layers are forbidden.
- Convenience imports that bypass owner-module contracts are forbidden.

---

## Scope Ownership and Lifetime

Long-lived coroutine scopes are architecture-significant. They are allowed only
when ownership and teardown are explicit.

Rules:
- Every long-lived scope must have a named owner and a documented cancellation path.
- Preferred owners are lifecycle/runtime hosts (`viewModelScope`, foreground service scope, injected runtime owner scope, repository/runtime owner scope when continuous collection is required).
- Pure helpers, mappers, value models, and policy objects must not create their own long-lived scopes.
- Constructor-created scopes are allowed only in explicit lifecycle/runtime owners or repositories that continuously coordinate authoritative/runtime state.
- Nested/internal scopes are allowed only when they are clearly subordinate to one parent owner and their teardown relationship is explicit.
- New scope creation must be justified in the change plan with owner, dispatcher, and cancellation trigger.

---

## Logging Architecture

Logging is infrastructure and must not become an ungoverned side channel.

Rules:
- `AppLogger` is the canonical logging path for production Kotlin logging where redaction, debug gating, sampling, or rate limiting may matter.
- Direct `android.util.Log` calls are allowed only in narrow platform/bootstrap edges, low-level wrappers, or short-lived debug investigations with explicit rationale.
- Hot-path, replay, sensor, traffic, and location-adjacent logs must use rate limiting/sampling and redaction where applicable.
- Logs must not be the only place where correctness-critical events are observable; user-visible or domain-relevant failures must still flow through models/state.
- Temporary debug logging that bypasses the canonical path must be removed before merge or tracked as an explicit approved exception.

---

## Responsibility Ownership Matrix

This matrix defines the default owner for new feature responsibilities.

| Layer | Owns | Must Not Own |
|---|---|---|
| UI | rendering, event forwarding, display-only formatting, visual-only smoothing | business rules, persistence, repository access, authoritative state |
| ViewModel | screen state, user-intent handling, orchestration, UI-model mapping | business calculations, direct I/O, Android UI types, persistence |
| UseCase / Domain | business rules, policy, calculations, state decisions | rendering, platform I/O, Android/UI types |
| Repository | authoritative data coordination, persistence-facing state, SSOT exposure | rendering, UI logic, business decisions that belong in domain |
| Data Source / Adapter | API, database, file, sensor, and device access | business policy, UI state, rendering |
| Mapper | conversion between data, domain, and UI models | business decisions, persistence, I/O |

Rules:
- New feature work must keep ownership aligned with this matrix.
- Do not bypass layers for convenience.
- If one file mixes multiple owners, split it by responsibility.

---

## Authoritative State Contract

Ownership is required but not sufficient. Every authoritative or derived state item must also have a documented contract.

Required contract fields for new or changed state:
- authoritative owner
- allowed mutator(s) or mutation entrypoint(s)
- exposed/read path
- upstream source or derived-from dependency
- persistence owner, if persisted
- reset or clear conditions
- time base, if time-dependent
- required test coverage

Rules:
- Authoritative state has exactly one write authority.
- Derived state must be recomputable from lower-layer authorities; if it cannot be recomputed, ownership is wrong or incomplete.
- `null`, zero, or default objects are not acceptable substitutes for undocumented loading, error, degraded, or unavailable states.
- If lifecycle, source switching, or replay/live mode changes reset the state, that reset behavior must be documented explicitly.

---

## Stateless Objects and No-Op Boundaries

Rules:
- Kotlin `object` is allowed for stateless helpers, constants, pure policy/math utilities, DI modules, sealed singleton values, and explicit `NoOp` boundary implementations.
- Use `object` when shared behavior is genuinely stateless; do not introduce allocation-only wrapper classes solely to avoid `object`.
- Kotlin `object` must not become an application state owner, lifecycle host, hidden service locator, or authoritative business-state container.
- If an `object` has mutable internal state, that state must be bounded, thread-safe, non-authoritative, and documented.
- `NoOp` implementations are allowed only for tests, optional capabilities, safe degraded modes, or explicit disabled-feature boundaries.
- Mandatory production behavior must not silently fall back to `NoOp` without documented degraded behavior and review visibility.
- Public convenience wiring that hides `NoOp`, time/random generation, or ad-hoc scope creation is forbidden unless the boundary is explicitly optional and documented.

---

## Compatibility Shim Lifecycle

Temporary compatibility code is a migration tool, not a resting state.

Rules:
- Every compatibility shim/bridge/wrapper must declare: owner, reason, target replacement, and removal trigger.
- Shim code comments should use the prefix `Compatibility shim:` so reviewers and cleanup passes can find them reliably.
- Every shim must have a test that locks current expected behavior during the migration window.
- If a shim is expected to outlive one refactor phase, record the decision in the change plan and, when durable, in an ADR.
- Permanent compatibility layers must be promoted into explicit supported API/contracts; untracked legacy shims are forbidden.

---

## Canonical Formula and Policy Owners

Shared formulas, thresholds, and policy constants must have one clear owner.

Rules:
- Physical formulas, atmospheric/QNH math, normalization rules, and policy constants reused across slices must have one canonical owner file/module.
- Copy-pasted math or duplicated policy constants are forbidden unless required by a boundary adapter, performance constraint, or approved temporary migration path.
- If duplication is temporarily required, the canonical owner and re-sync/removal plan must be documented in the change plan or deviation.
- Naming and units must stay aligned with the canonical owner.
- Tests, adapters, and docs that restate a shared formula or threshold must point back to the canonical owner when practical.

---

## Identity and Model Creation Policy

Rules:
- IDs, timestamps, and other generated identity values must be created at explicit ownership boundaries (factory, repository, use case, creation command, or adapter).
- Domain/data models must not hide important creation side effects behind default property values when identity/time semantics matter.
- Deterministic/replay-sensitive paths must use deterministic IDs or injected generators.
- Random or wall-time-backed IDs are allowed only at explicit creation boundaries where non-determinism is acceptable and documented.
- Persistent entities should prefer required constructor parameters or explicit factory methods over default-generated IDs/timestamps.

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
- If a source cannot provide monotonic time, treat that source as invalid for fusion timing unless an explicit adapter policy is documented.
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
  - Live: monotonic source timestamps for fusion/display provenance; do not silently fall back to wall time in fusion paths.
  - Replay: IGC timestamps as the simulation clock.
- Camera updates are gated and short-animated to avoid jumps.

### MapScreen Visual Runtime Contract (Mandatory)

Any change that affects map interaction or map-rendered overlays
(pan/zoom/rotate, task drag, traffic/weather overlays, replay scrubbing,
startup map readiness) must:

- Declare impacted visual SLO IDs from
  `docs/MAPSCREEN/02_BASELINE_PROFILING_AND_SLO_MATRIX_2026-03-05.md`.
- Use test/validation paths from
  `docs/MAPSCREEN/04_TEST_VALIDATION_AND_ROLLBACK_2026-03-05.md`.
- Attach baseline + post-change evidence in PR/change notes.
- Meet all impacted mandatory SLO thresholds before merge.

If a mandatory SLO is missed:
- either fix before merge, or
- add a time-boxed deviation in `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md`
  with issue ID, owner, expiry, and rollback plan.

Forbidden:
- Subjective-only acceptance ("looks smooth") without measured SLO evidence.

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

## 5C. Live Wind IAS/TAS Source Contract

This contract applies to the live flight path.

### Rules
- Wind-derived IAS/TAS source switching must be stateful and deterministic.
- Live WIND vs GPS switching must use explicit hysteresis, minimum dwell, and transient grace in domain logic.
- Frame-local source flip-flop logic (`WIND` else `GPS` per sample) is forbidden.
- `tasValid`, airspeed source label, and TE eligibility must be derived from the same stabilized source decision.
- Domain is the authority for source reliability policy; UI/trail/heading code must not use independent ad-hoc thresholds.
- Threshold values must be centralized in one domain policy/constants location; scattered literals are forbidden.
- Live correctness must not require an external device path; phone-only operation is a supported baseline.
- Replay behavior must remain unchanged unless a replay-specific plan explicitly changes it.

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

### Required Modeling
- Non-happy-path behavior must be classified as one of: recoverable error, degraded-but-usable, unavailable/not-enough-data, terminal failure, or user-action-required.
- Domain/use-case layers own retry, fallback, staleness, and confidence semantics.
- UI renders state, labels, and actions only; it must not invent error semantics.
- Invalid or unavailable values must not be silently mapped to valid-looking defaults.
- Degraded states that affect user trust must be stable under noise (dwell/hysteresis/hold where needed).

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

Required proof by change type:
- Domain/policy/math change -> unit tests with edge cases and invariants
- Replay/time-base/cadence change -> fake-clock tests plus deterministic repeat-run coverage
- Persistence/settings/schema change -> round-trip, restore, and migration/compat tests
- UI interaction/lifecycle change -> UI or instrumentation coverage when behavior depends on runtime collection/gesture sequencing
- Hot-path/performance/SLO change -> measured evidence or benchmark/SLO artifact

If required proof is intentionally skipped, the rationale belongs in the change plan or an approved deviation.

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
- Boundary/module/API surface decisions that should outlive one task must be recorded with `ADR_TEMPLATE.md`.
- SSOT ownership must be explicit with a simple flow diagram or bullet flow.
- New long-lived scopes, compatibility shims, canonical formula owners, and non-deterministic identity generation points must be explicit in plans and tests.
- Time base must be specified and enforced in code and tests (monotonic or replay only).
- State machines must be explicit: list states and transitions in docs.
- Global contract docs must remain durable; they must not hardcode temporary active-plan pointers or one-off execution state.
- Regression tests are mandatory for new behavior (unit tests first, replay tests when applicable).

Red flags in review (reject changes):
- Business logic in UI code.
- Shared utilities across task types.
- Wall time used in navigation or scoring logic.
- Duplicate state owners for the same data.

---

## 14A. Kotlin File Size Budget

These rules keep files reviewable and refactor-safe.

Rules:
- Kotlin source files in this repo must be `<= 500` lines by default.
- Stricter per-file budgets may be enforced in `scripts/ci/enforce_rules.ps1` for hotspot files.
- Splits must preserve architecture boundaries and behavior contracts.
- Temporary budget violations require a time-boxed exception in `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md` with issue ID, owner, and expiry.

---

## Related Docs
- `CHANGE_PLAN_TEMPLATE.md`: neutral plan template for new features and refactors.
- `ADR_TEMPLATE.md`: durable record template for non-trivial architecture decisions.
- `../../mapposition.md`: map display update flow and time-base rules.

## Final Rule

Architecture is not optional.  
If it is not enforced, it does not exist.

