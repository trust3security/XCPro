# XCPro Subscription Change Plan — v1 Working Contract

## Status

This is a **v1 working contract**, not a forever-frozen business model.
It is stable enough to plan, implement, test, and audit the first safe subscription slice.
Future rebalancing is allowed, but only through explicit doc + code + test updates.

---

## 1. Goal

Introduce runtime subscriptions into XCPro using a single entitlement system for these tiers:

- Free
- Basic
- Soaring
- XC
- Pro

The app remains a **single Android app** with **runtime entitlement gating**.
There will be **no separate APKs/flavors** for different plans.

The first implementation must be:

- backend-authoritative
- Play Billing based
- architecture-consistent with XCPro
- narrow in scope
- low churn
- free of ad hoc premium checks scattered through UI

---

## 2. Non-negotiables

- Preserve MVVM + UDF + SSOT.
- Preserve dependency direction: UI -> domain -> data.
- Backend is authoritative for paid entitlements.
- Client cache is allowed; client authority is not.
- No direct `BillingClient` calls in composables.
- No random `if (tier == PRO)` checks across screens.
- No hardcoded price strings in production logic.
- No unrelated cleanup/refactors bundled into the first subscription patch.
- No TODO-based production paths for restore/expiry/revoke handling.

---

## 3. v1 Product Model

### 3.1 User-facing plan names

- Free
- Basic
- Soaring
- XC
- Pro

### 3.2 Internal Play product IDs

Use stable internal identifiers and do not rename casually after Play setup.

- `xcpro_basic`
- `xcpro_soaring`
- `xcpro_xc`
- `xcpro_pro`

Free is **not** a Play product. Free is the absence of a paid entitlement.

### 3.3 Base plan strategy

**Assumption for v1:** monthly only.

Reason:
- smallest safe commercial slice
- lowest operational complexity
- simplest testing matrix for first rollout

Reserve these base-plan names for future use if/when annual is added:

- `monthly`
- `annual`

### 3.4 Offers

**v1 assumption:** no trials, intro offers, or win-back offers in the first implementation.

Reason:
- reduce lifecycle complexity
- prove the base entitlement flow first

---

## 4. v1 Tier Positioning

### Free
Useful baseline XCPro experience.
Allows core flying use without paid workflow depth.

### Basic
Low-friction entry paid tier.
Adds practical everyday value without introducing the full planning/workflow stack.

### Soaring
First serious soaring workflow tier.
Adds task workflow and higher-value connected soaring surfaces.

### XC
Serious cross-country workflow tier above Soaring.
Adds replay, follow/watch, premium export/share, and future advanced XC workflow tools.
This is the first tier explicitly positioned for committed XC pilots rather than general soaring users.

### Pro
Full unlock tier.
Owns top-end premium tools, premium data surfaces, costlier integrations, and future flagship features.

---

## 5. Capability Table (v1 Working Contract)

This table is the current implementation contract for planning and the first slice.
It may evolve later, but only through explicit updates.

| Capability | Free | Basic | Soaring | XC | Pro | Notes |
|---|---:|---:|---:|---:|---:|---|
| Airspace | ✅ | ✅ | ✅ | ✅ | ✅ | Core baseline |
| Home waypoint / direct-to-home | ✅ | ✅ | ✅ | ✅ | ✅ | Broader waypoint/task workflow remains gated separately |
| Flight mode screen selection | ✅ | ✅ | ✅ | ✅ | ✅ | Free may select screens |
| Essentials card pack | ✅ | ✅ | ✅ | ✅ | ✅ | Free limited to Essentials cards |
| Distance circles | ❌ | ✅ | ✅ | ✅ | ✅ | Basic value item |
| ADS-B traffic overlay | ❌ | ✅ | ✅ | ✅ | ✅ | Basic and above |
| RainViewer | ❌ | ✅ | ✅ | ✅ | ✅ | Basic and above |
| WeGlide sync | ❌ | ✅ | ✅ | ✅ | ✅ | Basic and above |
| Task add / create / edit | ❌ | ❌ | ✅ | ✅ | ✅ | Soaring and above |
| OGN | ❌ | ❌ | ✅ | ✅ | ✅ | Soaring and above |
| SkySight basic/free products | ✅ | ✅ | ✅ | ✅ | ✅ | Free/public/basic SkySight surfaces only |
| SkySight credential entry / account linking | ❌ | ❌ | ✅ | ✅ | ✅ | Soaring and above |
| SkySight premium products in XCPro | ❌ | ❌ | ✅* | ✅* | ✅* | Requires linked paid SkySight account |
| IGC replay | ❌ | ❌ | ❌ | ✅ | ✅ | XC differentiator |
| LiveFollow view / watch | ❌ | ❌ | ❌ | ✅ | ✅ | XC differentiator |
| Premium exports / advanced sharing | ❌ | ❌ | ❌ | ✅ | ✅ | XC differentiator |
| LiveFollow broadcast / share | ❌ | ❌ | ❌ | ❌ | ✅ | Pro-only premium/networked surface |
| Scia | ❌ | ❌ | ❌ | ❌ | ✅ | Pro only |
| Hotspots | ❌ | ❌ | ❌ | ❌ | ✅* | Pro only; if provider-backed, still requires provider entitlement |
| Advanced vario tuning / premium audio profiles | ❌ | ❌ | ❌ | ❌ | ✅ | Pro-only advanced tools |

