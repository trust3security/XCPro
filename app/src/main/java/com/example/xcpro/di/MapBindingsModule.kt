package com.example.xcpro.di

import com.example.xcpro.map.RealWaypointLoader
import com.example.xcpro.map.WaypointLoader
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
