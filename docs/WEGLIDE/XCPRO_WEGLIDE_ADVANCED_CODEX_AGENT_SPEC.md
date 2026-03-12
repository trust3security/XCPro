
# XCPro – WeGlide Advanced Codex Agent Specification
**Version:** 2026-03-11  
**Audience:** Codex autonomous implementation agent for XCPro Android app  
**Status:** Engineering-ready draft  
**Scope:** OAuth account linking, per-profile aircraft mapping, resilient IGC upload queue, background upload, diagnostics, testing, and rollout

---

## 0. Read This First

This document is intentionally written as an execution contract for an autonomous coding agent.

The agent must:

1. Preserve XCPro architecture boundaries.
2. Avoid leaking WeGlide-specific code into unrelated domains.
3. Treat replay/simulator flows as non-uploadable by default.
4. Keep all time-sensitive and network-sensitive behavior deterministic and testable.
5. Prefer queueing over direct synchronous uploads.
6. Never store raw WeGlide passwords.
7. Build for partial API uncertainty:
   - public WeGlide docs clearly document Public API, OAuth, and Maps;
   - account-specific functionality such as uploading flights is explicitly described as OAuth-based;
   - exact endpoint details may evolve, so isolate endpoint contracts in a single adapter layer.

---

## 1. Product Goal

Add a production-grade WeGlide integration to XCPro so that a pilot can:

- connect a WeGlide account securely,
- map each XCPro aircraft profile to a corresponding WeGlide aircraft when appropriate,
- upload finalized IGC flights manually or automatically,
- retry safely when offline or when tokens expire,
- avoid duplicate uploads,
- inspect current integration health from the app.

This integration must feel like a native XCPro feature, not a bolted-on utility panel.

---

## 2. External Constraints We Must Respect

### 2.1 WeGlide developer model

WeGlide public docs say:

- their Public API provides read access to public data,
- OAuth is for acting on behalf of individual users, such as uploading flights or editing tasks,
- users grant and revoke access on a per-account basis,
- for public-facing apps requiring account-specific functionality, developers should contact WeGlide for an OAuth application token.

### 2.2 API stability constraint

WeGlide also says their API is **not finalized** and they plan versioning to reduce breaking changes.

Implication for XCPro:

- never scatter WeGlide request/response assumptions through the codebase,
- centralize endpoint paths, DTOs, and translation logic,
- make it easy to patch API contracts in one place.

### 2.3 Server IP vs phone client constraint

WeGlide docs mention that non-residential/server IP access may be blocked and that server-side access may require contacting them for an API key.

Implication for XCPro:

- prefer direct device-to-WeGlide API communication from the phone for pilot-owned uploads,
- do not introduce an unnecessary XCPro relay backend for upload unless there is a separate business reason.

### 2.4 Security constraint

XCPro must not keep user passwords. OAuth tokens only. Stored encrypted.

---

## 3. High-Level Architecture

Use a strict feature boundary.

Recommended module/package layout:

```text
feature/weglide/
  api/
    WeGlideApi.kt
    WeGlideAuthApi.kt
    WeGlideEndpointConfig.kt
    WeGlideDtos.kt
    WeGlideDtoMappers.kt
  auth/
    WeGlideAuthManager.kt
    WeGlidePkceFactory.kt
    WeGlideAuthStateStore.kt
    WeGlideRedirectParser.kt
  data/
    local/
      WeGlideDatabase.kt
      dao/
        WeGlideAircraftDao.kt
        WeGlideAircraftMappingDao.kt
        WeGlideUploadQueueDao.kt
        WeGlideUploadEventDao.kt
      entity/
        WeGlideAircraftEntity.kt
        WeGlideAircraftMappingEntity.kt
        WeGlideUploadQueueEntity.kt
        WeGlideUploadEventEntity.kt
    remote/
      WeGlideRemoteDataSource.kt
    repository/
      WeGlideAccountRepositoryImpl.kt
      WeGlideAircraftRepositoryImpl.kt
      WeGlideUploadRepositoryImpl.kt
  domain/
    model/
      WeGlideAccountLink.kt
      WeGlideAircraft.kt
      WeGlideAircraftMapping.kt
      WeGlideUploadCandidate.kt
      WeGlideUploadJob.kt
      WeGlideUploadAttempt.kt
      WeGlideConnectionState.kt
      WeGlideUploadPolicy.kt
    repository/
      WeGlideAccountRepository.kt
      WeGlideAircraftRepository.kt
      WeGlideUploadRepository.kt
    usecase/
      StartWeGlideConnectUseCase.kt
      CompleteWeGlideConnectUseCase.kt
      DisconnectWeGlideUseCase.kt
      RefreshWeGlideAircraftUseCase.kt
      ResolveWeGlideAircraftForProfileUseCase.kt
      QueueFlightUploadUseCase.kt
      QueueFinalizedFlightForUploadUseCase.kt
      ExecuteQueuedUploadUseCase.kt
      RetryFailedUploadsUseCase.kt
      ObserveWeGlideStatusUseCase.kt
  worker/
    WeGlideUploadWorker.kt
    WeGlideWorkScheduler.kt
  presentation/
    settings/
      WeGlideSettingsViewModel.kt
      WeGlideSettingsUiState.kt
      WeGlideSettingsScreen.kt
    aircraft/
      WeGlideAircraftMappingViewModel.kt
      WeGlideAircraftMappingUiState.kt
      WeGlideAircraftMappingScreen.kt
    uploads/
      WeGlideUploadsViewModel.kt
      WeGlideUploadsUiState.kt
      WeGlideUploadsScreen.kt
    shared/
      WeGlideStatusCard.kt
      WeGlideErrorFormatter.kt
```

