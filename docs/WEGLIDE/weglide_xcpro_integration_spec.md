# WeGlide IGC Upload for XCPro Android

## Goal
Add **manual and automatic upload of finalized IGC files to WeGlide** inside the XCPro Android app.

This document is written so Codex can implement the feature directly.

---

## Product decision

### Implement **v1** with WeGlide's existing simple upload flow
Do **not** ask for WeGlide username/password in v1.

Instead, implement upload using these user-provided values stored in XCPro settings:
- `user_id` (numeric WeGlide user ID)
- `date_of_birth` (`YYYY-MM-DD`)
- `aircraft_id` (numeric WeGlide aircraft type ID)

Plus the selected `.igc` file.

### Why this is the best first step
1. There is a working production implementation in XCSoar that uploads to `POST https://api.weglide.org/v1/igcfile` with multipart fields:
   - `file`
   - `user_id`
   - `date_of_birth`
   - `aircraft_id`
2. WeGlide's developer docs say **OAuth 2** is the official path for account-linked integrations and that developers should contact WeGlide for an OAuth application token.
3. For a public Android app, collecting and storing a third-party password is a worse UX/security trade-off than using the existing simple upload flow.
4. Architect the code so OAuth 2 / PKCE can be added later if WeGlide approves the app.

### Important consequence
- **v1 uploads flights without storing a WeGlide password.**
- **v2** can add OAuth later if/when WeGlide grants an OAuth client.

---

## What research found

### Official / reliable facts
- WeGlide supports direct flight upload and already documents direct integrations such as LXNav Connect.
- WeGlide has a developer section with:
  - Public API
  - OAuth
  - API Terms of Use
- WeGlide explicitly says OAuth is for integrations/services/hardware acting on behalf of users for actions like **uploading flights** or **editing tasks**.
- The public API is **not finalized**, so keep the implementation isolated behind a small client layer.
- XCSoar currently uses the following upload request shape:
  - `POST /v1/igcfile`
  - multipart field `file`
  - multipart field `user_id`
  - multipart field `date_of_birth`
  - multipart field `aircraft_id`
  - expects HTTP `201`
- XCSoar recently added a guard that blocks uploads when `aircraft_id == 0`, because WeGlide otherwise returns a confusing server-side error like `400 Aircraft not found`.

### Critical product note about scoring validity
Upload success does **not** guarantee the flight will count for official rankings/contests.

WeGlide contest docs say that for contest scoring the file must satisfy logger/signature validity rules, including a valid `G` record, and some scoring paths require an approved or WeGlide-approved logger. XCSoar appears on WeGlide's approved logger list, but **XCPro is not currently listed in the published table**.

That means:
- XCPro can still implement upload now.
- But if XCPro-generated IGC files are expected to count like XCSoar/LK8000/etc., you should separately contact WeGlide about logger approval / recognition.

---

## Scope for Codex

### In scope
- WeGlide settings screen
- Manual upload action from flight history / file detail
- Automatic upload for finalized IGC files
- Work queue + retry
- Success/failure UI
- Local validation before network calls
- Minimal response parsing
- Persisted upload state to avoid duplicates

### Out of scope for v1
- Username/password login to WeGlide
- OAuth 2 login flow
- Full user lookup by name
- Full aircraft search UI if the live aircraft endpoint is hard to integrate quickly
- Editing uploaded flights after upload

---

## UX requirements

### 1) Settings screen: `WeGlide`
Add a dedicated WeGlide integration section under app settings.

Fields:
- `Enable WeGlide uploads` (switch)
- `Auto-upload finalized flights` (switch, only enabled when WeGlide uploads are enabled)
- `WeGlide User ID` (numeric text field)
- `Date of Birth` (date picker -> stored as ISO `YYYY-MM-DD`)
- `WeGlide Aircraft Type ID` (numeric text field)
- `Test upload…` (optional debug/dev action to select an IGC file and try upload)

Helper text:
- “XCPro does not need your WeGlide password for this integration.”
- “Your WeGlide Aircraft Type ID must be set before upload.”
- “Upload success does not guarantee official contest validation; that depends on WeGlide logger/signature rules.”