### XC definition locked for v1

For v1 planning purposes, **XC** uniquely means:

- Soaring capabilities, plus
- IGC replay
- LiveFollow view/watch
- premium exports / advanced sharing
- future reserved slot for advanced XC workflow tools

That is enough to make XC a real product tier and avoid it becoming a vague middle plan.

---

## 6. External Provider Rules

### 6.1 SkySight dual-gate rule

SkySight is **not** bundled blindly as an XCPro premium entitlement.
Use two parallel state lanes:

1. XCPro entitlement tier
2. SkySight account state

#### Rules

- Free and Basic may access SkySight free/basic surfaces exposed in XCPro.
- Soaring and above may enter/link SkySight credentials.
- Premium SkySight-backed features require both:
  - XCPro tier = Soaring or above
  - linked SkySight account = paid/validated

### 6.2 Provider-state model

Add a separate provider account state instead of overloading `PlanTier`.

Suggested state model:

- `UNLINKED`
- `LINKED_FREE`
- `LINKED_PAID`
- `LINK_ERROR`
- `UNKNOWN`

---

## 7. Entitlement Authority / SSOT Ownership

### 7.1 Backend

Backend is the only durable authority for:

- verified subscription state
- entitlement grant/revoke decisions
- renewals
- cancellations
- expiries
- refunds/revocations
- grace/account-hold handling
- audit trail

### 7.2 Android client

Android client is responsible for:

- Play purchase launch
- purchase token handoff to backend
- local cache for degraded/offline continuity
- rendering the latest known entitlement state
- routing to paywall / upgrade / locked-state UI

The Android client is **not** the durable authority.

### 7.3 Root app SSOT

There must be one authoritative app read model for entitlements.

Suggested canonical model:

- `EntitlementState`
- `SkySightAccountState`

These are read centrally and consumed downstream by UI/features.

---

## 8. Architecture / Module Ownership

## 8.1 New module

Create:

- `:core:billing`

### `:core:billing` owns

- `PlanTier`
- `AppFeature`
- `EntitlementState`
- `FeatureAccessPolicy`
- billing catalog constants
- Play Billing adapter/wrapper
- repository interfaces + implementations
- purchase / restore / refresh orchestration
- provider-state policy helpers where appropriate

### App/root layer owns

- startup entitlement refresh trigger
- top-level entitlement observation
- navigation decisions
- upgrade/paywall entry points
- locked-route handling

### Feature layers own

- rendering locked/unlocked states for their surfaces
- sending upgrade intents
- no direct billing logic

### Backend owns

- Google Play verification
- RTDN processing
- durable entitlement storage
- canonical lifecycle handling

---

## 9. Required Backend Contract

Minimum required backend endpoints:

- `POST /billing/google/verify-subscription`
- `GET /me/entitlements`
- `POST /billing/google/rtdn`

Backend responsibilities:

- validate package name
- validate product ID
- validate purchase token ownership/status
- acknowledge correctly through the right flow
- persist purchase + entitlement history
- process renewals/cancel/expiry/refund/grace/account-hold
- maintain token chain / replacement handling where applicable

Entitlement ownership must be tied to the **XC account identity**, not device-local state.

---

## 10. File Ownership Plan

### 10.1 Build / module wiring

Modify:

- `settings.gradle.kts` — add `:core:billing`
- `gradle/libs.versions.toml` — add Play Billing dependency/version
- `app/build.gradle.kts` — app dependency wiring, applicationId finalization

### 10.2 Android DI / root wiring

Modify:

- `app/src/main/java/com/example/xcpro/di/AppModule.kt` — bind billing repo/use cases/adapters
- `app/src/main/java/com/example/xcpro/MainActivityScreen.kt` — root entitlement observation + refresh trigger
- `app/src/main/java/com/example/xcpro/AppNavGraph.kt` — paywall route + route-level gating hooks

