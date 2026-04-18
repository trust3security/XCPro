package com.trust3.xcpro

import com.trust3.xcpro.common.di.IoDispatcher
import com.trust3.xcpro.core.common.logging.AppLogger
import com.trust3.xcpro.ogn.OgnSciaStartupResetCoordinator
import com.trust3.xcpro.ogn.OgnSciaStartupResetState
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Singleton
class AppOgnSciaStartupResetCoordinator @Inject constructor(
    private val sciaStartupResetterProvider: Provider<SciaStartupResetter>,
    @IoDispatcher ioDispatcher: CoroutineDispatcher
) : OgnSciaStartupResetCoordinator {

    private companion object {
        private const val TAG = "SciaStartupResetCoord"
    }

    private val started = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val mutableResetState = MutableStateFlow(OgnSciaStartupResetState.PENDING)

    override val resetState: StateFlow<OgnSciaStartupResetState> = mutableResetState.asStateFlow()

    override fun startIfNeeded() {
        if (!started.compareAndSet(false, true)) {
            return
        }
        mutableResetState.value = OgnSciaStartupResetState.RUNNING
        scope.launch {
            val nextState = try {
                sciaStartupResetterProvider.get().resetForFreshProcessStart()
                OgnSciaStartupResetState.COMPLETED
            } catch (exception: Exception) {
                AppLogger.e(TAG, "Failed to reset SCIA startup state", exception)
                OgnSciaStartupResetState.FAILED
            }
            mutableResetState.value = nextState
        }
    }
}
