package com.example.xcpro.di

import android.content.Context
import com.example.dfcards.CardPreferences
import com.example.xcpro.AirspaceRepository
import com.example.xcpro.common.units.UnitsRepository
import com.example.xcpro.common.waypoint.HomeWaypointRepository
import com.example.xcpro.core.time.Clock
import com.example.xcpro.map.QnhPreferencesRepository
import com.example.xcpro.tasks.aat.AATTaskManager
import com.example.xcpro.tasks.TaskManagerCoordinator
import com.example.xcpro.tasks.domain.engine.AATTaskEngine
import com.example.xcpro.tasks.domain.engine.RacingTaskEngine
import com.example.xcpro.tasks.domain.persistence.TaskEnginePersistenceService
import com.example.xcpro.tasks.racing.RacingTaskManager
import com.example.xcpro.profiles.ProfileStorage
import com.example.xcpro.profiles.DataStoreProfileStorage
import com.example.xcpro.vario.LevoVarioPreferencesRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideRacingTaskManager(
        @ApplicationContext context: Context
    ): RacingTaskManager = RacingTaskManager(context)

    @Provides
    @Singleton
    fun provideAATTaskManager(
        @ApplicationContext context: Context
    ): AATTaskManager = AATTaskManager(context)

    @Provides
    @Singleton
    fun provideTaskManagerCoordinator(
        taskEnginePersistenceService: TaskEnginePersistenceService,
        racingTaskEngine: RacingTaskEngine,
        aatTaskEngine: AATTaskEngine,
        racingTaskManager: RacingTaskManager,
        aatTaskManager: AATTaskManager
    ): TaskManagerCoordinator = TaskManagerCoordinator(
        taskEnginePersistenceService = taskEnginePersistenceService,
        racingTaskEngine = racingTaskEngine,
        aatTaskEngine = aatTaskEngine,
        racingTaskManager = racingTaskManager,
        aatTaskManager = aatTaskManager
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
    fun provideQnhPreferencesRepository(
        @ApplicationContext context: Context
    ): QnhPreferencesRepository = QnhPreferencesRepository(context)

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
}
