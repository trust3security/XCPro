# XCPro ADS-B / OpenSky: Keep, Refactor, Do Not Rewrite

Date: 2026-03-16  
Audience: Codex agent working in `trust3security/XCPro`  
Status: Decision doc  
Decision owner: XCPro product/engineering

---

## 1) Blunt verdict

The current XCPro OpenSky implementation is **good**. It is not a throwaway. It should **not** be ripped out.

If XCPro gets explicit OpenSky approval for paid/commercial use, the current implementation is still **not the final best production architecture**, but it is a **solid base** and should be **evolved**, not rewritten.

### Score
- Current implementation with OpenSky approval: **7.5–8.5 / 10**
- Same implementation with backend relay + provider abstraction: **9+ / 10**

---

## 2) What is already good and should be preserved

Codex must assume the following work is already valuable and should stay unless there is a hard technical reason to change it.

### 2.1 OAuth2 client-credentials token handling is already strong
Keep the current token repository design.

It already has:
- OAuth2 client-credentials token fetch
- token caching
- expiry buffering
- transient failure cooldown
- invalidation support
- single-flight concurrency control via mutex/locking

This is real engineering, not prototype junk.

### 2.2 ADS-B repository/runtime architecture is already the right shape
Keep the current repository/runtime/store flow.

It already has:
- dedicated provider client boundary
- repository-owned polling/runtime state
- stale/expiry handling
- connectivity handling
- retry/backoff behavior
- runtime snapshot/debug state
- existing map/use-case/viewmodel integration

This matches XCPro’s broader architecture style and is the correct place for the traffic logic.

### 2.3 Adaptive polling / credit-awareness work is worth keeping
Keep the adaptive cadence and budget-aware polling behavior.

This work is useful even if the provider changes later.

### 2.4 Existing filtering/settings work is worth keeping
Keep the following user/runtime controls:
- max distance filtering
- vertical above/below filtering
- immediate reconnect/apply behavior when radius changes
- icon size/settings integration

These are not OpenSky-specific mistakes. They are reusable traffic-domain behavior.

---

## 3) What is not good enough for a paid production release

The main weakness is **deployment architecture**, not the Kotlin implementation.

### 3.1 Do not treat device-held provider secrets as the final release model
Even if encrypted locally, shipping provider client secrets in a public Android app is not strong enough as the default production architecture.

### 3.2 Do not tie the product to one provider endpoint
The app should not be hard-wired long-term to direct OpenSky calls as the only serious path.

### 3.3 Do not make every phone a first-class provider client forever
Direct device polling is okay for development, internal use, and possibly advanced-user mode.
It is not the best long-term commercial architecture for quota control, rotation, failover, observability, or provider switching.

---

## 4) Final product direction

### Decision
**Continue with the current codebase. Do not rewrite it. Refactor it into a provider-agnostic architecture and make backend relay the default commercial path.**

### Strategic target state

```text
XCPro App
  -> Traffic domain/use-cases/repository/store (keep)
  -> AdsbProviderClient abstraction (keep and strengthen)
  -> Default provider = XCPro backend relay
  -> Optional provider = direct OpenSky (debug/dev/advanced)
  -> Optional future providers = ADS-B Exchange / airplanes.live / custom feed
```

---

## 5) Keep / Refactor / Delete

## 5.1 KEEP
Codex should preserve and reuse these concepts and files/patterns wherever practical.

### Keep as-is or very close
- `OpenSkyTokenRepository` concept and implementation shape
- `AdsbProviderClient` boundary
- `AdsbTrafficRepository` authority over runtime state
- `AdsbTrafficStore` domain selection/filtering pipeline
- current polling/runtime policy modules
- current connection state model
- current stale/expiry modeling
- current settings integration for distance/vertical filters/icon size
- current map overlay integration path
- current unit-test style and fake-provider testing approach

### Keep with only light cleanup
- naming cleanup if needed
- package alignment if feature paths drifted during refactors
- debug/snapshot exposure consistency
- provider-specific wording in UI/settings

---

## 5.2 REFACTOR
These are the high-value changes.

### Refactor A — Make provider selection explicit
Introduce a provider selection model such as:
- `BACKEND_RELAY`
- `OPENSKY_DIRECT`
- `DISABLED`
- optional future values later

This must live in domain/data settings, not scattered ad hoc through UI.

### Refactor B — Split provider-neutral traffic domain from provider-specific auth/config
Provider-neutral traffic logic must stay independent from OpenSky-specific credential handling.

#### Provider-neutral
- target models
- store/filtering
- repository runtime state
- polling policy
- overlay mapping
- emergency classification

#### OpenSky-specific
- token fetch
- auth headers
- OpenSky response mapping/index parsing
- OpenSky-specific header/credit semantics

### Refactor C — Add backend relay provider client
Create a new provider client implementation that talks to an XCPro backend endpoint.

Suggested shape:
- `BackendRelayProviderClient : AdsbProviderClient`
- app sends bbox / ownship / filter settings / client app version / anonymous session id if needed
- backend handles provider auth and upstream provider calls
- backend returns normalized traffic DTOs

