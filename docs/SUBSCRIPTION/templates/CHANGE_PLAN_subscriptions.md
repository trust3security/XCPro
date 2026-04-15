# Subscription Change Plan - XCPro

## 0) Metadata
- Title: XCPro subscriptions (Free / Basic / Soaring / XC / Pro)
- Owner:
- Date:
- Issue/PR:
- Status: Draft

## 1) Scope
- Problem statement: XCPro needs subscription monetization with clean runtime entitlements and no architecture drift.
- Why now:
- In scope:
  - Play subscription catalog for Basic / Soaring / XC / Pro
  - backend-authoritative entitlements
  - client billing integration
  - paywall / upgrade flow
  - first gated feature slice
- Out of scope:
  - unrelated refactors
  - iOS / non-Android storefronts
  - broad redesign of unrelated features
- User-visible impact:
- Rule class touched: Invariant / Default / Guideline

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| Subscription entitlement state | | | |
| Product catalog | | | |
| Purchase in-flight state | | | |
| Paywall display state | | | |

### 2.1A State Contract

| State Item | Authoritative Owner | Allowed Mutators | Read Path | Derived From | Persistence Owner | Reset/Clear Conditions | Time Base | Required Tests |
|---|---|---|---|---|---|---|---|---|
| EntitlementState | | | | | | | | |
| ProductDetailsState | | | | | | | | |
| PurchaseFlowState | | | | | | | | |

### 2.2 Dependency Direction
- Modules/files touched:
- Any boundary risk:

### 2.2A Reference Pattern Check
| Reference File | Why It Is Similar | Pattern To Reuse | Planned Deviation |
|---|---|---|---|
| MainActivityScreen.kt | root state collection | collect + pass state downward | |
| AppModule.kt | DI wiring | Hilt singleton binding patterns | |

### 2.2B Boundary Moves
| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| Premium capability checks | random UI / none | FeatureAccessPolicy | centralize access rules | tests |

### 2.2C Bypass Removal Plan
| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| | | | |

### 2.2D File Ownership Plan
| File | New / Existing | Owner / Responsibility | Why Here | Why Not Another Layer/File | Split Needed? |
|---|---|---|---|---|---|
| settings.gradle.kts | Existing | module inclusion only | build wiring | not business logic | no |
| gradle/libs.versions.toml | Existing | dependency coordinates | catalog source | not runtime logic | no |
| app/build.gradle.kts | Existing | app dependency wiring | app module wiring | not domain | no |
| app/.../MainActivityScreen.kt | Existing | top-level state collection and callbacks | root entry point | not feature logic owner | maybe |
| app/.../AppNavGraph.kt | Existing | route-level access hooks only | central navigation | not business rules owner | maybe |
| app/.../di/AppModule.kt | Existing | DI bindings only | DI owner | not policy owner | no |
| core/billing/... | New | billing and entitlements core | dedicated ownership | avoid app bloat | no |

### 2.2E Module and API Surface
| Contract / API | Owner | Consumers | Visibility | Why Needed | Compatibility / Removal Plan |
|---|---|---|---|---|---|
| ObserveEntitlementsUseCase | | | | | |
| PurchaseSubscriptionUseCase | | | | | |

### 2.2F Scope Ownership and Lifetime
| Scope / Owner | Why It Exists | Dispatcher | Cancellation Trigger | Why Not Caller-Owned / Existing Scope |
|---|---|---|---|---|
| Billing refresh scope | | | | |

### 2.2H Canonical Formula / Policy Owner
| Formula / Constant / Policy | Canonical Owner File | Reused By | Why This Owner Is Canonical | Temporary Duplicates Allowed? |
|---|---|---|---|---|
| Tier -> feature mapping | | | | no |

### 2.3 Time Base
| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| Entitlement fetch timestamp | Wall | backend freshness / support correlation |
| Local refresh staleness window | Monotonic | elapsed-time logic |

### 2.5 Replay Determinism
- Deterministic for same input:
- Randomness used:
- Replay/live divergence rules:

## 3) Data Flow (Before -> After)

```text
Google Play BillingClient
-> PlayBillingClientAdapter
-> Purchase verification API
-> SubscriptionRepository (authoritative client read path, server-backed)
-> ObserveEntitlementsUseCase
-> BillingViewModel
-> MainActivityScreen / AppNavGraph / feature screens
```

## 4) Implementation Phases
- Phase 0:
- Phase 1:
- Phase 2:
- Phase 3:
- Phase 4:

## 5) Test Plan
- Unit tests:
- VM tests:
- Device/runtime tests:
- Boundary tests:
- Required checks:
  - ./gradlew enforceRules
  - ./gradlew testDebugUnitTest
  - ./gradlew assembleDebug

## 6) Risks and Mitigations
| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| Placeholder package name not finalized | broken Play setup | lock package name before store config | |

## 7) Acceptance Gates
- No duplicate entitlement SSOT
- No client-only premium authority
- No hardcoded price strings in production paywall logic
- No direct BillingClient use in composables
- Tests and verification commands pass
- Post-implementation audit passes

## 8) Rollback Plan
- What can be reverted independently:
- Recovery steps if regression is detected:
