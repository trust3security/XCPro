package com.trust3.xcpro.di

import com.trust3.xcpro.map.RealWaypointLoader
import com.trust3.xcpro.common.waypoint.WaypointLoader
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent

@Module
@InstallIn(ViewModelComponent::class)
abstract class MapBindingsModule {
    @Binds
    abstract fun bindWaypointLoader(impl: RealWaypointLoader): WaypointLoader
}
