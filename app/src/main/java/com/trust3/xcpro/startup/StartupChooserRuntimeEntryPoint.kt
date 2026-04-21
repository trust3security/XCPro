package com.trust3.xcpro.startup

import com.trust3.xcpro.livesource.LiveSourceStatePort
import com.trust3.xcpro.map.VarioRuntimeControlPort
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface StartupChooserRuntimeEntryPoint {
    fun varioRuntimeControlPort(): VarioRuntimeControlPort
    fun liveSourceStatePort(): LiveSourceStatePort
}
