package com.example.dfcards.dfcards.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DfCardsIoDispatcher

@Module
@InstallIn(SingletonComponent::class)
object DfCardsDispatchersModule {
    @Provides
    @DfCardsIoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
}