### 10.3 New billing code area

Create under `core/billing/...`:

- `PlanTier.kt`
- `AppFeature.kt`
- `EntitlementState.kt`
- `FeatureAccessPolicy.kt`
- `BillingCatalog.kt`
- `PlayBillingClientAdapter.kt`
- `SubscriptionRepository.kt`
- `ObserveEntitlementsUseCase.kt`
- `RefreshEntitlementsUseCase.kt`
- `PurchaseSubscriptionUseCase.kt`
- `RestorePurchasesUseCase.kt`
- `BillingViewModel.kt` (if screen-owned and placed in appropriate module)

### 10.4 UI/paywall area

Create in app or a dedicated paywall feature if kept narrow:

- `UpgradeScreen.kt`
- supporting paywall UI models / route

### 10.5 First representative feature gates

Touch only the smallest real surfaces first:

- distance circles surface
- task add/create surface
- account/settings entry point for upgrade/manage/restore

Do **not** gate every premium feature in the first implementation patch.

---

## 11. First Implementation Slice

### Included in first slice

- stable models: plans, features, entitlements
- Play Billing product fetch
- purchase launch path
- restore path
- backend verification handoff
- root entitlement observation/refresh
- one upgrade/paywall entry point
- representative feature gates:
  - Distance Circles
  - Task Add/Create

### Explicitly deferred from first slice

- full rollout of every gated feature in the capability table
- annual plans
- offers / trials / win-back
- large analytics/CRM sophistication
- broad monetization copy experimentation
- wide refactors across unrelated modules

---

## 12. Offline / Cache Policy

- Cached entitlements may be shown for continuity.
- New premium access must not be granted solely from an unverified local assumption.
- Stale-cache behavior must be explicit.
- Fresh install with no verified entitlement must not unlock paid capability from local guesswork.

---

## 13. Application ID / Play Setup Assumption

The current applicationId appears temporary/placeholder and must be finalized before real Play Billing product setup.

### Required precondition before live Play setup

- lock the real production package name
- update the Android project to that package identity
- create the Play Console app using that exact package name
- only then create production subscription products

This is a blocker for real Play Billing rollout.

---

## 14. Testing / Verification Plan

### Unit tests

- tier -> capability mapping
- SkySight dual-gate policy
- entitlement refresh / merge logic
- stale cache behavior
- upgrade/downgrade transitions

### ViewModel / orchestration tests

- paywall rendering
- purchase in-flight/loading/error states
- restore flow state
- app-start refresh behavior

### Integration / device tests where relevant

- launch purchase flow
- app background/return during purchase
- process death / restart with cached entitlement
- restore on clean install / second device

### Required repo verification

- `./gradlew enforceRules`
- `./gradlew testDebugUnitTest`
- `./gradlew assembleDebug`

---

## 15. Rollout Plan

1. Internal test only
2. License testers enabled
3. Validate purchase / restore / expiry / downgrade / refund behavior
4. Validate support playbook
5. Release gradually
6. Expand lifecycle complexity (annual/offers) only after the base subscription flow is proven

---

## 16. Risks / Blockers / Assumptions

### Current blockers

- production applicationId not yet finalized
- backend entitlement authority not yet implemented
- Play Billing module not yet present
- some tier definitions may still evolve

### Managed assumptions for v1

- monthly-only subscriptions first
- no trial/offer complexity in first release
- XC defined primarily by replay/follow/export XC workflow value
- Hotspots may need provider-linked premium validation depending on data source

### Drift risks to actively avoid

- duplicated entitlement logic in multiple ViewModels
- direct BillingClient usage in UI
- scattered tier-name checks
- local-only unlock paths
- changing product meaning mid-implementation without updating docs/tests

---

## 17. Definition of Done for the First Subscription Patch

The first subscription patch is done only when:

- backend-authoritative entitlement flow works end-to-end
- purchase and restore are working in internal testing
- Distance Circles and Task Add/Create are gated through reusable policy seams
- pricing shown in UI comes from store product details
- docs are updated to match actual behavior
- repo verification gates pass
- post-implementation audit finds no architecture drift or ad hoc monetization logic

---

## 18. Immediate Next Actions

1. Lock this v1 change plan in the repo.
2. Finalize the production applicationId.
3. Update subscription docs/briefs to match this 5-tier model.
4. Implement backend entitlement authority.
5. Add `:core:billing`.
6. Build the smallest safe slice.
7. Run verification + post-implementation audit.