Validation rules on save:
- if WeGlide is enabled:
  - `user_id > 0`
  - `date_of_birth` is present and valid
  - `aircraft_id > 0`

### 2) Flight list / file detail actions
For each finalized IGC file, add:
- `Upload to WeGlide`

Behavior:
- If config invalid: show a blocking dialog and do not make a network call.
- If upload succeeds: show a success dialog/snackbar with returned flight ID.
- If upload fails: show concise error message + keep item retryable.

### 3) Automatic upload
When the app knows an IGC file is **finalized/closed**, queue it for upload if:
- WeGlide enabled = true
- Auto-upload enabled = true
- Upload config is valid
- File has not already been uploaded successfully

If XCPro does not have an explicit “flight finalized” event, implement a conservative fallback:
- schedule a delayed upload job after flight end / file close
- cancel/reschedule while file size keeps changing
- only upload once the file is stable

---

## Network/API contract to implement

### Base URL
`https://api.weglide.org/v1`

### Upload endpoint
`POST /igcfile`

### Headers
Required / recommended:
- `User-Agent: XCPro-Android/<version>`
- `Accept: application/json`

### Request type
`multipart/form-data`

### Multipart fields
Use these exact field names:
- `file` -> actual IGC file bytes
- `user_id` -> numeric WeGlide user ID as string
- `date_of_birth` -> ISO date string `YYYY-MM-DD`
- `aircraft_id` -> numeric aircraft type ID as string

### Expected success
- HTTP `201 Created`
- Response body is JSON; XCSoar parses the first returned flight object from an array

Parse at least these fields if present:
- `id` -> remote flight ID
- `scoring_date`
- `registration`
- `competition_id`
- `user.id`
- `user.name`
- `aircraft.id`
- `aircraft.name`
- `aircraft.kind`
- `aircraft.sc_class`

### Known failure cases
- `400` with body like `Aircraft not found` if `aircraft_id` is missing/invalid
- `422` validation errors
- Any non-`201` should be treated as failure and surfaced to the user

### Do not do this in v1
- Do not call a WeGlide username/password login flow from the app
- Do not require a backend server for upload

---

## Android implementation plan

## Suggested package structure
Adapt names to the existing XCPro codebase, but keep the layering clear.

```text
app/
  data/
    weglide/
      WeGlideConfig.kt
      WeGlideApi.kt
      WeGlideModels.kt
      WeGlideRepository.kt
      WeGlideUploadDao.kt
      WeGlideUploadEntity.kt
  workers/
      WeGlideUploadWorker.kt
  ui/
    settings/
      WeGlideSettingsScreen.kt
    flights/
      FlightDetailViewModel.kt
      FlightListViewModel.kt
```

### Config model
Create a persisted config model:

```kotlin
data class WeGlideConfig(
    val enabled: Boolean = false,
    val autoUpload: Boolean = false,
    val userId: Long? = null,
    val dateOfBirthIso: String? = null, // YYYY-MM-DD
    val aircraftId: Long? = null,
)
```

Store this in encrypted app storage if available.

### Upload state table
Create a Room table for dedupe + retries.

```kotlin
@Entity(tableName = "weglide_uploads")
data class WeGlideUploadEntity(
    @PrimaryKey val localFileHash: String,
    val localPath: String,
    val fileSize: Long,
    val lastModified: Long,
    val state: String, // QUEUED, UPLOADING, SUCCESS, FAILED
    val attemptCount: Int,
    val remoteFlightId: Long?,
    val lastError: String?,
    val createdAt: Long,
    val updatedAt: Long,
)
```

### Dedupe strategy
Compute SHA-256 of the finalized file and use that as the upload key.

Rules:
- If hash already has `SUCCESS`, do not re-upload automatically.
- If hash already has `QUEUED` or `UPLOADING`, do not enqueue duplicate work.
- Manual “Retry upload” can reuse the same row and reset state to `QUEUED`.

### Background execution
Use `WorkManager`.

Worker constraints:
- network connected
- battery not low (optional)

