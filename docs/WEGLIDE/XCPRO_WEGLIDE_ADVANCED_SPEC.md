# XCPro → WeGlide Advanced Integration Spec

## Status / Direction

This spec updates the earlier WeGlide upload plan for XCPro and makes one decision explicit:

**XCPro should not use stored WeGlide username/password credentials.**

XCPro should be built around **OAuth 2** for account-specific access, because WeGlide’s developer docs say OAuth endpoints are used to interact with WeGlide on behalf of individual users for actions such as uploading flights or editing tasks. citeturn640509view1

At the same time, XCSoar’s public code is useful as a **wire-format reference** for the current upload endpoint and multipart field names. XCSoar posts to `/v1/igcfile`, appending that to a default base URL of `https://api.weglide.org/v1`, and sends multipart fields named `file`, `user_id`, `date_of_birth`, and `aircraft_id`, expecting HTTP `201` on success. citeturn924962view0turn924962view1

There is one real uncertainty you must preserve in the implementation:

- WeGlide’s public developer docs clearly recommend OAuth for account-specific integrations. citeturn640509view1
- XCSoar’s currently published code does **not** show OAuth in the upload request itself; it still sends `user_id`, `date_of_birth`, and `aircraft_id` in multipart form data. citeturn924962view0turn924962view1

So XCPro should be implemented with a **provider abstraction** that supports:

1. **Preferred path:** OAuth bearer-token upload.
2. **Compatibility fallback path:** XCSoar-style field-based upload, behind a feature flag and only if WeGlide confirms that this is valid for third-party public apps.

---

# What We Can Reliably Infer Right Now

## Confirmed from WeGlide docs

- WeGlide offers a public API and OAuth. citeturn640509view1
- OAuth is meant for interacting on behalf of individual users, including uploading flights. citeturn640509view1
- Public-facing apps needing account-specific functionality should contact WeGlide for an OAuth application token. citeturn640509view1
- The public API includes publicly readable entities such as flights, aircraft, airports, users, and clubs. citeturn640509view1

## Confirmed from XCSoar code

- XCSoar uses default base URL `https://api.weglide.org/v1`. citeturn924962view1
- XCSoar uploads flight IGC files to `https://api.weglide.org/v1/igcfile`. citeturn924962view0
- XCSoar sends multipart fields `file`, `user_id`, `date_of_birth`, and `aircraft_id`. citeturn924962view0
- XCSoar treats any non-`201` upload response as an error. citeturn924962view0
- XCSoar stores `pilot_id` and `pilot_birthdate` in settings. citeturn924962view1
- XCSoar blocks upload if the glider type / aircraft ID is `0`. citeturn924962view2
- XCSoar’s returned upload JSON parsing expects flight metadata including flight id, user id, user name, aircraft id, aircraft name, registration, and competition id. citeturn924962view2

## Confirmed from recent XCSoar issue discussion

- XCSoar currently requires users to manually enter a numeric WeGlide aircraft type.
- That issue proposes replacing manual entry with in-app discovery from `https://api.weglide.org/v1/aircraft`. citeturn208825search0

---

# Product Decision for XCPro

## Final recommended authentication strategy

### Do this
- Use **OAuth 2 authorization code flow with PKCE** in the Android app.
- Open the auth page in the browser or Custom Tabs.
- Store only access token, refresh token, expiry, and low-risk profile linkage metadata.
- Treat WeGlide user id as server-linked identity, not as a secret.

### Do not do this
- Do not ask the user for raw WeGlide password and store it.
- Do not build XCPro around embedded credential replay.
- Do not hard-code an assumption that bearer-token upload omits `user_id` / `date_of_birth` / `aircraft_id`; make this server-configurable until verified with WeGlide.

---

# Required XCPro Capabilities

1. Connect a WeGlide account.
2. Fetch and cache the aircraft list.
3. Let the user map each XCPro aircraft profile to a WeGlide aircraft id.
4. Queue completed IGC files for background upload.
5. Retry safely on transient failures.
6. Prevent duplicate uploads.
7. Surface upload success/failure clearly in UI.
8. Keep the rest of XCPro working even when WeGlide is disconnected or unavailable.

