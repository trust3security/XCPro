# XCPro Repo Targets

This is the repo-focused targeting guide for the first subscription slice.

## What already exists in XCPro

- modular Gradle setup with `:app`, `:core:*`, and multiple `:feature:*` modules
- Hilt-based DI
- root screen orchestration in `MainActivityScreen`
- root navigation in `AppNavGraph`
- `AppModule` and `TimeModule` under app DI
- strict agent / architecture workflow docs
- strict required verification commands

## Recommended first touched files

### Build / module wiring

- `settings.gradle.kts`
- `gradle/libs.versions.toml`
- `app/build.gradle.kts`

### Dependency injection

- `app/src/main/java/com/example/xcpro/di/AppModule.kt`
- new DI files in `:core:billing` if created

### Root app wiring

- `app/src/main/java/com/example/xcpro/MainActivityScreen.kt`
- `app/src/main/java/com/example/xcpro/AppNavGraph.kt`

### Likely feature / settings entry points

- existing settings / profile area for SkySight credential entry
- task-creation entry points
- map overlays / controls for Distance Circles, ADS-B, OGN, RainViewer, Scia, and Hotspots
- WeGlide entry points

### New code areas

- `core/billing/...`
- existing SkySight integration slice for narrow linked-account state exposure
- `app/.../billing/...` only if a new module is deferred
- `app/.../paywall/...` or `feature/paywall/...`

## Recommended first slice responsibilities

### `MainActivityScreen`
- collect root entitlement state
- collect or receive the narrow provider-linked state needed for access decisions
- trigger initial refresh
- pass entitlement / access state and upgrade callbacks downward

### `AppNavGraph`
- route to upgrade / paywall
- route to provider-link entry points when the plan allows linking but account setup is missing
- apply generic access checks for gated destinations
- do not become the owner of entitlement business rules

### `AppModule`
- wire repositories, adapters, and use cases
- avoid putting business policy into DI modules

## Recommended first representative gates

- `Basic`: Distance Circles
- `Basic`: ADS-B
- `Soaring`: Add / create / edit Task
- `Soaring+`: SkySight credential entry
- dual gate: one premium SkySight-backed surface
- `Pro`: Hotspots or Scia

## Strong recommendation

Do not put the whole subscription system directly into `app` just because it is quicker. Create a dedicated billing core now if the feature is intended to survive.

## Explicit repo caution

The current Android application ID appears temporary. Finalize the real production package name before serious Play Billing setup or product creation.
