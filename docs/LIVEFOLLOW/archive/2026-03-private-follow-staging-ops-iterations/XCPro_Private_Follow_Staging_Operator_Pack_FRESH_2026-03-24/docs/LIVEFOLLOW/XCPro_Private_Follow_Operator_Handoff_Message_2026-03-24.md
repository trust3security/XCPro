# XCPro Private Follow — Operator Handoff Message
## Short message to send to whoever controls staging access and the test device

Use this as the human/operator handoff when the repo is ready but staging proof still needs real access.

---

We are ready for the XCPro private-follow staging pass.

Please use only these release candidates:

- **Server:** `b696f039540480468195087fa3f44338338f6fba`
- **App:** `c25d0f6520686643c2670502048fee3a83b91ca9`

Current operator docs:

- `docs/LIVEFOLLOW/Private_Follow_Current_Repo_State_2026-03-24.md`
- `docs/LIVEFOLLOW/Private_Follow_Google_Server_Exchange_Setup_2026-03-24.md`
- `docs/LIVEFOLLOW/XCPro_Private_Follow_Rollout_Release_Checklist_2026-03-24.md`
- `docs/LIVEFOLLOW/XCPro_Private_Follow_Staging_Smoke_Guide_2026-03-24.md`
- `docs/LIVEFOLLOW/XCPro_Private_Follow_Staging_Operator_Inputs_Worksheet_2026-03-24.md`
- `docs/LIVEFOLLOW/XCPro_Private_Follow_Staging_Smoke_Results_Template_2026-03-24.md`

What is needed from ops / staging owner:

1. Set staging env:
   - `XCPRO_RUNTIME_ENV=staging`
   - `XCPRO_GOOGLE_SERVER_CLIENT_ID` or `XCPRO_GOOGLE_SERVER_CLIENT_IDS`
   - `XCPRO_PRIVATE_FOLLOW_BEARER_SECRET`
   - ensure `XCPRO_ALLOW_DEV_STATIC_BEARER_AUTH` is unset or `0`
2. Deploy the pinned server RC to staging and apply migrations.
3. Run `python scripts/private_follow_env_preflight.py` in staging and confirm PASS.
4. Install the pinned app RC on a real Android device.
5. Prove Google sign-in and `POST /api/v2/auth/google/exchange` against staging.
6. Run the staging smoke guide end to end.
7. Fill the smoke results template and report `GO`, `PAUSE`, or `FIX SPECIFIC BLOCKER`.

Do not change code during this pass unless staging exposes a real blocker bug.
