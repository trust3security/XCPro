# XCPro Subscription Change Plan - v1 Working Contract

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
- **Every tier requires a signed-in XCPro account, including Free.**
- Signed-out state is **not** equivalent to Free.
- The Google Play account is **not** the XCPro account.

---

## 3. v1 Product and Identity Model

### 3.1 User-facing plan names

- Free
- Basic
- Soaring
- XC
- Pro

### 3.2 Account-required rule

Every XCPro user must have an XCPro account.

For v1, the minimum required identity path is the repo's XCPro email/password auth flow unless the repo already has a stricter existing authenticated identity contract.

Rules:

- Free users are not anonymous.
- Normal app usage requires signed-in XCPro identity.
- Purchases and entitlements attach to the signed-in XCPro account.
- The app must not silently attach a detected Play purchase to the wrong XCPro account.
- Signed-out state is a separate app-auth state, not a plan tier.

### 3.3 Internal Play product IDs

Use stable internal identifiers and do not rename casually after Play setup.

- `xcpro_basic`
- `xcpro_soaring`
- `xcpro_xc`
- `xcpro_pro`

Free is **not** a Play product. Free is the absence of a paid entitlement for a signed-in XCPro account.

### 3.4 Base plan strategy

**Locked for v1:** monthly and annual.

Base-plan IDs:

- `monthly`
- `annual`

### 3.5 Offers

**v1 assumption:** no trials, intro offers, or win-back offers in the first implementation.

Reason:
- reduce lifecycle complexity
- prove the base entitlement flow first

### 3.6 Free semantics

`FREE` means:

- XCPro account is signed in
- no active paid subscription is currently entitled
- canonical entitlement snapshot is still returned by backend
- `billingPeriod = NONE`
- `source = NONE`

---

## 4. v1 Tier Positioning

### Free
Useful baseline XCPro experience for a signed-in user.
Allows core flying use without paid workflow depth.

### Basic
Low-friction entry paid tier.
Adds practical everyday value without introducing the full planning/workflow stack.

### Soaring
First serious soaring workflow tier.
Adds task workflow, OGN, and higher-value connected soaring surfaces.

### XC
Serious cross-country workflow tier above Soaring.
Adds replay, follow/watch, premium export/share, and future advanced XC workflow tools.

### Pro
Full unlock tier.
Owns top-end premium tools, premium data surfaces, costlier integrations, and future flagship features.

---

## 5. Capability Table (v1 Working Contract)

This table is the current implementation contract for planning and the first slice.
It may evolve later, but only through explicit updates.

| Capability | Free | Basic | Soaring | XC | Pro | Notes |
|---|---:|---:|---:|---:|---:|---|
| Airspace | Yes | Yes | Yes | Yes | Yes | Core baseline |
| Home waypoint / direct-to-home | Yes | Yes | Yes | Yes | Yes | Broader waypoint/task workflow remains gated separately |
| Flight mode screen selection | Yes | Yes | Yes | Yes | Yes | Free may select screens |
| Essentials card pack | Yes | Yes | Yes | Yes | Yes | Free limited to Essentials cards |
| Distance circles | No | Yes | Yes | Yes | Yes | Basic value item |
| ADS-B traffic overlay | No | Yes | Yes | Yes | Yes | Basic and above |
| RainViewer | No | Yes | Yes | Yes | Yes | Basic and above |
| WeGlide sync | No | Yes | Yes | Yes | Yes | Basic and above |
| Task add / create / edit | No | No | Yes | Yes | Yes | Soaring and above |
| OGN | No | No | Yes | Yes | Yes | Soaring and above |
| SkySight free/public overlays | Yes | Yes | Yes | Yes | Yes | No credential entry required |
| SkySight credential entry / account linking | No | No | Yes | Yes | Yes | Soaring and above |
| SkySight premium/full features in XCPro | No | No | Yes* | Yes* | Yes* | Requires linked paid SkySight account |
| IGC replay | No | No | No | Yes | Yes | XC differentiator |
| LiveFollow view / watch | No | No | No | Yes | Yes | XC differentiator |
| Premium exports / advanced sharing | No | No | No | Yes | Yes | XC differentiator |
| PureTrack Traffic API fetch | No | No | No | Yes* | Yes* | Requires XCPro app-key/config plus PureTrack Pro user access |
| PureTrack Insert API live point publish | No | No | No | Yes* | Yes* | Requires PureTrack Insert API configuration; sends live tracking points, not route/turnpoint data |
| LiveFollow broadcast / share | No | No | No | No | Yes | Pro-only premium/networked surface |
| Scia | No | No | No | No | Yes | Pro only |
| Hotspots | No | No | No | No | Yes* | Pro only; if provider-backed, still requires provider entitlement |
| Advanced vario tuning / premium audio profiles | No | No | No | No | Yes | Pro-only advanced tools |

### XC definition locked for v1

For v1 planning purposes, **XC** uniquely means:

- Soaring capabilities, plus
- IGC replay
- LiveFollow view/watch
- premium exports / advanced sharing
- PureTrack Traffic API fetch, when XCPro app-key/config and PureTrack Pro user access are valid
- PureTrack Insert API live point publishing, when PureTrack Insert API configuration is valid
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

- Free and Basic may access SkySight free/public overlays exposed in XCPro.
- Free and Basic must not show SkySight credential entry or account-linking actions.
- Soaring, XC, and Pro may enter/link SkySight credentials.
- Premium/full SkySight-backed features require both:
  - XCPro tier = Soaring or above
  - linked SkySight account = paid/validated

### 6.2 PureTrack API dual-gate rule

PureTrack API surfaces are **not** unlocked by XCPro subscription state alone.
Use parallel state lanes:

1. XCPro entitlement tier
2. PureTrack provider access/config state

#### Rules

- PureTrack Traffic API fetch requires:
  - XCPro tier = XC or above
  - XCPro app-key/config present
  - PureTrack user access = Pro
- PureTrack Insert API live point publishing requires:
  - XCPro tier = XC or above
  - PureTrack Insert API configuration present
- PureTrack Insert API live point publishing sends live tracking points into PureTrack, not route or turnpoint data.
- No PureTrack API runtime or subscription code is approved by this documentation update.

### 6.3 Provider-state model

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

- `EntitlementSnapshot`
- `SkySightAccountState`

These are read centrally and consumed downstream by UI/features.

### 7.4 Auth/session ownership

Auth/session is a separate root state lane.

Rules:

- signed-out -> sign-in / create-account flow
- signed-in + no paid subscription -> `FREE`
- signed-in + paid subscription -> paid tier

---

## 8. Plan-change policy

### 8.1 Same-tier monthly <-> annual
- same subscription product
- switch base plans
- default v1 replacement behavior: `WITHOUT_PRORATION`

### 8.2 Cross-tier upgrade
- different subscription product
- default v1 replacement behavior: `CHARGE_PRORATED_PRICE`

### 8.3 Cross-tier downgrade
- different subscription product
- default v1 replacement behavior: `DEFERRED`

### 8.4 Free -> paid
- normal new purchase

### 8.5 Paid -> Free
- occurs by cancellation + end-of-term, expiry, refund, or revoke
- do not model this as a local client-only toggle

---

## 9. Architecture / Module Ownership

### 9.1 New module

Create:

- `:core:billing`

### 9.2 `:core:billing` owns

- `PlanTier`
- `BillingPeriod`
- `AppFeature`
- `EntitlementSnapshot`
- `FeatureAccessPolicy`
- billing catalog constants
- Play Billing adapter/wrapper
- repository interfaces + implementations
- purchase / restore / refresh orchestration
- provider-state policy helpers where appropriate

### 9.3 Existing auth slice owns

- XCPro account sign-in / sign-out
- session state
- root "account required" decision
- account identity display source

### 9.4 App/root layer owns

- startup entitlement refresh trigger
- top-level entitlement observation
- navigation decisions
- upgrade/paywall entry points
- locked-route handling

### 9.5 Feature layers own

- rendering locked/unlocked states for their surfaces
- sending upgrade intents
- no direct billing logic

### 9.6 Backend owns

- Google Play verification
- RTDN processing
- durable entitlement storage
- canonical lifecycle handling

---

## 10. Required Backend Contract

Minimum required backend behaviors:

- sync Google Play purchase token to backend
- fetch canonical entitlements
- ingest RTDN
- expose provider-linked state needed for dual-gated features

Recommended minimum endpoints:

- `POST /api/v1/subscriptions/googleplay/sync`
- `GET /api/v1/subscriptions/entitlements`
- `POST /api/v1/subscriptions/googleplay/rtdn`

Backend responsibilities:

- validate package name
- validate product ID
- validate base plan ID
- validate purchase token ownership/status
- acknowledge correctly through the right flow
- persist purchase + entitlement history
- process renewals/cancel/expiry/refund/grace/account-hold
- maintain token chain / replacement handling where applicable
- return canonical `FREE` entitlement state for signed-in non-paying users

Entitlement ownership must be tied to the **XCPro account identity**, not device-local state.

---

## 11. File Ownership Plan

### 11.1 Build / module wiring

Modify:

- `settings.gradle.kts` - add `:core:billing`
- `gradle/libs.versions.toml` - add Play Billing dependency/version
- `app/build.gradle.kts` - app dependency wiring, applicationId finalization

### 11.2 Android DI / root wiring

Modify:

- `app/src/main/java/.../di/AppModule.kt` - bind billing repo/use cases/adapters
- `app/src/main/java/.../MainActivityScreen.kt` - root auth + entitlement observation + refresh trigger
- `app/src/main/java/.../AppNavGraph.kt` - sign-in route, paywall route, route-level gating hooks

### 11.3 New billing code area

Create under `core/billing/...`:

- `PlanTier.kt`
- `BillingPeriod.kt`
- `AppFeature.kt`
- `EntitlementSnapshot.kt`
- `FeatureAccessPolicy.kt`
- `BillingCatalog.kt`
- `PlayBillingClientAdapter.kt`
- `SubscriptionRepository.kt`
- `ObserveEntitlementsUseCase.kt`
- `RefreshEntitlementsUseCase.kt`
- `PurchaseSubscriptionUseCase.kt`
- `RestorePurchasesUseCase.kt`
- `OpenManageSubscriptionUseCase.kt`
- `BillingViewModel.kt` (if screen-owned and placed in appropriate module)

### 11.4 UI/paywall area

Create in app or a dedicated paywall feature if kept narrow:

- `UpgradeScreen.kt`
- supporting paywall UI models / route
- current-account / current-plan summary surface if not already present

### 11.5 First representative feature gates

Touch only the smallest real surfaces first:

- distance circles surface
- task add/create surface
- account/settings entry point for upgrade/manage/restore
- one premium SkySight-backed surface

Do **not** gate every premium feature in the first implementation patch.

---

## 12. First Implementation Slice

### Included in first slice

- stable models: plans, billing period, features, entitlements
- account-required root gate
- Play Billing product fetch
- purchase launch path
- restore path
- backend verification handoff
- root entitlement observation/refresh
- one upgrade/paywall entry point
- representative feature gates:
  - Distance Circles
  - Task Add/Create
  - one SkySight premium surface
- account/current-plan surface in settings/profile

### Explicitly deferred from first slice

- full rollout of every gated feature in the capability table
- offers / trials / win-back
- large analytics/CRM sophistication
- broad monetization copy experimentation
- wide refactors across unrelated modules

---

## 13. Offline / Cache Policy

- Cached entitlements may be shown for continuity.
- New premium access must not be granted solely from an unverified local assumption.
- Stale-cache behavior must be explicit.
- Fresh install with no verified entitlement must not unlock paid capability from local guesswork.
- Signed-in free users should still receive a canonical `FREE` entitlement snapshot when online.

---

## 14. Application ID / Play Setup Assumption

The current applicationId appears temporary/placeholder and must be finalized before real Play Billing product setup.

### Required precondition before live Play setup

- lock the real production package name
- update the Android project to that package identity
- create the Play Console app using that exact package name
- only then create production subscription products

This is a blocker for real Play Billing rollout.

---

## 15. Testing / Verification Plan

### Unit tests

- tier -> capability mapping
- SkySight dual-gate policy
- free-signed-in entitlement mapping
- entitlement refresh / merge logic
- stale cache behavior
- upgrade/downgrade transitions
- monthly/annual transitions

### ViewModel / orchestration tests

- paywall rendering
- purchase in-flight/loading/error states
- restore flow state
- app-start refresh behavior
- signed-out -> sign-in routing behavior if handled in ViewModel/state holder

### Integration / device tests where relevant

- launch purchase flow
- app background/return during purchase
- process death / restart with cached entitlement
- restore on clean install / second device
- wrong-account restore / account mismatch handling
- monthly <-> annual switching

### Required repo verification

- `./gradlew enforceRules`
- `./gradlew testDebugUnitTest`
- `./gradlew assembleDebug`

---

## 16. Rollout Plan

1. Internal test only
2. License testers enabled
3. Validate sign-in / purchase / restore / monthly-annual switch / expiry / downgrade / refund behavior
4. Validate support playbook
5. Release gradually
6. Expand lifecycle complexity (offers/trials) only after the base subscription flow is proven

---

## 17. Risks / Blockers / Assumptions

### Current blockers

- production applicationId not yet finalized
- backend entitlement authority not yet implemented
- Play Billing module not yet present
- some repo auth details may still need hardening

### Managed assumptions for v1

- monthly and annual base plans both exist from day one
- no trial/offer complexity in first release
- XC defined primarily by replay/follow/export XC workflow value
- Hotspots may need provider-linked premium validation depending on data source

### Drift risks to actively avoid

- duplicated entitlement logic in multiple ViewModels
- direct BillingClient usage in UI
- scattered tier-name checks
- local-only unlock paths
- treating signed-out state as Free
- changing product meaning mid-implementation without updating docs/tests

---

## 18. Definition of Done for the First Subscription Patch

The first subscription patch is done only when:

- backend-authoritative entitlement flow works end-to-end
- account-required root flow is enforced
- purchase and restore are working in internal testing
- monthly and annual paths both exist and are tested
- Distance Circles and Task Add/Create are gated through reusable policy seams
- pricing shown in UI comes from store product details
- docs are updated to match actual behavior
- repo verification gates pass
- post-implementation audit finds no architecture drift or ad hoc monetization logic

---

## 19. Immediate Next Actions

1. Lock this v1 change plan in the repo.
2. Finalize the production applicationId.
3. Update subscription docs/briefs to match this 5-tier, monthly+annual, account-required model.
4. Implement backend entitlement authority.
5. Add `:core:billing`.
6. Build the smallest safe slice.
7. Run verification + post-implementation audit.

---

## 20. Authority rule

If any old non-Markdown template, sample, JSON, CSV, or YAML file conflicts with this working contract, **this Markdown working contract wins**.