---

# Proposed Android Architecture

Keep this feature isolated behind a dedicated module boundary.

```text
core/network/
core/storage/
core/database/
feature/weglide/
    api/
    auth/
    data/
    domain/
    worker/
    ui/
```

## Suggested package layout

```text
feature/weglide/
  api/
    WeGlideApi.kt
    WeGlideAuthApi.kt
    WeGlideDtos.kt
  auth/
    WeGlideAuthManager.kt
    WeGlidePkceFactory.kt
    WeGlideRedirectHandler.kt
    WeGlideTokenStore.kt
  data/
    WeGlideRepositoryImpl.kt
    WeGlideAircraftSyncService.kt
    WeGlideUploadMapper.kt
  domain/
    WeGlideRepository.kt
    ConnectWeGlideUseCase.kt
    DisconnectWeGlideUseCase.kt
    SyncWeGlideAircraftUseCase.kt
    QueueWeGlideUploadUseCase.kt
    ExecuteWeGlideUploadUseCase.kt
  worker/
    WeGlideUploadWorker.kt
    WeGlideAircraftSyncWorker.kt
  ui/
    WeGlideSettingsScreen.kt
    WeGlideAircraftPickerScreen.kt
    WeGlideUploadStatusCard.kt
```

---

# Data Model

## Token storage model

```kotlin
data class WeGlideTokenBundle(
    val accessToken: String,
    val refreshToken: String?,
    val expiresAtEpochMs: Long,
    val tokenType: String = "Bearer",
)
```

## Linked account model

```kotlin
data class WeGlideAccountLink(
    val userId: Long?,
    val displayName: String?,
    val email: String?,
    val connectedAtEpochMs: Long,
    val authMode: WeGlideAuthMode,
)

enum class WeGlideAuthMode {
    OAUTH,
    LEGACY_FIELD_UPLOAD
}
```

## Aircraft cache entity

```kotlin
@Entity(tableName = "weglide_aircraft")
data class WeGlideAircraftEntity(
    @PrimaryKey val aircraftId: Long,
    val name: String,
    val kind: String?,
    val scoringClass: String?,
    val rawJson: String,
    val updatedAtEpochMs: Long,
)
```

## Profile linkage entity

Each XCPro local aircraft profile should map to one WeGlide aircraft id.

```kotlin
@Entity(tableName = "weglide_aircraft_mapping")
data class WeGlideAircraftMappingEntity(
    @PrimaryKey val localProfileId: String,
    val weglideAircraftId: Long,
    val weglideAircraftName: String,
    val updatedAtEpochMs: Long,
)
```

## Upload queue entity

```kotlin
@Entity(tableName = "weglide_upload_queue")
data class WeGlideUploadQueueEntity(
    @PrimaryKey val localFlightId: String,
    val igcPath: String,
    val localProfileId: String,
    val scoringDate: String?,
    val sha256: String,
    val uploadState: String,
    val retryCount: Int,
    val lastErrorCode: Int?,
    val lastErrorMessage: String?,
    val remoteFlightId: Long?,
    val queuedAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)
```

Recommended states:

```text
QUEUED
UPLOADING
UPLOADED
FAILED_RETRYABLE
FAILED_PERMANENT
SKIPPED_DUPLICATE
```

---

# Retrofit Interfaces

## Upload API

Keep the upload request flexible. Do not lock yourself to only one auth mode.

```kotlin
interface WeGlideApi {

    @Multipart
    @POST("igcfile")
    suspend fun uploadIgcOAuth(
        @Header("Authorization") authorization: String,
        @Part file: MultipartBody.Part,
        @Part("user_id") userId: RequestBody?,
        @Part("date_of_birth") dateOfBirth: RequestBody?,
        @Part("aircraft_id") aircraftId: RequestBody,
    ): Response<ResponseBody>

    @Multipart
    @POST("igcfile")
    suspend fun uploadIgcLegacy(
        @Part file: MultipartBody.Part,
        @Part("user_id") userId: RequestBody,
        @Part("date_of_birth") dateOfBirth: RequestBody,
        @Part("aircraft_id") aircraftId: RequestBody,
    ): Response<ResponseBody>

    @GET("aircraft")
    suspend fun getAircraft(
        @Header("Authorization") authorization: String? = null,
    ): Response<List<WeGlideAircraftDto>>
}
```

