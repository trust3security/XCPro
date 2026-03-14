package com.example.xcpro.map

class MapLocationRenderFrameBinderAdapter(
    useRenderFrameSync: () -> Boolean,
    onRenderFrame: () -> Unit
) : MapLocationRenderFrameBinder, MapRenderFrameCleanupPort {
    private val renderFrameSync = RenderFrameSync(
        isEnabled = useRenderFrameSync,
        onRenderFrame = onRenderFrame
    )

    override fun bindRenderFrameListener(mapView: org.maplibre.android.maps.MapView) {
        renderFrameSync.bind(mapView)
    }

    override fun unbindRenderFrameListener() {
        renderFrameSync.unbind()
    }
}
