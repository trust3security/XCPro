# Codex Brief — Plan Only

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
- SUBSCRIPTION/00_START_HERE.md
- SUBSCRIPTION/01_PRODUCT_AND_ENTITLEMENT_MODEL.md
- SUBSCRIPTION/02_RECOMMENDED_FEATURE_MATRIX.md
- SUBSCRIPTION/03_PLAY_CONSOLE_AND_BILLING_SETUP.md
- SUBSCRIPTION/04_BACKEND_VERIFICATION_AND_ENTITLEMENTS.md
- SUBSCRIPTION/05_ANDROID_CLIENT_ARCHITECTURE.md
- SUBSCRIPTION/06_XCPRO_REPO_TARGETS.md
- SUBSCRIPTION/07_IMPLEMENTATION_PHASES.md
- SUBSCRIPTION/08_ACCEPTANCE_CRITERIA_AND_TEST_GATES.md
- SUBSCRIPTION/09_DRIFT_GUARDRAILS.md
- SUBSCRIPTION/templates/CHANGE_PLAN_subscriptions.md
- SUBSCRIPTION/templates/feature_matrix.csv
- SUBSCRIPTION/templates/product_catalog.json
- SUBSCRIPTION/templates/backend_api_contract.yaml

TASK:
Design the smallest safe implementation plan for adding subscription monetization to XCPro with these tiers:
- Free
- Soar
- XC
- Pro

MANDATORY PRODUCT MODEL:
- Free is the default state and is not a Play subscription product
- Paid tiers are separate subscription products:
  - xcpro_soar
  - xcpro_xc
  - xcpro_pro
- Base plan IDs:
  - monthly
  - annual
- Backend is authoritative for entitlements
- Client-side cache is allowed, client-side authority is not

NON-NEGOTIABLES:
- Preserve MVVM + UDF + SSOT
- Preserve dependency direction: UI -> domain -> data
- No direct BillingClient calls from composables
- No ad hoc premium checks scattered through random screens
- No widening scope into unrelated refactors
- No TODOs in production paths
- No “temporary” client-only entitlement unlocks
- Use the smallest safe slice
- Respect existing repo rules and verification gates

OUTPUT REQUIRED:
1. A filled change plan using the repo’s planning model
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
- Do not ask clarifying questions unless truly blocked by missing information that cannot be inferred from the repo and the SUBSCRIPTION docs
- If ambiguity exists, choose the most architecture-consistent option and document the assumption
- Call out that the current Android applicationId looks temporary and should be finalized before Play setup if that is still true in the repo
```