Notes:

- The `uploadIgcOAuth()` signature intentionally allows nullable `user_id` and `date_of_birth` until WeGlide confirms whether OAuth bearer auth still expects them in form fields.
- The `uploadIgcLegacy()` method mirrors the XCSoar field set exactly. XCSoar’s code sends `file`, `user_id`, `date_of_birth`, and `aircraft_id`. citeturn924962view0
- Base URL should default to `https://api.weglide.org/v1/`, matching XCSoar. citeturn924962view1

## OAuth API

Endpoint names are not verified from readable public docs, so keep them configurable.

```kotlin
interface WeGlideAuthApi {
    @FormUrlEncoded
    @POST
    suspend fun exchangeCode(
        @Url tokenUrl: String,
        @Field("grant_type") grantType: String = "authorization_code",
        @Field("code") code: String,
        @Field("redirect_uri") redirectUri: String,
        @Field("client_id") clientId: String,
        @Field("code_verifier") codeVerifier: String,
    ): Response<WeGlideTokenResponseDto>

    @FormUrlEncoded
    @POST
    suspend fun refreshToken(
        @Url tokenUrl: String,
        @Field("grant_type") grantType: String = "refresh_token",
        @Field("refresh_token") refreshToken: String,
        @Field("client_id") clientId: String,
    ): Response<WeGlideTokenResponseDto>
}
```

Because WeGlide’s docs say to contact them for an OAuth application token but do not expose readable endpoint details in the browsable docs we could access, treat auth URLs and scopes as remote-config or `BuildConfig` values until confirmed. citeturn640509view1

---

# DTOs

```kotlin
@Serializable
data class WeGlideTokenResponseDto(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresInSeconds: Long? = null,
    @SerialName("token_type") val tokenType: String? = null,
    val scope: String? = null,
)

@Serializable
data class WeGlideAircraftDto(
    val id: Long,
    val name: String,
    val kind: String? = null,
    @SerialName("sc_class") val scoringClass: String? = null,
)
```

## Upload response DTO

XCSoar’s parsing shows the upload response body includes an array whose first item contains flight metadata and nested `user` and `aircraft` objects. citeturn924962view2

Mirror that loosely so parsing survives server-side expansion.

```kotlin
@Serializable
data class WeGlideUploadFlightDto(
    val id: Long,
    @SerialName("scoring_date") val scoringDate: String? = null,
    val registration: String? = null,
    @SerialName("competition_id") val competitionId: String? = null,
    val user: WeGlideUploadUserDto? = null,
    val aircraft: WeGlideUploadAircraftDto? = null,
)

@Serializable
data class WeGlideUploadUserDto(
    val id: Long,
    val name: String? = null,
)

@Serializable
data class WeGlideUploadAircraftDto(
    val id: Long,
    val name: String? = null,
    val kind: String? = null,
    @SerialName("sc_class") val scoringClass: String? = null,
)
```

---

# OAuth Browser Flow Implementation

## Required Android pieces

- Custom Tabs or default browser
- Redirect URI registered in manifest
- PKCE code verifier + code challenge
- CSRF `state`
- Token exchange after redirect
- Refresh flow
- Disconnect flow

## Manifest

```xml
<activity
    android:name=".weglide.auth.WeGlideRedirectActivity"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data
            android:scheme="xcpro"
            android:host="weglide-auth"
            android:path="/callback" />
    </intent-filter>
</activity>
```

## Auth manager skeleton

