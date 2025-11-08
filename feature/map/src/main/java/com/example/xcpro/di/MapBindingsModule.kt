package com.example.xcpro.di

import android.content.Context
import com.example.xcpro.common.waypoint.WaypointLoader
import com.example.xcpro.glider.GliderRepository
import com.example.xcpro.map.RealWaypointLoader
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.qualifiers.ApplicationContext

@Module
@InstallIn(ViewModelComponent::class)
abstract class MapBindingsModule {
    @Binds
    abstract fun bindWaypointLoader(impl: RealWaypointLoader): WaypointLoader

}

@Module
@InstallIn(SingletonComponent::class)
object GliderRepositoryModule {
    @Provides
    fun provideGliderRepository(
        @ApplicationContext context: Context
    ): GliderRepository = GliderRepository.getInstance(context)
}
