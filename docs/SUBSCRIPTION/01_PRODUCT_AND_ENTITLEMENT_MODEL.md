# Product and Entitlement Model

## User-facing plan names

- Free
- Soar
- XC
- Pro

## Stable internal identifiers

Use permanent identifiers for billing and entitlement logic. Do not rename these later unless migration is unavoidable.

### Recommended Google Play subscription product IDs

- `xcpro_soar`
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

## Kotlin model

```kotlin
enum class PlanTier {
    FREE,
    SOAR,
    XC,
    PRO
}

enum class AppFeature {
    MAP_CORE,
    PROFILES,
    TASKS_BASIC,
    WEATHER_BASIC,
    FORECAST_BASIC,
    IGC_REPLAY,
    LIVEFOLLOW_VIEW,
    LIVEFOLLOW_BROADCAST,
    TRAFFIC_ADSB,
    VARIO_ADVANCED,
    WEGLIDE_SYNC,
    EXPORT_PREMIUM,
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

- Backend verification result is authoritative
- Client-side cache is a convenience and degraded-mode aid only
- UI reads a single entitlement state
- Feature access checks map from entitlements to capabilities
- Prices, copy, and offer display are separate from entitlement enforcement

## Upgrade and downgrade policy

- Soar -> XC -> Pro: upgrade path
- Pro -> XC -> Soar: downgrade path
- Free -> paid: new purchase
- Paid -> Free: expiry, refund, chargeback, or cancellation reaching end of term

## Guardrails

- Never use marketing copy as logic keys
- Never use display names as database keys
- Never use “pro unlocked” booleans as the primary model
- Use capabilities/features for enforcement, tier names for display and top-level packageing only
