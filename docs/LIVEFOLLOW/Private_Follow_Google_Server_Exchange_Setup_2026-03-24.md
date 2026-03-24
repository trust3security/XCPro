# XCPro Private Follow Google Server Exchange Setup

This note documents the repo state for the approved non-Firebase Google auth path.

## What Is Implemented In Repo

- `Manage Account` uses Android Credential Manager to request a Google ID token.
- The app exchanges that Google ID token with XCPro-Server at `POST /api/v2/auth/google/exchange`.
- XCPro-Server verifies the Google ID token, resolves or creates the XCPro account primitives, and issues the XCPro bearer used by authenticated `/api/v2/*`.
- The app stores that XCPro bearer locally and uses it for `/api/v2/me*` and the private-follow relationship endpoints.
- The existing configured dev bearer token seam can still be used intentionally for local/dev support.

## What Is Still External Setup

You still need:

1. A Google OAuth web client ID for the XCPro backend audience
2. `XCPRO_GOOGLE_SERVER_CLIENT_ID` in the Android build
3. `XCPRO_GOOGLE_SERVER_CLIENT_ID` or `XCPRO_GOOGLE_SERVER_CLIENT_IDS` on XCPro-Server
4. `XCPRO_PRIVATE_FOLLOW_BEARER_SECRET` on XCPro-Server for issued XCPro bearer tokens
5. Network access from XCPro-Server to Google token verification endpoints at runtime

## Non-Goals

This auth slice does not add:

- email-link sign-in
- email/password
- Firebase Auth
- FCM push
- follower-only live gating

The current public share-code LiveFollow lane remains unchanged.