---

## 4. Architecture Rules

### 4.1 Ownership and SSOT

Single sources of truth:

| Data | Owner |
|---|---|
| OAuth token bundle | encrypted token store |
| linked WeGlide account metadata | account repository |
| cached WeGlide aircraft | local Room tables |
| profile -> WeGlide aircraft mapping | mapping repository/DAO |
| upload queue state | queue repository/DAO |
| upload attempt audit/history | upload event store |

Forbidden:

- storing token copies in ViewModels,
- storing global single WeGlide aircraft id for all profiles,
- using UI state as authority,
- using in-memory only queue state.

### 4.2 Dependency direction

Allowed dependency direction:

```text
presentation -> domain -> data -> api/local
worker -> domain -> data -> api/local
```

Forbidden:

- presentation calling Retrofit directly,
- worker updating Compose state directly,
- domain depending on Android UI classes,
- unrelated features importing WeGlide DTOs.

### 4.3 Replay safety

Replay, simulator, and IGC playback modes must not create network side effects automatically.

Rules:

- replay finalization never auto-queues upload,
- replay manual upload must be hidden or explicitly blocked,
- background worker ignores replay jobs if somehow inserted.

### 4.4 Queue-first principle

Uploading immediately from a live finalize callback is forbidden.

Allowed behavior:

- finalize event constructs upload candidate,
- policy layer validates eligibility,
- job is inserted into queue,
- worker executes later.

This keeps finalization low-latency and robust.

---

## 5. Data Model

### 5.1 Domain models

#### `WeGlideAccountLink`
```kotlin
data class WeGlideAccountLink(
    val userId: String,
    val displayName: String?,
    val email: String?,
    val connectedAtEpochMs: Long,
    val authMode: AuthMode,
    val scopes: Set<String>,
    val tokenExpiresAtEpochMs: Long?,
    val lastRefreshEpochMs: Long?,
    val lastSyncEpochMs: Long?
) {
    enum class AuthMode {
        OAUTH_PKCE
    }
}
```

#### `WeGlideAircraft`
```kotlin
data class WeGlideAircraft(
    val aircraftId: String,
    val registration: String?,
    val competitionId: String?,
    val type: String?,
    val model: String?,
    val category: String?,
    val club: String?,
    val isActive: Boolean
)
```

#### `WeGlideAircraftMapping`
```kotlin
data class WeGlideAircraftMapping(
    val xcProfileId: String,
    val weGlideAircraftId: String,
    val mappingSource: MappingSource,
    val lastValidatedEpochMs: Long?
) {
    enum class MappingSource {
        USER_SELECTED,
        AUTO_SUGGESTED
    }
}
```

#### `WeGlideUploadCandidate`
```kotlin
data class WeGlideUploadCandidate(
    val localFlightId: String,
    val igcFilePath: String,
    val sha256: String,
    val profileId: String,
    val isReplay: Boolean,
    val finalizedAtEpochMs: Long,
    val fileSizeBytes: Long,
    val gliderRegistration: String?,
    val competitionId: String?,
    val pilotName: String?
)
```

#### `WeGlideUploadJob`
```kotlin
data class WeGlideUploadJob(
    val jobId: String,
    val localFlightId: String,
    val igcFilePath: String,
    val sha256: String,
    val state: State,
    val attemptCount: Int,
    val nextRetryAtEpochMs: Long?,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val lastHttpCode: Int?,
    val lastErrorCode: String?,
    val lastErrorMessage: String?,
    val remoteFlightId: String?,
    val mappedWeGlideAircraftId: String?,
    val profileId: String
) {
    enum class State {
        QUEUED,
        WAITING_FOR_NETWORK,
        RUNNING,
        SUCCESS,
        FAILED_RETRYABLE,
        FAILED_PERMANENT,
        CANCELLED
    }
}
```

#### `WeGlideUploadPolicy`
```kotlin
data class WeGlideUploadPolicy(
    val autoUploadEnabled: Boolean,
    val wifiOnly: Boolean,
    val requireMappedAircraft: Boolean,
    val blockReplay: Boolean,
    val maxAttempts: Int,
    val backoffBaseMinutes: Int
)
```

---

