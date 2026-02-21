# RainViewer Radar Loop in MapLibre Native Android (XCPro, Reference)

This file is reference material only. Execution source of truth is:
`docs/RAINVIEWER/01_RAINVIEWER_INDUSTRY_HARDENING_PLAN_2026-02-20.md`.

This document describes a **production-minded** way to integrate RainViewer **past radar frames** as an **animated raster overlay** using **MapLibre Native Android**.

> Key point: RainViewer’s public API (as of **Jan 1, 2026**) is **past-only**, **Universal Blue only**, **max zoom 7**, and has a **rate limit**. Design the UX so animation is *optional* and conservative.

---

## 1) What RainViewer provides

### Frames list (what you animate)
RainViewer exposes a JSON with the available frames:

- `https://api.rainviewer.com/public/weather-maps.json`

It contains:
- `generated`: Unix timestamp (UTC) when the file was generated (good for update checks)
- `host`: base host for tiles (includes protocol)
- `radar.past[]`: an array of frames with `{ time, path }`

RainViewer says this includes the **past 2 hours**, with **10-minute steps**.

### Tile URL template (what you render per frame)
For a given frame’s `path`, tile URLs follow:

```
{host}{path}/{size}/{z}/{x}/{y}/{color}/{options}.png
```

Where:
- `size` is `256` or `512`
- max zoom is **7**
- `color` is the color scheme ID (2026: Universal Blue only)
- `options` is `{smooth}_{snow}` e.g. `1_1`

A concrete template you’ll typically use in 2026:

```
{host}{path}/512/{z}/{x}/{y}/2/1_1.png
```

### 2026 constraints you must handle (non-negotiable)
From RainViewer’s transition notes (Jan 1, 2026):
- Nowcast/future radar discontinued (past only)
- Universal Blue is the only remaining color scheme
- Maximum zoom level set to **7**
- Rate limiting set to **100 requests / IP / minute**

---

## 2) MapLibre Native Android implications

### You cannot “setTiles()” like MapLibre GL JS
MapLibre GL JS has `setTiles()` for raster tile sources; **MapLibre Native Android does not**.  
`RasterSource` exposes constructors but no documented setter to change the tile template after creation.

So, to change frames you must **swap/recreate raster sources** (and often layers).

### Use TileSet + RasterSource for XYZ tile templates
**Important:** In MapLibre Native Android, the `RasterSource(id, uriString, tileSize)` constructor is commonly interpreted as a **TileJSON URL**, not an XYZ template.

There is a known report where `{z}/{x}/{y}` placeholders were requested literally/URL-encoded when passed as a raw string. If you see requests like `.../%7Bz%7D/%7Bx%7D/%7By%7D.png`, switch to **TileSet** usage.

TileSet exists to represent TileJSON metadata (including templated tile URLs), and it supports `minZoom`/`maxZoom`, `scheme`, and `attribution`.

---

## 3) Recommended architecture in XCPro

### Components
1) **RainViewerClient**
   - Fetches `weather-maps.json`
   - Parses `host`, `generated`, `radar.past[]`
   - Exposes a list of frames + latest index

2) **RadarOverlayController**
   - Owns the current MapLibre `Style`
   - Manages the radar raster sources/layers
   - Provides:
     - `setEnabled(true/false)`
     - `setOpacity(0..1)`
     - `showFrame(index)`
     - `play()` / `pause()`

3) **Animator**
   - Runs only when user presses play
   - Pauses during map movement (optional)
   - Uses a conservative frame interval (e.g., 10–15 seconds per frame)

### Why conservative animation matters
Even a single frame can require **multiple tile requests** depending on viewport/zoom.  
With a limit like **100 req/IP/min**, continuous animation can easily trigger rate limiting.

---

## 4) Kotlin data models (weather-maps.json)

Using kotlinx.serialization as an example:

```kotlin
@kotlinx.serialization.Serializable
data class RainViewerMaps(
    val version: String? = null,
    val generated: Long,
    val host: String,
    val radar: Radar
)

@kotlinx.serialization.Serializable
data class Radar(
    val past: List<Frame> = emptyList()
)

@kotlinx.serialization.Serializable
data class Frame(
    val time: Long,
    val path: String
)
```

---

## 5) Tile template builder

```kotlin
object RainViewerTile {
    // 2026+ constraints
    const val COLOR_UNIVERSAL_BLUE = 2   // RainViewer “Universal Blue”
    const val MAX_ZOOM = 7f

    // Recommended defaults (tune for bandwidth vs clarity)
    const val TILE_SIZE = 512
    const val OPTIONS = "1_1" // smooth=1, snow=1 (or "1_0" if you want no snow colors)

    fun template(host: String, path: String): String {
        // host includes protocol; path begins with "/v2/radar/..."
        return "$host$path/$TILE_SIZE/{z}/{x}/{y}/$COLOR_UNIVERSAL_BLUE/$OPTIONS.png"
    }
}
```

---

## 6) Creating a RasterSource correctly (TileSet)

