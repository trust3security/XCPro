package com.example.xcpro.di

import android.content.Context
import androidx.room.Room
import com.example.xcpro.adsb.metadata.data.AdsbMetadataDatabase
import com.example.xcpro.adsb.metadata.data.AircraftMetadataDao
import com.example.xcpro.adsb.metadata.data.AircraftMetadataRepositoryImpl
import com.example.xcpro.adsb.metadata.data.AircraftMetadataSyncPolicy
import com.example.xcpro.adsb.metadata.data.AircraftMetadataSyncRepositoryImpl
import com.example.xcpro.adsb.metadata.data.AircraftMetadataSyncSchedulerImpl
import com.example.xcpro.adsb.metadata.domain.AircraftMetadataRepository
import com.example.xcpro.adsb.metadata.domain.AircraftMetadataSyncRepository
import com.example.xcpro.adsb.metadata.domain.AircraftMetadataSyncScheduler
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AdsbMetadataProvidesModule {

    @Provides
    @Singleton
    fun provideAdsbMetadataDatabase(
        @ApplicationContext context: Context
    ): AdsbMetadataDatabase {
        return Room.databaseBuilder(
            context,
            AdsbMetadataDatabase::class.java,
            AircraftMetadataSyncPolicy.DATABASE_NAME
        ).build()
    }

    @Provides
    fun provideAircraftMetadataDao(
        database: AdsbMetadataDatabase
    ): AircraftMetadataDao = database.aircraftMetadataDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AdsbMetadataBindingsModule {

    @Binds
    abstract fun bindAircraftMetadataRepository(
        impl: AircraftMetadataRepositoryImpl
    ): AircraftMetadataRepository

    @Binds
    abstract fun bindAircraftMetadataSyncRepository(
        impl: AircraftMetadataSyncRepositoryImpl
    ): AircraftMetadataSyncRepository

    @Binds
    abstract fun bindAircraftMetadataSyncScheduler(
        impl: AircraftMetadataSyncSchedulerImpl
    ): AircraftMetadataSyncScheduler
}

