package com.trust3.xcpro.variometer.bluetooth.lxnav.control

import com.trust3.xcpro.bluetooth.BluetoothConnectionState
import com.trust3.xcpro.bluetooth.BondedBluetoothDevice
import com.trust3.xcpro.variometer.bluetooth.lxnav.runtime.LxExternalRuntimeSnapshot
import java.util.Locale

internal data class ControlStateInputs(
    val permissionRequired: Boolean,
    val bondedDevices: List<BondedBluetoothDevice>,
    val selectedDevice: PersistedLxBluetoothDevice?,
    val connectionState: BluetoothConnectionState
)

internal fun buildLxBluetoothControlState(
    permissionRequired: Boolean,
    bondedDevices: List<BondedBluetoothDevice>,
    selectedDevice: PersistedLxBluetoothDevice?,
    connectionState: BluetoothConnectionState,
    runtimeSnapshot: LxExternalRuntimeSnapshot,
    reconnectState: LxBluetoothReconnectState,
    reconnectCount: Int,
    lastDisconnectReason: LxBluetoothDisconnectReason?,
    nowMonoMs: Long,
    streamStaleMs: Long
): LxBluetoothControlState {
    val selectedBondedDevice = selectedDevice?.address?.let { address ->
        bondedDevices.firstOrNull { it.address == address }
    }
    val selectedDisplayName =
        selectedBondedDevice?.displayName ?: selectedDevice?.displayNameSnapshot
    val activeDevice = when (connectionState) {
        is BluetoothConnectionState.Connected -> connectionState.device
        is BluetoothConnectionState.Connecting -> connectionState.device
        is BluetoothConnectionState.Error -> connectionState.device
        BluetoothConnectionState.Disconnected -> null
    }
    val lastError = (connectionState as? BluetoothConnectionState.Error)?.error
    val lastReceivedMonoMs = runtimeSnapshot.diagnostics.lastReceivedMonoMs
    val lastReceivedAgeMs =
        lastReceivedMonoMs?.let { (nowMonoMs - it).coerceAtLeast(0L) }
    val streamAlive =
        connectionState is BluetoothConnectionState.Connected &&
            lastReceivedAgeMs != null &&
            lastReceivedAgeMs <= streamStaleMs
    val reconnectActive =
        reconnectState is LxBluetoothReconnectState.Waiting ||
            reconnectState is LxBluetoothReconnectState.Attempting
    val canConnect =
        !permissionRequired &&
            selectedDevice != null &&
            selectedBondedDevice != null &&
            connectionState !is BluetoothConnectionState.Connecting &&
            connectionState !is BluetoothConnectionState.Connected &&
            !reconnectActive
    val canDisconnect =
        reconnectActive || connectionState !is BluetoothConnectionState.Disconnected

    return LxBluetoothControlState(
        permissionRequired = permissionRequired,
        bondedDevices = bondedDevices.map { bondedDevice ->
            BluetoothBondedDeviceItem(
                address = bondedDevice.address,
                displayName = bondedDevice.displayName
            )
        },
        selectedDeviceAddress = selectedDevice?.address,
        selectedDeviceDisplayName = selectedDisplayName,
        selectedDeviceAvailable = selectedDevice != null && selectedBondedDevice != null,
        activeDeviceAddress = activeDevice?.address,
        activeDeviceName = activeDevice?.displayName,
        connectionState = connectionState,
        lastError = lastError,
        lastDisconnectReason = lastDisconnectReason,
        reconnectState = reconnectState,
        reconnectCount = reconnectCount,
        streamAlive = streamAlive,
        lastReceivedMonoMs = lastReceivedMonoMs,
        lastReceivedAgeMs = lastReceivedAgeMs,
        rollingSentenceRatePerSecond = runtimeSnapshot.diagnostics.rollingSentenceRatePerSecond,
        detailSections = runtimeSnapshot.toDetailSections(),
        canConnect = canConnect,
        canDisconnect = canDisconnect
    )
}