## 6. Local Persistence

### 6.1 Database schema overview

Create a dedicated Room database or add a WeGlide section to the existing app database if that better matches XCPro conventions.

Required tables:

1. `weglide_aircraft`
2. `weglide_aircraft_mapping`
3. `weglide_upload_queue`
4. `weglide_upload_event`

Optional table:

5. `weglide_account_cache`

Do **not** store access tokens in Room. Use encrypted preferences/DataStore.

### 6.2 `weglide_aircraft`

Suggested columns:

- `aircraft_id` TEXT PRIMARY KEY
- `registration` TEXT
- `competition_id` TEXT
- `type` TEXT
- `model` TEXT
- `category` TEXT
- `club` TEXT
- `is_active` INTEGER NOT NULL
- `fetched_at_epoch_ms` INTEGER NOT NULL
- `raw_json` TEXT NULL

Purpose:

- cache user-visible aircraft list,
- support offline selection UI,
- allow light diagnostic inspection if API changes.

### 6.3 `weglide_aircraft_mapping`

Suggested columns:

- `xc_profile_id` TEXT PRIMARY KEY
- `weglide_aircraft_id` TEXT NOT NULL
- `mapping_source` TEXT NOT NULL
- `last_validated_epoch_ms` INTEGER NULL
- `created_at_epoch_ms` INTEGER NOT NULL
- `updated_at_epoch_ms` INTEGER NOT NULL

Constraints:

- exactly one mapping per XC profile,
- foreign key to `weglide_aircraft.aircraft_id` if practical,
- delete mapping if aircraft disappears only after explicit user handling, not silently.

### 6.4 `weglide_upload_queue`

Suggested columns:

- `job_id` TEXT PRIMARY KEY
- `local_flight_id` TEXT NOT NULL
- `profile_id` TEXT NOT NULL
- `igc_file_path` TEXT NOT NULL
- `sha256` TEXT NOT NULL
- `state` TEXT NOT NULL
- `attempt_count` INTEGER NOT NULL
- `next_retry_at_epoch_ms` INTEGER NULL
- `created_at_epoch_ms` INTEGER NOT NULL
- `updated_at_epoch_ms` INTEGER NOT NULL
- `finalized_at_epoch_ms` INTEGER NOT NULL
- `file_size_bytes` INTEGER NOT NULL
- `mapped_weglide_aircraft_id` TEXT NULL
- `remote_flight_id` TEXT NULL
- `last_http_code` INTEGER NULL
- `last_error_code` TEXT NULL
- `last_error_message` TEXT NULL
- `is_replay` INTEGER NOT NULL
- `pilot_name` TEXT NULL
- `registration` TEXT NULL
- `competition_id` TEXT NULL

Indexes:

- unique index on `sha256` where state in success-like states if supported,
- index on `state`,
- index on `next_retry_at_epoch_ms`,
- index on `local_flight_id`.

### 6.5 `weglide_upload_event`

This is a useful diagnostic/audit table.

Suggested columns:

- `event_id` TEXT PRIMARY KEY
- `job_id` TEXT NOT NULL
- `timestamp_epoch_ms` INTEGER NOT NULL
- `kind` TEXT NOT NULL
- `http_code` INTEGER NULL
- `message` TEXT NULL
- `payload_summary` TEXT NULL

Event kinds:

- `JOB_CREATED`
- `RUN_STARTED`
- `TOKEN_REFRESHED`
- `UPLOAD_SUCCEEDED`
- `UPLOAD_FAILED_RETRYABLE`
- `UPLOAD_FAILED_PERMANENT`
- `JOB_CANCELLED`

---

## 7. Auth Design

### 7.1 Chosen pattern

OAuth 2 Authorization Code Flow with PKCE.

Why:

- correct standard for public mobile clients,
- avoids embedded password collection,
- allows revocation,
- future-proof if WeGlide expands account-scoped APIs.

### 7.2 Auth launch

Preferred implementation:

- use AppAuth-Android if compatible with XCPro architecture,
- otherwise implement a small standards-compliant custom-tab PKCE launcher.

Required state:

- `codeVerifier`
- `codeChallenge`
- `state`
- `redirectUri`

Suggested redirect URI:

```text
xcpro://auth/weglide/callback
```

### 7.3 Auth state machine

```text
IDLE
  -> LAUNCHING
  -> WAITING_FOR_CALLBACK
  -> CALLBACK_RECEIVED
  -> EXCHANGING_CODE
  -> LINKED
  -> ERROR
```

The state machine must be recoverable after process death.

### 7.4 Token storage

Store in encrypted DataStore or EncryptedSharedPreferences:

- `access_token`
- `refresh_token`
- `token_type`
- `expires_at_epoch_ms`
- `scope`
- `obtained_at_epoch_ms`

Never log token values.

### 7.5 Refresh logic

Refresh token only in repository/worker layer.

Rules:

- consider token stale before expiry with a small safety window, e.g. 60 seconds,
- synchronize refresh so multiple parallel uploads do not all refresh at once,
- if refresh fails with auth-related error, mark account disconnected/reauth required.

### 7.6 Disconnect logic

When user disconnects WeGlide:

- wipe token store,
- wipe linked account cache,
- keep upload queue rows but mark them blocked due to auth missing,
- optionally offer "delete cached aircraft and mappings" as a separate explicit action.

Reason:

- pilots may relink the same account later and preserve configuration.

---

## 8. Endpoint Isolation Strategy

### 8.1 Problem

WeGlide docs clearly describe OAuth and public API responsibilities, but the exact upload endpoint contract may change or differ from assumptions found in older snippets.

### 8.2 Solution

Build a single endpoint adapter layer.

Create:

- `WeGlideEndpointConfig`
- `WeGlideApiContractVersion`
- `WeGlideRequestFactory`
- `WeGlideResponseParser`

This layer owns:

- base URL,
- endpoint paths,
- multipart field naming,
- auth header construction,
- response parsing,
- temporary compatibility shims.

### 8.3 Example config structure

```kotlin
data class WeGlideEndpointConfig(
    val apiBaseUrl: String,
    val authorizeUrl: String?,
    val tokenUrl: String?,
    val uploadFlightPath: String,
    val currentUserPath: String?,
    val aircraftPath: String?,
    val contractVersion: String
)
```

### 8.4 Practical default

Use build-time or runtime config to allow quick changes without touching unrelated files.

Example:

```kotlin
object DefaultWeGlideConfig {
    const val API_BASE = "https://api.weglide.org"
    const val UPLOAD_PATH = "/v1/igcfile"
    const val USER_PATH = "/v1/users/me"
    const val AIRCRAFT_PATH = "/v1/aircraft"
}
```

If WeGlide later says a different upload path or field name is required, patch only here and associated DTO mappers/tests.

---

## 9. Retrofit / Network Layer

### 9.1 API interface sketch

```kotlin
interface WeGlideApi {

    @Multipart
    @POST
    suspend fun uploadIgcFile(
        @Url url: String,
        @Part file: MultipartBody.Part,
        @PartMap metadata: Map<String, @JvmSuppressWildcards RequestBody>
    ): Response<ResponseBody>

    @GET
    suspend fun getCurrentUser(
        @Url url: String
    ): Response<WeGlideUserDto>

    @GET
    suspend fun getAircraft(
        @Url url: String
    ): Response<List<WeGlideAircraftDto>>
}
```

Use dynamic `@Url` if contract agility matters.

### 9.2 Auth interceptor

Implement bearer token header injection in a dedicated OkHttp interceptor **only** for non-auth endpoints.

Avoid:

- making the interceptor perform heavy blocking refresh logic without coordination.

Preferred:

- repository ensures valid token before issuing request,
- interceptor only attaches current token.

### 9.3 Network result model

Create a small sealed result:

```kotlin
sealed interface WeGlideNetworkResult<out T> {
    data class Success<T>(val value: T, val httpCode: Int) : WeGlideNetworkResult<T>
    data class RetryableFailure(val httpCode: Int?, val message: String?) : WeGlideNetworkResult<Nothing>
    data class PermanentFailure(val httpCode: Int?, val message: String?) : WeGlideNetworkResult<Nothing>
    data class AuthFailure(val httpCode: Int?, val message: String?) : WeGlideNetworkResult<Nothing>
}
```

Map HTTP behavior centrally.

Suggested default classification:

- `401`, `403` -> auth failure
- `408`, `429`, `500`, `502`, `503`, `504` -> retryable
- `400`, `404`, `409`, `410`, `422` -> usually permanent unless proven otherwise

---

## 10. Account Resolution

### 10.1 Goal

After successful token exchange, XCPro should resolve a stable user identity for the linked account.

Preferred source order:

1. current-user endpoint,
2. token response if it already includes identity,
3. user info endpoint if OAuth/OpenID flow supports it.

Persist:

- `userId`
- `displayName`
- `email` if available
- granted scopes
- timestamps

### 10.2 Why user ID matters

Used for:

- diagnostics,
- showing linked account in settings,
- future account mismatch detection,
- support logs,
- relink safety.

---

## 11. Aircraft Sync and Mapping

### 11.1 Why this matters

XCPro already supports multiple aircraft types and profile-specific UI setups. A single global WeGlide aircraft selection is wrong.

Mapping must be profile-specific.

### 11.2 Aircraft sync flow

```text
User opens WeGlide settings
  -> if linked, tap Refresh Aircraft
  -> fetch aircraft list from WeGlide
  -> normalize and cache locally
  -> update mapping suggestions
```

### 11.3 Auto-suggestion heuristics

When aircraft list is fetched, suggest mapping by this priority order:

1. exact registration match (case-insensitive, trimmed, punctuation normalized)
2. exact competition ID match
3. registration partial match if safe
4. exact type/model match only as weak suggestion, never auto-apply silently

