package com.example.xcpro.adsb.metadata.data

import com.example.xcpro.adsb.metadata.domain.AircraftMetadataSyncRepository
import com.example.xcpro.adsb.metadata.domain.MetadataSyncRunResult
import com.example.xcpro.adsb.metadata.domain.MetadataSyncState
import com.example.xcpro.common.di.IoDispatcher
import com.example.xcpro.core.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.max

@Singleton
class AircraftMetadataSyncRepositoryImpl @Inject constructor(
    private val clock: Clock,
    private val metadataClient: OpenSkyMetadataClient,
    private val selector: AircraftMetadataFileSelector,
    private val importer: AircraftMetadataImporter,
    private val checkpointStore: AircraftMetadataSyncCheckpointStore,
    @IoDispatcher ioDispatcher: CoroutineDispatcher
) : AircraftMetadataSyncRepository {

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)

    private val _syncState = MutableStateFlow<MetadataSyncState>(MetadataSyncState.Idle)
    override val syncState: StateFlow<MetadataSyncState> = _syncState.asStateFlow()

    init {
        scope.launch {
            val checkpoint = checkpointStore.snapshot()
            val lastSuccessWall = checkpoint.lastSuccessWallMs
            if (lastSuccessWall != null) {
                _syncState.value = MetadataSyncState.Success(
                    lastSuccessWallMs = lastSuccessWall,
                    sourceKey = checkpoint.sourceKey ?: "unknown",
                    etag = checkpoint.etag
                )
            } else if (!checkpoint.lastError.isNullOrBlank() && checkpoint.lastAttemptWallMs != null) {
                _syncState.value = MetadataSyncState.Failed(
                    reason = checkpoint.lastError,
                    lastAttemptWallMs = checkpoint.lastAttemptWallMs,
                    retryAtWallMs = null
                )
            }
        }
    }

    override suspend fun onScheduled() {
        val current = _syncState.value
        _syncState.value = when (current) {
            is MetadataSyncState.PausedByUser -> MetadataSyncState.Scheduled
            MetadataSyncState.Running -> MetadataSyncState.Running
            else -> MetadataSyncState.Scheduled
        }
    }

    override suspend fun onPausedByUser() {
        val checkpoint = checkpointStore.snapshot()
        _syncState.value = MetadataSyncState.PausedByUser(checkpoint.lastSuccessWallMs)
    }

    override suspend fun runSyncNow(): MetadataSyncRunResult {
        _syncState.value = MetadataSyncState.Running
        val startWallMs = clock.nowWallMs()
        checkpointStore.markScheduledAttempt(startWallMs)

        val checkpoint = checkpointStore.snapshot()
        val sources = discoverSources()

        var lastFailureReason = "Unknown metadata sync failure"
        for ((index, source) in sources.withIndex()) {
            val downloadResult = metadataClient.downloadCsv(source.url) { inputStream, responseEtag ->
                val skipForNoChange = checkpoint.sourceKey == source.sourceKey &&
                    !responseEtag.isNullOrBlank() &&
                    checkpoint.etag == responseEtag
                if (skipForNoChange) {
                    return@downloadCsv DownloadOutcome.SkippedNoChange(responseEtag)
                }
                importer.importCsv(inputStream)
                DownloadOutcome.Imported(responseEtag)
            }

            if (downloadResult.isSuccess) {
                val outcome = downloadResult.getOrThrow()
                val nowWall = max(clock.nowWallMs(), startWallMs)
                when (outcome) {
                    is DownloadOutcome.Imported -> {
                        checkpointStore.markSuccess(
                            sourceKey = source.sourceKey,
                            etag = outcome.etag,
                            nowWallMs = nowWall
                        )
                        _syncState.value = MetadataSyncState.Success(
                            lastSuccessWallMs = nowWall,
                            sourceKey = source.sourceKey,
                            etag = outcome.etag
                        )
                        return MetadataSyncRunResult.Succeeded
                    }

                    is DownloadOutcome.SkippedNoChange -> {
                        checkpointStore.markSuccess(
                            sourceKey = source.sourceKey,
                            etag = outcome.etag,
                            nowWallMs = nowWall
                        )
                        _syncState.value = MetadataSyncState.Success(
                            lastSuccessWallMs = nowWall,
                            sourceKey = source.sourceKey,
                            etag = outcome.etag
                        )
                        return MetadataSyncRunResult.Succeeded
                    }
                }
            } else {
                val error = downloadResult.exceptionOrNull()
                lastFailureReason = error?.message ?: "Metadata download failed"
                val isLast = index == sources.lastIndex
                if (!isLast) {
                    continue
                }
            }
        }

        val failureWallMs = max(clock.nowWallMs(), startWallMs)
        checkpointStore.markFailure(lastFailureReason, failureWallMs)
        _syncState.value = MetadataSyncState.Failed(
            reason = lastFailureReason,
            lastAttemptWallMs = failureWallMs,
            retryAtWallMs = null
        )
        return MetadataSyncRunResult.Retry
    }

    private suspend fun discoverSources(): List<MetadataSource> {
        val listing = metadataClient.listMetadataKeys()
        if (listing.isSuccess) {
            val keys = listing.getOrNull().orEmpty()
            val latestCompleteKey = selector.selectLatestCompleteKey(keys)
            if (!latestCompleteKey.isNullOrBlank()) {
                return listOf(
                    MetadataSource(
                        sourceKey = latestCompleteKey,
                        url = buildBucketUrl(latestCompleteKey)
                    ),
                    MetadataSource(
                        sourceKey = "direct_fallback",
                        url = AircraftMetadataSyncPolicy.SOURCE_DIRECT_FALLBACK
                    )
                )
            }
            return listOf(
                MetadataSource(
                    sourceKey = AircraftMetadataSyncPolicy.SOURCE_BUCKET_FALLBACK_KEY,
                    url = buildBucketUrl(AircraftMetadataSyncPolicy.SOURCE_BUCKET_FALLBACK_KEY)
                ),
                MetadataSource(
                    sourceKey = "direct_fallback",
                    url = AircraftMetadataSyncPolicy.SOURCE_DIRECT_FALLBACK
                )
            )
        }

        return listOf(
            MetadataSource(
                sourceKey = AircraftMetadataSyncPolicy.SOURCE_BUCKET_FALLBACK_KEY,
                url = buildBucketUrl(AircraftMetadataSyncPolicy.SOURCE_BUCKET_FALLBACK_KEY)
            ),
            MetadataSource(
                sourceKey = "direct_fallback",
                url = AircraftMetadataSyncPolicy.SOURCE_DIRECT_FALLBACK
            )
        )
    }

    private fun buildBucketUrl(key: String): String {
        return AircraftMetadataSyncPolicy.SOURCE_BUCKET_OBJECT_BASE + key
    }

    private sealed interface DownloadOutcome {
        data class Imported(val etag: String?) : DownloadOutcome
        data class SkippedNoChange(val etag: String?) : DownloadOutcome
    }

    private data class MetadataSource(
        val sourceKey: String,
        val url: String
    )
}
