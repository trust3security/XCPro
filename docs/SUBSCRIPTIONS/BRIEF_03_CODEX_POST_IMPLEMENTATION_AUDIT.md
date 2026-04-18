# Codex Brief - Post-Implementation Drift Audit

Paste this after the implementation is done. Use it as a separate audit pass.

```text
You are performing a post-implementation audit of the XCPro subscription work.

READ FIRST:
1. AGENTS.md
2. docs/ARCHITECTURE/ARCHITECTURE.md
3. docs/ARCHITECTURE/CODING_RULES.md
4. docs/ARCHITECTURE/AGENT.md
5. all docs/SUBSCRIPTIONS/*.md artifacts
6. the approved subscription change plan
7. the actual changed files in the branch

AUTHORITY RULE:
If any old non-Markdown template or sample conflicts with the approved plan or docs/SUBSCRIPTIONS/*.md files, the Markdown files are authoritative and the stale template/sample must be ignored.

AUDIT GOAL:
Confirm there is no architecture drift, no ad hoc monetization logic, and no unnecessary churn.

AUDIT RULES:
- Do not do cosmetic refactors
- Do not widen scope
- Do not invent new behavior
- Only propose or apply the smallest fixes needed to remove real drift or broken behavior
- If you apply any fix, keep it minimal and explain why it was necessary

MANDATORY CHECKS:
1. Exactly one authoritative XCPro entitlement owner exists
2. Backend is still authoritative for XCPro entitlements
3. No client-only unlock path exists in production logic
4. No direct BillingClient calls exist in composables
5. No business logic lives in UI classes
6. No duplicated entitlement state exists across random ViewModels or managers
7. No hardcoded price strings are used where `ProductDetails` should be used
8. Product IDs and base plan IDs remain stable and centralized
9. Feature access is capability-based, not scattered tier-name branching
10. Third-party provider state is not collapsed into `PlanTier`
11. SkySight premium features require both the allowed XCPro tier and linked paid SkySight state
12. No TODOs, temporary shims, or silent fallbacks remain in production paths
13. No unrelated files were changed without reason
14. Tests and verification evidence match the claimed behavior
15. Docs were updated where required
16. File ownership remains narrow and architecture-consistent
17. No anonymous production flow exists
18. Signed-out state is not treated as `FREE`
19. Free users receive canonical entitlement behavior only when signed in
20. Monthly and annual plan paths are both implemented consistently
21. Purchase identity uses obfuscated account linkage and does not silently attach to the wrong XCPro account

REQUIRED OUTPUT:
1. Scope summary
2. Files changed, grouped by responsibility
3. Pass/fail matrix for each mandatory check
4. Exact drift findings with severity: critical / major / minor
5. Any unnecessary churn detected
6. Missing tests or missing docs
7. Minimal remediation plan
8. Go / no-go verdict for merge

IF YOU FIND A REAL PROBLEM:
- Fix only the smallest issue set required for correctness and architecture integrity
- Re-run the minimum relevant verification
- Report exactly what was fixed and why

DO NOT:
- rewrite working code for style
- broaden the patch
- mix audit fixes with unrelated cleanup
```
