package com.trust3.xcpro.di

import com.trust3.xcpro.common.di.DefaultDispatcher
import com.trust3.xcpro.qnh.QnhCalibrationConfig
import com.trust3.xcpro.qnh.QnhRepository
import com.trust3.xcpro.qnh.QnhRepositoryImpl
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
object QnhModule {

    @Provides
    @Singleton
    @QnhRuntimeScope
    fun provideQnhRuntimeScope(
        @DefaultDispatcher defaultDispatcher: CoroutineDispatcher
    ): CoroutineScope = CoroutineScope(SupervisorJob() + defaultDispatcher)

    @Provides
    @Singleton
    fun provideQnhRepository(
        impl: QnhRepositoryImpl
    ): QnhRepository = impl

    @Provides
    fun provideQnhCalibrationConfig(): QnhCalibrationConfig = QnhCalibrationConfig()
}