```kotlin
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet

fun buildRainViewerRasterSource(
    sourceId: String,
    tileTemplate: String,
    attribution: String = "RainViewer",
    minZoom: Float = 0f,
    maxZoom: Float = 7f,
    tileSize: Int = 512
): RasterSource {
    val tileSet = TileSet("2.2.0", tileTemplate).apply {
        this.attribution = attribution
        this.scheme = "xyz"
        this.setMinZoom(minZoom)
        this.setMaxZoom(maxZoom)
    }

    return RasterSource(sourceId, tileSet, tileSize).apply {
        // Avoid caching a lot of unique tiles across frames:
        // Set a flag defining whether fetched tiles should be stored in local cache.
        setVolatile(true)
    }
}
```

Notes:
- `TileSet("2.2.0", ...)` sets the TileJSON spec version string.
- `TileSet` supports `attribution`, `scheme`, `minZoom`, `maxZoom`, etc.

---

## 7) Creating the RasterLayer + opacity/transition

```kotlin
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.TransitionOptions

fun buildRadarLayer(layerId: String, sourceId: String, opacity: Float): RasterLayer {
    return RasterLayer(layerId, sourceId).apply {
        // Keep radar visible at all map zooms; the source maxZoom caps tile requests.
        // (You can also choose to hide at high zoom for aesthetics.)
        setProperties(
            PropertyFactory.rasterOpacity(opacity)
        )

        // Optional: animate opacity changes
        setRasterOpacityTransition(TransitionOptions(200, 0))
    }
}
```

---

## 8) Overlay controller: “two-layer crossfade” strategy

### Why two layers?
Crossfading reduces flicker:
1) Create a new “next” overlay (source + layer) at opacity 0
2) Let it start loading tiles
3) Fade it in, fade the old one out
4) Remove old overlay objects

### IDs strategy (important!)
On MapLibre Native Android, re-adding a layer/source with the same ID after removal can be fragile depending on SDK version and removal method.

A robust approach is to:
- Create **new unique IDs** per frame swap (e.g., suffix with frame time)
- Remove old layers/sources by **object reference** when possible

This avoids “cannot add layer twice” style edge cases.

---

## 9) Example controller (Kotlin skeleton)

> This is intentionally a *skeleton* you can integrate into XCPro’s architecture (DI, lifecycle, etc.).

```kotlin
import kotlinx.coroutines.*
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.RasterSource
import java.util.concurrent.atomic.AtomicReference

class RainViewerRadarController(
    private val map: MapLibreMap,
    private val scope: CoroutineScope
) {
    private val overlayRef = AtomicReference<Overlay?>(null)

    private var host: String? = null
    private var frames: List<Frame> = emptyList()
    private var frameIndex: Int = 0

    private var playJob: Job? = null
    private var targetOpacity: Float = 0.65f

    data class Overlay(
        val source: RasterSource,
        val layer: RasterLayer
    )

    /** Call after you fetch weather-maps.json */
    fun setFrames(host: String, frames: List<Frame>) {
        this.host = host
        this.frames = frames
        this.frameIndex = (frames.size - 1).coerceAtLeast(0) // latest
    }

    fun setOpacity(opacity: Float) {
        targetOpacity = opacity.coerceIn(0f, 1f)
        map.getStyle { style ->
            overlayRef.get()?.layer?.setProperties(
                org.maplibre.android.style.layers.PropertyFactory.rasterOpacity(targetOpacity)
            )
        }
    }

    fun showLatest() = showFrame((frames.size - 1).coerceAtLeast(0))

    fun showFrame(index: Int) {
        val h = host ?: return
        if (frames.isEmpty()) return

        frameIndex = ((index % frames.size) + frames.size) % frames.size
        val frame = frames[frameIndex]
        val template = RainViewerTile.template(h, frame.path)

        map.getStyle { style ->
            swapOverlay(style, template, frame.time)
        }
    }

    fun play(frameDelayMs: Long = 12_000L) {
        pause()
        playJob = scope.launch(Dispatchers.Main.immediate) {
            while (isActive) {
                delay(frameDelayMs)
                // Optional: pause while map is moving, if you track gestures/camera state
                showFrame(frameIndex + 1)
            }
        }
    }

    fun pause() {
        playJob?.cancel()
        playJob = null
    }

    fun clear() {
        pause()
        map.getStyle { style ->
            overlayRef.getAndSet(null)?.let { old ->
                style.removeLayer(old.layer)
                style.removeSource(old.source)
            }
        }
    }

    private fun swapOverlay(style: Style, template: String, frameTime: Long) {
        // Build new overlay with unique IDs
        val sourceId = "rv-radar-src-$frameTime"
        val layerId  = "rv-radar-lyr-$frameTime"

        val newSource = buildRainViewerRasterSource(
            sourceId = sourceId,
            tileTemplate = template,
            minZoom = 0f,
            maxZoom = RainViewerTile.MAX_ZOOM,
            tileSize = RainViewerTile.TILE_SIZE
        )
        val newLayer = buildRadarLayer(layerId, sourceId, opacity = 0f)

        // Add new source + layer
        style.addSource(newSource)

        // Insert above/below a known layer if needed:
        // style.addLayerAbove(newLayer, "some-layer-id")
        style.addLayer(newLayer)

        val old = overlayRef.getAndSet(Overlay(newSource, newLayer))

        // Crossfade (simple)
        fadeLayer(newLayer, from = 0f, to = targetOpacity, durationMs = 200L)

        old?.let {
            fadeLayer(it.layer, from = targetOpacity, to = 0f, durationMs = 200L) {
                // Remove old after fade
                style.removeLayer(it.layer)
                style.removeSource(it.source)
            }
        }
    }

    private fun fadeLayer(
        layer: RasterLayer,
        from: Float,
        to: Float,
        durationMs: Long,
        onEnd: (() -> Unit)? = null
    ) {
        // Minimal dependency version: just “jump” opacity.
        // If you want a real animation, use ValueAnimator on main thread.
        layer.setProperties(org.maplibre.android.style.layers.PropertyFactory.rasterOpacity(to))
        onEnd?.invoke()
    }
}
```

