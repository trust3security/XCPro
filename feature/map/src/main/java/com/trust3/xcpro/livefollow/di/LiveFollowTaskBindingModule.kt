package com.trust3.xcpro.livefollow.di

import com.trust3.xcpro.livefollow.data.task.LiveFollowTaskSnapshotSource
import com.trust3.xcpro.livefollow.data.task.TaskCoordinatorLiveFollowTaskSnapshotSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LiveFollowTaskBindingModule {
    @Binds
    @Singleton
    abstract fun bindLiveFollowTaskSnapshotSource(
        implementation: TaskCoordinatorLiveFollowTaskSnapshotSource
    ): LiveFollowTaskSnapshotSource
}
