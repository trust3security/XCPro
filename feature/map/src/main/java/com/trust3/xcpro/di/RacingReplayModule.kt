package com.trust3.xcpro.di

import com.trust3.xcpro.map.replay.RacingReplayLogBuilder
import com.trust3.xcpro.tasks.racing.boundary.RacingBoundaryEpsilonPolicy
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
}
