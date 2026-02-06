package com.example.xcpro.profiles.ui

import androidx.compose.ui.graphics.vector.ImageVector
import com.example.ui1.icons.Glider
import com.example.ui1.icons.Hangglider
import com.example.ui1.icons.Paraglider
import com.example.ui1.icons.Sailplane
import com.example.xcpro.profiles.AircraftType

fun AircraftType.icon(): ImageVector = when (this) {
    AircraftType.PARAGLIDER -> Paraglider
    AircraftType.HANG_GLIDER -> Hangglider
    AircraftType.SAILPLANE -> Sailplane
    AircraftType.GLIDER -> Glider
}