private fun LxExternalRuntimeSnapshot.toDetailSections(): List<LxBluetoothDetailSection> {
    val sections = mutableListOf<LxBluetoothDetailSection>()

    val deviceInfoRows = buildList {
        deviceInfo?.product?.let { add(LxBluetoothDetailRow("Product", it)) }
        deviceInfo?.serial?.let { add(LxBluetoothDetailRow("Serial", it)) }
        deviceInfo?.softwareVersion?.let { add(LxBluetoothDetailRow("Software", it)) }
        deviceInfo?.hardwareVersion?.let { add(LxBluetoothDetailRow("Hardware", it)) }
    }
    if (deviceInfoRows.isNotEmpty()) {
        sections += LxBluetoothDetailSection(
            title = "Device info",
            rows = deviceInfoRows
        )
    }

    val activeOverrideRows = buildList {
        liveSettingsOverrides.macCreadyMps?.value?.let {
            add(LxBluetoothDetailRow("MacCready", formatDouble(it, 1, "m/s")))
        }
        liveSettingsOverrides.bugsPercent?.value?.let {
            add(LxBluetoothDetailRow("Bugs", "${it.coerceIn(0, 50)}%"))
        }
        liveSettingsOverrides.ballastOverloadFactor?.value?.let {
            add(LxBluetoothDetailRow("Ballast", formatDouble(it, 2, "x")))
        }
        liveSettingsOverrides.qnhHpa?.value?.let {
            add(LxBluetoothDetailRow("QNH", formatDouble(it, 1, "hPa")))
        }
    }
    if (activeOverrideRows.isNotEmpty()) {
        sections += LxBluetoothDetailSection(
            title = "Active overrides",
            rows = activeOverrideRows
        )
    }

    val environmentRows = buildList {
        environmentStatus.outsideAirTemperatureC?.value?.let {
            add(LxBluetoothDetailRow("OAT", formatSignedDouble(it, 1, "C")))
        }
        environmentStatus.mode?.value?.let {
            add(LxBluetoothDetailRow("Mode", it.toFlightModeLabel()))
        }
        environmentStatus.voltageV?.value?.let {
            add(LxBluetoothDetailRow("Voltage", formatDouble(it, 1, "V")))
        }
    }
    if (environmentRows.isNotEmpty()) {
        sections += LxBluetoothDetailSection(
            title = "Device / environment",
            rows = environmentRows
        )
    }

    val configRows = buildList {
        deviceConfiguration.audioVolume?.value?.let {
            add(LxBluetoothDetailRow("Audio volume", it.toString()))
        }
        deviceConfiguration.polarA?.value?.let {
            add(LxBluetoothDetailRow("Polar A", formatDouble(it, 3)))
        }
        deviceConfiguration.polarB?.value?.let {
            add(LxBluetoothDetailRow("Polar B", formatDouble(it, 3)))
        }
        deviceConfiguration.polarC?.value?.let {
            add(LxBluetoothDetailRow("Polar C", formatDouble(it, 3)))
        }
        deviceConfiguration.altitudeOffsetFeet?.value?.let {
            add(LxBluetoothDetailRow("Altitude offset", formatSignedDouble(it, 0, "ft")))
        }
        deviceConfiguration.scMode?.value?.let {
            add(LxBluetoothDetailRow("SC mode", formatDouble(it, 0)))
        }
        deviceConfiguration.varioFilter?.value?.let {
            add(LxBluetoothDetailRow("Vario filter", formatDouble(it, 0)))
        }
        deviceConfiguration.teFilter?.value?.let {
            add(LxBluetoothDetailRow("TE filter", formatDouble(it, 0)))
        }
        deviceConfiguration.teLevel?.value?.let {
            add(LxBluetoothDetailRow("TE level", formatDouble(it, 0)))
        }
        deviceConfiguration.varioAverage?.value?.let {
            add(LxBluetoothDetailRow("Vario avg", formatDouble(it, 0)))
        }
        deviceConfiguration.varioRange?.value?.let {
            add(LxBluetoothDetailRow("Vario range", formatDouble(it, 0)))
        }
        deviceConfiguration.scTab?.value?.let {
            add(LxBluetoothDetailRow("SC tab", formatDouble(it, 0)))
        }
        deviceConfiguration.scLow?.value?.let {
            add(LxBluetoothDetailRow("SC low", formatDouble(it, 0)))
        }
        deviceConfiguration.scSpeed?.value?.let {
            add(LxBluetoothDetailRow("SC speed", formatDouble(it, 0)))
        }
        deviceConfiguration.smartDiff?.value?.let {
            add(LxBluetoothDetailRow("SmartDiff", formatDouble(it, 0)))
        }
        deviceConfiguration.gliderName?.value?.let {
            add(LxBluetoothDetailRow("Glider", it))
        }
        deviceConfiguration.timeOffsetMinutes?.value?.let {
            add(LxBluetoothDetailRow("Time offset", "${it} min"))
        }
    }
    if (configRows.isNotEmpty()) {
        sections += LxBluetoothDetailSection(
            title = "Config / status",
            rows = configRows
        )
    }

    return sections
}

private fun formatDouble(value: Double, decimals: Int, unit: String? = null): String {
    val formatted = String.format(Locale.US, "%.${decimals}f", value)
    return if (unit.isNullOrBlank()) formatted else "$formatted $unit"
}

private fun formatSignedDouble(value: Double, decimals: Int, unit: String? = null): String {
    val sign = if (value > 0.0) "+" else ""
    return sign + formatDouble(value, decimals, unit)
}

private fun Int.toFlightModeLabel(): String =
    when (this) {
        0 -> "Circling"
        1 -> "Cruise"
        else -> "Unknown ($this)"
    }


