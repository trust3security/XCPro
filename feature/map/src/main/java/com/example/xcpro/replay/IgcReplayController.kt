package com.example.xcpro.replay

import android.content.Context
import android.net.Uri
import com.example.xcpro.core.common.logging.AppLogger
import com.example.xcpro.common.documents.DocumentRef
import com.example.xcpro.flightdata.FlightDataRepository
import com.example.xcpro.weather.wind.data.ReplayAirspeedRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.isActive
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.coroutines.coroutineContext


@Singleton
class IgcReplayController @Inject constructor(
    @ApplicationContext appContext: Context,
    flightDataRepository: FlightDataRepository,
    replaySensorSource: ReplaySensorSource,
    replayAirspeedRepository: ReplayAirspeedRepository,
    replayPipelineFactory: ReplayPipelineFactory,
    igcParser: IgcParser
) : IgcReplayControllerRuntime(
    appContext = appContext,
    flightDataRepository = flightDataRepository,
    replaySensorSource = replaySensorSource,
    replayAirspeedRepository = replayAirspeedRepository,
    replayPipelineFactory = replayPipelineFactory,
    igcParser = igcParser
), IgcReplayControllerPort
