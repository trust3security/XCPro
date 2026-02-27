# LOCAL_SECRETS_SETUP.md

## Purpose

Define where local secrets are allowed and how they are consumed by this repo.

## Non-Negotiable Rules

- Never commit real credentials, tokens, or API keys to tracked files.
- Use only local, untracked configuration files for real values.
- Rotate any value immediately if it was ever committed.

## Allowed Local Secret Sources

Order of precedence (where implemented in Gradle):

1. User/system Gradle properties (`~/.gradle/gradle.properties` or `-PKEY=value`)
2. Project `local.properties` (gitignored)

`local.properties` is ignored by git in `.gitignore`.

## Keys Currently Consumed

- `MAPLIBRE_API_KEY`
- `OPENSKY_CLIENT_ID`
- `OPENSKY_CLIENT_SECRET`
- `SKYSIGHT_API_KEY`

## Example `local.properties`

```properties
MAPLIBRE_API_KEY=<your-local-value>
OPENSKY_CLIENT_ID=<your-local-value>
OPENSKY_CLIENT_SECRET=<your-local-value>
SKYSIGHT_API_KEY=<your-local-value>
```

## Tracked File Policy

- If a tracked file must document credential shape, use placeholders only.
- Recommended placeholder format:
  - `<set-in-local.properties-or-user-gradle.properties>`

## Release Hygiene Checklist

- Remove secret-bearing tracked artifacts before release.
- Run secret scan across tracked files.
- Confirm only placeholders remain in docs/templates.
- Confirm exposed credentials were rotated.

