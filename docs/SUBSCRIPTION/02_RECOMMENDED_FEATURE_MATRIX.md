# Recommended Feature Matrix (Draft v0)

This is a **starting matrix**, not a law. Finalize it before implementation.

## Recommended positioning

- **Free**: useful baseline that lets people fly and understand the app
- **Soar**: first paid tier for weather/forecast value
- **XC**: serious cross-country tools
- **Pro**: full unlock and future premium surface area

## Draft matrix

| Feature | Free | Soar | XC | Pro | Notes |
|---|---:|---:|---:|---:|---|
| Core map and flight display | ✅ | ✅ | ✅ | ✅ | Core app value; do not cripple baseline usability |
| Profiles and local settings | ✅ | ✅ | ✅ | ✅ | Required for all users |
| Basic task creation/view | ✅ | ✅ | ✅ | ✅ | Keep entry-level usability |
| Weather snapshot/basic weather products | ❌ | ✅ | ✅ | ✅ | Good first paid value |
| Forecast/basic forecast tools | ❌ | ✅ | ✅ | ✅ | Good first paid value |
| IGC replay tools | ❌ | ❌ | ✅ | ✅ | Better fit for serious users |
| WeGlide sync | ❌ | ❌ | ✅ | ✅ | Strong XC tier candidate |
| LiveFollow view/watch | ❌ | ❌ | ✅ | ✅ | Good XC+ capability |
| LiveFollow broadcast/share | ❌ | ❌ | ❌ | ✅ | Server/support cost justification |
| Traffic / ADS-B overlays | ❌ | ❌ | ❌ | ✅ | Premium/safety-adjacent complexity |
| Advanced vario tuning / premium audio profiles | ❌ | ❌ | ❌ | ✅ | Best kept at top tier |
| Premium exports / advanced sharing | ❌ | ❌ | ✅ | ✅ | Strong mid/high tier value |

## Recommended user-facing copy

### Soar
Weather and forecast tools for everyday flying.

### XC
Cross-country tools, replay, sync, and follow features.

### Pro
Everything unlocked, including advanced premium tools.

## Decision checklist before coding

- Is Free still valuable enough to acquire users?
- Is Soar compelling enough to convert?
- Is XC clearly better than Soar?
- Is Pro meaningfully broader than XC?
- Are any safety-critical capabilities being gated in a way that harms trust?
- Are any server-costly features left in Free by mistake?
