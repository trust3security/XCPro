package com.trust3.xcpro.weglide.di

import com.trust3.xcpro.weglide.api.WeGlideUploadApi
import com.trust3.xcpro.weglide.data.RoomWeGlideUploadQueueRepository
import com.trust3.xcpro.weglide.data.WeGlideFlightUploadRepositoryImpl
import com.trust3.xcpro.weglide.data.WeGlideUploadWorkSchedulerImpl
import com.trust3.xcpro.weglide.domain.WeGlideFlightUploadRepository
import com.trust3.xcpro.weglide.domain.WeGlideUploadQueueRepository
import com.trust3.xcpro.weglide.domain.WeGlideUploadWorkScheduler
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

@Module
@InstallIn(SingletonComponent::class)
abstract class WeGlideUploadModule {

    @Binds
    @Singleton
    abstract fun bindWeGlideUploadQueueRepository(
        impl: RoomWeGlideUploadQueueRepository
    ): WeGlideUploadQueueRepository

    @Binds
    @Singleton
    abstract fun bindWeGlideFlightUploadRepository(
        impl: WeGlideFlightUploadRepositoryImpl
    ): WeGlideFlightUploadRepository

    @Binds
    @Singleton
    abstract fun bindWeGlideUploadWorkScheduler(
        impl: WeGlideUploadWorkSchedulerImpl
    ): WeGlideUploadWorkScheduler

    companion object {
        private const val WEGLIDE_RETROFIT_BASE_URL = "https://api.weglide.org/"

        @Provides
        @Singleton
        fun provideWeGlideUploadApi(): WeGlideUploadApi {
            return Retrofit.Builder()
                .baseUrl(WEGLIDE_RETROFIT_BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(WeGlideUploadApi::class.java)
        }
    }
}
