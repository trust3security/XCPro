package com.trust3.xcpro.ogn

import android.content.Context
import com.trust3.xcpro.common.di.IoDispatcher
import com.trust3.xcpro.core.common.logging.AppLogger
import com.trust3.xcpro.core.time.Clock
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

sealed interface OgnDdbRefreshResult {
    data object Updated : OgnDdbRefreshResult
    data object NotDue : OgnDdbRefreshResult
}

@Singleton
class OgnDdbRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: Clock,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    private val identitiesByTypedKey = ConcurrentHashMap<String, OgnTrafficIdentity>()
    private val fallbackIdentityByHex = ConcurrentHashMap<String, OgnTrafficIdentity>()

    @Volatile
    private var typedSingleIdentityByHex: Map<String, OgnTrafficIdentity> = emptyMap()

    @Volatile
    private var ambiguousTypedHexes: Set<String> = emptySet()

    @Volatile
    private var lastUpdateWallMs: Long = 0L

    internal var connectionFactory: () -> HttpURLConnection = {
        URL(DDB_URL).openConnection() as HttpURLConnection
    }

    suspend fun refreshIfNeeded(force: Boolean = false): OgnDdbRefreshResult {
        return withContext(ioDispatcher) {
            loadFromDiskIfNeeded()
            if (!force && !isRefreshDue()) return@withContext OgnDdbRefreshResult.NotDue

            val json = downloadDdbJsonOrThrow()
            val parsed = try {
                OgnDdbJsonParser.parse(json)
            } catch (throwable: Throwable) {
                AppLogger.w(TAG, "Failed to parse OGN DDB JSON: ${throwable.message}")
                throw IllegalStateException("OGN DDB parse failure", throwable)
            }
            if (parsed.isEmpty()) {
                throw IllegalStateException("OGN DDB parse returned empty payload")
            }
            applyParsedEntries(parsed)
            lastUpdateWallMs = clock.nowWallMs()
            writeCache(json, lastUpdateWallMs)
            OgnDdbRefreshResult.Updated
        }
    }

    fun lookup(addressType: OgnAddressType, deviceIdHex: String): OgnTrafficIdentity? {
        val normalized = normalizeOgnHex6OrNull(deviceIdHex) ?: return null
        return when (addressType) {
            OgnAddressType.FLARM,
            OgnAddressType.ICAO -> {
                val typedKey = canonicalOgnTargetKey(
                    type = addressType,
                    addressHex = normalized,
                    fallbackId = normalized
                )
                identitiesByTypedKey[typedKey] ?: fallbackIdentityByHex[normalized]
            }
            OgnAddressType.UNKNOWN -> {
                fallbackIdentityByHex[normalized]
                    ?: if (ambiguousTypedHexes.contains(normalized)) null else typedSingleIdentityByHex[normalized]
            }
        }
    }

    fun lookup(deviceIdHex: String): OgnTrafficIdentity? {
        return lookup(addressType = OgnAddressType.UNKNOWN, deviceIdHex = deviceIdHex)
    }

    fun lastUpdateWallMs(): Long = lastUpdateWallMs

    private fun loadFromDiskIfNeeded() {
        if (identitiesByTypedKey.isNotEmpty() || fallbackIdentityByHex.isNotEmpty()) return
        val cacheFile = cacheFile()
        if (!cacheFile.exists()) return
        val json = runCatching { cacheFile.readText(StandardCharsets.UTF_8) }.getOrNull() ?: return
        val parsed = runCatching { OgnDdbJsonParser.parse(json) }.getOrNull() ?: return
        applyParsedEntries(parsed)
        lastUpdateWallMs = readTimestampFile()
    }

    private fun applyParsedEntries(entries: List<OgnDdbEntry>) {
        identitiesByTypedKey.clear()
        fallbackIdentityByHex.clear()

        val typedFirstIdentityByHex = HashMap<String, OgnTrafficIdentity>()
        val typedAmbiguousHexes = HashSet<String>()

        for (entry in entries) {
            val normalizedHex = normalizeOgnHex6OrNull(entry.deviceIdHex) ?: continue
            val identity = entry.identity
            if (entry.addressType == OgnAddressType.UNKNOWN) {
                fallbackIdentityByHex[normalizedHex] = identity
                continue
            }

            val typedKey = canonicalOgnTargetKey(
                type = entry.addressType,
                addressHex = normalizedHex,
                fallbackId = normalizedHex
            )
            identitiesByTypedKey[typedKey] = identity

            val previous = typedFirstIdentityByHex[normalizedHex]
            if (previous == null) {
                typedFirstIdentityByHex[normalizedHex] = identity
            } else if (previous != identity) {
                typedAmbiguousHexes += normalizedHex
            }
        }

        typedSingleIdentityByHex = typedFirstIdentityByHex
            .filterKeys { hex -> !typedAmbiguousHexes.contains(hex) }
        ambiguousTypedHexes = typedAmbiguousHexes
    }

    private fun isRefreshDue(): Boolean {
        val nowWall = clock.nowWallMs()
        if (lastUpdateWallMs <= 0L) return true
        return nowWall - lastUpdateWallMs >= DDB_REFRESH_INTERVAL_MS
    }

    private fun downloadDdbJsonOrThrow(): String {
        val connection = connectionFactory().apply {
            connectTimeout = HTTP_TIMEOUT_MS
            readTimeout = HTTP_TIMEOUT_MS
            requestMethod = "GET"
            setRequestProperty("User-Agent", USER_AGENT)
        }
        try {
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                AppLogger.w(TAG, "OGN DDB HTTP $responseCode")
                throw IllegalStateException("OGN DDB HTTP $responseCode")
            }
            return connection.inputStream.bufferedReader(StandardCharsets.UTF_8).use { reader ->
                reader.readText()
            }
        } catch (throwable: Throwable) {
            AppLogger.w(TAG, "Failed to download OGN DDB: ${throwable.message}")
            throw throwable
        } finally {
            connection.disconnect()
        }
    }

    private fun writeCache(json: String, timestampMs: Long) {
        runCatching {
            val folder = cacheFolder()
            if (!folder.exists()) {
                folder.mkdirs()
            }
            cacheFile().writeText(json, StandardCharsets.UTF_8)
            timestampFile().writeText(timestampMs.toString(), StandardCharsets.UTF_8)
        }.onFailure {
            AppLogger.w(TAG, "Failed to persist OGN DDB cache: ${it.message}")
        }
    }

    private fun readTimestampFile(): Long {
        return runCatching {
            val file = timestampFile()
            if (!file.exists()) return@runCatching 0L
            file.readText(StandardCharsets.UTF_8).trim().toLongOrNull() ?: 0L
        }.getOrDefault(0L)
    }

    private fun cacheFolder(): File = File(context.filesDir, CACHE_FOLDER_NAME)

    private fun cacheFile(): File = File(cacheFolder(), CACHE_JSON_FILE_NAME)

    private fun timestampFile(): File = File(cacheFolder(), CACHE_TIMESTAMP_FILE_NAME)

    private companion object {
        private const val TAG = "OgnDdbRepository"
        private const val DDB_URL = "https://ddb.glidernet.org/download/?j=1&t=1"
        private const val USER_AGENT = "XCPro OGN DDB"
        private const val HTTP_TIMEOUT_MS = 20_000
        private const val DDB_REFRESH_INTERVAL_MS = 24L * 60L * 60L * 1000L
        private const val CACHE_FOLDER_NAME = "ogn-ddb"
        private const val CACHE_JSON_FILE_NAME = "ddb.json"
        private const val CACHE_TIMESTAMP_FILE_NAME = "ddb.timestamp"
    }
}
