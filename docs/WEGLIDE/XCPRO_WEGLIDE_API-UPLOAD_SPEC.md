# XCPro → WeGlide Upload Integration Spec

## Purpose

Add a production-grade WeGlide integration to XCPro so a pilot can:

1. connect their WeGlide account,
2. upload completed IGC files directly from XCPro,
3. keep the app linked to the correct WeGlide user,
4. handle failures safely,
5. avoid bad security practices.

This spec is written so a Codex agent can implement the feature inside the existing XCPro Android architecture.

---

## What we know from current WeGlide docs

### 1) WeGlide has a public API and OAuth flow
WeGlide’s developer docs state that:
- the public API provides read access to public data such as flights, aircraft, airports, users, and clubs,
- OAuth is the mechanism for account-specific actions,
- OAuth is used for operations done in the name of a user such as **uploading flights** or **editing tasks**,
- for public-facing apps, WeGlide says you should contact them for an OAuth application token.  
Source: https://docs.weglide.org/creators/developers.html

### 2) There is a flight upload API endpoint
Search snippets from WeGlide’s ReDoc show an upload endpoint:
- `POST /v1/igcfile`
- URL: `https://api.weglide.org/v1/igcfile`
- success response shown as `201`
- validation failure shown as `422`  
Source: https://api.weglide.org/redoc

### 3) WeGlide user accounts have an account ID
WeGlide docs for duplicate-account handling explicitly mention using an **account ID or URL**. That strongly confirms a stable account identifier exists and is meaningful in operations/support flows.  
Source: https://docs.weglide.org/how_to/duplicate_account.html

### 4) IGC uploads are public by nature on WeGlide
WeGlide’s privacy policy says that when users upload flight logs or use live tracking, they are creating a permanent public record, and location history becomes public.  
Source: https://docs.weglide.org/legal/privacy_policy.html

### 5) IGC validity matters for scored flights
WeGlide contest docs repeatedly state that league/speed scoring requires approved records and typically a valid `G` record signature for qualifying flights.  
Source examples:
- https://docs.weglide.org/contests/international/free.html
- https://docs.weglide.org/contests/international/travel.html
- https://docs.weglide.org/contests/international/sprint.html

---

## Key product decision

## Use OAuth, not raw username/password storage

Do **not** ask the XCPro user to type their WeGlide password into XCPro for long-term storage.

That is the wrong pattern for a modern public app.

Instead:
- launch the user into WeGlide authorization,
- let WeGlide authenticate them,
- receive tokens back in XCPro,
- store tokens securely,
- use the token for upload calls.

This lines up with WeGlide’s own developer documentation.

### Why this matters
- safer for users,
- less liability for XCPro,
- revocable by the user,
- fits industry-standard mobile auth,
- avoids handling raw credentials beyond the WeGlide login page.

---

## Architecture recommendation for XCPro

Create a dedicated integration module/layer:

- `feature/integrations/weglide/`
- or equivalent package structure already used by XCPro.

Suggested internal structure:

```text
feature/weglide/
  data/
    api/
      WeGlideApi.kt
      WeGlideAuthApi.kt
    dto/
      WeGlideUploadResponseDto.kt
      WeGlideUserDto.kt
      WeGlideTokenDto.kt
      WeGlideErrorDto.kt
    repository/
      WeGlideRepositoryImpl.kt
    auth/
      WeGlideOAuthManager.kt
      PkceHelper.kt
  domain/
    model/
      WeGlideAccountLink.kt
      WeGlideUploadRequest.kt
      WeGlideUploadResult.kt
      WeGlideConnectionState.kt
    repository/
      WeGlideRepository.kt
    usecase/
      StartWeGlideLinkUseCase.kt
      CompleteWeGlideLinkUseCase.kt
      UploadFlightToWeGlideUseCase.kt
      GetWeGlideAccountUseCase.kt
      DisconnectWeGlideUseCase.kt
  presentation/
    WeGlideSettingsViewModel.kt
    WeGlideUploadViewModel.kt
    WeGlideSettingsScreen.kt
    WeGlideUploadUiState.kt
```

Keep it isolated from core flight logic.

The upload feature should consume already-generated XCPro IGC files, not reimplement IGC generation inside the upload path.

---

## Required user-facing feature set

