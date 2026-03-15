package com.example.xcpro.hawk

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class HawkAudioVarioReadPortAdapter @Inject constructor(
    private val repository: HawkVarioRepository
) : HawkAudioVarioReadPort {
    override val audioVarioMps: Flow<Double?> = repository.output.map { output ->
        output?.vAudioMps?.takeIf { it.isFinite() }
    }
}
