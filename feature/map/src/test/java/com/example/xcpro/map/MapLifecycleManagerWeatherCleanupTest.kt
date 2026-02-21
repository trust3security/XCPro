package com.example.xcpro.map

import androidx.lifecycle.Lifecycle
import com.example.xcpro.MapOrientationManager
import com.example.xcpro.replay.SessionState
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class MapLifecycleManagerWeatherCleanupTest {

    @Test
    fun cleanup_clearsWeatherOverlayHandle_andInvokesCleanup() {
        val mapState = MapScreenState()
        val weatherOverlay: WeatherRainOverlay = mock()
        mapState.weatherRainOverlay = weatherOverlay
        val manager = createLifecycleManager(mapState)

        manager.cleanup()

        verify(weatherOverlay, times(1)).cleanup()
        assertNull(mapState.weatherRainOverlay)
    }

    @Test
    fun onDestroyEvent_clearsWeatherOverlayHandle_andInvokesCleanup() {
        val mapState = MapScreenState()
        val weatherOverlay: WeatherRainOverlay = mock()
        mapState.weatherRainOverlay = weatherOverlay
        val manager = createLifecycleManager(mapState)

        manager.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)

        verify(weatherOverlay, times(1)).cleanup()
        assertNull(mapState.weatherRainOverlay)
    }

    private fun createLifecycleManager(mapState: MapScreenState): MapLifecycleManager {
        return MapLifecycleManager(
            mapState = mapState,
            orientationManager = mock<MapOrientationManager>(),
            locationManager = mock<LocationManager>(),
            replaySessionState = MutableStateFlow(SessionState()),
            stateActions = mock<MapStateActions>()
        )
    }
}