Auto-apply only if confidence is high and unique.

Otherwise present suggestions.

### 11.4 Mapping UI requirements

For each XCPro profile show:

- local aircraft/profile name,
- local registration,
- local competition ID,
- mapped WeGlide aircraft summary or "Not mapped",
- refresh button,
- clear mapping action.

### 11.5 Validation

If a mapped aircraft no longer exists in refreshed list:

- mark mapping as stale,
- show warning,
- do not silently delete,
- uploads may still proceed if policy allows no mapping.

---

## 12. Upload Eligibility Policy

### 12.1 Core rules

A flight is eligible for queueing only if:

- it is finalized,
- local IGC file exists,
- file size > 0,
- file hash computed successfully,
- it is not replay if replay uploads are blocked,
- WeGlide account is linked if upload is requested,
- required policy conditions are met.

### 12.2 Auto-upload policy inputs

Inputs:

- account linked?
- network available?
- wifi only enabled?
- replay?
- mapped aircraft required?
- finalized flight already queued/uploaded?
- file already uploaded by same hash?

### 12.3 Decision table

| Condition | Action |
|---|---|
| replay flight | reject permanently |
| same hash already successful | skip queue insertion |
| account not linked | if manual request, surface re-link error; if auto, store blocked event or do nothing based on UX decision |
| file missing | permanent failure |
| network missing | queue as waiting |
| token stale | queue and let worker refresh |
| everything okay | queue |

### 12.4 Duplicate suppression

Primary key = SHA-256 of file contents.

Secondary checks:

- localFlightId,
- remote flight ID if returned,
- existing successful job with same hash,
- existing queued/running job with same hash.

Insertion logic:

- if success exists for same hash -> do not insert
- if queued/running exists for same hash -> do not insert duplicate
- if failed permanent exists for same hash and user manually retries -> either clone or reset same job based on UX preference

---

## 13. Upload Queue Lifecycle

### 13.1 States

```text
QUEUED
WAITING_FOR_NETWORK
RUNNING
SUCCESS
FAILED_RETRYABLE
FAILED_PERMANENT
CANCELLED
```

### 13.2 Transition rules

- `QUEUED -> RUNNING` when worker picks up
- `RUNNING -> SUCCESS` on accepted remote result
- `RUNNING -> FAILED_RETRYABLE` on transient error
- `RUNNING -> FAILED_PERMANENT` on unrecoverable validation/auth mismatch after policy
- `FAILED_RETRYABLE -> QUEUED` when rescheduled
- `ANY -> CANCELLED` only by explicit user action or cleanup logic

### 13.3 Backoff policy

Default recommendation:

- exponential backoff
- base = 15 minutes
- max = 12 hours
- max attempts = 8

Pseudo:

```kotlin
nextRetry = now + min(maxBackoff, base * 2.0.pow(attemptCount))
```

### 13.4 Success semantics

A job is `SUCCESS` only when the remote server indicates accepted upload.

Persist:

- `remoteFlightId` if available,
- final HTTP code,
- success timestamp in event log.

---

## 14. WorkManager Strategy

### 14.1 Why WorkManager

Need:

- persistence across app restarts,
- connectivity awareness,
- deferred execution,
- resilient retries.

### 14.2 Scheduling design

Use a unique work name per queue drain type, for example:

- `weglide-upload-drain`

Use:

- enqueue unique work with `KEEP` or `APPEND_OR_REPLACE` depending on preferred behavior,
- worker itself drains multiple queued jobs in one run to reduce overhead.

### 14.3 Worker algorithm

Pseudo:

```text
load policy
if account not linked -> exit retry/no-op depending on queue state
if no network -> mark waiting and retry
load next eligible jobs ordered by createdAt
for each job:
    if replay -> mark permanent failure
    ensure token valid
    build multipart request
    upload
    classify result
    update queue and event tables
if queued jobs remain and conditions good:
    reschedule immediate/short delay
```

### 14.4 Cancellation

User may cancel queued jobs that are not running. Running job cancellation should be best-effort only.

---

## 15. Multipart Upload Builder

### 15.1 Isolate it

Create a dedicated builder:

```kotlin
class WeGlideMultipartBuilder {
    fun build(
        candidate: WeGlideUploadCandidate,
        mappedAircraftId: String?
    ): WeGlideMultipartPayload
}
```

### 15.2 Responsibilities

- open file safely,
- assign correct media type,
- create file part,
- include any optional metadata only when contract says so,
- never expose local file path to UI or logs beyond debug-safe redacted form.

### 15.3 Logging

Do not log full file path or payload bodies in release builds.

Allowed log fields:

- localFlightId,
- file size,
- jobId,
- profileId,
- hash prefix (short),
- endpoint path,
- response code.

---

## 16. Finalized Flight Integration

### 16.1 Input source

XCPro already has or will have a finalized flight/IGC flow. The WeGlide integration must subscribe to the finalized-flight publication point, not duplicate flight lifecycle logic.

