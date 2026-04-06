package com.example.xcpro.profiles

import com.example.xcpro.common.di.IoDispatcher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
object ProfileRepositoryRuntimeModule {

    @Provides
    @Singleton
    @ProfileRepositoryScope
    fun provideProfileRepositoryScope(
        @IoDispatcher dispatcher: CoroutineDispatcher
    ): CoroutineScope =
        // AI-NOTE: ProfileRepository continuously coordinates storage hydration
        // and managed backup sync, so its long-lived scope is DI-owned.
        CoroutineScope(SupervisorJob() + dispatcher)
}
