# Codex Brief - Plan Only

Paste this into Codex as-is, then adjust only the clearly marked placeholders if needed.

```text
You are working in the XCPro repository.

READ FIRST, IN THIS ORDER:
1. AGENTS.md
2. docs/ARCHITECTURE/PLAN_MODE_START_HERE.md
3. docs/ARCHITECTURE/ARCHITECTURE.md
4. docs/ARCHITECTURE/CODING_RULES.md
5. docs/ARCHITECTURE/PIPELINE_INDEX.md
6. relevant sections of docs/ARCHITECTURE/PIPELINE.md
7. docs/ARCHITECTURE/CODEBASE_CONTEXT_AND_INTENT.md
8. docs/ARCHITECTURE/CONTRIBUTING.md
9. docs/ARCHITECTURE/KNOWN_DEVIATIONS.md
10. docs/ARCHITECTURE/CHANGE_PLAN_TEMPLATE.md
11. docs/ARCHITECTURE/AGENT.md

THEN READ THESE LOCAL TASK ARTIFACTS:
- docs/SUBSCRIPTIONS/README.md
- docs/SUBSCRIPTIONS/00_START_HERE.md
- docs/SUBSCRIPTIONS/01_PRODUCT_AND_ENTITLEMENT_MODEL.md
- docs/SUBSCRIPTIONS/02_RECOMMENDED_FEATURE_MATRIX.md
- docs/SUBSCRIPTIONS/03_PLAY_CONSOLE_AND_BILLING_SETUP.md
- docs/SUBSCRIPTIONS/04_BACKEND_VERIFICATION_AND_ENTITLEMENTS.md
- docs/SUBSCRIPTIONS/05_ANDROID_CLIENT_ARCHITECTURE.md
- docs/SUBSCRIPTIONS/06_XCPRO_REPO_TARGETS.md
- docs/SUBSCRIPTIONS/07_IMPLEMENTATION_PHASES.md
- docs/SUBSCRIPTIONS/08_ACCEPTANCE_CRITERIA_AND_TEST_GATES.md
- docs/SUBSCRIPTIONS/09_DRIFT_GUARDRAILS.md
- docs/SUBSCRIPTIONS/10_ROLLOUT_AND_OPERATIONS.md
- docs/SUBSCRIPTIONS/XCPro_Subscription_Change_Plan_v1.md

OPTIONAL REFERENCE ONLY:
- old templates, JSON, CSV, YAML, and Kotlin samples if they exist

AUTHORITY RULE:
If any old non-Markdown template or sample conflicts with the Markdown files in docs/SUBSCRIPTIONS/, the Markdown files in docs/SUBSCRIPTIONS/ are authoritative and the stale template/sample must be ignored.

TASK:
Design the smallest safe implementation plan for adding subscription monetization to XCPro with these tiers:
- Free
- Basic
- Soaring
- XC
- Pro

MANDATORY PRODUCT MODEL:
- Free is the default entitlement state for a signed-in XCPro account and is not a Play subscription product
- Paid tiers are separate subscription products:
  - xcpro_basic
  - xcpro_soaring
  - xcpro_xc
  - xcpro_pro
- Base plan IDs:
  - monthly
  - annual
- Backend is authoritative for entitlements
- Client-side cache is allowed, client-side authority is not

MANDATORY IDENTITY MODEL:
- every XCPro user must have an XCPro account
- Free users are not anonymous users
- signed-out state is not equivalent to Free
- normal app usage requires signed-in XCPro identity
- v1 account identity path is XCPro email/password unless the repo already has a stricter existing auth contract
- Google Play account is not the XCPro account
- purchases and entitlements attach to the signed-in XCPro account

LOCKED CAPABILITY DECISIONS:
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
  - SkySight premium/full features only when a linked paid SkySight account is validated
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
- If Hotspots is ultimately SkySight-backed, require both:
  - plan >= PRO
  - linked paid SkySight account
- Free and Basic must not expose SkySight credential entry / account linking.

NON-NEGOTIABLES:
- Preserve MVVM + UDF + SSOT
- Preserve dependency direction: UI -> domain -> data
- No direct BillingClient calls from composables
- No ad hoc premium checks scattered through random screens
- No ad hoc anonymous mode or signed-out fallback
- No widening scope into unrelated refactors
- No TODOs in production paths
- No "temporary" client-only entitlement unlocks
- Do not collapse SkySight provider state into PlanTier
- Use the smallest safe slice
- Respect existing repo rules and verification gates

OUTPUT REQUIRED:
1. A filled change plan using the repo's planning model
2. Explicit SSOT ownership for every new or changed state item
3. A file ownership plan listing each file to create or modify and why it owns the work
4. A proposed module/file layout for the first slice
5. A recommended first gated feature slice
6. A backend API and lifecycle handling summary
7. A test and verification plan
8. Docs that must be updated
9. Risks, blockers, and assumptions

IMPORTANT:
- Do plan only
- Do not write production code yet
- Do not ask clarifying questions unless truly blocked by missing information that cannot be inferred from the repo and the SUBSCRIPTIONS docs
- If ambiguity exists, choose the most architecture-consistent option and document the assumption
- Call out that the current Android applicationId looks temporary and should be finalized before Play setup if that is still true in the repo
- Treat docs/SUBSCRIPTIONS/XCPro_Subscription_Change_Plan_v1.md as the working contract
- If any repo code conflicts with the working contract, call it out as implementation risk rather than silently drifting from the contract
```
