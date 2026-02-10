# OGN File Manifest (what Codex should create/change)

This is a concrete file-level plan. Paths are examples; Codex must adapt to the actual module/package layout found in the repo.

---

## A. New Kotlin files (recommended)

### Domain models (no Android types)
- feature/map/src/main/java/<basepkg>/traffic/ogn/domain/model/GliderTrafficTarget.kt
- feature/map/src/main/java/<basepkg>/traffic/ogn/domain/model/GliderTrafficIdentity.kt
- feature/map/src/main/java/<basepkg>/traffic/ogn/domain/model/GliderTrafficSnapshot.kt
- feature/map/src/main/java/<basepkg>/traffic/ogn/domain/model/GliderTrafficConnectionState.kt

### Domain interfaces + use case
- feature/map/src/main/java/<basepkg>/traffic/ogn/domain/GliderTrafficRepository.kt
- feature/map/src/main/java/<basepkg>/traffic/ogn/domain/GliderTrafficUseCase.kt
- feature/map/src/main/java/<basepkg>/traffic/ogn/domain/GliderTrafficSubscriptionSpec.kt

### Data: APRS ingest + parsing
- feature/map/src/main/java/<basepkg>/traffic/ogn/data/aprs/AprsTnc2Frame.kt
- feature/map/src/main/java/<basepkg>/traffic/ogn/data/aprs/AprsParser.kt
- feature/map/src/main/java/<basepkg>/traffic/ogn/data/aprs/AprsPositionParser.kt
- feature/map/src/main/java/<basepkg>/traffic/ogn/data/aprs/Base91.kt   (if compressed positions supported)
- feature/map/src/main/java/<basepkg>/traffic/ogn/data/aprs/OgnCommentParser.kt

### Data: TCP client
- feature/map/src/main/java/<basepkg>/traffic/ogn/data/net/OgnAprsTcpClient.kt
- feature/map/src/main/java/<basepkg>/traffic/ogn/data/net/OgnAprsLoginLine.kt
- feature/map/src/main/java/<basepkg>/traffic/ogn/data/net/OgnReconnectBackoff.kt

### Data: DDB
- feature/map/src/main/java/<basepkg>/traffic/ogn/data/ddb/OgnDdbModels.kt
- feature/map/src/main/java/<basepkg>/traffic/ogn/data/ddb/OgnDdbRepository.kt
- feature/map/src/main/java/<basepkg>/traffic/ogn/data/ddb/OgnDdbDiskCache.kt

### Data: Repository implementation (SSOT)
- feature/map/src/main/java/<basepkg>/traffic/ogn/data/OgnGliderTrafficRepository.kt

### DI wiring
- feature/map/src/main/java/<basepkg>/traffic/ogn/di/OgnTrafficModule.kt

### UI overlay
(Exact files depend on existing map architecture.)
- feature/map/src/main/java/<basepkg>/traffic/ogn/ui/GliderTrafficOverlayController.kt
- feature/map/src/main/java/<basepkg>/traffic/ogn/ui/GliderTrafficGeoJsonBuilder.kt
- feature/map/src/main/java/<basepkg>/traffic/ogn/ui/GliderTrafficUiMapper.kt

---

## B. Existing files Codex must modify

These names are taken from PIPELINE.md / existing plans; Codex must locate the actual files in repo.

1. MapScreenViewModel
- Inject GliderTrafficUseCase.
- Add UI state for:
  - overlayEnabled
  - cameraViewport/bounds (for display filtering)
  - subscriptionCenter (for diagnostics)
  - traffic snapshot (targets + connection state)
- Provide intent handlers for toggle, map visibility, and camera-idle viewport updates.

2. Map screen UI (Compose)
- Add toggle in overlay menu ("Glider traffic").
- Provide DisposableEffect / lifecycle callback to inform ViewModel when screen is visible.
- Provide camera-idle callback to push center + viewport bounds + zoom to ViewModel.
- Render overlay controller only when enabled, and only with viewport-filtered targets.

3. Settings storage (DataStore)
- Add keys:
  - gliderTrafficEnabled: Boolean (default false)
- No radius key for MVP; receive radius is fixed to 300 km by domain constant.

4. Map overlay stack
- Register the new overlay source + layers.
- Ensure layers are removed when overlay disabled.
- Ensure overlay rendering is filtered by current viewport bounds (not by entire received set).

5. AndroidManifest (verify only)
- Ensure INTERNET permission exists (it likely does).
- No additional permissions required.

---

## C. New unit test files

- feature/map/src/test/java/<basepkg>/traffic/ogn/data/aprs/AprsParserTest.kt
- feature/map/src/test/java/<basepkg>/traffic/ogn/data/aprs/OgnCommentParserTest.kt
- feature/map/src/test/java/<basepkg>/traffic/ogn/data/ddb/OgnDdbRepositoryTest.kt
- feature/map/src/test/java/<basepkg>/traffic/ogn/data/OgnGliderTrafficRepositoryTest.kt
- feature/map/src/test/java/<basepkg>/traffic/ogn/domain/GliderTrafficViewportPolicyTest.kt

Test resources:
- feature/map/src/test/resources/ogn_aprs_samples.txt
- feature/map/src/test/resources/ogn_ddb_sample.json

---

## D. Notes for Codex while integrating

- Prefer to copy patterns from any existing traffic overlay (if OpenSky/ADS-B exists).
- Keep everything behind an explicit user toggle.
- Be strict about cancellation and lifecycle; do not keep sockets open when map is not visible.
- Ensure no non-ASCII in Kotlin sources.