### Phase 1 — Account linking
User can:
- tap **Connect WeGlide**,
- complete OAuth in browser/custom tab,
- return to XCPro,
- see linked status,
- see basic linked account identity.

Display:
- pilot name if available,
- WeGlide account ID if available,
- token status,
- last successful upload time.

### Phase 2 — Manual upload
User can:
- pick a completed XCPro IGC flight,
- upload it to WeGlide,
- see success/failure result.

### Phase 3 — Auto-upload after flight
User can enable:
- auto-upload on flight save,
- Wi‑Fi only or any network,
- upload only if file passes XCPro validation,
- retry on later connectivity.

### Phase 4 — Robust background delivery
Use WorkManager for:
- guaranteed upload,
- retry with backoff,
- persistence across app restarts,
- respecting connectivity constraints.

---

## Authentication design

## Preferred auth flow
Implement OAuth 2.0 Authorization Code Flow with PKCE for Android.

Even though WeGlide docs do not spell out the mobile PKCE example on the page we could access, this is the correct mobile implementation pattern when OAuth is available.

### Flow
1. XCPro asks backend config for WeGlide OAuth details.
2. XCPro generates PKCE verifier/challenge and state.
3. XCPro opens custom tab to WeGlide authorization URL.
4. User logs into WeGlide and grants access.
5. WeGlide redirects back to XCPro app deep link.
6. XCPro exchanges authorization code for token.
7. XCPro stores tokens in encrypted local storage.
8. XCPro fetches current user/account details if endpoint available.
9. XCPro stores linked account metadata.

### Android implementation notes
Use:
- AppAuth for Android, or
- a small standards-compliant PKCE client if you already have auth infrastructure.

Use a deep link such as:

```text
xcpro://auth/weglide/callback
```

Add anti-CSRF `state` validation.

### Storage
Store only:
- access token,
- refresh token if provided,
- expiry time,
- token type,
- linked WeGlide user/account ID,
- linked account display name,
- granted scopes.

Use:
- `EncryptedSharedPreferences` or encrypted DataStore,
- Android Keystore-backed encryption.

Do **not** store the pilot password.

---

## How to identify the WeGlide user

Best case:
- the token exchange response or a follow-up user endpoint returns the WeGlide user/account ID.

Fallback options:
1. parse it from an explicit user API response,
2. if WeGlide returns a profile URL, parse the numeric ID from that URL,
3. store the linked account identifier after first successful authenticated response.

Persist locally as something like:

```kotlin
data class WeGlideAccountLink(
    val accountId: Long?,
    val displayName: String?,
    val profileUrl: String?,
    val accessToken: String,
    val refreshToken: String?,
    val expiresAtEpochMs: Long?,
    val scopes: List<String>,
    val linkedAtEpochMs: Long,
)
```

Do not block uploads purely because `accountId` is unknown if the OAuth token is valid and upload works.

The token is the real authority.

---

## Upload API design inside XCPro

Create a repository contract like:

```kotlin
interface WeGlideRepository {
    suspend fun getConnectionState(): WeGlideConnectionState
    suspend fun startOAuthLink(): WeGlideOAuthStart
    suspend fun completeOAuthLink(callbackUri: Uri): WeGlideAccountLink
    suspend fun uploadIgc(file: File, metadata: WeGlideUploadMetadata? = null): WeGlideUploadResult
    suspend fun disconnect()
}
```

### HTTP upload shape
The ReDoc snippet confirms `POST /v1/igcfile`, but the exact multipart field names were not visible from accessible docs.

So Codex should implement the upload client behind an abstraction and verify the final request shape against live WeGlide API docs or a real integration test account.

Probable implementation pattern:
- `POST https://api.weglide.org/v1/igcfile`
- `Authorization: Bearer <token>`
- `multipart/form-data`
- include the IGC file as a multipart part
- optional fields only if confirmed by docs

Pseudo-code:

```kotlin
@Multipart
@POST("v1/igcfile")
suspend fun uploadIgc(
    @Part igcFilePart: MultipartBody.Part,
    @Header("Authorization") authorization: String,
): Response<WeGlideUploadResponseDto>
```

### Important
Do not hard-code assumptions about optional fields until verified against live docs.

