package com.trust3.xcpro.puretrack

data class PureTrackBounds(
    val topRightLatitude: Double,
    val topRightLongitude: Double,
    val bottomLeftLatitude: Double,
    val bottomLeftLongitude: Double
)

enum class PureTrackCategory(val apiValue: String) {
    AIR("air"),
    GROUND("ground"),
    OTHER("other"),
    WATER("water")
}

data class PureTrackTrafficRequest(
    val bounds: PureTrackBounds,
    val category: PureTrackCategory? = null,
    val objectTypeIds: Set<Int> = emptySet(),
    val maxAgeMinutes: Int = 5,
    val alwaysIncludeKeys: Set<String> = emptySet(),
    val isolateAlwaysIncludeKeys: Boolean = false
)

data class PureTrackLoginSession(
    val accessToken: String,
    val pro: Boolean
)

data class PureTrackTrafficResponse(
    val targets: List<PureTrackTarget>,
    val diagnostics: PureTrackTrafficDiagnostics
)

data class PureTrackTrafficDiagnostics(
    val parsedRows: Int,
    val droppedRows: Int,
    val duplicateTokenCount: Int
)

data class PureTrackTarget(
    val key: String,
    val sourceTimestampEpochSec: Long,
    val latitude: Double,
    val longitude: Double,
    val altitudeMetersGps: Double?,
    val altitudeMetersStandard: Double?,
    val pressure: Double?,
    val courseDegrees: Double?,
    val speedMetersPerSecond: Double?,
    val calculatedSpeedMetersPerSecond: Double?,
    val verticalSpeedMetersPerSecond: Double?,
    val objectTypeId: Int?,
    val sourceTypeId: Int?,
    val trackerUid: String?,
    val targetId: String?,
    val label: String?,
    val name: String?,
    val registration: String?,
    val model: String?,
    val callsign: String?,
    val horizontalAccuracyMeters: Double?,
    val verticalAccuracyMeters: Double?,
    val onGround: Boolean?,
    val rawFieldKeys: Set<Char>
)

enum class PureTrackRowDropReason {
    EMPTY_ROW,
    MISSING_REQUIRED_FIELD,
    INVALID_TIMESTAMP,
    INVALID_COORDINATE,
    BLANK_KEY
}

data class PureTrackParsedRow(
    val target: PureTrackTarget?,
    val dropReason: PureTrackRowDropReason?,
    val duplicateTokenCount: Int,
    val rawFieldKeys: Set<Char>
) {
    val parsed: Boolean
        get() = target != null
}

data class PureTrackObjectType(
    val id: Int,
    val category: PureTrackCategory,
    val name: String
)

data class PureTrackSourceType(
    val id: Int,
    val name: String
)

enum class PureTrackNetworkFailureKind {
    DNS,
    TIMEOUT,
    CONNECT,
    NO_ROUTE,
    TLS,
    MALFORMED_RESPONSE,
    UNKNOWN
}

sealed interface PureTrackProviderResult<out T> {
    data class Success<T>(
        val value: T,
        val httpCode: Int
    ) : PureTrackProviderResult<T>

    data class RateLimited(
        val retryAfterSec: Int?
    ) : PureTrackProviderResult<Nothing>

    data class HttpError(
        val code: Int,
        val message: String
    ) : PureTrackProviderResult<Nothing>

    data class NetworkError(
        val kind: PureTrackNetworkFailureKind,
        val message: String
    ) : PureTrackProviderResult<Nothing>

    data object MissingAppKey : PureTrackProviderResult<Nothing>

    data object MissingBearerToken : PureTrackProviderResult<Nothing>
}

data class PureTrackStoredSession(
    val accessToken: String,
    val pro: Boolean
)

enum class PureTrackSessionErrorKind {
    MISSING_APP_KEY,
    CREDENTIALS_REJECTED,
    TRANSIENT_FAILURE,
    MALFORMED_RESPONSE,
    MISSING_TOKEN,
    PERSISTENCE_UNAVAILABLE
}

sealed interface PureTrackSessionState {
    data object LoggedOut : PureTrackSessionState
    data class LoggedInPro(val tokenAvailable: Boolean = true) : PureTrackSessionState
    data class LoggedInNotPro(val tokenAvailable: Boolean = true) : PureTrackSessionState
    data object TokenInvalid : PureTrackSessionState
    data class Error(val kind: PureTrackSessionErrorKind, val reason: String) : PureTrackSessionState
}
