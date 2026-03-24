# XCPro Private Follow Google Server Exchange Setup

Date: 2026-03-24  
Status: Current setup note for the approved non-Firebase Google auth path used by XCPro private follow.

## Purpose

This note documents the current repo state and the required external setup for:

- Android Credential Manager Google sign-in
- `POST /api/v2/auth/google/exchange`
- XCPro-Server bearer issuance for authenticated `/api/v2/*`
- staging/prod safety rules for auth configuration

## What Is Implemented In Repo

- `Manage Account` uses Android Credential Manager to request a Google ID token.
- The app exchanges that Google ID token with XCPro-Server at `POST /api/v2/auth/google/exchange`.
- XCPro-Server verifies the Google ID token, resolves or creates the XCPro account primitives, and issues the XCPro bearer used by authenticated `/api/v2/*`.
- The app stores that XCPro bearer locally and uses it for `/api/v2/me*`, private-follow relationship endpoints, and authenticated private-follow live endpoints.
- Release Android builds hard-disable the configured dev bearer sign-in seam.
- Server-side static/dev bearer auth is fail-closed unless both of these are true:
  - `XCPRO_RUNTIME_ENV=dev`
  - `XCPRO_ALLOW_DEV_STATIC_BEARER_AUTH=1`
- Repo now includes an environment preflight command at:
  - `XCPro_Server/scripts/private_follow_env_preflight.py`

## Required External Setup

You still need all of the following in the real target environment:

1. A Google OAuth web client ID for the XCPro backend audience.
2. Matching Android build configuration for that backend audience.
3. Matching XCPro-Server environment configuration for Google audience verification.
4. A real bearer-signing secret for XCPro-issued bearer tokens.
5. Network access from XCPro-Server to Google token verification endpoints at runtime.

## Required Android Build Input

The Android build must provide:

- `XCPRO_GOOGLE_SERVER_CLIENT_ID`

Important rule:
- the Android build must use the same Google server client ID that the staging/prod server expects for audience verification

## Required Server Environment

Set these on staging/prod as appropriate:

- `XCPRO_RUNTIME_ENV`
- `XCPRO_GOOGLE_SERVER_CLIENT_ID` or `XCPRO_GOOGLE_SERVER_CLIENT_IDS`
- `XCPRO_PRIVATE_FOLLOW_BEARER_SECRET`

Optional:

- `XCPRO_PRIVATE_FOLLOW_BEARER_TTL_SECONDS`

## Staging / Production Safety Rules

These must remain true outside local dev:

- `XCPRO_ALLOW_DEV_STATIC_BEARER_AUTH` must be unset or `0`
- staging/prod must not rely on the configured dev bearer path
- release builds must not expose `Use configured dev account`
- real-device Google sign-in and server exchange must be proven before rollout approval

## Required Preflight

Before staging rollout work proceeds, run:

```bash
python scripts/private_follow_env_preflight.py
```

Pass this in the real staging environment before continuing.

The purpose of this check is to catch:

- missing Google audience env
- missing bearer secret
- unexpected dev-auth configuration
- staging/prod auth misconfiguration before smoke testing starts

## What Is Still Manual / External

The repo cannot complete these by itself:

- create or confirm the real Google OAuth web client configuration
- set real staging/prod env vars and secrets
- verify real-device Google sign-in against the real backend audience
- verify `POST /api/v2/auth/google/exchange` in the real target environment

## Non-Goals

This auth path does not add:

- email-link sign-in
- email/password
- Firebase Auth
- FCM push

The current public share-code LiveFollow lane remains separate and unchanged.
