package com.example.xcpro.di

import com.example.xcpro.map.replay.RacingReplayLogBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent

@Module
@InstallIn(ViewModelComponent::class)
object RacingReplayModule {

    @Provides
    fun provideRacingReplayLogBuilder(): RacingReplayLogBuilder = RacingReplayLogBuilder()
}
