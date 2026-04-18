package com.trust3.xcpro.map

class MapLocationRenderFrameBinderAdapter(
    useRenderFrameSync: () -> Boolean,
    onRenderFrame: () -> Unit,
    renderSurfaceDiagnostics: MapRenderSurfaceDiagnostics
) : MapLocationRenderFrameBinder, MapRenderFrameCleanupPort {
    private val renderFrameSync = RenderFrameSync(
        isEnabled = useRenderFrameSync,
        onRenderFrame = onRenderFrame,
        diagnostics = renderSurfaceDiagnostics
    )

    override fun bindRenderFrameListener(mapView: org.maplibre.android.maps.MapView) {
        renderFrameSync.bind(mapView)
    }

    override fun unbindRenderFrameListener() {
        renderFrameSync.unbind()
    }
}
