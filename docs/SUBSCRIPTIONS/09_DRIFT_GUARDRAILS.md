# Drift Guardrails

This is the anti-chaos checklist.

## Architectural drift red flags

- entitlement logic duplicated in multiple ViewModels
- direct `BillingClient` use inside UI
- business logic added to composables
- random `isPro` or `premiumEnabled` booleans spreading through the codebase
- DataStore or SharedPreferences becoming the durable purchase authority
- backend verification skipped "for now"
- paywall copy treated as business logic
- product IDs or tier labels hardcoded in too many places
- SkySight provider state collapsed into `PlanTier`
- unrelated refactors bundled into the same feature branch
- signed-out state silently treated as `FREE`
- guest mode reintroduced "temporarily"
- cleartext email used as the Play account identifier
- wrong-account purchase attach handled implicitly instead of explicitly

## Ad hoc logic red flags

- a quick `when(tier)` check inserted in 10 unrelated screens
- provider-gated access checks duplicated across random screens instead of using the central access policy
- feature access inferred from screen names rather than capability IDs
- placeholder products or fake server responses left in production paths
- TODOs for expiry / revoke / restore / provider-link failure flows
- debug-only shortcuts accidentally available in release
- one-off compatibility shims with no removal plan
- monthly/annual behavior implemented with scattered special cases instead of one subscription-policy seam

## Churn red flags

- renaming unrelated packages or classes without functional need
- moving modules before the first subscription slice works
- changing paywall, backend, analytics, every premium screen, and every integration in one first patch
- changing copy, architecture, and business rules in a single commit
- touching files only for style while implementing billing
- pushing SkySight networking into the billing module without a real ownership reason

## Required self-audit before merge

- Is there exactly one authoritative XCPro entitlement owner?
- Is every premium capability enforced through a reusable policy seam?
- Are provider-linked premium checks also enforced through the same reusable policy seam?
- Are UI classes only rendering and sending intents?
- Did we avoid hardcoded price strings?
- Did we avoid widening scope?
- Are all new files responsibility-focused?
- Are docs synced with the implementation?
- Is there any dead or temporary path left behind?
- Is signed-out state clearly separate from `FREE`?
- Does the app require XCPro account identity for every tier?
- Are monthly and annual handled centrally rather than ad hoc?
- Are non-Markdown templates ignored when they conflict with the Markdown contract files?

## Authority rule

If any old guardrail checklist conflicts with this file, this file wins.