```kotlin
class WeGlideAuthManager(
    private val tokenStore: WeGlideTokenStore,
    private val authApi: WeGlideAuthApi,
    private val appConfig: WeGlideConfig,
) {
    suspend fun buildAuthorizationUrl(): Uri {
        val pkce = WeGlidePkceFactory.create()
        tokenStore.savePendingPkce(pkce)

        return appConfig.authorizationEndpoint.buildUpon()
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", appConfig.clientId)
            .appendQueryParameter("redirect_uri", appConfig.redirectUri)
            .appendQueryParameter("code_challenge", pkce.codeChallenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("state", pkce.state)
            .appendQueryParameter("scope", appConfig.scope)
            .build()
    }

    suspend fun handleAuthorizationCode(code: String, state: String) {
        val pkce = tokenStore.getPendingPkce() ?: error("Missing PKCE state")
        require(state == pkce.state) { "State mismatch" }

        val response = authApi.exchangeCode(
            tokenUrl = appConfig.tokenEndpoint,
            code = code,
            redirectUri = appConfig.redirectUri,
            clientId = appConfig.clientId,
            codeVerifier = pkce.codeVerifier,
        )

        val body = response.body() ?: error("Empty token body")
        tokenStore.saveTokens(body)
        tokenStore.clearPendingPkce()
    }
}
```

## Redirect activity skeleton

```kotlin
class WeGlideRedirectActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uri = intent?.data
        val code = uri?.getQueryParameter("code")
        val state = uri?.getQueryParameter("state")
        val error = uri?.getQueryParameter("error")

        lifecycleScope.launch {
            when {
                error != null -> finishWithResult(false)
                code != null && state != null -> {
                    authEntryPoint.authManager.handleAuthorizationCode(code, state)
                    finishWithResult(true)
                }
                else -> finishWithResult(false)
            }
        }
    }
}
```

---

# Token Storage

Use AndroidX Security or encrypted storage already present in XCPro.

```kotlin
interface WeGlideTokenStore {
    suspend fun getTokens(): WeGlideTokenBundle?
    suspend fun saveTokens(dto: WeGlideTokenResponseDto)
    suspend fun clearTokens()
    suspend fun isTokenFresh(nowEpochMs: Long): Boolean
    suspend fun getValidAccessToken(): String?
    suspend fun savePendingPkce(pkce: PendingPkce)
    suspend fun getPendingPkce(): PendingPkce?
    suspend fun clearPendingPkce()
}
```

Rules:

- Never store username or password.
- Encrypt token data at rest.
- Clear tokens on disconnect or repeated 401/403.
- Log only redacted token information.

---

# Aircraft Sync Service

XCPro should not make users manually type numeric WeGlide aircraft ids if it can avoid it.

That direction is reinforced by the recent XCSoar issue calling manual aircraft-type entry error-prone and proposing in-app discovery from `/v1/aircraft`. citeturn208825search0

## Sync strategy

- Fetch aircraft list on first successful connection.
- Refresh every 7 days, app launch, or manual refresh.
- Cache in Room.
- Support local search by aircraft name and kind.
- Let the user bind a local XCPro aircraft profile to a remote WeGlide aircraft id.

## Service skeleton

```kotlin
class WeGlideAircraftSyncService(
    private val api: WeGlideApi,
    private val dao: WeGlideAircraftDao,
    private val tokenStore: WeGlideTokenStore,
) {
    suspend fun sync(): Result<Unit> = runCatching {
        val token = tokenStore.getValidAccessToken()
        val response = api.getAircraft(
            authorization = token?.let { "Bearer $it" }
        )

        if (!response.isSuccessful) error("Aircraft sync failed: ${response.code()}")
        val body = response.body().orEmpty()
        dao.replaceAll(body.map { it.toEntity() })
    }
}
```

---

# Flight Upload Queue Database

Use Room-backed queueing instead of direct fire-and-forget network calls.

## Why

- Flight completion often happens with unstable mobile data.
- Users may close the app after landing.
- Automatic upload needs retry and idempotency.
- You need auditability.

## DAO example