### 16.2 Event contract

Input event should provide enough info to construct `WeGlideUploadCandidate`:

- local flight id
- file path
- finalization time
- profile id
- replay/live flag
- optional glider metadata

### 16.3 Post-finalization UX

Suggested behavior:

- if auto-upload enabled and linked -> queue silently and show lightweight status
- if auto-upload disabled but linked -> show prompt "Upload to WeGlide?"
- if not linked -> show non-blocking CTA in file details/settings, not during critical landing moments

Do not interrupt safety-critical or immediate post-flight flows with heavy dialogs.

---

## 17. Manual Upload UX

### 17.1 Entry points

Provide manual upload from:

- finalized flight details screen,
- IGC files list,
- WeGlide uploads screen.

### 17.2 Manual upload behavior

When pilot taps upload:

1. validate file exists,
2. resolve duplicate state,
3. if account not linked -> route to connect flow,
4. if mapped aircraft required and missing -> show mapping prompt,
5. queue upload instead of direct foreground upload.

### 17.3 UX messages

Examples:

- `Connected as David Blank`
- `Queued for upload`
- `Uploaded to WeGlide`
- `Needs Wi‑Fi to upload`
- `WeGlide login expired`
- `Replay flights cannot be uploaded`

---

## 18. Error Taxonomy

### 18.1 User-facing categories

Create friendly error categories:

- `AUTH_REQUIRED`
- `AUTH_EXPIRED`
- `NETWORK_UNAVAILABLE`
- `SERVER_TEMPORARY`
- `UPLOAD_REJECTED`
- `DUPLICATE_ALREADY_UPLOADED`
- `FILE_NOT_FOUND`
- `REPLAY_BLOCKED`
- `AIRCRAFT_MAPPING_REQUIRED`
- `UNKNOWN`

### 18.2 Internal error mapping

Map raw causes into typed internal errors:

```kotlin
sealed interface WeGlideUploadError {
    data object ReplayBlocked : WeGlideUploadError
    data object MissingFile : WeGlideUploadError
    data class Auth(val recoverable: Boolean) : WeGlideUploadError
    data class Http(val code: Int, val retryable: Boolean, val body: String?) : WeGlideUploadError
    data class Network(val reason: String?) : WeGlideUploadError
    data class Duplicate(val existingJobId: String?) : WeGlideUploadError
}
```

### 18.3 Permanent vs retryable examples

Retryable:

- timeout
- DNS failure
- 429
- 5xx
- temporary TLS/network

Permanent:

- replay blocked
- file missing
- malformed file rejected
- validation error clearly tied to bad content and not transient
- user revoked access and refresh fails until relink

---

## 19. Diagnostics and Observability

### 19.1 In-app diagnostics screen

Add a WeGlide status surface with:

- linked account display name
- account ID if available
- token expiry summary
- last aircraft sync time
- mapped aircraft per profile count
- queue summary: queued/running/success/failed
- last upload result
- last refresh result

### 19.2 Structured logs

Introduce a stable logging tag set:

- `WG_AUTH`
- `WG_API`
- `WG_QUEUE`
- `WG_WORKER`
- `WG_MAPPING`

Logs must be concise and redact secrets.

### 19.3 Debug export

Optional but recommended:

- allow export of WeGlide diagnostics summary as plain text for support,
- never include token values,
- do not include entire IGC contents.

---

## 20. Security Requirements

### 20.1 Must do

- encrypted token storage,
- custom tabs/external browser for auth,
- state verification,
- PKCE verifier/challenge,
- token redaction in logs,
- release-safe exception handling.

### 20.2 Must not do

- no raw password collection,
- no WebView login,
- no plaintext token logs,
- no permanent token copies in Room,
- no passing tokens through navigation routes or Compose state save bundles.

### 20.3 File access safety

When uploading:

- verify file still exists,
- open read-only,
- close streams promptly,
- guard against content URI vs file path differences if XCPro uses both.

---

## 21. Settings Model

### 21.1 Required user settings

```kotlin
data class WeGlideSettings(
    val autoUploadEnabled: Boolean,
    val wifiOnly: Boolean,
    val requireMappedAircraft: Boolean,
    val showPostFlightPrompt: Boolean,
    val diagnosticsEnabled: Boolean
)
```

### 21.2 Defaults

Recommended defaults:

- autoUploadEnabled = false initially
- wifiOnly = false
- requireMappedAircraft = false
- showPostFlightPrompt = true
- diagnosticsEnabled = true in debug, optional in release

Rationale:

- avoid surprise uploads until pilot opts in,
- still make manual flow easy,
- allow gradual trust building.

---

## 22. Testing Strategy

### 22.1 Unit tests

Required unit coverage areas:

1. PKCE generation and state verification
2. token expiry and refresh decision
3. aircraft auto-suggestion heuristics
4. upload eligibility policy
5. duplicate suppression
6. queue state transitions
7. error classification
8. replay blocking

