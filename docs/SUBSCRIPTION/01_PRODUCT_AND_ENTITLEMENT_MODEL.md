# Product and Entitlement Model

## User-facing plan names

- Free
- Basic
- Soaring
- XC
- Pro

## Stable internal identifiers

Use permanent identifiers for billing and entitlement logic. Do not rename these later unless migration is unavoidable.

### Recommended Google Play subscription product IDs

- `xcpro_basic`
- `xcpro_soaring`
- `xcpro_xc`
- `xcpro_pro`

### Recommended base plan IDs

- `monthly`
- `annual`

### Optional offer IDs

- `trial7d`
- `intro_50pct_3m`
- `winback_20pct_3m`

Free is **not** a Play subscription product. Free is simply the absence of a paid entitlement.

## Commercial note

- Basic is the low-friction entry tier and currently targets about **USD 5.99** in Play Console.
- That price is an operations setting only. Do **not** hardcode it in app logic or copy-generation code.
- Regional pricing, tax, and experiments remain store-side concerns.

## Kotlin model

```kotlin
enum class PlanTier {
    FREE,
    BASIC,
    SOARING,
    XC,
    PRO
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
    SCIA,
    HOTSPOTS,
}

enum class SkySightAccountState {
    UNLINKED,
    LINKED_FREE,
    LINKED_PAID,
    LINK_ERROR,
    UNKNOWN,
}
```

## Entitlement state contract

```kotlin
data class EntitlementState(
    val tier: PlanTier = PlanTier.FREE,
    val features: Set<AppFeature> = emptySet(),
    val source: EntitlementSource = EntitlementSource.UNKNOWN,
    val expiresAtMs: Long? = null,
    val fetchedAtMs: Long? = null,
    val isLoading: Boolean = true,
)
```

## Source of truth

- Backend verification result for XCPro subscriptions is authoritative.
- Client-side cache is a convenience and degraded-mode aid only.
- UI reads a single entitlement state for XCPro plan access.
- Third-party provider state, such as SkySight account status, is a **separate** authoritative state and must not be collapsed into `PlanTier`.
- Feature access checks map from XCPro plan entitlements **and**, when relevant, linked provider state to capabilities.
- Prices, copy, and offer display are separate from entitlement enforcement.

## Locked access rules so far

- Free includes airspace, home waypoint only, flight mode screen selection, and Essentials cards.
- Basic adds:
  - Distance Circles
  - ADS-B
  - RainViewer
  - WeGlide
  - SkySight basic/free surfaces
- Soaring adds:
  - Add / create / edit Task
  - OGN
  - SkySight credential entry / account linking
  - SkySight premium surfaces, but only when the linked SkySight account validates as paid
- Pro adds:
  - Scia
  - Hotspots
- If Hotspots is ultimately sourced from premium SkySight data, the effective rule is:
  - `plan >= PRO`
  - and `SkySightAccountState == LINKED_PAID`

## Upgrade and downgrade policy

- Free -> Basic -> Soaring -> XC -> Pro: upgrade path
- Pro -> XC -> Soaring -> Basic -> Free: downgrade / expiry path
- Free -> paid: new purchase
- Paid -> Free: expiry, refund, chargeback, or cancellation reaching end of term

## Guardrails

- Never use marketing copy as logic keys.
- Never use display names as database keys.
- Never use `isProUnlocked`-style booleans as the primary model.
- Never treat linked SkySight paid status as if it were an XCPro subscription purchase.
- Use capabilities/features for enforcement, tier names for display and top-level packaging only.
- Keep XCPro subscription state and third-party provider state separate, then compose them centrally in the access policy.
