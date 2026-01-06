# CODING_RULES.md

## Purpose
This document defines **day-to-day coding rules** that enforce the architecture.

These rules exist to:
- Prevent architectural drift
- Keep AI output predictable
- Reduce review friction
- Make refactors safe
- Make simulator and replay modes trivial

If code violates this file, it is wrong — even if it “works”.

---

## 1. General Rules
- Clarity beats cleverness
- Explicit beats implicit
- Predictable beats concise
- Architecture beats convenience

---

## 2. Package Structure Rules

Code MUST follow this conceptual structure (names may vary):

```
data/
  sensors/
  repository/

domain/
  usecase/
  model/

ui/
  screen/
  viewmodel/
```

### Rules
- UI code never imports `data`
- Repositories never import `ui`
- UseCases never import Android UI classes
- Domain models contain no Android types

---

## 3. Naming Rules

### Classes
- Repositories: `XRepository`
- UseCases: `XUseCase`
- ViewModels: `XViewModel`
- UI state: `XUiState`

### Flows
- Private mutable: `_state`
- Public immutable: `state`

```kotlin
private val _state = MutableStateFlow(...)
val state: StateFlow<UiState> = _state
```

---

## 4. State Rules (Critical)

### Allowed
- Repositories may hold mutable state internally
- ViewModels may derive UI state
- UI reads state only

### Forbidden
- Mutable state exposed publicly
- State duplication across layers
- UI remembering derived values
- ViewModel caching domain data

If state exists in two places, one is wrong.

---

## 5. Flow & Coroutine Rules

### Flow
- Prefer `StateFlow` for UI state
- Prefer cold `Flow` for data streams
- No shared mutable state outside Flow

### Coroutines
- Use structured concurrency only
- Every coroutine must have an owner
- Jobs must be cancellable

### Forbidden
- `GlobalScope`
- `runBlocking`
- Manual thread management
- Blocking calls inside flows

---

## 6. Dispatcher Rules

Dispatchers must be explicit.

- Math / filtering: `Dispatchers.Default`
- File I/O: `Dispatchers.IO`
- UI: `Dispatchers.Main`

Forbidden:
- Heavy work on Main
- Implicit dispatcher assumptions

---

## 7. ViewModel Rules

Allowed:
- State transformation
- Intent handling
- Combining flows
- Mapping domain → UI models

Forbidden:
- File I/O
- Sensor access
- Long-running loops
- Platform APIs
- Business math

---

## 8. UI (Compose) Rules

Allowed:
- Rendering
- Animations
- User input
- Visual effects

Forbidden:
- Business logic
- State derivation
- Domain calculations
- Manual coroutine scopes
- Side effects outside `LaunchedEffect`

---

## 9. Repository Rules

Repositories:
- Own authoritative data (SSOT)
- Hide data sources
- Expose `Flow` / `StateFlow` only

Forbidden:
- UI logic
- Android UI imports
- Caching UI state

---

## 10. UseCase Rules

UseCases:
- Contain business logic
- Are pure where possible
- Are testable without Android

Forbidden:
- UI concerns
- Lifecycle awareness
- Platform APIs
- Hidden state

---

## 11. Service Rules

Foreground services:
- Exist for OS constraints only
- Do not own state
- Do not perform business logic

Forbidden:
- Acting as SSOT
- Holding mutable domain state
- Becoming a “manager” god object

---

## 12. Error Handling Rules

- Errors are values
- No silent failures
- No swallowed exceptions
- Errors flow through the same pipeline as data

---

## 13. Logging Rules

- No logs in tight loops
- Do not log location data in release builds
- Logs must not be required for correctness

---

## 14. File Encoding Rules

- Keep source files ASCII or UTF-8 (no smart quotes or special punctuation)
- Avoid hidden non-ASCII characters in code, docs, and commit messages
- If a file is not UTF-8, edit with byte-safe tools and document why

---

## 15. Testing Rules

Must be testable without Android:
- Repositories (with fakes)
- UseCases
- ViewModels

No “hard to test” exceptions.

---

## 16. AI / Codex Rules

Assume:
- AI forgets everything
- Prompts will be partial
- Code will be regenerated

Therefore:
- Code must be explicit
- Intent must be readable from files
- Architecture must enforce correctness

---

## Final Rule

If a rule is unclear:
- Clarify it
- Write it down
- Enforce it

Unwritten rules do not exist.
