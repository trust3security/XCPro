package com.trust3.xcpro.adsb.metadata.data

import android.content.Context
import com.trust3.xcpro.adsb.awaitResponse
import com.trust3.xcpro.common.di.IoDispatcher
import com.trust3.xcpro.di.AdsbMetadataHttpClient
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

internal data class MetadataListingPage(
    val keys: List<String>,
    val isTruncated: Boolean,
    val nextContinuationToken: String?
)

@Singleton
class OpenSkyMetadataClient @Inject constructor(
    @ApplicationContext private val context: Context,
    @AdsbMetadataHttpClient private val httpClient: OkHttpClient,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    suspend fun listMetadataKeys(): Result<List<String>> = withContext(ioDispatcher) {
        try {
            val keys = linkedSetOf<String>()
            var continuationToken: String? = null
            val seenTokens = linkedSetOf<String?>()
            var pageCount = 0
            do {
                if (!seenTokens.add(continuationToken)) {
                    throw IOException("Bucket listing continuation token loop detected")
                }
                pageCount += 1
                if (pageCount > MAX_LISTING_PAGES) {
                    throw IOException("Bucket listing exceeded max page limit: $MAX_LISTING_PAGES")
                }
                val url = AircraftMetadataSyncPolicy.SOURCE_BUCKET_LISTING
                    .toHttpUrl()
                    .newBuilder()
                    .apply {
                        if (!continuationToken.isNullOrBlank()) {
                            addQueryParameter("continuation-token", continuationToken)
                        }
                    }
                    .build()

                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/xml")
                    .get()
                    .build()

                httpClient.newCall(request).awaitResponse().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("Bucket listing failed with HTTP ${response.code}")
                    }
                    val xml = response.body?.string()
                        ?: throw IOException("Bucket listing response body missing")
                    val page = parseListingPage(xml)
                    keys += page.keys
                    continuationToken = if (page.isTruncated) {
                        page.nextContinuationToken
                            ?: throw IOException("Bucket listing is truncated but has no continuation token")
                    } else {
                        null
                    }
                }
            } while (!continuationToken.isNullOrBlank())
            Result.success(keys.toList())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun <T> downloadCsv(
        url: String,
        block: suspend (InputStream, String?) -> T
    ): Result<T> = withContext(ioDispatcher) {
        var tempFile: File? = null
        try {
            val request = Request.Builder()
                .url(url)
                .header("Accept", "text/csv,*/*")
                .get()
                .build()

            var etag: String? = null
            tempFile = createTempCsvFile()
            httpClient.newCall(request).awaitResponse().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Download failed with HTTP ${response.code}: $url")
                }
                val responseBody = response.body
                    ?: throw IOException("Download response body missing: $url")
                etag = response.header("ETag")?.trim()?.trim('"')
                responseBody.byteStream().use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
            val value = tempFile.inputStream().use { input ->
                block(input, etag)
            }
            Result.success(value)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            runCatching {
                tempFile?.delete()
            }
        }
    }

    private fun createTempCsvFile(): File {
        val cacheDir = context.cacheDir
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        return File.createTempFile(TEMP_FILE_PREFIX, TEMP_FILE_SUFFIX, cacheDir)
    }

    private fun parseListingPage(xml: String): MetadataListingPage {
        val keys = KEY_REGEX.findAll(xml)
            .map { it.groupValues[1].trim() }
            .filter { it.isNotBlank() }
            .toList()
        val truncated = IS_TRUNCATED_REGEX.find(xml)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.equals("true", ignoreCase = true)
            ?: false
        val token = NEXT_TOKEN_REGEX.find(xml)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: NEXT_MARKER_REGEX.find(xml)
                ?.groupValues
                ?.getOrNull(1)
                ?.trim()
            ?.takeIf { it.isNotBlank() }
        return MetadataListingPage(
            keys = keys,
            isTruncated = truncated,
            nextContinuationToken = token
        )
    }

    private companion object {
        val KEY_REGEX = Regex(
            "<Key>([^<]+)</Key>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)
        )
        val IS_TRUNCATED_REGEX = Regex(
            "<IsTruncated>(true|false)</IsTruncated>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)
        )
        val NEXT_TOKEN_REGEX = Regex(
            "<NextContinuationToken>([^<]+)</NextContinuationToken>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)
        )
        val NEXT_MARKER_REGEX = Regex(
            "<NextMarker>([^<]+)</NextMarker>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)
        )
        const val MAX_LISTING_PAGES = 200
        const val TEMP_FILE_PREFIX = "adsb_metadata_"
        const val TEMP_FILE_SUFFIX = ".csv"
    }
}

