package com.example.xcpro.map

import android.content.Context
import com.example.xcpro.ConfigurationRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MapStyleRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun initialStyle(): String {
        val defaultStyle = "Topo"
        val cached = ConfigurationRepository(context).getCachedConfig()
        return cached
            ?.optJSONObject("app")
            ?.optString("mapStyle")
            ?.takeUnless { it.isNullOrBlank() }
            ?: defaultStyle
    }
}