### Refactor D — Keep direct OpenSky as non-default mode
Direct OpenSky should remain available for:
- internal testing
- development
- provider debugging
- advanced user self-supplied credentials mode

But it should not be the default paid-release path.

### Refactor E — Normalize DTOs at the provider edge
Do not let provider-specific response details bleed deep into the repository/store.
Normalize provider responses into XCPro traffic domain models as early as possible.

### Refactor F — Harden observability
Add explicit debug info for:
- selected provider
- poll delay chosen
- provider response status
- last auth state
- remaining credits if applicable
- normalized target count vs displayed count
- backend-vs-direct mode

This belongs in repository snapshot/debug output, not business logic in UI.

---

## 5.3 DELETE OR DE-EMPHASIZE
Do not waste time here.

### Do not rewrite just because it “feels cleaner”
No rewrite of:
- repository/store architecture
- traffic models unless required for provider abstraction
- map integration pipeline
- existing tests that already cover correct behavior

### Do not move business logic into UI/ViewModel
No traffic policy logic in:
- Composables
- `MapScreenViewModel`
- settings UI

### Do not build a giant generic provider framework
Keep it practical.
Do not over-engineer a plugin platform.
A clean interface plus 2 implementations is enough.

### Do not rebuild polling from scratch
Reuse the current cadence/runtime logic and only adapt it where the provider contract changes.

---

## 6) What Codex should implement next, in order

### Phase 1 — Stabilize the existing OpenSky path without functional regression
Goal:
- no behavior loss
- no map regression
- no filter regression
- no auth regression

Tasks:
1. inventory current ADS-B/OpenSky classes and confirm boundaries
2. document current data flow in code comments if missing
3. ensure tests cover current behavior before refactor begins
4. add any missing seam/interface needed for provider swap without changing behavior

Exit criteria:
- all existing ADS-B tests pass
- direct OpenSky path still works exactly as before

### Phase 2 — Introduce explicit provider mode and backend-ready interfaces
Goal:
- make provider selection first-class

Tasks:
1. add provider mode enum/model
2. add preferences/config for selected provider mode
3. update DI wiring/factories to choose provider client by mode
4. keep OpenSky direct path intact under the selected mode

Exit criteria:
- app compiles
- provider mode can switch implementation cleanly
- no UI breakage

### Phase 3 — Add XCPro backend relay provider client
Goal:
- make backend relay the production/default architecture

Tasks:
1. define backend traffic API contract
2. implement backend DTO mapper
3. add authenticated/anonymous client contract as needed
4. implement `BackendRelayProviderClient`
5. normalize backend payload into existing XCPro traffic models
6. preserve repository/store downstream behavior

Exit criteria:
- app can run using backend relay with minimal downstream changes
- direct OpenSky remains available behind a setting or build flag

### Phase 4 — Make release behavior sane
Goal:
- avoid shipping public-app shared secrets as default

Tasks:
1. make backend relay the default release provider mode
2. hide direct OpenSky credentials UI from normal release users unless explicitly enabled for debug/advanced mode
3. keep developer diagnostics available
4. confirm no provider secrets are required for normal release operation

Exit criteria:
- release build works without end-user OpenSky secrets
- advanced/dev mode can still test direct OpenSky if needed

### Phase 5 — Future-proof, but only after relay works
Optional:
- secondary provider client
- provider failover
- server-side caching/aggregation
- provider health routing

Do this only after the relay path is stable.

---

## 7) Guardrails for Codex

### Mandatory rules
- Preserve current ADS-B feature behavior unless the task explicitly changes it.
- Preserve current user-facing map overlay behavior unless replacing provider-specific wording.
- Prefer extraction/refactor over rewrite.
- Do not delete passing tests without replacing coverage.
- Keep SSOT ownership in repository/domain layers.
- Do not move traffic policy into UI.
- Do not introduce global mutable singleton state.
- Keep timebase discipline: monotonic for polling/retry/cooldown logic.

### Strong preference
- additive refactor
- small PR-sized changes
- compile and test after each phase
- keep a rollback-safe path

---

## 8) Concrete examples of good vs bad changes

### Good
- extract provider selection into DI + config
- create `BackendRelayProviderClient`
- keep `AdsbTrafficRepository` downstream consumers stable
- keep current filtering/store logic untouched while changing upstream source
- preserve existing tests and add new ones for provider switching

### Bad
- rewrite the whole traffic stack because backend relay exists
- move polling decisions into a ViewModel
- remove OpenSky direct path entirely before relay is proven
- entangle backend DTOs with map UI models
- rebuild map overlay for no reason

---

## 9) Recommended implementation stance

Codex should act as if the current OpenSky implementation is a **valuable v1.5**.

That means:
- **respect it**
- **reuse it**
- **abstract it**
- **do not worship it**
- **do not throw it away**

---

## 10) Final instruction to Codex

Implement the next version of XCPro ADS-B by **refactoring around the existing code**, not by replacing it.

The current code is good enough to be the base.
The next win is architectural:
- provider abstraction
- backend relay default path
- direct OpenSky retained for dev/advanced mode

That is the shortest path to a stronger commercial XCPro traffic stack.