```kotlin
@Dao
interface WeGlideUploadQueueDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WeGlideUploadQueueEntity)

    @Query("SELECT * FROM weglide_upload_queue WHERE uploadState IN ('QUEUED','FAILED_RETRYABLE') ORDER BY queuedAtEpochMs LIMIT 1")
    suspend fun nextPending(): WeGlideUploadQueueEntity?

    @Query("UPDATE weglide_upload_queue SET uploadState = :state, retryCount = :retryCount, lastErrorCode = :code, lastErrorMessage = :message, updatedAtEpochMs = :updatedAt WHERE localFlightId = :localFlightId")
    suspend fun updateState(
        localFlightId: String,
        state: String,
        retryCount: Int,
        code: Int?,
        message: String?,
        updatedAt: Long,
    )

    @Query("UPDATE weglide_upload_queue SET uploadState = 'UPLOADED', remoteFlightId = :remoteFlightId, updatedAtEpochMs = :updatedAt WHERE localFlightId = :localFlightId")
    suspend fun markUploaded(localFlightId: String, remoteFlightId: Long?, updatedAt: Long)
}
```

---

# Automatic Upload After IGC Creation

This should hook into your existing XCPro flight-finalization pipeline.

## Trigger point

When XCPro marks a flight as complete and the final IGC has been written successfully:

1. Compute SHA-256 of the file.
2. Check if this local flight already exists in queue or uploaded history.
3. Resolve active XCPro aircraft profile.
4. Resolve mapped WeGlide aircraft id.
5. Insert queue row.
6. Enqueue `WeGlideUploadWorker`.

## Use case skeleton

```kotlin
class QueueWeGlideUploadUseCase(
    private val queueDao: WeGlideUploadQueueDao,
    private val mappingDao: WeGlideAircraftMappingDao,
    private val settingsRepo: SettingsRepository,
    private val digestService: FileDigestService,
    private val workScheduler: WeGlideWorkScheduler,
) {
    suspend operator fun invoke(localFlightId: String, igcPath: String, localProfileId: String) {
        if (!settingsRepo.isWeGlideAutoUploadEnabled()) return

        val mapping = mappingDao.getByProfileId(localProfileId) ?: return
        val sha256 = digestService.sha256(igcPath)

        queueDao.upsert(
            WeGlideUploadQueueEntity(
                localFlightId = localFlightId,
                igcPath = igcPath,
                localProfileId = localProfileId,
                scoringDate = null,
                sha256 = sha256,
                uploadState = "QUEUED",
                retryCount = 0,
                lastErrorCode = null,
                lastErrorMessage = null,
                remoteFlightId = null,
                queuedAtEpochMs = System.currentTimeMillis(),
                updatedAtEpochMs = System.currentTimeMillis(),
            )
        )

        workScheduler.enqueueUpload(localFlightId)
    }
}
```

---

# Upload Execution Logic

## Decision tree

```text
Has valid token?
  yes -> try OAuth upload path
  no  -> if legacy mode explicitly enabled and required fields available, try legacy upload
       -> else fail permanent and prompt user to connect WeGlide
```

## Worker skeleton

```kotlin
class WeGlideUploadWorker(
    appContext: Context,
    params: WorkerParameters,
    private val uploadUseCase: ExecuteWeGlideUploadUseCase,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val localFlightId = inputData.getString("localFlightId") ?: return Result.failure()

        return when (val result = uploadUseCase(localFlightId)) {
            is UploadExecutionResult.Success -> Result.success()
            is UploadExecutionResult.RetryableFailure -> Result.retry()
            is UploadExecutionResult.PermanentFailure -> Result.failure()
        }
    }
}
```

## Use case skeleton

```kotlin
class ExecuteWeGlideUploadUseCase(
    private val queueDao: WeGlideUploadQueueDao,
    private val repository: WeGlideRepository,
) {
    suspend operator fun invoke(localFlightId: String): UploadExecutionResult {
        val item = queueDao.getById(localFlightId) ?: return UploadExecutionResult.PermanentFailure("Missing queue row")

        return repository.uploadQueuedFlight(item)
    }
}
```

---

# Repository Logic

