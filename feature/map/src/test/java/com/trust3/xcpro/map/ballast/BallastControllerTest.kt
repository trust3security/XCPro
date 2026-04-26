package com.trust3.xcpro.map.ballast

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.trust3.xcpro.external.ExternalFlightSettingsReadPort
import com.trust3.xcpro.external.ExternalFlightSettingsSnapshot
import com.trust3.xcpro.glider.GliderRepository
import com.trust3.xcpro.glider.PolarCatalogAssetDataSource
import com.trust3.xcpro.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@OptIn(ExperimentalCoroutinesApi::class)
class BallastControllerTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var appContext: Context

    @Before
    fun setUp() {
        appContext = ApplicationProvider.getApplicationContext()
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After
    fun tearDown() {
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun external_override_ignores_local_commands_and_restores_local_control() = runTest {
        val repository = GliderRepository(appContext, PolarCatalogAssetDataSource(appContext))
        repository.selectModelById("js1-18")
        repository.updateConfig { it.copy(waterBallastKg = 40.0) }
        val externalSnapshotFlow = MutableStateFlow(ExternalFlightSettingsSnapshot())
        val controller = BallastController(
            repository = repository,
            externalFlightSettingsReadPort = FakeExternalFlightSettingsReadPort(externalSnapshotFlow),
            scope = backgroundScope,
            dispatcher = mainDispatcherRule.dispatcher
        )
        try {
            runCurrent()
            assertFalse(controller.state.value.isReadOnlyExternal)
            assertEquals(40.0, repository.config.value.waterBallastKg, 0.0)

            externalSnapshotFlow.value = ExternalFlightSettingsSnapshot(ballastOverloadFactor = 1.2)
            runCurrent()

            assertTrue(controller.state.value.isReadOnlyExternal)
            assertEquals(BallastSource.EXTERNAL, controller.state.value.snapshot.source)
            assertEquals(1.2, controller.state.value.snapshot.externalFactor ?: Double.NaN, 0.0)

            controller.submit(BallastCommand.StartFill)
            controller.submit(BallastCommand.StartDrain)
            controller.submit(BallastCommand.ImmediateSet(120.0))
            runCurrent()

            assertTrue(controller.state.value.isReadOnlyExternal)
            assertEquals(BallastMode.Idle, controller.state.value.mode)
            assertEquals(40.0, repository.config.value.waterBallastKg, 0.0)

            externalSnapshotFlow.value = ExternalFlightSettingsSnapshot()
            runCurrent()

            assertFalse(controller.state.value.isReadOnlyExternal)
            assertEquals(BallastSource.INTERNAL, controller.state.value.snapshot.source)

            controller.submit(BallastCommand.ImmediateSet(120.0))
            runCurrent()

            assertEquals(120.0, repository.config.value.waterBallastKg, 0.0)
        } finally {
            controller.dispose()
        }
    }

    private class FakeExternalFlightSettingsReadPort(
        override val externalFlightSettingsSnapshot: StateFlow<ExternalFlightSettingsSnapshot>
    ) : ExternalFlightSettingsReadPort

    private companion object {
        const val PREFS_NAME = "glider_prefs"
    }
}
