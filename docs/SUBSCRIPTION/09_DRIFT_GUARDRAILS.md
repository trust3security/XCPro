# Drift Guardrails

This is the anti-chaos checklist.

## Architectural drift red flags

- entitlement logic duplicated in multiple ViewModels
- direct `BillingClient` use inside UI
- business logic added to composables
- random `isPro` or `premiumEnabled` booleans spreading through the codebase
- DataStore or SharedPreferences becoming the durable purchase authority
- backend verification skipped “for now”
- paywall copy treated as business logic
- product IDs or tier labels hardcoded in too many places
- unrelated refactors bundled into the same feature branch

## Ad hoc logic red flags

- a quick `when(tier)` check inserted in 10 unrelated screens
- feature access inferred from screen names rather than capability IDs
- placeholder products or fake server responses left in production paths
- TODOs for expiry/revoke/restore flows
- debug-only shortcuts accidentally available in release
- one-off compatibility shims with no removal plan

## Churn red flags

- renaming unrelated packages or classes without functional need
- moving modules before the first subscription slice works
- changing paywall, backend, analytics, and every premium screen in one first patch
- changing copy, architecture, and business rules in a single commit
- touching files only for style while implementing billing

## Required self-audit before merge

- Is there exactly one authoritative entitlement owner?
- Is every premium capability enforced through a reusable policy seam?
- Are UI classes only rendering and sending intents?
- Did we avoid hardcoded price strings?
- Did we avoid widening scope?
- Are all new files responsibility-focused?
- Are docs synced with the implementation?
- Is there any dead or temporary path left behind?
