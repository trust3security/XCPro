# Product and Entitlement Model

## User-facing plan names

- Free
- Basic
- Soaring
- XC
- Pro

## Stable internal identifiers

Use permanent identifiers for billing and entitlement logic. Do not rename these later unless migration is unavoidable.

### Google Play subscription product IDs

- `xcpro_basic`
- `xcpro_soaring`
- `xcpro_xc`
- `xcpro_pro`

### Base plan IDs

- `monthly`
- `annual`

### Offer IDs

For v1 implementation, do **not** add trial or introductory offers.
If offers are added later, they must remain store-side offers under the same subscription product and base-plan model.

Free is **not** a Play subscription product. Free is simply the absence of an active paid subscription for a signed-in XCPro account.

## Account and identity contract

- Every user must have an XCPro account.
- Free users are **not** anonymous users.
- The required app identity for v1 is the XCPro account, using the repo's email/password sign-in path unless the repo already has a stricter existing auth contract.
- The Google Play account is **not** the XCPro account.
- Purchases, entitlements, restore behavior, and support flows attach to the signed-in XCPro account.
- If a Play purchase is detected while the wrong XCPro account is signed in, the app must not silently grant access to that wrong account.

## Commercial notes

- Basic is the low-friction entry tier and currently targets about **USD 5.99** in Play Console.
- That price is an operations setting only. Do **not** hardcode it in app logic or copy-generation code.
- Regional pricing, tax, and experiments remain store-side concerns.

## Recommended Kotlin model

```kotlin
enum class PlanTier {
    FREE,
    BASIC,
    SOARING,
    XC,
    PRO
}

enum class BillingPeriod {
    NONE,
    MONTHLY,
    ANNUAL
}

enum class SubscriptionStatus {
    NONE,
    FREE_ACTIVE,
    PENDING,
    ACTIVE,
    GRACE_PERIOD,
    ON_HOLD,
    CANCELED_BUT_ACTIVE,
    EXPIRED,
    REVOKED
}

enum class EntitlementSource {
    NONE,
    GOOGLE_PLAY,
    UNKNOWN
}

enum class AppFeature {
    AIRSPACE,
    WAYPOINT_HOME_ONLY,
    FLIGHT_MODE_SCREEN_SELECTION,
    CARD_PACK_ESSENTIALS,
    DISTANCE_CIRCLES,
    TRAFFIC_ADSB,
    SKYSIGHT_BASIC,
    SKYSIGHT_LINK_ACCOUNT,
    SKYSIGHT_PREMIUM,
    RAINVIEWER,
    WEGLIDE_SYNC,
    TASKS_CREATE,
    TRAFFIC_OGN,
    PURETRACK_TRAFFIC_FETCH,
    PURETRACK_LIVE_PUBLISH,
    IGC_REPLAY,
    LIVEFOLLOW_VIEW,
    PREMIUM_EXPORTS,
    LIVEFOLLOW_BROADCAST,
    SCIA,
    HOTSPOTS,
    ADVANCED_VARIO_AUDIO
}

enum class SkySightAccountState {
    UNLINKED,
    LINKED_FREE,
    LINKED_PAID,
    LINK_ERROR,
    UNKNOWN,
}

data class EntitlementSnapshot(
    val currentPlan: PlanTier = PlanTier.FREE,
    val billingPeriod: BillingPeriod = BillingPeriod.NONE,
    val activeEntitlements: Set<AppFeature> = emptySet(),
    val status: SubscriptionStatus = SubscriptionStatus.FREE_ACTIVE,
    val source: EntitlementSource = EntitlementSource.NONE,
    val validUntilMs: Long? = null,
    val fetchedAtMs: Long? = null,
    val isAutoRenewing: Boolean = false,
    val isAccountRequired: Boolean = true,
)
```

## Source of truth

- Backend verification result for XCPro subscriptions is authoritative.
- Client-side cache is a convenience and degraded-mode aid only.
- UI reads a single entitlement snapshot for XCPro plan access.
- Third-party provider state, such as SkySight account status or PureTrack access/config state, is a **separate** authoritative state and must not be collapsed into `PlanTier`.
- Feature access checks map from XCPro plan entitlements **and**, when relevant, linked provider state to capabilities.
- Prices, copy, and offer display are separate from entitlement enforcement.

## Locked access rules

- Free includes:
  - airspace
  - home waypoint only / direct-to-home only
  - flight mode screen selection
  - Essentials card pack
- Basic adds:
  - Distance Circles
  - ADS-B
  - RainViewer
  - WeGlide
  - SkySight free/public overlays only
- Soaring adds:
  - Add / create / edit Task
  - OGN
  - SkySight credential entry / account linking
  - SkySight premium/full features, but only when the linked SkySight account validates as paid
- XC adds:
  - IGC replay
  - LiveFollow view/watch
  - premium exports / advanced sharing
  - PureTrack Traffic API fetch, when XCPro app-key/config and PureTrack Pro user access are valid
  - PureTrack Insert API live point publishing, when PureTrack Insert API configuration is valid
- Pro adds:
  - LiveFollow broadcast / share
  - Scia
  - Hotspots
  - advanced vario tuning / premium audio profiles
- If Hotspots is ultimately sourced from premium SkySight data, the effective rule is:
  - `plan >= PRO`
  - and `SkySightAccountState == LINKED_PAID`
- SkySight access rules are:
  - Free and Basic may see SkySight free/public overlays exposed in XCPro.
  - Free and Basic must not show SkySight credential entry or account-linking actions.
  - Soaring, XC, and Pro may enter/link SkySight credentials.
  - Premium SkySight-backed features require both `plan >= SOARING` and `SkySightAccountState == LINKED_PAID`.
- PureTrack Traffic API access is dual-gated:
  - `plan >= XC`
  - XCPro app-key/config is present
  - PureTrack user access is Pro
- PureTrack Insert API live publishing is dual-gated:
  - `plan >= XC`
  - PureTrack Insert API configuration is present
  - the capability sends live tracking points into PureTrack and is not route or turnpoint publishing

## Plan change policy for v1

- Free -> paid: new purchase
- Paid -> Free: cancellation reaching end of term, expiry, refund, or revoke
- Same-tier `monthly <-> annual`:
  - same subscription product
  - base-plan switch
  - default v1 behavior: `WITHOUT_PRORATION` unless business rules later change
- Cross-tier upgrade:
  - different subscription product
  - default v1 behavior: `CHARGE_PRORATED_PRICE`
- Cross-tier downgrade:
  - different subscription product
  - default v1 behavior: `DEFERRED`

## Guardrails

- Never use marketing copy as logic keys.
- Never use display names as database keys.
- Never use `isProUnlocked`-style booleans as the primary model.
- Never treat linked SkySight paid status as if it were an XCPro subscription purchase.
- Never infer "Free" from signed-out state.
- Use capabilities/features for enforcement, tier names for display and top-level packaging only.
- Keep XCPro subscription state and third-party provider state separate, then compose them centrally in the access policy.
- If any old non-Markdown template conflicts with this file, this file wins.