```kotlin
interface WeGlideRepository {
    suspend fun connect(): Result<Unit>
    suspend fun disconnect()
    suspend fun syncAircraft(): Result<Unit>
    suspend fun uploadQueuedFlight(item: WeGlideUploadQueueEntity): UploadExecutionResult
}
```

## Implementation outline

```kotlin
class WeGlideRepositoryImpl(
    private val api: WeGlideApi,
    private val tokenStore: WeGlideTokenStore,
    private val accountStore: WeGlideAccountStore,
    private val profileStore: PilotProfileStore,
    private val parser: Json,
    private val queueDao: WeGlideUploadQueueDao,
    private val filePartFactory: WeGlideMultipartFactory,
    private val config: WeGlideConfig,
) : WeGlideRepository {

    override suspend fun uploadQueuedFlight(item: WeGlideUploadQueueEntity): UploadExecutionResult {
        val token = tokenStore.getValidAccessToken()
        val aircraftId = resolveAircraftId(item.localProfileId)
            ?: return permanent(item, "Missing aircraft mapping")

        val filePart = filePartFactory.create(item.igcPath)

        val response = if (token != null) {
            api.uploadIgcOAuth(
                authorization = "Bearer $token",
                file = filePart,
                userId = config.sendUserIdWithOAuth.thenBody(accountStore.userId),
                dateOfBirth = config.sendDobWithOAuth.thenBody(profileStore.birthDateIso),
                aircraftId = aircraftId.asRequestBody(),
            )
        } else if (config.allowLegacyFieldUpload) {
            val userId = accountStore.userId ?: return permanent(item, "Missing user id")
            val dob = profileStore.birthDateIso ?: return permanent(item, "Missing date of birth")
            api.uploadIgcLegacy(
                file = filePart,
                userId = userId.asRequestBody(),
                dateOfBirth = dob.asRequestBody(),
                aircraftId = aircraftId.asRequestBody(),
            )
        } else {
            return permanent(item, "WeGlide not connected")
        }

        return handleUploadResponse(item, response)
    }
}
```

---

# HTTP Handling Rules

XCSoar throws on any status other than `201`, so treat `201` as canonical success. citeturn924962view0

## Recommended XCPro rules

### Success
- `201`: upload succeeded. Parse returned body for remote flight id if present.

### Retryable
- network timeout
- DNS failure
- TLS transient error
- `408`
- `425`
- `429`
- `500`
- `502`
- `503`
- `504`

### Permanent
- `400`
- `401` after one refresh attempt
- `403`
- `404`
- `409` if server uses it for duplicates
- `422`

### Unknown
- Any unclassified 4xx should default to permanent.
- Any unclassified 5xx should default to retryable.

## Response handler example

```kotlin
private suspend fun handleUploadResponse(
    item: WeGlideUploadQueueEntity,
    response: Response<ResponseBody>
): UploadExecutionResult {
    return when (response.code()) {
        201 -> {
            val flightId = parseRemoteFlightId(response.body()?.string())
            queueDao.markUploaded(item.localFlightId, flightId, System.currentTimeMillis())
            UploadExecutionResult.Success(flightId)
        }
        401 -> UploadExecutionResult.PermanentFailure("Unauthorized")
        422 -> UploadExecutionResult.PermanentFailure("Validation failed")
        408, 425, 429, 500, 502, 503, 504 -> UploadExecutionResult.RetryableFailure("Server/transient failure")
        else -> if (response.code() >= 500) {
            UploadExecutionResult.RetryableFailure("Unexpected server error ${response.code()}")
        } else {
            UploadExecutionResult.PermanentFailure("Unexpected client error ${response.code()}")
        }
    }
}
```

---

# Duplicate Protection

You should assume users may hit finish twice, reimport, or replay sync.

## Strategy

- Keep `localFlightId` unique.
- Store SHA-256 of IGC content.
- Store returned remote flight id.
- If a queue row already exists for the same `localFlightId`, do not enqueue again.
- If a previous successful upload exists for the same SHA-256, mark new attempts as `SKIPPED_DUPLICATE`.
- If server returns duplicate semantics in `422` body, parse and surface a user-friendly message.

