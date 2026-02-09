package com.example.xcpro.di

import com.example.xcpro.common.waypoint.WaypointLoader
import com.example.xcpro.common.glider.GliderConfigRepository
import com.example.xcpro.glider.GliderRepository
import com.example.xcpro.map.RealWaypointLoader
import com.example.xcpro.ogn.OgnTrafficRepository
import com.example.xcpro.ogn.OgnTrafficRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(ViewModelComponent::class)
abstract class MapBindingsModule {
    @Binds
    abstract fun bindWaypointLoader(impl: RealWaypointLoader): WaypointLoader

}

@Module
@InstallIn(SingletonComponent::class)
abstract class GliderRepositoryModule {
    @Binds
    abstract fun bindGliderConfigRepository(impl: GliderRepository): GliderConfigRepository
}

@Module
@InstallIn(SingletonComponent::class)
abstract class OgnBindingsModule {
    @Binds
    abstract fun bindOgnTrafficRepository(impl: OgnTrafficRepositoryImpl): OgnTrafficRepository
}
