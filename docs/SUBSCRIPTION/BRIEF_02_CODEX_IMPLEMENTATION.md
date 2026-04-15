# Codex Brief — Implementation

Use this only after the plan has been reviewed and approved.

```text
You are implementing the approved XCPro subscription plan.

READ FIRST, IN THIS ORDER:
1. AGENTS.md
2. docs/ARCHITECTURE/ARCHITECTURE.md
3. docs/ARCHITECTURE/CODING_RULES.md
4. docs/ARCHITECTURE/PIPELINE_INDEX.md
5. relevant sections of docs/ARCHITECTURE/PIPELINE.md
6. docs/ARCHITECTURE/CODEBASE_CONTEXT_AND_INTENT.md
7. docs/ARCHITECTURE/CONTRIBUTING.md
8. docs/ARCHITECTURE/KNOWN_DEVIATIONS.md
9. docs/ARCHITECTURE/AGENT.md

THEN READ:
- the approved subscription change plan
- all SUBSCRIPTION/*.md task artifacts
- SUBSCRIPTION/reference/kotlin-samples/*

TASK:
Implement the smallest safe end-to-end subscription slice for XCPro using the approved plan.

IMPLEMENTATION BOUNDARY:
- runtime entitlements for Free / Basic / Soaring / XC / Pro
- billing / catalog infrastructure
- backend-authoritative entitlement sync
- paywall / upgrade entry point
- reusable feature access policy
- root state wiring
- a small representative set of gated features
- SkySight provider-linked access state needed for dual-gated features
- tests and docs needed for this slice

LOCKED ACCESS RULES:
- Free:
  - airspace
  - home waypoint / direct-to-home only
  - flight mode screen selection
  - Essentials cards only
- Basic:
  - Distance Circles
  - ADS-B
  - RainViewer
  - WeGlide
  - SkySight basic/free surfaces
- Soaring:
  - Add / create / edit Task
  - OGN
  - SkySight credential entry / account linking
  - SkySight premium surfaces only when linked paid SkySight state is validated
- Pro:
  - Scia
  - Hotspots
- If Hotspots is SkySight-backed, require both Pro tier and linked paid SkySight state
- XC-specific bundle must follow the approved plan only

NON-NEGOTIABLES:
- Backend remains authoritative
- No UI-only premium unlocks
- No BillingClient calls in composables
- No scattered ad hoc tier checks
- No scattered ad hoc provider-state checks
- No scope widening into unrelated refactors
- No dead shims, no TODOs, no placeholder production logic
- Keep file ownership narrow and explicit
- Split files if they become mixed-responsibility
- Preserve dependency direction and repo architecture
- Do not let the billing module absorb unrelated SkySight integration ownership

REQUIRED WORKFLOW:
1. Restate file ownership before editing
2. Implement phase-by-phase
3. Add or update tests as you go
4. Run required verification:
   - ./gradlew enforceRules
   - ./gradlew testDebugUnitTest
   - ./gradlew assembleDebug
5. Update docs if architecture or flow changed
6. Perform a self-audit against SUBSCRIPTION/09_DRIFT_GUARDRAILS.md before finishing

REQUIRED FINAL OUTPUT:
1. Summary of changes
2. Files modified / added
3. File ownership summary
4. How the implementation matches the approved plan
5. Tests run and results
6. Verification commands run and results
7. Any risks or assumptions
8. Manual verification steps
9. Quality rescore with evidence

DO NOT:
- perform broad cleanup unrelated to subscriptions
- rename products or tiers
- hardcode price strings
- leave edge cases unhandled in production paths
- treat linked paid SkySight as if it were an XCPro purchase
```
