package com.trust3.xcpro.bluetooth

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidBluetoothConnectPermissionPort @Inject constructor(
    @ApplicationContext private val context: Context
) : BluetoothConnectPermissionPort {
    override fun isGranted(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
}