---

# UI / UX Specification

## Settings screen

Sections:

### WeGlide connection
- Connect WeGlide button
- Connected account name
- Connection status
- Disconnect button

### Upload behavior
- Auto-upload finished flights toggle
- Upload on Wi-Fi only toggle
- Retry on mobile data toggle
- Show notification on upload completion toggle

### Aircraft mapping
- List XCPro profiles
- Current mapped WeGlide aircraft name/id
- Searchable picker
- Refresh aircraft list action

### Diagnostics
- Last aircraft sync time
- Last upload attempt
- Last upload error
- Advanced debug toggle

## Post-flight UX

- On landing / finalization: `Queued for WeGlide upload`
- In progress: `Uploading to WeGlide…`
- Success: `Uploaded to WeGlide`
- Failure: `Upload failed` + reason + retry action

## Flight details screen

Add a small WeGlide status card:

```text
WeGlide
Connected: Yes
Aircraft: JS1C 18m (ID 45)
Upload: Uploaded
Remote Flight ID: 987654
```

---

# Security Requirements

## Hard rules

- Never persist raw password.
- Never log access token.
- Redact `Authorization` header in OkHttp logging.
- Encrypt token storage.
- Prefer Custom Tabs over WebView for login.
- Use PKCE.
- Validate `state` on return.
- Fail closed on malformed redirect.
- Clear tokens on disconnect.

## OkHttp redaction

```kotlin
val logging = HttpLoggingInterceptor().apply {
    level = HttpLoggingInterceptor.Level.BASIC
    redactHeader("Authorization")
}
```

---

# WorkManager Policy

## Constraints

Recommended default constraints:

```kotlin
Constraints.Builder()
    .setRequiredNetworkType(NetworkType.CONNECTED)
    .build()
```

Optional user setting:
- Wi-Fi only → `NetworkType.UNMETERED`

## Backoff

- Exponential backoff
- Initial delay: 30 seconds
- Max retries from your own queue state: 5
- After 5 retries, mark `FAILED_RETRYABLE` and require manual retry

## Unique work name

Use one unique work per local flight id:

```text
weglide_upload_<localFlightId>
```

---

# Configuration / Feature Flags

Until WeGlide confirms the final OAuth upload body requirements, use config flags.

```kotlin
data class WeGlideConfig(
    val apiBaseUrl: String,
    val authorizationEndpoint: Uri,
    val tokenEndpoint: String,
    val clientId: String,
    val redirectUri: String,
    val scope: String,
    val allowLegacyFieldUpload: Boolean,
    val sendUserIdWithOAuth: Boolean,
    val sendDobWithOAuth: Boolean,
)
```

Recommended defaults for production until confirmed by WeGlide:

```text
allowLegacyFieldUpload = false
sendUserIdWithOAuth = true
sendDobWithOAuth = true
```

Reason: this keeps XCPro closer to the currently observed XCSoar request contract while still using OAuth for auth. XCSoar clearly sends `user_id`, `date_of_birth`, and `aircraft_id` with the file. citeturn924962view0

---

# Testing Plan

## Unit tests

- PKCE generation
- auth state validation
- token expiry logic
- upload response classification
- duplicate detection
- aircraft mapping resolution
- queue state transitions

## Integration tests

- connect flow success
- token refresh success
- token refresh failure
- aircraft sync with cached fallback
- upload success `201`
- upload `422`
- upload `401` then disconnect prompt
- upload `503` then retry

## UI tests

- connect/disconnect buttons
- aircraft picker search
- post-flight queued state
- error banner rendering

## Manual test matrix

1. Flight finishes with network available.
2. Flight finishes offline, uploads later.
3. No aircraft mapping exists.
4. Token expired but refresh succeeds.
5. Token expired and refresh fails.
6. Same IGC queued twice.
7. WeGlide server returns malformed body.
8. User disconnects account while upload is pending.

---

# Observability

