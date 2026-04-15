# Recommended Feature Matrix (Draft v1)

This is the **current locked matrix for the decisions made so far**, not the final last-word catalog. Keep capability ownership explicit and central.

## Recommended positioning

- **Free**: useful baseline that lets people fly, see airspace, go direct-to-home, and use essential screens
- **Basic**: low-friction entry paid tier for visibility overlays and starter integrations
- **Soaring**: first serious workflow tier with tasking, OGN, and credentialed premium SkySight access
- **XC**: serious cross-country bundle; final XC-only differentiators still need to be locked
- **Pro**: full unlock and top-end premium surface area

## Current locked capability table

| Capability | Free | Basic | Soaring | XC | Pro | Extra gate / note |
|---|---:|---:|---:|---:|---:|---|
| Airspace | ✅ | ✅ | ✅ | ✅ | ✅ | Core baseline |
| Home waypoint / direct-to-home | ✅ | ✅ | ✅ | ✅ | ✅ | Keep broader waypoint/task workflow gated separately |
| Flight mode screen selection | ✅ | ✅ | ✅ | ✅ | ✅ | Free can select screens, but only Essentials cards |
| Essentials card pack | ✅ | ✅ | ✅ | ✅ | ✅ | Baseline cards only |
| Distance Circles | ❌ | ✅ | ✅ | ✅ | ✅ | Basic and above |
| ADS-B traffic overlay | ❌ | ✅ | ✅ | ✅ | ✅ | Basic and above |
| SkySight basic/free products | ✅ | ✅ | ✅ | ✅ | ✅ | Free/public SkySight level only |
| SkySight credential entry / account linking | ❌ | ❌ | ✅ | ✅ | ✅ | Soaring and above |
| SkySight premium products in XCPro | ❌ | ❌ | ✅* | ✅* | ✅* | `*` requires valid linked **paid** SkySight account |
| RainViewer | ❌ | ✅ | ✅ | ✅ | ✅ | Basic and above |
| WeGlide sync | ❌ | ✅ | ✅ | ✅ | ✅ | Basic and above |
| Task add / create / edit | ❌ | ❌ | ✅ | ✅ | ✅ | Soaring and above |
| OGN | ❌ | ❌ | ✅ | ✅ | ✅ | Soaring and above |
| Scia | ❌ | ❌ | ❌ | ❌ | ✅ | Pro only |
| Hotspots | ❌ | ❌ | ❌ | ❌ | ✅* | Pro only; if SkySight-backed, also require linked paid SkySight account |

## Still pending before launch

The table above reflects the capability decisions explicitly discussed so far. XC-specific differentiation is **not fully locked yet**. Before launch, either define the XC bundle clearly or collapse the tier from launch scope.

Candidate items still to confirm from the earlier draft:

| Capability | Likely tier | Status |
|---|---|---|
| IGC replay tools | XC or Pro | still draft |
| LiveFollow view/watch | XC or Pro | still draft |
| LiveFollow broadcast/share | Pro | still draft |
| Premium exports / advanced sharing | XC or Pro | still draft |
| Advanced vario tuning / premium audio profiles | Pro | still draft |

## Recommended user-facing copy

### Basic
Starter visibility and integration tools.

### Soaring
Tasking, OGN, and premium SkySight access with your linked paid SkySight account.

### XC
Serious cross-country tools and the approved XC bundle.

### Pro
Everything unlocked, including Scia, Hotspots, and top-end premium tools.

## Important SkySight rule

Do **not** market SkySight premium as “included” in Soaring, XC, or Pro unless a real commercial agreement exists.

The rule for now is:

- XCPro plan decides whether SkySight premium surfaces are allowed in XCPro.
- Linked SkySight account state decides whether those premium surfaces can actually unlock.
- Soaring / XC / Pro enable the integration surface.
- Only a valid linked **paid** SkySight account enables premium SkySight-backed output.

## Decision checklist before coding

- Is Free still valuable enough to acquire users?
- Is Basic compelling enough to convert at the intended low price point?
- Is Soaring clearly better than Basic?
- Is XC clearly better than Soaring?
- Is Pro meaningfully broader than XC?
- Are any safety-critical capabilities being gated in a way that harms trust?
- Are any provider-linked premium features being marketed as included when they are actually dual-gated?
