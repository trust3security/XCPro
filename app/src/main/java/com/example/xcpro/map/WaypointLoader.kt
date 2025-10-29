package com.example.xcpro.map

import android.content.Context
import com.example.xcpro.WaypointData
import com.example.xcpro.di.IoDispatcher
import com.example.xcpro.WaypointParser
import com.example.xcpro.loadWaypointFiles
import dagger.hilt.android.scopes.ViewModelScoped
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

fun interface WaypointLoader {
    suspend fun load(context: Context): List<WaypointData>
}

@ViewModelScoped
class RealWaypointLoader @Inject constructor(
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : WaypointLoader {
    override suspend fun load(context: Context): List<WaypointData> = withContext(ioDispatcher) {
        val (waypointFiles, _) = loadWaypointFiles(context)
        waypointFiles.flatMap { uri ->
            runCatching { WaypointParser.parseWaypointFile(context, uri) }
                .getOrElse { emptyList() }
        }
    }
}
