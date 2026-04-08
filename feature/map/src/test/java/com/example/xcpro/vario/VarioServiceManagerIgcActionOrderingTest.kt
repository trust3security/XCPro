package com.example.xcpro.vario

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.xcpro.audio.AudioFocusManager
import com.example.xcpro.audio.VarioAudioSettings
import com.example.xcpro.common.documents.DocumentRef
import com.example.xcpro.common.flight.FlightMode
import com.example.xcpro.flightdata.FlightDataRepository
import com.example.xcpro.hawk.HawkConfigRepository
import com.example.xcpro.hawk.HawkVarioRepository
import com.example.xcpro.igc.IgcRecordingActionSink
import com.example.xcpro.igc.data.IgcFinalizeResult
import com.example.xcpro.igc.data.IgcLogEntry
import com.example.xcpro.igc.domain.IgcSessionStateMachine
import com.example.xcpro.igc.usecase.IgcRecordingUseCase
import com.example.xcpro.profiles.AircraftType
import com.example.xcpro.profiles.ProfilePreferences
import com.example.xcpro.profiles.ProfileRepository
import com.example.xcpro.profiles.UILayout
import com.example.xcpro.profiles.UIPosition
import com.example.xcpro.profiles.UnitSystem
import com.example.xcpro.profiles.UserProfile
import com.example.xcpro.sensors.CompleteFlightData
import com.example.xcpro.sensors.FlightStateSource
import com.example.xcpro.sensors.SensorFusionRepository
import com.example.xcpro.sensors.SensorStatus
import com.example.xcpro.sensors.UnifiedSensorManager
import com.example.xcpro.sensors.VarioDiagnosticsSample
import com.example.xcpro.sensors.domain.FlyingState
import com.example.xcpro.testing.MainDispatcherRule
import com.example.xcpro.weglide.data.WeGlideIgcDocumentStore
import com.example.xcpro.weglide.domain.EvaluateWeGlidePostFlightPromptUseCase
import com.example.xcpro.weglide.domain.ResolveWeGlideAircraftForProfileUseCase
import com.example.xcpro.weglide.domain.WeGlideAccountLink
import com.example.xcpro.weglide.domain.WeGlideAccountStore
import com.example.xcpro.weglide.domain.WeGlideAircraft
import com.example.xcpro.weglide.domain.WeGlideAircraftMapping
import com.example.xcpro.weglide.domain.WeGlideAircraftMappingReadRepository
import com.example.xcpro.weglide.domain.WeGlideAuthMode
import com.example.xcpro.weglide.domain.WeGlidePostFlightPromptCoordinator
import com.example.xcpro.weglide.domain.WeGlidePreferencesStore
import com.example.xcpro.weglide.domain.WeGlideUploadPreferences
import com.example.xcpro.weglide.domain.WeGlideUploadQueueRecord
import com.example.xcpro.weglide.domain.WeGlideUploadQueueRepository
import com.example.xcpro.weglide.notifications.WeGlidePostFlightPromptNotificationController
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@OptIn(ExperimentalCoroutinesApi::class)
class VarioServiceManagerIgcActionOrderingTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())

    @Test
    fun `igc finalize success is not canceled by later terminal action while prompt path suspends`() = runTest {
        val context: Context = ApplicationProvider.getApplicationContext()
        val audioFocusManager = AudioFocusManager(context)
        val serviceDispatcher = UnconfinedTestDispatcher(testScheduler)
        val unifiedSensorManager = mock<UnifiedSensorManager>()
        whenever(unifiedSensorManager.startAllSensors()).thenReturn(true)
        whenever(unifiedSensorManager.getSensorStatus()).thenReturn(readySensorStatus())

        val flightDataRepository = FlightDataRepository()
        val levoRepo = mock<LevoVarioPreferencesRepository>()
        whenever(levoRepo.config).thenReturn(MutableStateFlow(LevoVarioConfig()))
        val hawkConfigRepository = HawkConfigRepository()
        val hawkVarioRepository = mock<HawkVarioRepository>()
        val flightStateSource = object : FlightStateSource {
            override val flightState = MutableStateFlow(FlyingState())
        }

        val accountLinkFlow = MutableSharedFlow<WeGlideAccountLink?>(extraBufferCapacity = 1)
        val promptCoordinator = WeGlidePostFlightPromptCoordinator()
        val promptUseCase = EvaluateWeGlidePostFlightPromptUseCase(
            profileRepository = mock<ProfileRepository>().also { profileRepository ->
                whenever(profileRepository.activeProfile).thenReturn(MutableStateFlow(testUserProfile()))
            },
            accountStore = object : WeGlideAccountStore {
                override val accountLink = accountLinkFlow
                override suspend fun saveAccountLink(accountLink: WeGlideAccountLink) = Unit
                override suspend fun clearAccountLink() = Unit
            },
            preferencesStore = object : WeGlidePreferencesStore {
                override val preferences = MutableStateFlow(
                    WeGlideUploadPreferences(autoUploadFinishedFlights = true)
                )
                override suspend fun setAutoUploadFinishedFlights(enabled: Boolean) = Unit
                override suspend fun setUploadOnWifiOnly(enabled: Boolean) = Unit
                override suspend fun setRetryOnMobileData(enabled: Boolean) = Unit
                override suspend fun setShowCompletionNotification(enabled: Boolean) = Unit
                override suspend fun setDebugEnabled(enabled: Boolean) = Unit
            },
            queueRepository = object : WeGlideUploadQueueRepository {
                override suspend fun getByLocalFlightId(localFlightId: String): WeGlideUploadQueueRecord? = null
                override suspend fun getUploadedBySha256(sha256: String): WeGlideUploadQueueRecord? = null
                override suspend fun upsert(record: WeGlideUploadQueueRecord) = Unit
            },
            resolveWeGlideAircraftForProfileUseCase = ResolveWeGlideAircraftForProfileUseCase(
                object : WeGlideAircraftMappingReadRepository {
                    override suspend fun getMapping(profileId: String): WeGlideAircraftMapping {
                        return WeGlideAircraftMapping(
                            localProfileId = profileId,
                            weglideAircraftId = 7L,
                            weglideAircraftName = "Test Sailplane",
                            updatedAtEpochMs = 0L
                        )
                    }

                    override suspend fun getAircraftById(aircraftId: Long): WeGlideAircraft {
                        return WeGlideAircraft(
                            aircraftId = aircraftId,
                            name = "Test Sailplane",
                            kind = "glider",
                            scoringClass = "club"
                        )
                    }
                }
            ),
            igcDocumentStore = mock<WeGlideIgcDocumentStore>().also { igcDocumentStore ->
                whenever(igcDocumentStore.sha256Hex("test://igc/55")).thenReturn("sha-55")
            }
        )

        val actionFlow = MutableSharedFlow<IgcSessionStateMachine.Action>(extraBufferCapacity = 16)
        val recordingUseCase = mock<IgcRecordingUseCase>()
        whenever(recordingUseCase.actions).thenReturn(actionFlow.asSharedFlow())
        val actionSink = mock<IgcRecordingActionSink>()
        val document = DocumentRef(
            uri = "test://igc/55",
            displayName = "vario-finalize-ordering.igc"
        )
        whenever(actionSink.onFinalizeRecording(55L, 5_000L)).thenReturn(
            successfulFinalizeResult(document)
        )

        val manager = VarioServiceManager(
            audioFocusManager = audioFocusManager,
            unifiedSensorManager = unifiedSensorManager,
            sensorFusionRepository = FakeSensorFusionRepository(),
            flightDataRepository = flightDataRepository,
            levoVarioPreferencesRepository = levoRepo,
            hawkConfigRepository = hawkConfigRepository,
            hawkVarioRepository = hawkVarioRepository,
            flightStateSource = flightStateSource,
            defaultDispatcher = serviceDispatcher,
            igcRecordingUseCase = recordingUseCase,
            igcRecordingActionSink = actionSink,
            evaluateWeGlidePostFlightPromptUseCase = promptUseCase,
            weGlidePostFlightPromptCoordinator = promptCoordinator,
            weGlidePostFlightPromptNotificationController = mock<WeGlidePostFlightPromptNotificationController>()
        )

        assertTrue(manager.start(this))
        withTimeout(1_000L) {
            actionFlow.subscriptionCount.filter { it > 0 }.first()
        }

        actionFlow.emit(
            IgcSessionStateMachine.Action.FinalizeRecording(
                sessionId = 55L,
                postFlightGroundWindowMs = 5_000L
            )
        )
        advanceUntilIdle()
        verify(recordingUseCase, never()).onFinalizeSucceeded()
        verify(actionSink, never()).onMarkCompleted(55L)
        assertNull(promptCoordinator.pendingPrompt.value)

        actionFlow.emit(IgcSessionStateMachine.Action.MarkCompleted(55L))
        advanceUntilIdle()
        verify(recordingUseCase, never()).onFinalizeSucceeded()
        verify(actionSink, never()).onMarkCompleted(55L)
        assertNull(promptCoordinator.pendingPrompt.value)

        accountLinkFlow.emit(
            WeGlideAccountLink(
                userId = 100L,
                displayName = "Test Pilot",
                email = "pilot@example.com",
                connectedAtEpochMs = 0L,
                authMode = WeGlideAuthMode.OAUTH
            )
        )
        advanceUntilIdle()

        verify(recordingUseCase).onFinalizeSucceeded()
        verify(recordingUseCase, never()).onFinalizeFailed(org.mockito.kotlin.any())
        verify(actionSink).onMarkCompleted(55L)
        val prompt = promptCoordinator.pendingPrompt.value
        assertNotNull(prompt)
        assertEquals("55", prompt?.request?.localFlightId)
        assertEquals(document.displayName, prompt?.fileName)

        manager.stop()
    }

    private class FakeSensorFusionRepository : SensorFusionRepository {
        override val flightDataFlow = MutableStateFlow<CompleteFlightData?>(null)
        override val diagnosticsFlow = MutableStateFlow<VarioDiagnosticsSample?>(null)
        override val audioSettings = MutableStateFlow(VarioAudioSettings())

        override fun updateAudioSettings(settings: VarioAudioSettings) = Unit
        override fun setHawkAudioEnabled(enabled: Boolean) = Unit
        override fun setManualQnh(qnhHPa: Double) = Unit
        override fun resetQnhToStandard() = Unit
        override fun setMacCreadySetting(value: Double) = Unit
        override fun setMacCreadyRisk(value: Double) = Unit
        override fun setAutoMcEnabled(enabled: Boolean) = Unit
        override fun setTotalEnergyCompensationEnabled(enabled: Boolean) = Unit
        override fun setFlightMode(mode: FlightMode) = Unit
        override fun updateReplayRealVario(realVarioMs: Double?, timestampMillis: Long) = Unit
        override fun stop() = Unit
    }

    private fun readySensorStatus(): SensorStatus {
        return SensorStatus(
            gpsAvailable = true,
            gpsStarted = true,
            baroAvailable = true,
            baroStarted = true,
            compassAvailable = true,
            compassStarted = true,
            accelAvailable = true,
            accelStarted = true,
            rotationAvailable = true,
            rotationStarted = true,
            hasLocationPermissions = true
        )
    }

    private fun testUserProfile(): UserProfile {
        return UserProfile(
            id = "profile-1",
            name = "Test Profile",
            aircraftType = AircraftType.SAILPLANE,
            aircraftModel = "Unit Test",
            preferences = ProfilePreferences(
                units = UnitSystem.METRIC,
                uiLayout = UILayout(variometerPosition = UIPosition(0f, 0f))
            ),
            isActive = true,
            createdAt = 0L,
            lastUsed = 0L
        )
    }

    private fun successfulFinalizeResult(documentRef: DocumentRef): IgcFinalizeResult {
        return IgcFinalizeResult.Published(
            entry = IgcLogEntry(
                document = documentRef,
                displayName = documentRef.displayName ?: "vario-finalize-ordering.igc",
                sizeBytes = 128L,
                lastModifiedEpochMillis = 0L,
                utcDate = java.time.LocalDate.of(2026, 3, 9),
                durationSeconds = null
            ),
            fileName = documentRef.displayName ?: "vario-finalize-ordering.igc"
        )
    }
}
