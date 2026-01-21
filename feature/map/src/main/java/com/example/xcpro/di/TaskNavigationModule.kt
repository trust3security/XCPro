package com.example.xcpro.di

import com.example.xcpro.tasks.TaskFeatureFlags
import com.example.xcpro.tasks.TaskManagerCoordinator
import com.example.xcpro.tasks.TaskNavigationController
import com.example.xcpro.tasks.racing.boundary.RacingBoundaryCrossingPlanner
import com.example.xcpro.tasks.racing.boundary.RacingBoundaryEpsilonPolicy
import com.example.xcpro.tasks.racing.navigation.RacingAdvanceState
import com.example.xcpro.tasks.racing.navigation.RacingNavigationEngine
import com.example.xcpro.tasks.racing.navigation.RacingNavigationStateStore
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
        epsilonPolicy: RacingBoundaryEpsilonPolicy
    ): TaskNavigationController = TaskNavigationController(
        taskManager = taskManager,
        stateStore = RacingNavigationStateStore(),
        advanceState = RacingAdvanceState(),
        engine = RacingNavigationEngine(
            crossingPlanner = crossingPlanner,
            epsilonPolicy = epsilonPolicy
        ),
        featureFlags = TaskFeatureFlags
    )
}
