package com.example.xcpro.map.ui

import androidx.compose.runtime.Composable
import com.example.xcpro.map.MapScreenViewModel
import com.example.xcpro.map.MapStateReader

internal data class MapScreenBindings(
    val map: MapScreenMapBindings,
    val session: MapScreenSessionBindings,
    val task: MapScreenTaskBindings,
    val traffic: MapTrafficUiBinding
)

@Composable
internal fun rememberMapScreenBindings(
    mapViewModel: MapScreenViewModel,
    mapStateReader: MapStateReader
): MapScreenBindings =
    MapScreenBindings(
        map = rememberMapScreenMapBindings(
            mapViewModel = mapViewModel,
            mapStateReader = mapStateReader
        ),
        session = rememberMapScreenSessionBindings(mapViewModel = mapViewModel),
        task = rememberMapScreenTaskBindings(
            mapViewModel = mapViewModel,
            mapStateReader = mapStateReader
        ),
        traffic = rememberMapScreenTrafficBinding(mapViewModel = mapViewModel)
    )