### 22.2 DAO tests

Required DAO tests:

- insert and read aircraft cache
- one mapping per profile
- duplicate hash handling
- queue ordering by retry time and creation time
- event log insertion

### 22.3 Repository tests

Required repository tests:

- link/disconnect lifecycle
- aircraft refresh cache updates
- queue insertion idempotency
- refresh token before upload
- successful upload marks success and event records
- retryable failure reschedules correctly

### 22.4 Worker tests

Required worker tests:

- no network -> waiting/retry
- expired token with successful refresh -> upload succeeds
- refresh failure -> auth required state
- replay job -> permanent failure
- multiple queued jobs drained sequentially

### 22.5 Integration tests

Recommended integration tests with fake API server:

- connect flow callback parsing
- aircraft refresh end-to-end
- manual upload queueing
- auto-upload after finalization
- 422 rejection path
- 429 throttling path
- duplicate success suppression across app restarts

### 22.6 UI tests

Recommended UI tests:

- connect/disconnect actions visible
- profile mapping UI shows stale/missing mappings
- queue list shows state transitions
- error banners for auth expired/network unavailable

---

## 23. Acceptance Criteria

### 23.1 Phase-complete criteria

The feature is only complete when all are true:

- pilot can connect account using OAuth PKCE,
- linked account persists across app restarts,
- aircraft list can be refreshed and cached,
- per-profile mapping works,
- finalized live flight can be queued,
- replay finalization never auto-uploads,
- queued uploads survive process death,
- duplicate IGC file is not uploaded twice,
- failed uploads are diagnosable in UI,
- tests cover the critical state machine.

### 23.2 Non-goals for initial release

Do not block release waiting for:

- task sync
- story creation
- advanced remote flight editing
- multi-account support
- server relay architecture
- cross-device sync of WeGlide settings

---

## 24. Implementation Phases for Codex

### Phase 0 – Audit Existing XCPro Hooks

Goal:

- identify current finalized IGC event source,
- identify current profile repository and aircraft metadata sources,
- identify existing WorkManager, Room, encryption, and auth conventions.

Deliverables:

- implementation notes markdown in repo
- list of exact integration hook points
- no behavior changes yet

Exit criteria:

- agent can point to exact existing files/classes where finalization, profile ownership, and settings live

### Phase 1 – Foundations

Build:

- domain models
- token store interface
- Room entities/DAOs for aircraft, mapping, queue, event
- repository interfaces
- error/result models

Do not connect UI yet.

Exit criteria:

- compile passes
- DAO tests pass
- no leaking WeGlide types outside feature boundary

### Phase 2 – Auth

Build:

- PKCE helper
- auth manager
- redirect parser
- encrypted token store
- account repository
- connect/disconnect flow
- settings status card

Exit criteria:

- callback parsing tested
- token storage encrypted
- account link visible in settings

### Phase 3 – Aircraft Sync + Mapping

Build:

- aircraft fetch/cache repository
- mapping use cases
- mapping UI
- suggestion heuristics

Exit criteria:

- each XCPro profile can map independently
- mapping survives restart
- refresh updates cache

### Phase 4 – Upload Queue

Build:

- upload candidate builder
- queue insertion use cases
- duplicate suppression
- upload list UI
- policy settings

Exit criteria:

- manual upload queues correctly
- duplicate hash is suppressed
- replay queueing blocked

### Phase 5 – Worker Execution

Build:

- WorkManager worker
- network/auth refresh logic
- success/failure classification
- event log updates
- diagnostics screen

Exit criteria:

- queued uploads execute and update UI
- retryable failures back off
- auth failures require relink

### Phase 6 – Finalized Flight Hook

Build:

- subscription to finalized live flight event
- auto-upload and/or post-flight prompt policy
- queue scheduling after successful finalization only

Exit criteria:

- real finalized flight produces queue item
- replay finalization does nothing
- finalization path remains lightweight

### Phase 7 – Hardening

Build:

- structured logs
- support export
- stale mapping warnings
- cleanup/migration logic
- release notes and docs

Exit criteria:

- feature stable under process death and airplane mode toggling
- release checklist complete

---

## 25. Migration Notes

If XCPro already has temporary WeGlide settings fields such as:

- username
- password
- single aircraft ID
- upload enabled boolean without queue state

Then migrate as follows:

- ignore old password fields and remove from UI/storage,
- convert single aircraft ID into unmapped state unless it can be confidently attached to one active profile,
- preserve upload preference where sensible,
- create one-time migration markers to avoid repeated destructive migrations.

---

## 26. Suggested Kotlin Interfaces

### 26.1 Token store

```kotlin
interface WeGlideTokenStore {
    suspend fun getTokenBundle(): WeGlideTokenBundle?
    suspend fun saveTokenBundle(bundle: WeGlideTokenBundle)
    suspend fun clear()
}
```

### 26.2 Account repository

