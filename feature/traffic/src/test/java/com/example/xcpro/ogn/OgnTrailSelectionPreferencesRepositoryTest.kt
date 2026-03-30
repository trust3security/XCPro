package com.example.xcpro.ogn

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class OgnTrailSelectionPreferencesRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var storeScope: CoroutineScope
    private lateinit var storeDispatcher: ExecutorCoroutineDispatcher
    private lateinit var repository: OgnTrailSelectionPreferencesRepository
    private lateinit var startupResetCoordinator: FakeStartupResetCoordinator

    @Before
    fun setUp() {
        storeDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        storeScope = CoroutineScope(SupervisorJob() + storeDispatcher)
        val storeFile = File(
            context.cacheDir,
            "ogn_trail_selection_preferences_${System.nanoTime()}.preferences_pb"
        )
        if (storeFile.exists()) {
            storeFile.delete()
        }
        val dataStore = PreferenceDataStoreFactory.create(
            scope = storeScope,
            produceFile = { storeFile }
        )
        startupResetCoordinator = FakeStartupResetCoordinator()
        repository = OgnTrailSelectionPreferencesRepository(dataStore, startupResetCoordinator)
    }

    @After
    fun tearDown() {
        runBlocking {
            storeScope.coroutineContext.job.cancelAndJoin()
        }
        storeDispatcher.close()
    }

    @Test
    fun setAircraftSelected_normalizesAndPersists() = runTest {
        repository.setAircraftSelected("  ab12cd  ", selected = true)
        val keys = repository.selectedAircraftKeysFlow.first()

        assertEquals(setOf("AB12CD"), keys)
    }

    @Test
    fun setAircraftSelected_falseClearsNormalizedKey() = runTest {
        repository.setAircraftSelected(" ab12cd ", selected = true)
        repository.setAircraftSelected("AB12CD", selected = true)

        repository.setAircraftSelected("ab12cd", selected = false)
        val keys = repository.selectedAircraftKeysFlow.first()

        assertEquals(emptySet<String>(), keys)
    }

    @Test
    fun setAircraftSelected_canonicalKeyReplacesLegacyAlias() = runTest {
        repository.setAircraftSelected("AB12CD", selected = true)

        repository.setAircraftSelected("FLARM:AB12CD", selected = true)
        val keys = repository.selectedAircraftKeysFlow.first()

        assertEquals(setOf("FLARM:AB12CD"), keys)
    }

    @Test
    fun removeAircraftKeys_removesMatchingAliases() = runTest {
        repository.setAircraftSelected("FLARM:AB12CD", selected = true)

        repository.removeAircraftKeys(setOf("AB12CD"))
        val keys = repository.selectedAircraftKeysFlow.first()

        assertEquals(emptySet<String>(), keys)
    }

    @Test
    fun removeAircraftKeys_removesLegacyWhenCanonicalAliasProvided() = runTest {
        repository.setAircraftSelected("AB12CD", selected = true)

        repository.removeAircraftKeys(setOf("FLARM:AB12CD"))
        val keys = repository.selectedAircraftKeysFlow.first()

        assertEquals(emptySet<String>(), keys)
    }

    @Test
    fun selectedAircraftKeysFlow_staysClearedWhileStartupResetIsPending() = runTest {
        repository.setAircraftSelected("FLARM:AB12CD", selected = true)
        startupResetCoordinator.setState(OgnSciaStartupResetState.PENDING)

        assertEquals(emptySet<String>(), repository.selectedAircraftKeysFlow.first())
    }

    private class FakeStartupResetCoordinator(
        initialState: OgnSciaStartupResetState = OgnSciaStartupResetState.COMPLETED
    ) : OgnSciaStartupResetCoordinator {
        private val mutableState = MutableStateFlow(initialState)

        override val resetState: StateFlow<OgnSciaStartupResetState> = mutableState

        override fun startIfNeeded() = Unit

        fun setState(state: OgnSciaStartupResetState) {
            mutableState.value = state
        }
    }
}
