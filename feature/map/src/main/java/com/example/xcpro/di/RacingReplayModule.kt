package com.example.xcpro.di

import com.example.xcpro.map.replay.RacingReplayLogBuilder
import com.example.xcpro.map.replay.SyntheticThermalReplayLogBuilder
import com.example.xcpro.tasks.racing.boundary.RacingBoundaryEpsilonPolicy
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent

@Module
@InstallIn(ViewModelComponent::class)
object RacingReplayModule {

    @Provides
    fun provideRacingReplayLogBuilder(
        epsilonPolicy: RacingBoundaryEpsilonPolicy
    ): RacingReplayLogBuilder = RacingReplayLogBuilder(epsilonPolicy = epsilonPolicy)

    @Provides
    fun provideSyntheticThermalReplayLogBuilder(): SyntheticThermalReplayLogBuilder =
        SyntheticThermalReplayLogBuilder()
}
