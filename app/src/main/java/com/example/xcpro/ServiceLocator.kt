package com.example.xcpro

import com.example.xcpro.map.LocationManager

object ServiceLocator {
    @Volatile
    var locationManager: LocationManager? = null
}
