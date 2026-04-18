package com.trust3.xcpro.map

import android.content.Context
import android.view.Choreographer
import com.trust3.xcpro.common.units.UnitsPreferences
import com.trust3.xcpro.core.common.logging.AppLogger
import com.trust3.xcpro.core.time.TimeBridge
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection

class AdsbTrafficOverlay(
    private val context: Context,
    private val map: MapLibreMap,
    initialIconSizePx: Int = ADSB_ICON_SIZE_DEFAULT_PX
) : AdsbTrafficOverlayHandle {
    private val packedGroupLabelControl = TrafficPackedGroupLabelControl()
    private val selectedGroupFanoutLayout = TrafficSelectedGroupFanoutLayout()
    private val diagnosticsState = AdsbTrafficOverlayDiagnosticsState()
    private var currentIconSizePx: Int = clampAdsbIconSizePx(initialIconSizePx)
    private var currentViewportZoom: Float =
        map.cameraPosition.zoom.toFloat().takeIf { it.isFinite() } ?: ADSB_TRAFFIC_INITIAL_VIEWPORT_ZOOM
    private var currentViewportRangeMeters: Double? = resolveAdsbViewportRangeMeters(map)
    private var currentViewportPolicy: AdsbTrafficViewportDeclutterPolicy =
        resolveAdsbTrafficViewportDeclutterPolicy(
            zoomLevel = currentViewportZoom,
            viewportRangeMeters = currentViewportRangeMeters
        )
    private var emergencyFlashEnabled: Boolean = ADSB_EMERGENCY_FLASH_ENABLED_DEFAULT
    private var interactionReducedMotionActive: Boolean = false
    private var currentSelectedTargetId: Icao24? = null
    private var currentOwnshipAltitudeMeters: Double? = null
    private var currentUnitsPreferences: UnitsPreferences = UnitsPreferences()
    private var currentIconStyleIdOverrides: Map<String, String> = emptyMap()
    private val motionSmoother = AdsbDisplayMotionSmoother()
    private val frameLoopController = AdsbOverlayFrameLoopController(
        minRenderIntervalMs = ADSB_TRAFFIC_ANIMATION_FRAME_INTERVAL_MS
    )
    private val frameCallback = Choreographer.FrameCallback frame@{ _ ->
        frameLoopController.onFrameDispatched()
        // Use one monotonic clock source for both immediate and choreographer frames.
        val nowMonoMs = nowMonoMs()
        val frameSnapshot = motionSmoother.snapshot(nowMonoMs)
        val emergencyAnimatedTargetCount = activeEmergencyAnimatedTargetCount(frameSnapshot)
        val hasVisualAnimation = shouldAnimateAdsbVisuals(
            frameSnapshot = frameSnapshot,
            emergencyFlashEnabled = emergencyFlashEnabled,
            interactionReducedMotionActive = interactionReducedMotionActive
        )
        if (!hasVisualAnimation) {
            diagnosticsState.recordAnimationFrameSkipped(
                activeAnimatedTargetCount = frameSnapshot.activeAnimatedTargetCount,
                emergencyAnimatedTargetCount = emergencyAnimatedTargetCount
            )
            return@frame
        }
        if (!frameLoopController.shouldRenderFrame(nowMonoMs)) {
            diagnosticsState.recordAnimationFrameSkipped(
                activeAnimatedTargetCount = frameSnapshot.activeAnimatedTargetCount,
                emergencyAnimatedTargetCount = emergencyAnimatedTargetCount
            )
            if (map.style != null && hasVisualAnimation) {
                scheduleFrameLoop()
            }
            return@frame
        }
        renderFrame(nowMonoMs, frameSnapshot)
        diagnosticsState.recordAnimationFrameRendered(
            activeAnimatedTargetCount = frameSnapshot.activeAnimatedTargetCount,
            emergencyAnimatedTargetCount = emergencyAnimatedTargetCount
        )
        frameLoopController.markFrameRendered(nowMonoMs)
        if (map.style != null && hasVisualAnimation) {
            scheduleFrameLoop()
        }
    }

    override fun initialize() {
        val style = map.style ?: return
        try {
            if (style.getSource(SOURCE_ID) == null) {
                style.addSource(GeoJsonSource(SOURCE_ID))
            }
            if (style.getSource(ADSB_TRAFFIC_LEADER_LINE_SOURCE_ID) == null) {
                style.addSource(GeoJsonSource(ADSB_TRAFFIC_LEADER_LINE_SOURCE_ID))
            }
            ensureAdsbTrafficOverlayStyleImages(
                context = context,
                style = style,
                emergencyIconColor = ADSB_TRAFFIC_EMERGENCY_ICON_COLOR
            )
            if (style.getLayer(ADSB_TRAFFIC_LEADER_LINE_LAYER_ID) == null) {
                val leaderLineLayer = createAdsbLeaderLineLayer()
                val anchorId = BLUE_LOCATION_OVERLAY_LAYER_ID_FALLBACK
                when {
                    style.getLayer(ADSB_TRAFFIC_ICON_OUTLINE_LAYER_ID) != null ->
                        style.addLayerBelow(leaderLineLayer, ADSB_TRAFFIC_ICON_OUTLINE_LAYER_ID)

                    style.getLayer(anchorId) != null ->
                        style.addLayerAbove(leaderLineLayer, anchorId)

                    else -> style.addLayer(leaderLineLayer)
                }
            }
            if (style.getLayer(ADSB_TRAFFIC_ICON_OUTLINE_LAYER_ID) == null) {
                val outlineLayer = createAdsbIconOutlineLayer(
                    currentIconSizePx = currentIconSizePx,
                    viewportPolicy = currentViewportPolicy
                )
                if (style.getLayer(ADSB_TRAFFIC_LEADER_LINE_LAYER_ID) != null) {
                    style.addLayerAbove(outlineLayer, ADSB_TRAFFIC_LEADER_LINE_LAYER_ID)
                } else if (style.getLayer(BLUE_LOCATION_OVERLAY_LAYER_ID_FALLBACK) != null) {
                    style.addLayerBelow(outlineLayer, BLUE_LOCATION_OVERLAY_LAYER_ID_FALLBACK)
                } else {
                    style.addLayer(outlineLayer)
                }
            }
            if (style.getLayer(ADSB_TRAFFIC_ICON_LAYER_ID) == null) {
                val iconLayer = createAdsbIconLayer(
                    currentIconSizePx = currentIconSizePx,
                    viewportPolicy = currentViewportPolicy
                )
                if (style.getLayer(ADSB_TRAFFIC_ICON_OUTLINE_LAYER_ID) != null) {
                    style.addLayerAbove(iconLayer, ADSB_TRAFFIC_ICON_OUTLINE_LAYER_ID)
                } else {
                    val anchorId = BLUE_LOCATION_OVERLAY_LAYER_ID_FALLBACK
                    if (style.getLayer(anchorId) != null) {
                        style.addLayerBelow(iconLayer, anchorId)
                    } else {
                        style.addLayer(iconLayer)
                    }
                }
            }

            if (style.getLayer(ADSB_TRAFFIC_TOP_LABEL_LAYER_ID) == null) {
                val topLayer = createAdsbTopLabelLayer(
                    currentIconSizePx = currentIconSizePx,
                    viewportPolicy = currentViewportPolicy
                )
                if (style.getLayer(ADSB_TRAFFIC_ICON_LAYER_ID) != null) {
                    style.addLayerAbove(topLayer, ADSB_TRAFFIC_ICON_LAYER_ID)
                } else {
                    style.addLayer(topLayer)
                }
            }
            if (style.getLayer(ADSB_TRAFFIC_BOTTOM_LABEL_LAYER_ID) == null) {
                val bottomLayer = createAdsbBottomLabelLayer(
                    currentIconSizePx = currentIconSizePx,
                    viewportPolicy = currentViewportPolicy
                )
                if (style.getLayer(ADSB_TRAFFIC_TOP_LABEL_LAYER_ID) != null) {
                    style.addLayerAbove(bottomLayer, ADSB_TRAFFIC_TOP_LABEL_LAYER_ID)
                } else if (style.getLayer(ADSB_TRAFFIC_ICON_LAYER_ID) != null) {
                    style.addLayerAbove(bottomLayer, ADSB_TRAFFIC_ICON_LAYER_ID)
                } else {
                    style.addLayer(bottomLayer)
                }
            }
            applyViewportPolicyToStyle()
        } catch (t: Throwable) {
            AppLogger.e(ADSB_TRAFFIC_OVERLAY_TAG, "Failed to initialize ADS-B overlay: ${t.message}", t)
        }
    }

    override fun setIconSizePx(iconSizePx: Int) {
        val clamped = clampAdsbIconSizePx(iconSizePx)
        if (clamped == currentIconSizePx) return
        currentIconSizePx = clamped
        applyViewportPolicyToStyle()
    }

    override fun setViewportZoom(zoomLevel: Float) {
        val normalizedZoom = zoomLevel.takeIf { it.isFinite() } ?: return
        currentViewportZoom = normalizedZoom
        applyViewportPolicyToStyle()
    }

    override fun setEmergencyFlashEnabled(enabled: Boolean) {
        if (emergencyFlashEnabled == enabled) return
        emergencyFlashEnabled = enabled
        applyInteractionRenderMode()
    }

    override fun setInteractionReducedMotionActive(active: Boolean) {
        if (interactionReducedMotionActive == active) return
        interactionReducedMotionActive = active
        diagnosticsState.setInteractionReducedMotionActive(active)
        applyInteractionRenderMode()
    }

    override fun render(
        targets: List<AdsbTrafficUiModel>,
        selectedTargetId: Icao24?,
        ownshipAltitudeMeters: Double?,
        unitsPreferences: UnitsPreferences,
        iconStyleIdOverrides: Map<String, String>
    ) {
        initialize()
        val normalizedOwnshipAltitude = ownshipAltitudeMeters?.takeIf { it.isFinite() }
        val contextChanged =
            currentSelectedTargetId != selectedTargetId ||
            currentOwnshipAltitudeMeters != normalizedOwnshipAltitude ||
                currentUnitsPreferences != unitsPreferences ||
                currentIconStyleIdOverrides != iconStyleIdOverrides
        currentSelectedTargetId = selectedTargetId
        currentOwnshipAltitudeMeters = normalizedOwnshipAltitude
        currentUnitsPreferences = unitsPreferences
        currentIconStyleIdOverrides = iconStyleIdOverrides
        val nowMonoMs = nowMonoMs()
        val changed = motionSmoother.onTargets(targets, nowMonoMs)
        val frameSnapshot = motionSmoother.snapshot(nowMonoMs)
        val hasVisualAnimation = hasActiveVisualAnimation(frameSnapshot)
        if (!changed && !hasVisualAnimation && !contextChanged) {
            return
        }
        renderFrame(nowMonoMs, frameSnapshot)
        frameLoopController.markFrameRendered(nowMonoMs)
        if (hasVisualAnimation) {
            scheduleFrameLoop()
        } else {
            stopFrameLoop()
        }
    }

    override fun diagnosticsSnapshot(): AdsbTrafficOverlayDiagnosticsSnapshot =
        diagnosticsState.snapshot()

    override fun findTargetAt(tap: LatLng): Icao24? {
        val style = map.style ?: return null
        if (style.getSource(SOURCE_ID) == null) return null
        val screenPoint = map.projection.toScreenLocation(tap)
        val features = runCatching {
            map.queryRenderedFeatures(
                screenPoint,
                ADSB_TRAFFIC_ICON_LAYER_ID,
                ADSB_TRAFFIC_TOP_LABEL_LAYER_ID,
                ADSB_TRAFFIC_BOTTOM_LABEL_LAYER_ID
            )
        }.getOrNull().orEmpty()

        for (feature in features) {
            if (!feature.hasProperty(AdsbGeoJsonMapper.PROP_ICAO24)) continue
            val rawId = runCatching {
                feature.getStringProperty(AdsbGeoJsonMapper.PROP_ICAO24)
            }.getOrNull()
            val id = Icao24.from(rawId) ?: continue
            return id
        }
        return null
    }

    fun clear() {
        stopFrameLoop()
        motionSmoother.clear()
        frameLoopController.resetRenderClock()
        currentSelectedTargetId = null
        currentOwnshipAltitudeMeters = null
        currentUnitsPreferences = UnitsPreferences()
        currentIconStyleIdOverrides = emptyMap()
        val style = map.style ?: return
        val source = style.getSourceAs<GeoJsonSource>(SOURCE_ID) ?: return
        source.setGeoJson(FeatureCollection.fromFeatures(emptyArray<Feature>()))
        val leaderLineSource = style.getSourceAs<GeoJsonSource>(ADSB_TRAFFIC_LEADER_LINE_SOURCE_ID) ?: return
        leaderLineSource.setGeoJson(FeatureCollection.fromFeatures(emptyArray<Feature>()))
    }

    override fun cleanup() {
        stopFrameLoop()
        motionSmoother.clear()
        frameLoopController.resetRenderClock()
        currentSelectedTargetId = null
        currentOwnshipAltitudeMeters = null
        currentUnitsPreferences = UnitsPreferences()
        currentIconStyleIdOverrides = emptyMap()
        val style = map.style ?: return
        try {
            style.removeLayer(ADSB_TRAFFIC_BOTTOM_LABEL_LAYER_ID)
            style.removeLayer(ADSB_TRAFFIC_TOP_LABEL_LAYER_ID)
            style.removeLayer(ADSB_TRAFFIC_ICON_LAYER_ID)
            style.removeLayer(ADSB_TRAFFIC_ICON_OUTLINE_LAYER_ID)
            style.removeLayer(ADSB_TRAFFIC_LEADER_LINE_LAYER_ID)
            style.removeSource(SOURCE_ID)
            style.removeSource(ADSB_TRAFFIC_LEADER_LINE_SOURCE_ID)
            removeAdsbTrafficOverlayStyleImages(style)
        } catch (t: Throwable) {
            AppLogger.w(ADSB_TRAFFIC_OVERLAY_TAG, "Failed to cleanup ADS-B overlay: ${t.message}")
        }
    }

    override fun bringToFront() {
        val style = map.style ?: return
        if (style.getLayer(ADSB_TRAFFIC_LEADER_LINE_LAYER_ID) == null ||
            style.getLayer(ADSB_TRAFFIC_ICON_LAYER_ID) == null ||
            style.getLayer(ADSB_TRAFFIC_TOP_LABEL_LAYER_ID) == null ||
            style.getLayer(ADSB_TRAFFIC_BOTTOM_LABEL_LAYER_ID) == null
        ) {
            return
        }
        try {
            val topLayerId = style.layers.lastOrNull()?.id
            if (topLayerId == ADSB_TRAFFIC_BOTTOM_LABEL_LAYER_ID) return

            style.removeLayer(ADSB_TRAFFIC_BOTTOM_LABEL_LAYER_ID)
            style.removeLayer(ADSB_TRAFFIC_TOP_LABEL_LAYER_ID)
            style.removeLayer(ADSB_TRAFFIC_ICON_LAYER_ID)
            style.removeLayer(ADSB_TRAFFIC_ICON_OUTLINE_LAYER_ID)
            style.removeLayer(ADSB_TRAFFIC_LEADER_LINE_LAYER_ID)
            style.addLayer(createAdsbLeaderLineLayer())
            style.addLayer(
                createAdsbIconOutlineLayer(
                    currentIconSizePx = currentIconSizePx,
                    viewportPolicy = currentViewportPolicy
                )
            )
            style.addLayer(
                createAdsbIconLayer(
                    currentIconSizePx = currentIconSizePx,
                    viewportPolicy = currentViewportPolicy
                )
            )
            style.addLayer(
                createAdsbTopLabelLayer(
                    currentIconSizePx = currentIconSizePx,
                    viewportPolicy = currentViewportPolicy
                )
            )
            style.addLayer(
                createAdsbBottomLabelLayer(
                    currentIconSizePx = currentIconSizePx,
                    viewportPolicy = currentViewportPolicy
                )
            )
        } catch (t: Throwable) {
            AppLogger.w(ADSB_TRAFFIC_OVERLAY_TAG, "Failed to bring ADS-B overlay to front: ${t.message}")
        }
    }

    private fun applyViewportPolicyToStyle() {
        currentViewportRangeMeters = resolveAdsbViewportRangeMeters(map)
        currentViewportPolicy = resolveAdsbTrafficViewportDeclutterPolicy(
            zoomLevel = currentViewportZoom,
            viewportRangeMeters = currentViewportRangeMeters
        )
        val effectiveViewportPolicy = resolveEffectiveViewportPolicy()
        applyAdsbViewportPolicyToStyle(
            style = map.style ?: return,
            iconSizePx = currentIconSizePx,
            viewportPolicy = effectiveViewportPolicy
        )
    }

    private fun renderFrame(
        nowMonoMs: Long,
        frameSnapshot: AdsbDisplayMotionSmoother.FrameSnapshot
    ) {
        val style = map.style ?: return
        val source = style.getSourceAs<GeoJsonSource>(SOURCE_ID) ?: return
        val leaderLineSource = style.getSourceAs<GeoJsonSource>(ADSB_TRAFFIC_LEADER_LINE_SOURCE_ID) ?: return
        val effectiveViewportPolicy = resolveEffectiveViewportPolicy()
        val renderTargets = selectRenderableAdsbTargets(
            targets = frameSnapshot.targets,
            maxTargets = effectiveViewportPolicy.maxTargets
        )
        val renderFrameSnapshot = frameSnapshot.copy(targets = renderTargets)
        val fullLabelKeys = resolveFullLabelKeys(renderTargets)
        val displayCoordinatesByKey = resolveDisplayCoordinatesByKey(renderTargets)
        renderAdsbTrafficFrame(
            source = source,
            leaderLineSource = leaderLineSource,
            nowMonoMs = nowMonoMs,
            frameSnapshot = renderFrameSnapshot,
            fullLabelKeys = fullLabelKeys,
            displayCoordinatesByKey = displayCoordinatesByKey,
            ownshipAltitudeMeters = currentOwnshipAltitudeMeters,
            unitsPreferences = currentUnitsPreferences,
            iconStyleIdOverrides = currentIconStyleIdOverrides,
            emergencyFlashEnabled = resolveEffectiveAdsbEmergencyFlashEnabled(
                emergencyFlashEnabled = emergencyFlashEnabled,
                interactionReducedMotionActive = interactionReducedMotionActive
            ),
            maxTargets = effectiveViewportPolicy.maxTargets
        )
    }

    private fun nowMonoMs(): Long = TimeBridge.nowMonoMs()

    private fun scheduleFrameLoop() {
        if (frameLoopController.schedule(frameCallback)) {
            diagnosticsState.recordAnimationFrameScheduled()
        }
    }

    private fun stopFrameLoop() {
        frameLoopController.stop(frameCallback)
    }

    private fun hasActiveVisualAnimation(
        frameSnapshot: AdsbDisplayMotionSmoother.FrameSnapshot
    ): Boolean = shouldAnimateAdsbVisuals(
        frameSnapshot = frameSnapshot,
        emergencyFlashEnabled = emergencyFlashEnabled,
        interactionReducedMotionActive = interactionReducedMotionActive
    )

    private fun resolveFullLabelKeys(
        targets: List<AdsbTrafficUiModel>
    ): Set<String> {
        if (targets.isEmpty()) return emptySet()
        val priorityRankByKey = rankAdsbTargetsForPackedGroupLabels(
            targets = targets,
            selectedTargetId = currentSelectedTargetId
        )
        val collisionSizePx = resolvePackedGroupCollisionSizePx()
        val seeds = targets.map { target ->
            TrafficPackedGroupLabelSeed(
                key = target.id.raw,
                latitude = target.lat,
                longitude = target.lon,
                collisionWidthPx = collisionSizePx,
                collisionHeightPx = collisionSizePx,
                priorityRank = priorityRankByKey.getValue(target.id.raw)
            )
        }
        return packedGroupLabelControl.resolveFullLabelKeys(
            map = map,
            seeds = seeds
        )
    }

    private fun resolveDisplayCoordinatesByKey(
        targets: List<AdsbTrafficUiModel>
    ): Map<String, TrafficDisplayCoordinate> {
        val selectedTargetId = currentSelectedTargetId ?: return emptyMap()
        if (currentViewportZoom < ADSB_TRAFFIC_LABELS_MIN_ZOOM) return emptyMap()
        val collisionSizePx = resolvePackedGroupCollisionSizePx()
        val seeds = targets.map { target ->
            TrafficPackedGroupLabelSeed(
                key = target.id.raw,
                latitude = target.lat,
                longitude = target.lon,
                collisionWidthPx = collisionSizePx,
                collisionHeightPx = collisionSizePx,
                priorityRank = 0
            )
        }
        return selectedGroupFanoutLayout.resolveDisplayCoordinatesByKey(
            map = map,
            seeds = seeds,
            selectedTargetKey = selectedTargetId.raw
        )
    }

    private fun resolvePackedGroupCollisionSizePx(): Float {
        return adsbPackedGroupCollisionSizePx(
            iconSizePx = currentIconSizePx,
            viewportPolicy = resolveEffectiveViewportPolicy(),
            density = context.resources.displayMetrics.density
        )
    }

    private fun resolveEffectiveViewportPolicy(): AdsbTrafficViewportDeclutterPolicy =
        if (interactionReducedMotionActive) {
            resolveAdsbInteractionViewportDeclutterPolicy(currentViewportPolicy)
        } else {
            currentViewportPolicy
        }

    private fun applyInteractionRenderMode() {
        diagnosticsState.setInteractionReducedMotionActive(interactionReducedMotionActive)
        applyViewportPolicyToStyle()
        val nowMonoMs = nowMonoMs()
        val frameSnapshot = motionSmoother.snapshot(nowMonoMs)
        renderFrame(nowMonoMs, frameSnapshot)
        frameLoopController.markFrameRendered(nowMonoMs)
        if (hasActiveVisualAnimation(frameSnapshot)) {
            scheduleFrameLoop()
        } else {
            stopFrameLoop()
        }
    }

    private fun activeEmergencyAnimatedTargetCount(
        frameSnapshot: AdsbDisplayMotionSmoother.FrameSnapshot
    ): Int = frameSnapshot.targets.count { target ->
        !target.isPositionStale &&
            target.proximityTier == AdsbProximityTier.EMERGENCY
    }

    private companion object {
        private const val SOURCE_ID = ADSB_TRAFFIC_SOURCE_ID
        private const val ADSB_TRAFFIC_INITIAL_VIEWPORT_ZOOM = 10f
    }
}
