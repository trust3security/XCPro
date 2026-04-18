package com.trust3.xcpro.ogn

import com.trust3.xcpro.common.di.DefaultDispatcher
import com.trust3.xcpro.core.time.Clock
import com.trust3.xcpro.di.OgnHotspotsDisplayPercentFlow
import com.trust3.xcpro.di.OgnThermalRetentionHoursFlow
import com.trust3.xcpro.di.OgnThermalZoneId
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock


interface OgnThermalRepository {
    val hotspots: StateFlow<List<OgnThermalHotspot>>
}

@Singleton
class OgnThermalRepositoryImpl @Inject constructor(
    ognTrafficRepository: OgnTrafficRepository,
    clock: Clock,
    @OgnThermalRetentionHoursFlow thermalRetentionHoursFlow: Flow<Int>,
    @OgnHotspotsDisplayPercentFlow hotspotsDisplayPercentFlow: Flow<Int> =
        flowOf(OGN_HOTSPOTS_DISPLAY_PERCENT_DEFAULT),
    @OgnThermalZoneId localZoneId: ZoneId,
    @DefaultDispatcher dispatcher: CoroutineDispatcher
) : OgnThermalRepository {

    private val runtime = OgnThermalRepositoryRuntime(
        ognTrafficRepository = ognTrafficRepository,
        clock = clock,
        thermalRetentionHoursFlow = thermalRetentionHoursFlow,
        hotspotsDisplayPercentFlow = hotspotsDisplayPercentFlow,
        localZoneId = localZoneId,
        dispatcher = dispatcher
    ) 

    @Suppress("unused")
    private val trackerByTargetId = runtime.trackerByTargetIdForTests

    override val hotspots: StateFlow<List<OgnThermalHotspot>>
        get() = runtime.hotspots
}