### Inserting radar relative to other layers
If you need radar:
- **below labels**: insert below a known label layer ID via `style.addLayerBelow(layer, belowId)`
- **above basemap** but under symbols: `style.addLayerAbove(layer, aboveId)`

MapLibre provides:
- `addLayer(layer)`
- `addLayerAbove(layer, aboveId)`
- `addLayerBelow(layer, belowId)`
- `addLayerAt(layer, index)`

---

## 10) Update loop for frames list (recommended)

### Don’t refetch frames every animation tick
`weather-maps.json` changes only when new frames are produced. Use `generated` timestamp to detect updates.

Suggested approach:
- Refresh frames list every **5–10 minutes** (or on app resume)
- If `generated` changed, update `frames` and snap to latest (or keep current index if you prefer)

---

## 11) Zoom handling and visual quality

RainViewer max zoom is **7**. You have options:
1) **Allow overscaling** (pixels get blocky at high zoom; simplest)
2) **Clamp radar visibility** (hide radar overlay when `map.cameraPosition.zoom > 7`)
3) **Set TileSet.maxZoom to 6** to reduce tile churn and accept slightly blurrier visuals

---

## 12) Bandwidth & rate-limit mitigation checklist

- Default to **latest frame only** (no animation until user presses play)
- Use **512px tiles** to reduce number of tile requests (often fewer requests than 256px)
- Slow playback (start at **10–15 seconds/frame**)
- Pause playback while the user is actively panning/zooming
- Consider limiting radar to lower zoom (TileSet.maxZoom = 5–6)
- Use `source.setVolatile(true)` to avoid caching lots of one-off tiles
- Consider `RasterSource.setPrefetchZoomDelta(null/0)` if you see aggressive prefetching
- Handle HTTP 429 gracefully (disable play + show “Radar temporarily unavailable”)

---

## 13) Attribution / licensing notes

RainViewer’s API documentation describes the public API as **free for personal or educational use** and asks for attribution with a link.

In-app, you can:
- Set `TileSet.attribution = "RainViewer"`
- Add a visible attribution line (e.g., in settings/about, or a small “RainViewer” label in the map HUD)

---

## 14) References (source docs)

RainViewer:
- Weather Maps API docs: https://www.rainviewer.com/api/weather-maps-api.html
- API transition FAQ: https://www.rainviewer.com/api/transition-faq.html
- API documentation / attribution note: https://www.rainviewer.com/api.html

MapLibre Native Android:
- TileSet API: https://maplibre.org/maplibre-native/android/api/-map-libre%20-native%20-android/org.maplibre.android.style.sources/-tile-set/index.html
- RasterSource API: https://maplibre.org/maplibre-native/android/api/-map-libre%20-native%20-android/org.maplibre.android.style.sources/-raster-source/index.html
- RasterLayer API: https://maplibre.org/maplibre-native/android/api/-map-libre%20-native%20-android/org.maplibre.android.style.layers/-raster-layer/index.html
- PropertyFactory.rasterOpacity: https://maplibre.org/maplibre-native/android/api/-map-libre%20-native%20-android/org.maplibre.android.style.layers/-property-factory/raster-opacity.html
- Style add/remove methods:
  - addLayer: https://maplibre.org/maplibre-native/android/api/-map-libre%20-native%20-android/org.maplibre.android.maps/-style/add-layer.html
  - addLayerAbove: https://maplibre.org/maplibre-native/android/api/-map-libre%20-native%20-android/org.maplibre.android.maps/-style/add-layer-above.html
  - addLayerBelow: https://maplibre.org/maplibre-native/android/api/-map-libre%20-native%20-android/org.maplibre.android.maps/-style/add-layer-below.html
  - removeLayer: https://maplibre.org/maplibre-native/android/api/-map-libre%20-native%20-android/org.maplibre.android.maps/-style/remove-layer.html
  - removeSource: https://maplibre.org/maplibre-native/android/api/-map-libre%20-native%20-android/org.maplibre.android.maps/-style/remove-source.html

Known issue report (placeholder substitution / URL-encoded braces):
- https://github.com/maplibre/maplibre-native/issues/2757