Add structured events:

```text
weglide_connect_started
weglide_connect_succeeded
weglide_connect_failed
weglide_aircraft_sync_started
weglide_aircraft_sync_succeeded
weglide_aircraft_sync_failed
weglide_upload_queued
weglide_upload_started
weglide_upload_succeeded
weglide_upload_failed_retryable
weglide_upload_failed_permanent
weglide_upload_skipped_duplicate
```

Each event should include:
- local flight id when relevant
- profile id
- network type
- HTTP code if present
- retry count

Do not include token or DOB in analytics.

---

# Step-by-Step Codex Implementation Plan

## Phase 1 — Foundation
1. Add `feature/weglide` module/package structure.
2. Add config object and DI wiring.
3. Add encrypted token store.
4. Add Room entities + DAOs for aircraft cache, mapping, upload queue.

## Phase 2 — OAuth
1. Add PKCE utility.
2. Add Custom Tabs launch flow.
3. Add redirect activity.
4. Add token exchange + refresh service.
5. Add connect/disconnect UI.

## Phase 3 — Aircraft
1. Add `GET /aircraft` Retrofit API.
2. Add sync service.
3. Add searchable aircraft picker.
4. Add per-profile mapping persistence.

## Phase 4 — Upload Queue
1. Add queue insert on final IGC creation.
2. Add SHA-256 duplicate detection.
3. Add WorkManager scheduling.
4. Add upload execution use case.

## Phase 5 — Response Handling
1. Parse success body for remote flight id.
2. Add retryable/permanent classification.
3. Add token refresh on first `401` path if refresh token exists.
4. Add user-facing error messages.

## Phase 6 — Polish
1. Add settings diagnostics.
2. Add flight details WeGlide status card.
3. Add notifications.
4. Add tests and logging.

---

# Example User-Facing Messages

## Success
- `Uploaded to WeGlide`
- `Flight uploaded successfully`

## Retryable
- `WeGlide upload delayed — will retry automatically`
- `Server unavailable — retrying later`

## Permanent
- `WeGlide connection expired — reconnect required`
- `No WeGlide aircraft selected for this profile`
- `Flight rejected by WeGlide`

---

# Hard Unknowns To Preserve In Code Comments

These are not fully verified from the publicly readable docs we could access and must remain explicit TODOs:

1. Exact OAuth authorization endpoint URL.
2. Exact OAuth token endpoint URL.
3. Exact required OAuth scopes.
4. Whether OAuth-authenticated upload still requires `user_id` and `date_of_birth` multipart fields.
5. Whether `/v1/aircraft` requires auth in all cases.
6. Exact duplicate-flight server response semantics.

---

# Recommended Inline TODO Block for Codex

```kotlin
/*
TODO(WeGlide verification before release):
1. Confirm OAuth authorization endpoint, token endpoint, and scopes directly with WeGlide.
2. Confirm whether OAuth upload to POST /v1/igcfile still requires multipart user_id and date_of_birth.
3. Confirm whether GET /v1/aircraft is public or bearer-auth only for production use.
4. Validate duplicate-flight response body and code handling with real sandbox/live tests.
5. Replace feature flags with final server-confirmed behavior once verified.
*/
```

---

# Bottom Line

For XCPro, the right production architecture is:

- **OAuth 2 + PKCE** for authentication, because that is what WeGlide’s developer docs direct for account-specific integrations. citeturn640509view1
- **`POST https://api.weglide.org/v1/igcfile`** as the working upload endpoint, because XCSoar’s public code uses it. citeturn924962view0turn924962view1
- **Multipart upload support for `file`, `user_id`, `date_of_birth`, `aircraft_id`**, because XCSoar’s public code confirms those field names. citeturn924962view0
- **Aircraft discovery and mapping from `/v1/aircraft`**, because manual numeric id entry is a bad UX and even XCSoar is moving away from it. citeturn208825search0
- **Room + WorkManager queueing** so uploads are resilient after landing and across network loss.

That gives you a production-grade XCPro integration path without building on the wrong credential model.
