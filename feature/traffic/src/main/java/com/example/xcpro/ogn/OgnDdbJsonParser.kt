package com.example.xcpro.ogn

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.Locale

internal object OgnDdbJsonParser {

    fun parse(json: String): List<OgnDdbEntry> {
        val rootElement = JsonParser.parseString(json)
        val devices = when {
            rootElement.isJsonObject -> parseDevicesArray(rootElement.asJsonObject)
            rootElement.isJsonArray -> rootElement.asJsonArray
            else -> JsonArray()
        }
        if (devices.size() == 0) return emptyList()

        val entries = ArrayList<OgnDdbEntry>(devices.size())
        for (entry in devices) {
            val obj = entry.takeIf { it.isJsonObject }?.asJsonObject ?: continue
            val deviceId = obj.readString("device_id")
                ?.uppercase(Locale.US)
                ?.takeIf { it.length == 6 && it.all { ch -> ch in '0'..'9' || ch in 'A'..'F' } }
                ?: continue

            val identity = OgnTrafficIdentity(
                registration = obj.readString("registration"),
                competitionNumber = obj.readString("cn"),
                aircraftModel = obj.readString("aircraft_model"),
                tracked = obj.readBooleanFromYn("tracked"),
                identified = obj.readBooleanFromYn("identified"),
                aircraftTypeCode = obj.readString("aircraft_type")?.toIntOrNull()
            )
            entries += OgnDdbEntry(
                addressType = ognAddressTypeFromDdbDeviceType(obj.readString("device_type")),
                deviceIdHex = deviceId,
                identity = identity
            )
        }
        return entries
    }

    private fun parseDevicesArray(root: JsonObject): JsonArray {
        val devices = root.get("devices")
        if (devices != null && devices.isJsonArray) {
            return devices.asJsonArray
        }
        return JsonArray()
    }

    private fun JsonObject.readString(key: String): String? {
        val value = get(key) ?: return null
        if (!value.isJsonPrimitive) return null
        val raw = value.safeAsString()?.trim().orEmpty()
        return raw.takeIf { it.isNotEmpty() }
    }

    private fun JsonObject.readBooleanFromYn(key: String): Boolean? {
        val value = readString(key)?.uppercase(Locale.US) ?: return null
        return when (value) {
            "Y" -> true
            "N" -> false
            else -> null
        }
    }

    private fun JsonElement.safeAsString(): String? {
        return runCatching { asString }.getOrNull()
    }
}

internal data class OgnDdbEntry(
    val addressType: OgnAddressType,
    val deviceIdHex: String,
    val identity: OgnTrafficIdentity
)
