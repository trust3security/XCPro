# XCPro Private Follow — Staging Operator Inputs Worksheet
## Fill in the external inputs Codex cannot infer from repo contents

**Status:** Fill-in worksheet for staging execution  
**Purpose:** Capture the external values, access details, and test actors needed to run the staging execution pass.  
**Non-goal:** Do not commit real secrets to the repo. Store secrets in the real secret manager / staging environment only.

## Release Candidates

Use only these candidates unless a staging blocker forces a new RC.

- **Server commit:** `b696f039540480468195087fa3f44338338f6fba`
- **Server tag:** `private-follow-rollout-rc-server-2026-03-24`
- **App commit:** `c25d0f6520686643c2670502048fee3a83b91ca9`
- **App tag:** `private-follow-rollout-rc-app-2026-03-24`
- **Clean app RC worktree:** `C:/Users/Asus/AndroidStudioProjects/XCPro_private_follow_rollout_rc_app`

## 1. Staging Server Access

Fill these before any staging pass.

- **Staging hostname / URL:** `______________________________`
- **SSH / remote access method:** `______________________________`
- **Deploy user:** `______________________________`
- **Application root on server:** `______________________________`
- **Virtualenv path:** `______________________________`
- **Service/process manager:** `systemd / docker / compose / other: ______________________________`
- **Service name(s):** `______________________________`
- **Log command:** `______________________________`
- **Health-check endpoint / command:** `______________________________`
- **Rollback command/path:** `______________________________`

## 2. Staging Environment Variables

Record where each value is managed. Do **not** put real secrets into the repo.

- **`XCPRO_RUNTIME_ENV`** = `staging`
- **`XCPRO_GOOGLE_SERVER_CLIENT_ID`** = managed at `______________________________`
- **or `XCPRO_GOOGLE_SERVER_CLIENT_IDS`** = managed at `______________________________`
- **`XCPRO_PRIVATE_FOLLOW_BEARER_SECRET`** = managed at `______________________________`
- **`XCPRO_PRIVATE_FOLLOW_BEARER_TTL_SECONDS`** = `______________________________` (optional)
- **`XCPRO_ALLOW_DEV_STATIC_BEARER_AUTH`** = `unset / 0`

### Preflight ownership

- **Who sets / verifies staging env:** `______________________________`
- **Expected command location for preflight:** `______________________________`
- **Expected preflight command:** `python scripts/private_follow_env_preflight.py`

## 3. Google OAuth / Audience Alignment

These must match between app and staging server.

- **Google web/server client ID used for backend audience:** `______________________________`
- **Android package / application ID:** `______________________________`
- **Credential Manager Google sign-in config owner:** `______________________________`
- **Who can verify Google Cloud console config:** `______________________________`
- **Any staging-specific OAuth note:** `______________________________`

## 4. Deploy Commands

Fill the exact commands you will actually use.

### Server deploy
- **Fetch/update command:** `______________________________`
- **Checkout pinned RC command:** `______________________________`
- **Install/update dependencies command:** `______________________________`
- **Run migrations command:** `______________________________`
- **Restart service command:** `______________________________`
- **Tail logs command:** `______________________________`

### App install
- **Build artifact location:** `______________________________`
- **Install command / path:** `______________________________`
- **ADB device selector if needed:** `______________________________`

## 5. Real Device For Auth Proof

You need one real Android device for the first staging proof.

- **Device model:** `______________________________`
- **Android version:** `______________________________`
- **ADB serial (if available):** `______________________________`
- **Operator running the device test:** `______________________________`
- **Install confirmed?** `yes / no`

## 6. Test Accounts

Prepare at least these actors.

### Pilot account
- **Identifier / email:** `______________________________`
- **Handle:** `______________________________`
- **Display name:** `______________________________`
- **Default follow policy:** `approval_required / auto_approve / closed`

### Approved follower account
- **Identifier / email:** `______________________________`
- **Handle:** `______________________________`
- **Display name:** `______________________________`
- **Relationship to pilot ready before smoke?** `yes / no`

### Signed-in non-follower account
- **Identifier / email:** `______________________________`
- **Handle:** `______________________________`
- **Display name:** `______________________________`

### Signed-out/public viewer path
- **Test device / browser / app path:** `______________________________`

## 7. Immediate Operator Checklist

Mark these off before running the smoke guide.

- [ ] Server RC is pinned and the deploy command path is known.
- [ ] App RC is pinned and installable on a real device.
- [ ] Staging env values are present.
- [ ] Dev static bearer auth is disabled in staging.
- [ ] Google server client ID / audience alignment is confirmed.
- [ ] Test accounts are ready.
- [ ] One real Android device is available.
- [ ] Someone is assigned to run the smoke pass and record results.

## 8. Go / No-Go Precondition

Do not start the full smoke pass until:

- staging server is deployed
- migrations are applied
- preflight passes in staging
- real-device Google sign-in works
- `/api/v2/auth/google/exchange` succeeds against staging