Use unique work name like:
- `weglide-upload-<sha256>`

Retry behavior:
- retry network failures / timeouts / 5xx
- do **not** auto-retry 4xx config/validation errors

---

## Upload client implementation

Use OkHttp (or the app's existing HTTP stack) with multipart form upload.

### Reference implementation shape

```kotlin
class WeGlideApi(
    private val okHttpClient: OkHttpClient,
    private val appVersion: String,
) {
    fun uploadIgc(
        config: WeGlideConfig,
        igcFile: File,
    ): WeGlideUploadResponse {
        require(config.userId != null && config.userId > 0)
        require(!config.dateOfBirthIso.isNullOrBlank())
        require(config.aircraftId != null && config.aircraftId > 0)
        require(igcFile.exists())

        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                igcFile.name,
                igcFile.asRequestBody("application/octet-stream".toMediaType())
            )
            .addFormDataPart("user_id", config.userId.toString())
            .addFormDataPart("date_of_birth", config.dateOfBirthIso)
            .addFormDataPart("aircraft_id", config.aircraftId.toString())
            .build()

        val request = Request.Builder()
            .url("https://api.weglide.org/v1/igcfile")
            .header("User-Agent", "XCPro-Android/$appVersion")
            .header("Accept", "application/json")
            .post(body)
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (response.code != 201) {
                throw WeGlideUploadException(
                    code = response.code,
                    message = raw.ifBlank { "Unexpected WeGlide response" }
                )
            }
            return parseUploadResponse(raw)
        }
    }
}
```

### Parsing
Create a small parser that tolerates missing fields.

```kotlin
data class WeGlideUploadResponse(
    val remoteFlightId: Long?,
    val scoringDate: String?,
    val registration: String?,
    val competitionId: String?,
    val userName: String?,
    val aircraftName: String?,
)
```

Assume response root may be an array and use the first object.

---

## Local validation rules

Before starting upload, enforce all of these locally:
- file exists
- file extension is `.igc` (case-insensitive)
- file size > 0
- `user_id > 0`
- `date_of_birth` present and valid ISO date
- `aircraft_id > 0`

If `aircraft_id` is missing or zero, show:
> “Please set WeGlide Aircraft Type before uploading.”

Do this **before** any network request.

---

## Hooks Codex should add

### Manual upload hook
From flight detail / history item:
- button: `Upload to WeGlide`
- calls repository -> validate -> enqueue worker or perform immediate upload with progress UI

### Automatic upload hook
When an IGC file is finalized:
- compute hash
- store/refresh upload record
- if auto-upload enabled and config valid -> enqueue worker

### Suggested repository methods

```kotlin
interface WeGlideRepository {
    suspend fun enqueueUpload(file: File): Result<Unit>
    suspend fun uploadNow(file: File): Result<WeGlideUploadResponse>
    suspend fun markQueued(file: File)
    suspend fun markSuccess(hash: String, remoteFlightId: Long?)
    suspend fun markFailure(hash: String, message: String)
}
```

---

## Error handling UX

Map errors to user-facing text:

- missing config -> “Complete your WeGlide settings before uploading.”
- missing aircraft id -> “Set your WeGlide Aircraft Type before uploading.”
- `400` -> show server message if present
- `422` -> “WeGlide rejected the upload. Check your account details and IGC file.”
- offline / timeout -> “Upload queued. XCPro will retry when network is available.”
- unknown -> “Upload failed.”

Do not expose raw stack traces to normal users.

---

## Security / privacy requirements

- Do not ask for or store WeGlide password in v1.
- Store WeGlide config in encrypted local storage if available.
- Do not log full personal info in production logs.
- Do not log date of birth unless running explicit debug builds.
- Do not send uploads through your own backend in v1.

Why:
- WeGlide docs note their API/firewall behavior can differ for server IPs.
- A pure device-to-WeGlide upload is simpler and lower risk for the first version.

---

## Nice-to-have but not required for v1

### Aircraft picker
If easy to implement, add a simple searchable aircraft picker instead of a raw numeric `aircraft_id` field.

Potential source endpoint:
- `GET https://api.weglide.org/v1/aircraft`

But **do not block the v1 feature** on this. A numeric field is acceptable for the first release.

### Better success screen
If response parsing works, show:
- Flight ID
- Scoring date
- Aircraft name
- Registration

Otherwise a basic success toast/snackbar is fine.

---

## Future phase: OAuth 2 / PKCE (not v1)

After v1 ships, optionally add a better auth path **only after contacting WeGlide**.

Future v2 goals:
- Request official OAuth client/token from WeGlide
- Add Android OAuth 2 authorization flow with PKCE
- Stop asking for `user_id` manually
- Potentially fetch the authenticated user profile automatically
- Potentially fetch aircraft list automatically and present a picker

Do **not** start with this if the goal is to get upload working quickly.

---

## Acceptance criteria

A change is complete when all of the following are true:

1. User can enable WeGlide integration in settings.
2. User can save numeric `user_id`, ISO `date_of_birth`, and numeric `aircraft_id`.
3. Manual upload of a valid finalized IGC file works.
4. Automatic upload of finalized IGC files works when enabled.
5. Uploads use `POST https://api.weglide.org/v1/igcfile` with multipart fields:
   - `file`
   - `user_id`
   - `date_of_birth`
   - `aircraft_id`
6. Successful uploads treat HTTP `201` as success.
7. Missing `aircraft_id` is blocked locally before making a request.
8. Duplicate auto-uploads are prevented.
9. Offline uploads retry later via WorkManager.
10. Normal production logs do not contain date of birth or raw server dumps.

---

## Test plan

### Happy path
- Configure valid WeGlide settings
- Select a valid finalized `.igc` file
- Upload manually
- Verify HTTP `201`
- Verify success UI shows returned remote flight ID if parsed

### Auto-upload
- Enable auto-upload
- Finish a flight so the app finalizes an IGC file
- Verify a background job is queued
- Verify upload completes once network is available

### Missing aircraft id
- Set `aircraft_id = 0` or blank
- Tap upload
- Verify a local message is shown
- Verify **no network call** is made

### Bad user info
- Use invalid `user_id` or `date_of_birth`
- Verify non-201 is handled cleanly
- Verify failure remains retryable after config correction

### Offline retry
- Turn on airplane mode
- Trigger upload
- Verify the upload is queued / marked failed-retryable
- Restore network
- Verify worker retries and succeeds

### Dedupe
- Upload same finalized file twice via auto-upload trigger
- Verify only one remote upload occurs

### Manual retry
- Force a temporary network failure
- Retry from UI after network returns
- Verify status transitions to success

---

## Research/source appendix

Official / docs:
- WeGlide Developers: https://docs.weglide.org/creators/developers.html
- WeGlide API Terms of Use: https://docs.weglide.org/legal/api_terms_of_use.html
- Flight upload docs: https://docs.weglide.org/how_to/flight_upload.html
- Task integrations (shows direct device/service integrations are normal): https://docs.weglide.org/tasks/integrations.html
- Flight verification: https://docs.weglide.org/contests/verification.html
- Free contest rules: https://docs.weglide.org/contests/international/free.html
- Approved loggers: https://docs.weglide.org/contests/loggers.html

Reference implementation from XCSoar:
- Upload request code: https://raw.githubusercontent.com/XCSoar/XCSoar/master/src/net/client/WeGlide/UploadFlight.cpp
- Upload settings model: https://raw.githubusercontent.com/XCSoar/XCSoar/master/src/net/client/WeGlide/Settings.hpp
- Upload result parsing / success dialog: https://raw.githubusercontent.com/XCSoar/XCSoar/master/src/net/client/WeGlide/UploadIGCFile.cpp
- Recent guard against missing aircraft id: https://github.com/XCSoar/XCSoar/pull/2280

---

## Final instruction to Codex
Implement **v1 now** using the simple multipart upload flow and background queueing.

Do **not** build username/password login for WeGlide in this first version.

Keep the code modular so that a later WeGlide-approved OAuth 2 / PKCE integration can replace the manual `user_id` + `date_of_birth` setup without rewriting the upload pipeline.
