package com.example.xcpro.di

import android.content.Context
import com.example.dfcards.CardPreferences
import com.example.xcpro.AirspaceRepository
import com.example.xcpro.AppOgnSciaStartupResetCoordinator
import com.example.xcpro.BuildConfig
import com.example.xcpro.adsb.OpenSkyClientCredentials
import com.example.xcpro.adsb.OpenSkyConfiguredCredentialsProvider
import com.example.xcpro.common.units.UnitsRepository
import com.example.xcpro.common.waypoint.HomeWaypointRepository
import com.example.xcpro.core.time.Clock
import com.example.xcpro.igc.AppIgcRecoveryDiagnosticsReporter
import com.example.xcpro.igc.domain.IgcRecoveryDiagnosticsReporter
import com.example.xcpro.ogn.OgnSciaStartupResetCoordinator
import com.example.xcpro.tasks.aat.AATTaskManager
import com.example.xcpro.common.di.IoDispatcher
import com.example.xcpro.tasks.TaskManagerCoordinator
import com.example.xcpro.tasks.TaskCoordinatorScope
import com.example.xcpro.tasks.domain.engine.AATTaskEngine
import com.example.xcpro.tasks.domain.engine.RacingTaskEngine
import com.example.xcpro.tasks.domain.persistence.TaskEnginePersistenceService
import com.example.xcpro.tasks.racing.RacingTaskManager
import com.example.xcpro.profiles.AppProfileDiagnosticsReporter
import com.example.xcpro.profiles.DownloadsProfileBackupSink
import com.example.xcpro.profiles.ProfileBackupSink
import com.example.xcpro.profiles.ProfileDiagnosticsReporter
import com.example.xcpro.profiles.ProfileStorage
import com.example.xcpro.profiles.DataStoreProfileStorage
import com.example.xcpro.vario.LevoVarioPreferencesRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideRacingTaskManager(): RacingTaskManager = RacingTaskManager()

    @Provides
    @Singleton
    fun provideAATTaskManager(): AATTaskManager = AATTaskManager()

    @Provides
    @Singleton
    @TaskCoordinatorScope
    fun provideTaskCoordinatorScope(
        @IoDispatcher dispatcher: CoroutineDispatcher
    ): CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher)

    @Provides
    @Singleton
    fun provideTaskManagerCoordinator(
        taskEnginePersistenceService: TaskEnginePersistenceService,
        racingTaskEngine: RacingTaskEngine,
        aatTaskEngine: AATTaskEngine,
        racingTaskManager: RacingTaskManager,
        aatTaskManager: AATTaskManager,
        @TaskCoordinatorScope coordinatorScope: CoroutineScope
    ): TaskManagerCoordinator = TaskManagerCoordinator(
        taskEnginePersistenceService = taskEnginePersistenceService,
        racingTaskEngine = racingTaskEngine,
        aatTaskEngine = aatTaskEngine,
        racingTaskManager = racingTaskManager,
        aatTaskManager = aatTaskManager,
        coordinatorScope = coordinatorScope
    )

    @Provides
    @Singleton
    fun provideCardPreferences(
        @ApplicationContext context: Context,
        clock: Clock
    ): CardPreferences = CardPreferences(context, clock)

    @Provides
    @Singleton
    fun provideUnitsRepository(
        @ApplicationContext context: Context
    ): UnitsRepository = UnitsRepository(context)

    @Provides
    @Singleton
    fun provideLevoVarioPreferencesRepository(
        @ApplicationContext context: Context
    ): LevoVarioPreferencesRepository = LevoVarioPreferencesRepository(context)

    @Provides
    @Singleton
    fun provideAirspaceRepository(
        @ApplicationContext context: Context
    ): AirspaceRepository = AirspaceRepository(context)

    @Provides
    @Singleton
    fun provideHomeWaypointRepository(
        @ApplicationContext context: Context
    ): HomeWaypointRepository = HomeWaypointRepository(context)

    @Provides
    @Singleton
    fun provideProfileStorage(
        @ApplicationContext context: Context
    ): ProfileStorage = DataStoreProfileStorage(context)

    @Provides
    @Singleton
    fun provideProfileBackupSink(
        @ApplicationContext context: Context,
        clock: Clock
    ): ProfileBackupSink = DownloadsProfileBackupSink(context, clock)

    @Provides
    @Singleton
    fun provideProfileDiagnosticsReporter(
        reporter: AppProfileDiagnosticsReporter
    ): ProfileDiagnosticsReporter = reporter

    @Provides
    @Singleton
    fun provideIgcRecoveryDiagnosticsReporter(
        reporter: AppIgcRecoveryDiagnosticsReporter
    ): IgcRecoveryDiagnosticsReporter = reporter

    @Provides
    @Singleton
    fun provideOgnSciaStartupResetCoordinator(
        coordinator: AppOgnSciaStartupResetCoordinator
    ): OgnSciaStartupResetCoordinator = coordinator

    @Provides
    @Singleton
    fun provideOpenSkyConfiguredCredentialsProvider(): OpenSkyConfiguredCredentialsProvider {
        return object : OpenSkyConfiguredCredentialsProvider {
            override fun loadConfiguredCredentials(): OpenSkyClientCredentials? {
                val clientId = BuildConfig.OPENSKY_CLIENT_ID.trim()
                val clientSecret = BuildConfig.OPENSKY_CLIENT_SECRET.trim()
                if (clientId.isBlank() || clientSecret.isBlank()) {
                    return null
                }
                return OpenSkyClientCredentials(
                    clientId = clientId,
                    clientSecret = clientSecret
                )
            }
        }
    }
}
