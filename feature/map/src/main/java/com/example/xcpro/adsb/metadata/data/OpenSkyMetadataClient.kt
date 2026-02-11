package com.example.xcpro.adsb.metadata.data

import com.example.xcpro.common.di.IoDispatcher
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
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
    private val httpClient: OkHttpClient,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    suspend fun listMetadataKeys(): Result<List<String>> = withContext(ioDispatcher) {
        runCatching {
            val keys = mutableListOf<String>()
            var continuationToken: String? = null
            do {
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

                httpClient.newCall(request).execute().use { response ->
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
            keys
        }
    }

    suspend fun <T> downloadCsv(
        url: String,
        block: suspend (InputStream, String?) -> T
    ): Result<T> = withContext(ioDispatcher) {
        runCatching {
            val request = Request.Builder()
                .url(url)
                .header("Accept", "text/csv,*/*")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Download failed with HTTP ${response.code}: $url")
                }
                val responseBody = response.body
                    ?: throw IOException("Download response body missing: $url")
                val etag = response.header("ETag")?.trim()?.trim('"')
                responseBody.byteStream().use { input ->
                    block(input, etag)
                }
            }
        }
    }

    private fun parseListingPage(xml: String): MetadataListingPage {
        val keys = KEY_REGEX.findAll(xml).map { it.groupValues[1].trim() }.toList()
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
    }
}

