# Codex Brief - Implementation

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
- all docs/SUBSCRIPTIONS/*.md task artifacts

OPTIONAL REFERENCE ONLY:
- existing templates / samples if they exist and do not conflict with the Markdown contract files

AUTHORITY RULE:
If any old non-Markdown template or sample conflicts with the approved plan or docs/SUBSCRIPTIONS/*.md files, the Markdown files are authoritative and the stale template/sample must be ignored.

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
  - SkySight free/public overlays only
- Soaring:
  - Add / create / edit Task
  - OGN
  - SkySight credential entry / account linking
  - SkySight premium/full features only when linked paid SkySight state is validated
- XC:
  - IGC replay
  - LiveFollow view / watch
  - premium exports / advanced sharing
  - PureTrack Traffic API fetch when XCPro app-key/config and PureTrack Pro user access are valid
  - PureTrack Insert API live point publishing when PureTrack Insert API configuration is valid
- Pro:
  - LiveFollow broadcast / share
  - Scia
  - Hotspots
  - advanced vario tuning / premium audio profiles
- If Hotspots is SkySight-backed, require both Pro tier and linked paid SkySight state
- Free and Basic must not expose SkySight credential entry / account linking.

LOCKED IDENTITY RULES:
- every XCPro user must have an XCPro account
- signed-out state is not Free
- normal app usage requires signed-in XCPro identity
- purchase flow requires signed-in XCPro identity
- use obfuscated XCPro account identity in the billing flow
- do not silently attach a purchase to the wrong XCPro account

LOCKED PLAN-CHANGE RULES:
- same-tier monthly <-> annual:
  - same subscription product
  - default replacement behavior: WITHOUT_PRORATION
- cross-tier upgrade:
  - different subscription product
  - replacement behavior: CHARGE_PRORATED_PRICE
- cross-tier downgrade:
  - different subscription product
  - replacement behavior: DEFERRED

NON-NEGOTIABLES:
- Backend remains authoritative
- No UI-only premium unlocks
- No BillingClient calls in composables
- No scattered ad hoc tier checks
- No scattered ad hoc provider-state checks
- No anonymous fallback mode
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
6. Perform a self-audit against docs/SUBSCRIPTIONS/09_DRIFT_GUARDRAILS.md before finishing

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
- bypass the account-required rule to make billing easier
```
