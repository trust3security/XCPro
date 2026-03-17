# XCPro Module Map

Replace this file with the real repository structure as soon as possible.
This is an initial ownership guide only.

## Likely ownership areas

### app/
- app wiring
- navigation
- dependency injection entry points
- startup configuration

### core/model
- shared models
- units
- common domain types

### core/network
- API clients
- DTOs
- network configuration
- auth/session plumbing

### core/storage
- local persistence
- preferences
- database/file access

### feature/map
- map rendering
- overlays
- map interaction
- pilot/task/weather traffic presentation mapping

### feature/task
- task setup
- task state
- task navigation support
- start/finish/turnpoint interaction

### feature/aat
- AAT calculations
- scoring logic
- time-distance optimization rules
- AAT presentation mapping

### feature/traffic
- OGN / ADS-B ingestion
- filtering
- expiry
- prediction
- map presentation mapping

### feature/weather
- weather overlays
- forecasts
- data transformation
- refresh coordination

### feature/vario
- TE / netto / STF / audio logic
- smoothing
- sensor interpretation

### feature/profile
- aircraft/profile selection
- import/export
- backup/restore rules

## Ownership rule

Before changing code, determine:
1. which module owns the behavior,
2. whether logic belongs in UI, ViewModel, domain, or data,
3. whether a reusable service/use case already exists.

## Important

This file must be edited to match the real XCPro package/module structure before relying heavily on agents.
