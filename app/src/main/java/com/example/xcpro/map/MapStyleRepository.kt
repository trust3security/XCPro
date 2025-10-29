package com.example.xcpro.map

import android.content.Context
import com.example.xcpro.navdrawer.loadConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MapStyleRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun initialStyle(): String {
        val defaultStyle = "Topo"
        return runCatching {
            val config = loadConfig(context)
            config
                ?.optJSONObject("app")
                ?.optString("mapStyle")
                ?.takeUnless { it.isNullOrBlank() }
        }.getOrNull() ?: defaultStyle
    }
}
