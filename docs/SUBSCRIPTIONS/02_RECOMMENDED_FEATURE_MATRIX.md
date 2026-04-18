# Recommended Feature Matrix (Locked v1)

This is the **current locked matrix** for implementation planning. Keep capability ownership explicit and central.

## Account rule first

Every tier in this matrix assumes the user is signed in with an XCPro account.

- signed out = no normal production app use
- signed in + no paid subscription = `Free`
- signed in + active paid subscription = `Basic` / `Soaring` / `XC` / `Pro`

## Recommended positioning

- **Free**: useful baseline that lets people fly, see airspace, go direct-to-home, and use essential screens
- **Basic**: low-friction entry paid tier for visibility overlays and starter integrations
- **Soaring**: first serious workflow tier with tasking, OGN, and credentialed premium SkySight access
- **XC**: serious cross-country bundle with replay, LiveFollow view/watch, and premium exports/sharing
- **Pro**: full unlock and top-end premium surface area

## Locked capability table

| Capability | Free | Basic | Soaring | XC | Pro | Extra gate / note |
|---|---:|---:|---:|---:|---:|---|
| Airspace | Yes | Yes | Yes | Yes | Yes | Core baseline |
| Home waypoint / direct-to-home | Yes | Yes | Yes | Yes | Yes | Keep broader waypoint/task workflow gated separately |
| Flight mode screen selection | Yes | Yes | Yes | Yes | Yes | Free can select screens, but only Essentials cards |
| Essentials card pack | Yes | Yes | Yes | Yes | Yes | Baseline cards only |
| Distance Circles | No | Yes | Yes | Yes | Yes | Basic and above |
| ADS-B traffic overlay | No | Yes | Yes | Yes | Yes | Basic and above |
| SkySight basic/free products | Yes | Yes | Yes | Yes | Yes | Free/public SkySight level only |
| SkySight credential entry / account linking | No | No | Yes | Yes | Yes | Soaring and above |
| SkySight premium products in XCPro | No | No | Yes* | Yes* | Yes* | `*` requires valid linked **paid** SkySight account |
| RainViewer | No | Yes | Yes | Yes | Yes | Basic and above |
| WeGlide sync | No | Yes | Yes | Yes | Yes | Basic and above |
| Task add / create / edit | No | No | Yes | Yes | Yes | Soaring and above |
| OGN | No | No | Yes | Yes | Yes | Soaring and above |
| IGC replay | No | No | No | Yes | Yes | XC differentiator |
| LiveFollow view / watch | No | No | No | Yes | Yes | XC differentiator |
| Premium exports / advanced sharing | No | No | No | Yes | Yes | XC differentiator |
| LiveFollow broadcast / share | No | No | No | No | Yes | Pro-only premium/networked surface |
| Scia | No | No | No | No | Yes | Pro only |
| Hotspots | No | No | No | No | Yes* | Pro only; if SkySight-backed, also require linked paid SkySight account |
| Advanced vario tuning / premium audio profiles | No | No | No | No | Yes | Pro-only advanced tools |

## User-facing positioning copy

### Free
Core XCPro flying baseline for signed-in users.

### Basic
Starter visibility and integration tools.

### Soaring
Tasking, OGN, and premium SkySight access with your linked paid SkySight account.

### XC
Serious cross-country tools with replay, LiveFollow view/watch, and premium exports/sharing.

### Pro
Everything unlocked, including Scia, Hotspots, LiveFollow broadcast/share, and top-end premium tools.

## Important SkySight rule

Do **not** market SkySight premium as "included" in Soaring, XC, or Pro unless a real commercial agreement exists.

The rule for now is:

- XCPro plan decides whether SkySight premium surfaces are allowed in XCPro.
- Linked SkySight account state decides whether those premium surfaces can actually unlock.
- Soaring / XC / Pro enable the integration surface.
- Only a valid linked **paid** SkySight account enables premium SkySight-backed output.

## Decision checklist before coding

- Is Free still valuable enough to acquire users without becoming an anonymous mode?
- Is Basic compelling enough to convert at the intended low price point?
- Is Soaring clearly better than Basic?
- Is XC clearly better than Soaring?
- Is Pro meaningfully broader than XC?
- Are any safety-critical capabilities being gated in a way that harms trust?
- Are any provider-linked premium features being marketed as included when they are actually dual-gated?
- Is the monthly/annual base-plan model reflected consistently across backend, Android, and Play Console docs?

## Authority rule

If the old `feature_matrix.csv` or any other non-Markdown artifact conflicts with this file, this file wins.
