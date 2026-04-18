package com.trust3.xcpro.di

import com.trust3.xcpro.tasks.TaskFeatureFlags
import com.trust3.xcpro.tasks.TaskManagerCoordinator
import com.trust3.xcpro.tasks.TaskNavigationController
import com.trust3.xcpro.tasks.racing.boundary.RacingBoundaryCrossingPlanner
import com.trust3.xcpro.tasks.racing.boundary.RacingBoundaryEpsilonPolicy
import com.trust3.xcpro.tasks.racing.navigation.RacingNavigationEngine
import com.trust3.xcpro.tasks.racing.navigation.RacingNavigationStateStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ViewModelScoped

@Module
@InstallIn(ViewModelComponent::class)
object TaskNavigationModule {

    @Provides
    @ViewModelScoped
    fun provideRacingBoundaryEpsilonPolicy(): RacingBoundaryEpsilonPolicy = RacingBoundaryEpsilonPolicy()

    @Provides
    @ViewModelScoped
    fun provideRacingBoundaryCrossingPlanner(
        epsilonPolicy: RacingBoundaryEpsilonPolicy
    ): RacingBoundaryCrossingPlanner = RacingBoundaryCrossingPlanner(epsilonPolicy)

    @Provides
    @ViewModelScoped
    fun provideTaskNavigationController(
        taskManager: TaskManagerCoordinator,
        crossingPlanner: RacingBoundaryCrossingPlanner,
        epsilonPolicy: RacingBoundaryEpsilonPolicy,
        featureFlags: TaskFeatureFlags
    ): TaskNavigationController = TaskNavigationController(
        taskManager = taskManager,
        stateStore = RacingNavigationStateStore(),
        engine = RacingNavigationEngine(
            crossingPlanner = crossingPlanner,
            epsilonPolicy = epsilonPolicy
        ),
        featureFlags = featureFlags
    )
}