```kotlin
interface WeGlideAccountRepository {
    fun observeLinkedAccount(): Flow<WeGlideAccountLink?>
    suspend fun completeOAuthCallback(callbackUri: Uri): Result<WeGlideAccountLink>
    suspend fun disconnect()
    suspend fun ensureFreshAccessToken(): Result<String>
}
```

### 26.3 Aircraft repository

```kotlin
interface WeGlideAircraftRepository {
    fun observeAircraft(): Flow<List<WeGlideAircraft>>
    fun observeMapping(profileId: String): Flow<WeGlideAircraftMapping?>
    suspend fun refreshAircraft(): Result<Unit>
    suspend fun setMapping(profileId: String, weGlideAircraftId: String): Result<Unit>
    suspend fun clearMapping(profileId: String): Result<Unit>
}
```

### 26.4 Upload repository

```kotlin
interface WeGlideUploadRepository {
    fun observeJobs(): Flow<List<WeGlideUploadJob>>
    suspend fun queueUpload(candidate: WeGlideUploadCandidate): Result<QueueResult>
    suspend fun executeNextEligibleJob(nowEpochMs: Long): Result<ExecutionResult>
    suspend fun retry(jobId: String): Result<Unit>
    suspend fun cancel(jobId: String): Result<Unit>
}
```

---

## 27. Recommended Fake API Harness

For testing, create a fake service that can simulate:

- success with remote flight id
- 401 expired token
- refresh success
- refresh failure
- 422 malformed IGC
- 429 rate limit
- intermittent 503
- aircraft list empty
- aircraft list changed/stale mapping

This makes the worker and repository layers robust before real WeGlide credentials are available.

---

## 28. Code Quality Rules for Codex

The agent must follow these rules:

1. No giant god-class `WeGlideManager`.
2. No business logic inside Compose screens.
3. No direct `System.currentTimeMillis()` inside policy code if XCPro already uses a clock abstraction.
4. No direct network calls from ViewModels.
5. No mutable singleton state for auth/session.
6. Every queue state transition must be testable.
7. Every public repository method must have at least one unit/integration test.
8. Keep DTO names separate from domain names.

---

## 29. Explicit Unknowns and How to Handle Them

These are acceptable uncertainties and the agent must design around them:

### Unknown A: exact upload endpoint and form field contract
Action:
- isolate in `WeGlideEndpointConfig` and `WeGlideMultipartBuilder`
- cover with fake API tests
- avoid hard-coding assumptions throughout feature

### Unknown B: exact user-info endpoint shape
Action:
- parse conservatively
- store only validated fields
- fail gracefully if display name/email unavailable

### Unknown C: aircraft endpoint availability/shape for account-scoped data
Action:
- make aircraft sync optional
- feature still useful with manual uploads and unmapped aircraft

### Unknown D: exact refresh token support
Action:
- design token store for refresh tokens
- if refresh unsupported, degrade to re-auth required

---

## 30. Release Checklist

Before release, verify:

- WeGlide OAuth credentials obtained from WeGlide
- redirect URI registered in app and approved by WeGlide if required
- privacy wording updated if uploads become automatic/public
- support diagnostics tested on release build
- auth revocation path tested
- offline queue tested with airplane mode
- duplicate suppression tested with same IGC file twice
- replay mode tested
- migration from old settings tested
- no token leakage in logs

---

## 31. Minimal Privacy/Disclosure Copy

Suggested UI disclosure when enabling auto-upload:

> When enabled, XCPro can upload finalized live IGC flights to your linked WeGlide account. Uploaded flights may become public according to your WeGlide account and service rules.

Suggested connect screen note:

> XCPro does not store your WeGlide password. Sign-in is handled by WeGlide using secure browser-based authorization.

---

## 32. Final Instruction to Codex Agent

Implement this feature in the following order:

1. audit hook points,
2. foundations,
3. auth,
4. aircraft sync/mapping,
5. upload queue,
6. worker execution,
7. finalized flight hook,
8. hardening.

Do not jump straight to network calls from UI.  
Do not bypass the queue.  
Do not use a single global WeGlide aircraft ID.  
Do not allow replay uploads.  
Do not store passwords.  

If an API contract is uncertain, isolate it behind the adapter layer and continue implementing the rest of the feature.

---

## 33. Optional Future Extensions

Not required for v1, but design should not block them:

- WeGlide task sync/import
- edit/delete uploaded flight
- remote upload status deep links
- multi-account switching
- cloud backup of WeGlide mapping settings
- richer aircraft metadata reconciliation
- support for WeGlide maps if licensing later permits

---

## 34. Summary

This spec intentionally pushes XCPro toward the correct production architecture:

- OAuth-based account linking
- direct phone-to-WeGlide upload
- per-profile aircraft mapping
- queue-first upload pipeline
- WorkManager execution
- duplicate suppression
- replay safety
- diagnostics and testability

This is the right shape for a serious soaring app and is much closer to a production-quality integration than a simple “enter username/password and POST a file” approach.
