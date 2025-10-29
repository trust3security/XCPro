package com.example.xcpro.di

import android.content.Context
import com.example.dfcards.CardPreferences
import com.example.xcpro.common.units.UnitsRepository
import com.example.xcpro.map.QnhPreferencesRepository
import com.example.xcpro.tasks.TaskManagerCoordinator
import com.example.xcpro.tasks.getGlobalTaskManagerCoordinator
import com.example.xcpro.tasks.setGlobalTaskManagerCoordinator
import com.example.xcpro.profiles.ProfileStorage
import com.example.xcpro.profiles.DataStoreProfileStorage
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
    fun provideTaskManagerCoordinator(
        @ApplicationContext context: Context
    ): TaskManagerCoordinator {
        val existing = getGlobalTaskManagerCoordinator()
        if (existing != null) {
            return existing
        }
        val coordinator = TaskManagerCoordinator(context)
        setGlobalTaskManagerCoordinator(coordinator)
        return coordinator
    }

    @Provides
    @Singleton
    fun provideCardPreferences(
        @ApplicationContext context: Context
    ): CardPreferences = CardPreferences(context)

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
    fun provideProfileStorage(
        @ApplicationContext context: Context
    ): ProfileStorage = DataStoreProfileStorage(context)
}
