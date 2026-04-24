package com.trust3.xcpro.map.ui

import com.trust3.xcpro.map.config.MapFeatureFlags
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface MapScreenRuntimeEntryPoint {
    fun mapFeatureFlags(): MapFeatureFlags
}