The minimum viable implementation should support:
- authenticated upload of the IGC file,
- handling 201 success,
- handling 401/403 auth failures,
- handling 422 validation errors.

---

## Upload workflow in XCPro

### Manual upload flow
1. User opens saved flight details.
2. User taps **Upload to WeGlide**.
3. App checks linked account and token validity.
4. App refreshes token if needed.
5. App validates file exists and is readable.
6. App validates file extension is `.igc`.
7. App uploads file.
8. App stores result locally.
9. App shows success with any returned flight URL or identifier.

### Auto-upload flow
1. XCPro finalizes flight and writes IGC.
2. App enqueues `WeGlideUploadWorker`.
3. Worker checks connection + auth.
4. Worker uploads.
5. On success, mark local flight `weGlideUploadState = Uploaded`.
6. On transient failure, retry with exponential backoff.
7. On permanent failure, mark `FailedPermanent` and show user-visible reason.

---

## Local persistence model

Add fields to local flight entity, for example:

```kotlin
enum class WeGlideUploadState {
    NOT_LINKED,
    PENDING,
    UPLOADING,
    UPLOADED,
    FAILED_RETRYABLE,
    FAILED_PERMANENT
}
```

```kotlin
data class FlightUploadStatus(
    val weGlideState: WeGlideUploadState,
    val weGlideUploadedAtEpochMs: Long? = null,
    val weGlideRemoteFlightId: Long? = null,
    val weGlideRemoteFlightUrl: String? = null,
    val weGlideLastErrorCode: Int? = null,
    val weGlideLastErrorMessage: String? = null,
)
```

This prevents duplicate uploads and gives clean UI state.

---

## Duplicate upload prevention

This matters because pilots will retry, go offline, restart the app, or press the button twice.

### Prevent duplicates with three layers

#### 1) Local state guard
If a local flight is already marked uploaded with remote ID/URL, do not upload again unless user explicitly chooses **Upload again**.

#### 2) Content fingerprint
Compute SHA-256 of the IGC file and store it locally.

```kotlin
igcSha256: String
```

Use this to detect accidental duplicate enqueue.

#### 3) Idempotency strategy inside app
Before starting a new worker, check whether another upload job exists for the same local flight ID or same IGC hash.

Use unique WorkManager names such as:

```text
weglide-upload-flight-<localFlightId>
```

---

## Error handling rules

### 201 Created
Treat as success.
Persist remote identifiers/URL if returned.

### 401 / 403
Token invalid or permission denied.
- attempt token refresh once,
- if still failing, mark account disconnected/reauthorization required.

### 422 Validation Error
Treat as user/data problem, not retryable spam.
Possible causes may include:
- malformed IGC,
- unsupported upload shape,
- flight invalid for server rules,
- missing required multipart field.

Persist server message if available.
Show the user the exact reason.

### 429 / 5xx / network timeout
Retryable.
Use exponential backoff.

### Firewall / blocked server IP behavior
WeGlide docs mention that API access from non-residential IPs can be blocked and API keys may be needed for server IPs.
For XCPro, direct mobile-device upload is likely the safest design because the request originates from the pilot’s device rather than your own server infrastructure.

---

## Why direct mobile upload is better than routing through your own XCPro server

For this feature, direct-from-device upload is the cleanest design:
- less privacy exposure,
- less infrastructure cost,
- fewer legal headaches,
- fewer secrets on your side,
- less risk of becoming an IGC data broker.

Use your own server only if one of these becomes necessary:
- central queueing,
- enterprise fleet management,
- extra post-processing,
- audit trail requirements,
- server-side fan-out to multiple services.

For normal XCPro pilot uploads, skip the server.

---

## UI recommendations

### Settings screen section
Add a WeGlide block:
- `Connect WeGlide`
- `Disconnect`
- linked pilot name
- linked account ID
- auto-upload toggle
- upload over Wi‑Fi only toggle
- retry pending uploads button
- privacy note: uploads become public on WeGlide

### Flight detail screen
Add:
- `Upload to WeGlide`
- upload status chip
- last error message
- open uploaded flight button if URL exists

### Post-flight flow
After XCPro saves a flight:
- show `Upload to WeGlide` action,
- or automatically queue if enabled.

---

## Privacy and consent

Before first upload, show a one-time notice:

- uploaded flights become public on WeGlide,
- location history may be publicly visible,
- competition/club rules may require valid logger/signature,
- XCPro is only transferring the file the user chose to publish.

Require explicit acceptance before enabling auto-upload.

---

## Integration test plan

Codex should build a test matrix covering:

### Unit tests
- PKCE generation
- token expiry logic
- duplicate upload prevention
- error mapping
- upload state reducer/viewmodel state transitions

### Instrumented tests
- OAuth deep-link handling
- encrypted token persistence
- WorkManager retry behavior

### Manual QA with real WeGlide sandbox/live test account
- connect account
- upload valid IGC
- upload same IGC twice
- upload malformed IGC
- expire token and retry
- airplane mode then reconnect
- app kill during upload

---

## Suggested implementation order for Codex

### Phase A — foundation
1. Add WeGlide feature module/packages.
2. Add DTOs, repository contract, and domain models.
3. Add secure token storage.
4. Add remote config placeholders for OAuth endpoints/client details.

### Phase B — account linking
5. Implement OAuth launch and callback.
6. Persist tokens.
7. Fetch/store linked account identity.
8. Build settings UI for connect/disconnect status.

### Phase C — upload
9. Implement `POST /v1/igcfile` client.
10. Add manual upload button on saved-flight screen.
11. Map responses into local upload state.
12. Store remote flight data.

### Phase D — background automation
13. Add `WeGlideUploadWorker`.
14. Add unique work/idempotency logic.
15. Add auto-upload toggle.
16. Add retry/backoff and network constraints.

### Phase E — polish
17. Add privacy disclosure.
18. Add richer error text.
19. Add metrics/logging.
20. Add integration tests and release checklist.

---

## Retrofit / OkHttp recommendations

Use:
- Retrofit
- OkHttp
- Kotlinx Serialization or Moshi

Add:
- auth interceptor for bearer token,
- authenticator or refresh flow handler,
- structured logging with secrets redacted.

Never log:
- access token,
- refresh token,
- raw authorization headers,
- raw IGC file contents.

---

## Observability

Add structured internal events such as:
- `weglide_link_started`
- `weglide_link_succeeded`
- `weglide_link_failed`
- `weglide_upload_started`
- `weglide_upload_succeeded`
- `weglide_upload_failed`
- `weglide_upload_retry_scheduled`

Store only non-sensitive metadata in analytics/logging.

---

## Known unknowns Codex must verify during implementation

These are the parts that still need live confirmation against current WeGlide API docs or a real test account:

1. Exact OAuth authorization endpoint URL.
2. Exact token endpoint URL.
3. Required scopes for flight upload.
4. Exact multipart field name for the IGC file.
5. Exact upload response schema.
6. Whether a dedicated `me` endpoint exists for current user details.
7. Whether refresh tokens are issued for mobile clients.
8. Whether any optional metadata fields can be included alongside the IGC.

Codex should keep these values configurable and avoid baking assumptions deeply into domain logic.

---

## Recommended config placeholders

```properties
WEGLIDE_BASE_URL=https://api.weglide.org/
WEGLIDE_AUTH_BASE_URL=<to-confirm>
WEGLIDE_CLIENT_ID=<from-weglide>
WEGLIDE_REDIRECT_URI=xcpro://auth/weglide/callback
WEGLIDE_SCOPES=<to-confirm>
```

If XCPro is public-facing, request an official OAuth client from WeGlide before release.

---

## Release gate checklist

Do not ship until all are true:

- OAuth account linking works end to end.
- Tokens stored encrypted.
- No password stored locally.
- Upload works for real IGC files.
- 422 errors shown clearly.
- Duplicate uploads prevented.
- Auto-upload can be disabled.
- User has seen privacy/public-upload notice.
- Secrets are not present in logs.
- Retry behavior verified across process death and no-network conditions.

---

## Bottom line

For XCPro, the correct implementation is:
- **OAuth account linking**,
- **direct device-to-WeGlide upload**,
- **secure token storage**,
- **WorkManager-based resilient upload queue**,
- **local duplicate prevention**, and
- **clear privacy disclosure** because WeGlide uploads are public.

Do not build this around stored WeGlide username/password.
Do not build this around your own upload relay server unless a later business need forces it.

