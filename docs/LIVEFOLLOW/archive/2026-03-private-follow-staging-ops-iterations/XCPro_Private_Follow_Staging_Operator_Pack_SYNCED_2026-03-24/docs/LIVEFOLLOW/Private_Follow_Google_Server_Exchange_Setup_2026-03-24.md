# XCPro Private Follow Google Server Exchange Setup

This note documents the repo state for the approved non-Firebase Google auth path.

## What Is Implemented In Repo

- `Manage Account` uses Android Credential Manager to request a Google ID token.
- The app exchanges that Google ID token with XCPro-Server at `POST /api/v2/auth/google/exchange`.
- XCPro-Server verifies the Google ID token, resolves or creates the XCPro account primitives, and issues the XCPro bearer used by authenticated `/api/v2/*`.
- The app stores that XCPro bearer locally and uses it for `/api/v2/me*` and the private-follow relationship endpoints.
- The configured dev bearer token seam is now debug/dev only and is fail-closed in release/prod paths by default.

## What Is Still External Setup

You still need:

1. A Google OAuth web client ID for the XCPro backend audience
2. `XCPRO_GOOGLE_SERVER_CLIENT_ID` in the Android build
3. `XCPRO_RUNTIME_ENV=staging` or `XCPRO_RUNTIME_ENV=prod` in deployed XCPro-Server environments
4. `XCPRO_GOOGLE_SERVER_CLIENT_ID` or `XCPRO_GOOGLE_SERVER_CLIENT_IDS` on XCPro-Server
5. `XCPRO_PRIVATE_FOLLOW_BEARER_SECRET` on XCPro-Server for issued XCPro bearer tokens
6. Optional `XCPRO_PRIVATE_FOLLOW_BEARER_TTL_SECONDS` if you need a non-default bearer lifetime
7. Network access from XCPro-Server to Google token verification endpoints at runtime

## Dev Auth Hardening Rule

Android:

- release builds do not compile in a usable configured dev bearer token
- release builds do not surface `Use configured dev account`
- debug builds can still use `XCPRO_PRIVATE_FOLLOW_DEV_BEARER_TOKEN` intentionally for local support

XCPro-Server:

- static bearer auth is disabled by default
- static bearer auth is only active when both of these are true:
  - `XCPRO_RUNTIME_ENV=dev`
  - `XCPRO_ALLOW_DEV_STATIC_BEARER_AUTH=1`
- `XCPRO_STATIC_BEARER_TOKENS_JSON` must not be present in staging/prod

## Preflight Command

Before staging/prod rollout validation, run:

`python scripts/private_follow_env_preflight.py`

from the `XCPro_Server` repo root.

Expected behavior:

- exit `0` only when the runtime has the required private-follow auth config
- exit non-zero when Google audience config or bearer-secret config is missing,
  or when dev bearer auth is misconfigured outside dev

This does not verify real Google tokens by itself. Real-device sign-in and
server exchange still require staging/prod environment proof.

## Non-Goals

This auth slice does not add:

- email-link sign-in
- email/password
- Firebase Auth
- FCM push
- follower-only live gating

The current public share-code LiveFollow lane remains unchanged.
