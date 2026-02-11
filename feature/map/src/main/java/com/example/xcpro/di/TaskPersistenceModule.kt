package com.example.xcpro.di

import com.example.xcpro.tasks.data.persistence.AATTaskPersistenceAdapter
import com.example.xcpro.tasks.data.persistence.RacingTaskPersistenceAdapter
import com.example.xcpro.tasks.data.persistence.SharedPrefsTaskTypeSettingsRepository
import com.example.xcpro.tasks.domain.engine.AATTaskEngine
import com.example.xcpro.tasks.domain.engine.DefaultAATTaskEngine
import com.example.xcpro.tasks.domain.engine.DefaultRacingTaskEngine
import com.example.xcpro.tasks.domain.engine.RacingTaskEngine
import com.example.xcpro.tasks.domain.persistence.AATTaskPersistence
import com.example.xcpro.tasks.domain.persistence.RacingTaskPersistence
import com.example.xcpro.tasks.domain.persistence.TaskTypeSettingsRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TaskPersistenceBindingsModule {

    @Binds
    @Singleton
    abstract fun bindTaskTypeSettingsRepository(
        impl: SharedPrefsTaskTypeSettingsRepository
    ): TaskTypeSettingsRepository

    @Binds
    @Singleton
    abstract fun bindRacingTaskPersistence(
        impl: RacingTaskPersistenceAdapter
    ): RacingTaskPersistence

    @Binds
    @Singleton
    abstract fun bindAATTaskPersistence(
        impl: AATTaskPersistenceAdapter
    ): AATTaskPersistence
}

@Module
@InstallIn(SingletonComponent::class)
object TaskEngineModule {

    @Provides
    @Singleton
    fun provideRacingTaskEngine(): RacingTaskEngine = DefaultRacingTaskEngine()

    @Provides
    @Singleton
    fun provideAATTaskEngine(): AATTaskEngine = DefaultAATTaskEngine()
}
