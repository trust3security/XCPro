# XCPro_Server_Setup.md

## Purpose

This document is the implementation and deployment plan for the first real XCPro backend.

It is written to fit the current XCPro client architecture and the server you already created on Hetzner. The goal is to get from "phone-only app state" to a real backend that can:

- start and end a live session
- accept live ownship position updates from XCPro
- accept full task snapshots and task revisions
- serve a public follower view by share code
- keep hot live state in memory and durable history in PostgreSQL
- stay consistent with XCPro's SSOT, MVVM/UDF, time-base, and SI-unit rules

This is Phase S1/S2 server scope. It is not the final competition server.

---

## Scope

### In scope

- single Hetzner ARM VM deployment
- Docker-based deployment
- HTTPS public API
- FastAPI backend
- PostgreSQL durable storage
- Redis hot live cache
- ownship live session ingest from XCPro
- task revision persistence
- public follower polling endpoints
- session cleanup, dedupe, retry-safe ingest
- basic health checks, logging, and deploy commands

### Out of scope for this phase

- WebSocket/SSE first-class realtime
- OGN proxy/relay through this backend
- ADS-B proxy/relay through this backend
- scoring engine
- club/meet director dashboard
- full account system with email/password
- multi-region or HA deployment
- Kubernetes

---

## Client architecture facts this server must respect

The current XCPro app is not a random Android app. The server design has to fit the way the client already works.

### Flight pipeline facts

Current live flight flow is:

```text
VarioForegroundService
  -> VarioServiceManager
  -> FlightDataCalculatorEngine
  -> FlightDataRepository (SSOT)
  -> FlightDataUseCase
  -> MapScreenViewModel
  -> UI / cards / overlays
```

The client already uses injected clocks, SSOT ownership, and UI separation. The network boundary must not bypass that.

### Task pipeline facts

Current task flow is:

```text
Task UI
  -> TaskSheetViewModel intents
  -> Task use-cases / coordinator use-cases
  -> task repository / coordinator authoritative state
  -> TaskUiState
  -> Task UI render
```

Task definition and active leg are authoritative in task repository/coordinator owners. Map rendering is runtime-only, not the source of truth.

### Time base facts

XCPro rules are strict:

- live internal delta/validity logic uses monotonic time
- replay uses IGC time
- wall time is for storage/output/UX
- SI units are the internal contract

### What that means for the server

1. The backend must ingest exported domain snapshots, not raw UI state.
2. The backend must never depend on map/runtime details.
3. The backend must keep all numeric contracts SI:
   - distance/altitude in meters
   - speed/vario in m/s
   - angles in degrees
4. The backend must never compare raw device monotonic timestamps to wall time.

---

## Phase S1 design decisions

These are deliberate. Do not overbuild this.

### 1. Ownship first

Phase 1 backend is for the XCPro pilot's own live session. It is not yet the OGN/ADS-B aggregation layer.

### 2. Polling first

Followers poll every 2-5 seconds. Do not start with WebSockets. Add them later only if needed.

### 3. Full task snapshot revisions

When task changes, XCPro sends the full task snapshot again. The server stores a new revision. Do not attempt tiny patch diffs in Phase 1.

### 4. Single VM, single compose stack

One Hetzner VM is enough for initial deployment. Keep the system simple and inspectable.

### 5. Server stack

Use:

- Ubuntu 24.04 LTS on the Hetzner VM
- Docker Engine + Docker Compose plugin
- Caddy in Docker for TLS + reverse proxy
- FastAPI for the API
- PostgreSQL 16 for durable storage
- Redis 7 for hot state
- Alembic for migrations

### 6. No PostGIS in the first deploy

Because your Hetzner server is ARM and you want the least painful path, start with plain PostgreSQL 16.

Use app-side geo math plus standard indexes first.

Add PostGIS later when one of these becomes true:

- you need server-side zone intersection/scoring
- you need efficient geo queries on big history
- you migrate to a known-good image/build for your target CPU

---

## Recommended high-level architecture

```text
XCPro Android app
  -> HTTPS
  -> Caddy reverse proxy
  -> FastAPI API container
     -> Redis (latest session state, recent trail, share code lookup)
     -> PostgreSQL (sessions, positions, tasks, revisions, events)

Follower client / web follower
  -> HTTPS polling
  -> Caddy
  -> FastAPI read endpoints
     -> Redis for hot reads
     -> PostgreSQL fallback/history
```

Optional later:

```text
OGN worker
  -> external OGN feed
  -> normalized OGN cache/tables
  -> future merged follower view
```

---

## Repo and deploy layout

Use a separate deploy/backend repo or a dedicated `server/` root. Do not bury this inside Android UI modules.

Recommended layout:

```text
xcpro-server/
  .env
  compose.yml
  README.md
  deploy/
    Caddyfile
    bootstrap-host.sh
    backup.sh
  server/
    Dockerfile
    requirements.txt
    alembic.ini
    alembic/
      env.py
      versions/
    app/
      main.py
      config.py
      deps.py
      errors.py
      api/
        router.py
        health.py
        auth.py
        ingest.py
        public.py
      domain/
        model/
        ports/
        usecase/
      data/
        db/
        repository/
        redis/
        adapters/
      workers/
        cleanup.py
        tasks.py
      tests/
        unit/
        integration/
```

### Server-side architecture inside the backend

Mirror XCPro discipline, even though this is Python:

```text
api -> domain/usecase -> data/repository/adapters
```

Rules:

- route handlers validate and dispatch only
- use cases own business logic
- repositories own persistence/cache access
- raw SQL/Redis/HTTP code stays in adapters/data
- no god-object service
- one authoritative owner for each data item

---

## Host bootstrap on Hetzner

Assume a fresh Ubuntu 24.04 LTS ARM64 host.

### 1. Initial login

From your machine:

```bash
ssh root@YOUR_SERVER_IP
```

### 2. Basic packages

```bash
apt-get update
apt-get upgrade -y
apt-get install -y ca-certificates curl gnupg ufw fail2ban git
```

### 3. Install Docker Engine and Compose plugin

This follows Docker's official Ubuntu apt-repository flow.

```bash
install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /tmp/docker.asc
gpg --dearmor -o /etc/apt/keyrings/docker.gpg /tmp/docker.asc
chmod a+r /etc/apt/keyrings/docker.gpg

echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
  https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
  > /etc/apt/sources.list.d/docker.list

apt-get update
apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
systemctl enable docker
systemctl start docker
```

### 4. Firewall

Allow SSH, HTTP, HTTPS.

```bash
ufw allow 22/tcp
ufw allow 80/tcp
ufw allow 443/tcp
ufw --force enable
```

### 5. Optional deploy user

Recommended once basic access works:

```bash
adduser deploy
usermod -aG docker deploy
mkdir -p /home/deploy/.ssh
cp /root/.ssh/authorized_keys /home/deploy/.ssh/authorized_keys
chown -R deploy:deploy /home/deploy/.ssh
chmod 700 /home/deploy/.ssh
chmod 600 /home/deploy/.ssh/authorized_keys
```

### 6. Optional hardening after you verify deploy access

- disable root password login
- disable SSH password auth
- keep key auth only
- enable unattended upgrades if you want host auto-patching

---

## Domain and DNS

Use a real domain before you wire the app.

Example:

- `api.xcpro.app` -> backend API
- `live.xcpro.app` -> optional future public follower UI
- `admin.xcpro.app` -> optional future admin

At minimum point:

- `api.xcpro.app` -> your Hetzner server public IP

---

## Environment variables

Create `xcpro-server/.env`:

```dotenv
APP_NAME=xcpro-server
APP_ENV=production
APP_PORT=8080
APP_HOST=0.0.0.0

PUBLIC_BASE_URL=https://api.xcpro.app
CORS_ALLOWED_ORIGINS=https://api.xcpro.app

DATABASE_URL=postgresql+psycopg://xcpro:CHANGE_ME_DB_PASSWORD@db:5432/xcpro
REDIS_URL=redis://redis:6379/0

DB_NAME=xcpro
DB_USER=xcpro
DB_PASSWORD=CHANGE_ME_DB_PASSWORD

JWT_SECRET=CHANGE_ME_LONG_RANDOM_SECRET
JWT_ALGORITHM=HS256
INGEST_TOKEN_TTL_HOURS=24
PUBLIC_SHARE_TTL_HOURS=48

SESSION_STALE_AFTER_SECONDS=45
SESSION_AUTO_END_AFTER_SECONDS=900

MAX_POSITION_BATCH_SIZE=100
MAX_RECENT_TRAIL_POINTS=1500

LOG_LEVEL=INFO
```

Generate strong secrets. Do not commit this file.

---

## Docker Compose

Create `xcpro-server/compose.yml`:

```yaml
services:
  caddy:
    image: caddy:2
    restart: unless-stopped
    depends_on:
      - api
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./deploy/Caddyfile:/etc/caddy/Caddyfile:ro
      - caddy_data:/data
      - caddy_config:/config

  api:
    build:
      context: ./server
      dockerfile: Dockerfile
    restart: unless-stopped
    env_file:
      - .env
    depends_on:
      db:
        condition: service_healthy
      redis:
        condition: service_healthy
    expose:
      - "8080"
    command: >
      sh -c "
      alembic upgrade head &&
      uvicorn app.main:app --host 0.0.0.0 --port 8080
      "
    healthcheck:
      test: ["CMD-SHELL", "wget -qO- http://127.0.0.1:8080/health/live || exit 1"]
      interval: 15s
      timeout: 5s
      retries: 5
      start_period: 15s
    volumes:
      - ./server:/app

  db:
    image: postgres:16
    restart: unless-stopped
    environment:
      POSTGRES_DB: ${DB_NAME}
      POSTGRES_USER: ${DB_USER}
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    volumes:
      - db_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${DB_USER} -d ${DB_NAME}"]
      interval: 10s
      timeout: 5s
      retries: 10

  redis:
    image: redis:7
    restart: unless-stopped
    command: ["redis-server", "--save", "", "--appendonly", "no"]
    healthcheck:
      test: ["CMD", "redis-cli", "PING"]
      interval: 10s
      timeout: 3s
      retries: 10

volumes:
  caddy_data:
  caddy_config:
  db_data:
```

### Why this compose setup

- Caddy terminates TLS and reverse proxies to the API
- FastAPI stays private on the Docker network
- PostgreSQL is durable via volume
- Redis is hot-state only
- `alembic upgrade head` runs on startup so new deploys apply migrations
- single process is enough for first deploy

---

## Caddy config

Create `xcpro-server/deploy/Caddyfile`:

```caddyfile
{
    email admin@xcpro.app
}

api.xcpro.app {
    encode gzip zstd

    @health path /health/live /health/ready
    handle @health {
        reverse_proxy api:8080
    }

    handle {
        reverse_proxy api:8080
    }

    log {
        output stdout
        format console
    }
}
```

This gives you:

- automatic HTTPS
- automatic certificate renewals
- gzip/zstd compression
- simple reverse proxying

---

## Backend app scaffold

### `server/requirements.txt`

Use pinned versions in real implementation, but this is the package set:

```txt
fastapi
uvicorn[standard]
sqlalchemy
psycopg[binary]
alembic
redis
pydantic
pydantic-settings
python-jose[cryptography]
httpx
orjson
pytest
pytest-asyncio
```

### `server/Dockerfile`

```dockerfile
FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1

WORKDIR /app

RUN apt-get update && \
    apt-get install -y --no-install-recommends build-essential curl wget && \
    rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .
```

### `server/app/main.py`

```python
from fastapi import FastAPI
from app.api.router import api_router

app = FastAPI(title="XCPro Server", version="0.1.0")
app.include_router(api_router)

@app.get("/health/live")
def live() -> dict:
    return {"status": "ok"}

@app.get("/health/ready")
def ready() -> dict:
    return {"status": "ok"}
```

### `server/app/api/router.py`

```python
from fastapi import APIRouter
from app.api import health, ingest, public, auth

api_router = APIRouter()
api_router.include_router(health.router, tags=["health"])
api_router.include_router(auth.router, prefix="/api/v1/auth", tags=["auth"])
api_router.include_router(ingest.router, prefix="/api/v1/client", tags=["client"])
api_router.include_router(public.router, prefix="/api/v1/public", tags=["public"])
```

---

## Server-side package rules

Keep the backend clean. Follow the spirit of XCPro's architecture rules.

### Required rules

- API handlers own no business logic
- use cases own business logic
- repositories own persistence/cache interaction
- domain models are framework-agnostic
- one authoritative owner per data item
- request validation at API boundary
- SI units only inside the backend
- server time source injected/wrapped where logic depends on time
- public responses come from use cases, not direct SQL in route handlers

### Forbidden

- route handlers writing raw SQL
- Redis and PostgreSQL both pretending to be authoritative for the same field
- server logic depending on map/render/UI concepts
- a single `LiveTrackingManager` god object with DB + Redis + auth + cleanup mixed together
- logging raw GPS traces in normal production info logs

---

## Data model

These are the core entities.

### 1. Device

Represents the XCPro install or caller identity.

Fields:

- `id` UUID
- `device_label`
- `platform` (`android`)
- `created_at`
- `last_seen_at`
- `is_active`

### 2. Pilot

Basic owner record.

Fields:

- `id` UUID
- `display_name`
- `created_at`

For Phase 1 this can be very simple or even effectively one pilot per device.

### 3. LiveSession

Authoritative record for one active or historical live flight.

Fields:

- `id` UUID
- `pilot_id`
- `device_id`
- `status` (`ACTIVE`, `STALE`, `ENDED`)
- `share_code`
- `public_enabled`
- `started_at_wall_ms`
- `ended_at_wall_ms`
- `last_position_at_wall_ms`
- `last_server_receive_at_wall_ms`
- `latest_task_revision_id`
- `created_at`
- `updated_at`

### 4. PositionEvent

Immutable durable position history.

Fields:

- `id` bigserial
- `session_id`
- `sequence_no`
- `client_wall_time_ms`
- `sample_age_ms` nullable
- `received_at_wall_ms`
- `lat_deg`
- `lon_deg`
- `gps_alt_meters` nullable
- `pressure_alt_meters` nullable
- `agl_meters` nullable
- `ground_speed_ms` nullable
- `track_deg` nullable
- `vertical_speed_ms` nullable
- `te_vario_ms` nullable
- `wind_speed_ms` nullable
- `wind_from_deg` nullable
- `tas_ms` nullable
- `ias_ms` nullable
- `gps_accuracy_meters` nullable
- `flying_state` nullable
- `battery_pct` nullable
- `network_type` nullable
- `source` (`XCPro`)

### 5. TaskRevision

Durable full snapshot of a task at a point in time.

Fields:

- `id` UUID
- `session_id`
- `server_revision_no`
- `client_revision_no` nullable
- `task_hash`
- `task_type` (`RACING`, `AAT`)
- `name`
- `active_leg_index` nullable
- `payload_json`
- `received_at_wall_ms`

### 6. SessionEvent

Operational events.

Fields:

- `id` bigserial
- `session_id`
- `event_type`
- `payload_json`
- `created_at_wall_ms`

Examples:

- `SESSION_STARTED`
- `SESSION_STALE`
- `SESSION_ENDED`
- `TASK_UPDATED`

### 7. PublicCursorState

Optional helper if you want cheap follower delta polling.

Fields:

- `session_id`
- `latest_position_sequence`
- `latest_task_revision_no`
- `cursor_version`

This can also live only in Redis.

---

## SQL schema (initial shape)

Use Alembic migrations, but this is the target schema shape.

```sql
CREATE TABLE devices (
    id UUID PRIMARY KEY,
    device_label TEXT,
    platform TEXT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_seen_at TIMESTAMPTZ
);

CREATE TABLE pilots (
    id UUID PRIMARY KEY,
    display_name TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE live_sessions (
    id UUID PRIMARY KEY,
    pilot_id UUID REFERENCES pilots(id),
    device_id UUID NOT NULL REFERENCES devices(id),
    status TEXT NOT NULL,
    share_code TEXT NOT NULL UNIQUE,
    public_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    started_at_wall_ms BIGINT NOT NULL,
    ended_at_wall_ms BIGINT,
    last_position_at_wall_ms BIGINT,
    last_server_receive_at_wall_ms BIGINT,
    latest_task_revision_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE position_events (
    id BIGSERIAL PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES live_sessions(id) ON DELETE CASCADE,
    sequence_no BIGINT NOT NULL,
    client_wall_time_ms BIGINT NOT NULL,
    sample_age_ms BIGINT,
    received_at_wall_ms BIGINT NOT NULL,
    lat_deg DOUBLE PRECISION NOT NULL,
    lon_deg DOUBLE PRECISION NOT NULL,
    gps_alt_meters DOUBLE PRECISION,
    pressure_alt_meters DOUBLE PRECISION,
    agl_meters DOUBLE PRECISION,
    ground_speed_ms DOUBLE PRECISION,
    track_deg DOUBLE PRECISION,
    vertical_speed_ms DOUBLE PRECISION,
    te_vario_ms DOUBLE PRECISION,
    wind_speed_ms DOUBLE PRECISION,
    wind_from_deg DOUBLE PRECISION,
    tas_ms DOUBLE PRECISION,
    ias_ms DOUBLE PRECISION,
    gps_accuracy_meters DOUBLE PRECISION,
    flying_state TEXT,
    battery_pct INTEGER,
    network_type TEXT,
    source TEXT NOT NULL DEFAULT 'XCPro',
    UNIQUE(session_id, sequence_no)
);

CREATE TABLE task_revisions (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES live_sessions(id) ON DELETE CASCADE,
    server_revision_no INTEGER NOT NULL,
    client_revision_no INTEGER,
    task_hash TEXT NOT NULL,
    task_type TEXT NOT NULL,
    name TEXT,
    active_leg_index INTEGER,
    payload_json JSONB NOT NULL,
    received_at_wall_ms BIGINT NOT NULL,
    UNIQUE(session_id, server_revision_no)
);

CREATE TABLE session_events (
    id BIGSERIAL PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES live_sessions(id) ON DELETE CASCADE,
    event_type TEXT NOT NULL,
    payload_json JSONB,
    created_at_wall_ms BIGINT NOT NULL
);

CREATE INDEX idx_live_sessions_status ON live_sessions(status);
CREATE INDEX idx_live_sessions_last_position ON live_sessions(last_position_at_wall_ms);
CREATE INDEX idx_position_events_session_client_time ON position_events(session_id, client_wall_time_ms);
CREATE INDEX idx_task_revisions_session_revision ON task_revisions(session_id, server_revision_no);
CREATE INDEX idx_session_events_session_created ON session_events(session_id, created_at_wall_ms);
```

---

## Redis hot state

Redis is not authoritative for history. It is only for hot live reads.

### Keys

```text
live:share:{shareCode} -> sessionId
live:session:{sessionId}:snapshot -> JSON latest public snapshot
live:session:{sessionId}:latest_position -> JSON latest position
live:session:{sessionId}:recent_positions -> capped list of JSON positions
live:session:{sessionId}:cursor -> integer
live:session:{sessionId}:latest_task_revision -> JSON task revision summary
```

### Redis write rules

On accepted position ingest:

1. persist to PostgreSQL
2. update latest position key
3. append to capped recent trail list
4. update session snapshot
5. increment cursor

If PostgreSQL write fails, do not update Redis.

### Capped trail behavior

Keep only the latest N points in Redis, for example 1500.
That is enough for public follower recent trail fetches without hitting PostgreSQL every poll.

---

## Time base contract at the network boundary

This is critical.

### Client-side truth

XCPro uses monotonic time internally for live delta/validity logic.

### Server-side truth

The server cannot use raw device monotonic timestamps as global time because each device has its own monotonic clock origin.

### Correct contract

The client sends:

- `clientWallTimeMs` for the sample
- optional `sampleAgeMs` computed on-device from monotonic time
- sequence number
- current session id

The server records:

- `receivedAtWallMs`
- `storedAtWallMs` implicit via DB/server time
- last-seen timestamps
- stale session timing

### Rules

- never compare raw device monotonic timestamps with wall time
- if the client sends `sampleAgeMs`, treat it as a hint, not a global clock
- ordering inside a session is sequence-first, time-second
- public freshness is based on server receive time and/or client wall time, not raw monotonic values

---

## SI unit contract for API payloads

All numeric contracts stay SI.

### Required

- altitude/distance/radius/AGL: meters
- speed/vario/TAS/IAS/wind speed: m/s
- angles/headings/bearings: degrees
- timestamps: epoch milliseconds
- accuracy: meters

### Forbidden

- knots/kmh/feet/NM in backend internals
- field names without units for ambiguous numeric values

### Naming examples

Good:

- `gpsAltMeters`
- `groundSpeedMs`
- `verticalSpeedMs`
- `gpsAccuracyMeters`

Bad:

- `altitude`
- `speed`
- `vario`
- `accuracy`

---

## API contract

Use versioned paths under `/api/v1/`.

### Auth model for Phase 1

Keep it simple:

1. Device registers once.
2. Server issues a device token.
3. Device starts a session using that token.
4. Server returns:
   - `sessionId`
   - `ingestToken`
   - `shareCode`

The `ingestToken` is then used only for that live session.

You can simplify even further in a closed test build, but do not use anonymous ingest in production.

---

## Endpoint list

### Device auth

- `POST /api/v1/auth/device/register`

### Session lifecycle

- `POST /api/v1/client/sessions/start`
- `POST /api/v1/client/sessions/{sessionId}/heartbeat`
- `POST /api/v1/client/sessions/{sessionId}/end`

### Position ingest

- `POST /api/v1/client/sessions/{sessionId}/positions`

### Task ingest

- `PUT /api/v1/client/sessions/{sessionId}/task`

### Public follower reads

- `GET /api/v1/public/live/{shareCode}`
- `GET /api/v1/public/live/{shareCode}/delta`
- `GET /api/v1/public/live/{shareCode}/history`
- `GET /api/v1/public/live/{shareCode}/task`

### Health

- `GET /health/live`
- `GET /health/ready`

---

## Payloads

### 1. Register device

`POST /api/v1/auth/device/register`

Request:

```json
{
  "deviceLabel": "Pixel 8 Pro",
  "platform": "android",
  "appVersion": "0.1.0",
  "installId": "9f9d3c31-2222-4444-8888-111111111111"
}
```

Response:

```json
{
  "deviceId": "0f8fad5b-d9cb-469f-a165-70867728950e",
  "deviceToken": "JWT_OR_OPAQUE_TOKEN"
}
```

---

### 2. Start session

`POST /api/v1/client/sessions/start`

Headers:

```text
Authorization: Bearer DEVICE_TOKEN
```

Request:

```json
{
  "pilotName": "Pilot Name",
  "aircraftLabel": "Glider 1",
  "publicEnabled": true,
  "sessionStartWallTimeMs": 1739200000000,
  "clientBuild": "debug"
}
```

Response:

```json
{
  "sessionId": "11111111-2222-3333-4444-555555555555",
  "shareCode": "ABCD1234",
  "ingestToken": "SESSION_INGEST_TOKEN",
  "status": "ACTIVE"
}
```

---

### 3. Position batch ingest

`POST /api/v1/client/sessions/{sessionId}/positions`

Headers:

```text
Authorization: Bearer SESSION_INGEST_TOKEN
```

Request:

```json
{
  "batchSentWallTimeMs": 1739200005000,
  "positions": [
    {
      "sequenceNo": 1,
      "clientWallTimeMs": 1739200001000,
      "sampleAgeMs": 400,
      "latDeg": 52.123456,
      "lonDeg": 13.123456,
      "gpsAltMeters": 812.3,
      "pressureAltMeters": 798.1,
      "aglMeters": 123.4,
      "groundSpeedMs": 18.2,
      "trackDeg": 243.6,
      "verticalSpeedMs": 1.9,
      "teVarioMs": 2.1,
      "windSpeedMs": 7.8,
      "windFromDeg": 190.0,
      "tasMs": 20.3,
      "iasMs": 18.7,
      "gpsAccuracyMeters": 4.5,
      "flyingState": "FLYING",
      "batteryPct": 54,
      "networkType": "LTE"
    }
  ]
}
```

Response:

```json
{
  "acceptedCount": 1,
  "duplicateCount": 0,
  "rejectedCount": 0,
  "latestSequenceNo": 1,
  "serverCursor": 12
}
```

### Position ingest rules

- sequence number is unique per session
- duplicates are ignored, not fatal
- batch endpoint must be retry-safe
- reject impossible coordinates
- reject empty batch
- cap batch size with `MAX_POSITION_BATCH_SIZE`

### Recommended client cadence

Do not send raw fusion loop cadence.
The app's internal fusion may run at higher cadence, but the network export should be a throttled snapshot stream.

Recommended initial policy:

- while flying: every 2 seconds
- on ground / weak movement: every 10 seconds
- flush immediately on session end
- flush immediately after reconnect if backlog exists

---

### 4. Task snapshot upsert

`PUT /api/v1/client/sessions/{sessionId}/task`

Headers:

```text
Authorization: Bearer SESSION_INGEST_TOKEN
```

Request:

```json
{
  "clientRevisionNo": 3,
  "taskType": "RACING",
  "name": "Day 2 Task",
  "activeLegIndex": 1,
  "task": {
    "startOpenWallTimeMs": 1739200000000,
    "turnpoints": [
      {
        "index": 0,
        "role": "START",
        "name": "Start Gate",
        "latDeg": 52.111111,
        "lonDeg": 13.111111,
        "zoneType": "LINE",
        "lineLengthMeters": 5000.0
      },
      {
        "index": 1,
        "role": "TURNPOINT",
        "name": "TP1",
        "latDeg": 52.222222,
        "lonDeg": 13.222222,
        "zoneType": "CYLINDER",
        "radiusMeters": 500.0
      },
      {
        "index": 2,
        "role": "FINISH",
        "name": "Finish",
        "latDeg": 52.333333,
        "lonDeg": 13.333333,
        "zoneType": "CYLINDER",
        "radiusMeters": 3000.0
      }
    ]
  }
}
```

Response:

```json
{
  "serverRevisionNo": 4,
  "taskHash": "sha256:....",
  "changed": true
}
```

### Task revision rules

- client always sends a full snapshot
- server computes a stable hash of the canonical payload
- if the hash equals the current latest task hash, do nothing and return `changed=false`
- otherwise store a new task revision and update the session pointer

### Why full snapshot is the right call

XCPro task state is authoritative in repository/coordinator owners, not in map rendering.
A full snapshot keeps the server independent from UI/runtime-only render state and avoids patch drift.

---

### 5. Heartbeat

`POST /api/v1/client/sessions/{sessionId}/heartbeat`

Use only when there is no position batch for a while but the session is still open.

Request:

```json
{
  "clientWallTimeMs": 1739200100000
}
```

Response:

```json
{
  "status": "ACTIVE"
}
```

---

### 6. End session

`POST /api/v1/client/sessions/{sessionId}/end`

Request:

```json
{
  "clientWallTimeMs": 1739203600000,
  "reason": "USER_STOPPED"
}
```

Response:

```json
{
  "status": "ENDED"
}
```

---

### 7. Public live snapshot

`GET /api/v1/public/live/{shareCode}`

Response:

```json
{
  "session": {
    "sessionId": "11111111-2222-3333-4444-555555555555",
    "status": "ACTIVE",
    "pilotName": "Pilot Name",
    "shareCode": "ABCD1234",
    "startedAtWallTimeMs": 1739200000000,
    "lastPositionAtWallTimeMs": 1739200005000
  },
  "latestPosition": {
    "sequenceNo": 120,
    "clientWallTimeMs": 1739200005000,
    "receivedAtWallTimeMs": 1739200005200,
    "latDeg": 52.123456,
    "lonDeg": 13.123456,
    "gpsAltMeters": 812.3,
    "pressureAltMeters": 798.1,
    "groundSpeedMs": 18.2,
    "trackDeg": 243.6,
    "verticalSpeedMs": 1.9
  },
  "task": {
    "serverRevisionNo": 4,
    "taskType": "RACING",
    "activeLegIndex": 1
  },
  "recentTrail": [
    {
      "sequenceNo": 116,
      "latDeg": 52.100001,
      "lonDeg": 13.100001,
      "clientWallTimeMs": 1739200001000
    }
  ],
  "cursor": 77
}
```

---

### 8. Public delta polling

`GET /api/v1/public/live/{shareCode}/delta?cursor=77`

Response:

```json
{
  "cursor": 82,
  "positionUpdates": [
    {
      "sequenceNo": 121,
      "latDeg": 52.124000,
      "lonDeg": 13.124000,
      "clientWallTimeMs": 1739200007000,
      "groundSpeedMs": 18.5,
      "trackDeg": 244.0
    }
  ],
  "taskUpdated": false
}
```

### Delta polling rules

- if cursor is current, return empty arrays
- if cursor is too old or missing, client should re-fetch full snapshot
- keep this cheap by building delta from Redis hot state

---

### 9. Public task read

`GET /api/v1/public/live/{shareCode}/task`

Response:

```json
{
  "serverRevisionNo": 4,
  "taskType": "RACING",
  "payload": { }
}
```

---

## Validation rules

### Position validation

Hard reject:

- missing lat/lon
- lat outside `[-90, 90]`
- lon outside `[-180, 180]`
- non-finite numeric values
- negative batch size or oversized batch

Soft reject / quarantine candidate:

- absurd speed jump based on previous accepted point
- timestamp wildly earlier than session start
- accuracy too poor and speed jump too large

### Recommended first hard thresholds

Start conservative:

- batch size > 100 -> reject
- jump requiring > 120 m/s between close timestamps -> reject point
- `gpsAccuracyMeters > 500` and large jump -> reject point
- duplicate `sequenceNo` -> ignore as duplicate

Make thresholds config-driven, not hard-coded in random files.

### Task validation

- `taskType` must be `RACING` or `AAT`
- turnpoint indices must be contiguous
- zone fields required for their zone type
- no empty task if `taskType` is provided
- active leg index must fall within task bounds if present

---

## Error model

All errors should use one envelope.

```json
{
  "error": {
    "code": "INVALID_PAYLOAD",
    "message": "groundSpeedMs must be >= 0",
    "details": {
      "field": "groundSpeedMs"
    }
  }
}
```

Codes:

- `UNAUTHORIZED`
- `FORBIDDEN`
- `SESSION_NOT_FOUND`
- `SESSION_ENDED`
- `INVALID_PAYLOAD`
- `BATCH_TOO_LARGE`
- `DUPLICATE_SEQUENCE`
- `SHARE_NOT_FOUND`
- `INTERNAL_ERROR`

---

## Use case list

Create these use cases in the backend:

- `RegisterDeviceUseCase`
- `StartLiveSessionUseCase`
- `HeartbeatSessionUseCase`
- `IngestPositionBatchUseCase`
- `UpsertTaskRevisionUseCase`
- `EndLiveSessionUseCase`
- `GetPublicLiveSnapshotUseCase`
- `GetPublicLiveDeltaUseCase`
- `CleanupStaleSessionsUseCase`

### Ports / interfaces

- `ClockPort`
- `DeviceRepositoryPort`
- `SessionRepositoryPort`
- `PositionRepositoryPort`
- `TaskRevisionRepositoryPort`
- `SessionCachePort`
- `TokenServicePort`

This is the clean boundary set. Do not have use cases depend directly on SQLAlchemy sessions or Redis clients.

---

## Background jobs

You need at least one worker loop.

### 1. Stale session cleanup

Every 15 seconds:

- find sessions with no fresh position for `SESSION_STALE_AFTER_SECONDS`
- mark `ACTIVE -> STALE`
- write `SESSION_STALE` event
- refresh public snapshot cache

Every 60 seconds:

- find sessions stale/active for longer than `SESSION_AUTO_END_AFTER_SECONDS`
- mark `-> ENDED`
- write `SESSION_ENDED` event

### 2. Optional cache repair job

Useful later:

- scan active sessions
- rebuild Redis snapshot if cache is missing/corrupt

---

## Public follower model

### Phase 1 follower UX

- follower opens a link or enters share code
- follower loads full snapshot
- follower polls delta every 2-5 seconds
- follower shows latest position, trail, and current task

### Share code rules

- short, human-enterable code
- unique
- expires after the session retention window
- revocable later if you add private mode

### Recommended format

8-10 chars, uppercase base32, no confusing characters.

Example:

- `ABCD1234`
- `X7K9Q2MP`

---

## Privacy and security

This matters. XCPro handles location.

### Required

- HTTPS only
- auth required for ingest
- public reads allowed only by share code
- no raw location logs in normal INFO logs
- secrets only in `.env` or external secret store
- DB not exposed on host ports
- Redis not exposed on host ports

### Logging policy

Good:

- session started
- batch accepted count
- duplicate count
- rejected count
- task revision changed
- session stale/ended

Bad:

- full coordinate traces in INFO logs
- giant JSON payload dumps in normal production logs
- secrets/tokens

### Data retention

Initial recommendation:

- live sessions: keep indefinitely for now, or prune later
- public share access: expire 48h after session end
- Redis hot state: TTL 72h after session end

Make retention explicit in config.

---

## Android integration points still required

This is not server code, but it tells you exactly where the app should connect.

### 1. Export source for live position uploads

Export from `FlightDataRepository` SSOT, not from map UI.

Create something like:

- `LiveTrackingRepository`
- `ObserveUploadableFlightSnapshotUseCase`
- `LiveTrackingApi`

The uploader should live in data/domain/service layers, not in Composables or ViewModels.

### 2. Export source for task sync

Do not export map-render state as the server source of truth.

Create a canonical task sync snapshot from task repository/coordinator authoritative state.
A render snapshot can still exist for UI, but server sync should consume a domain snapshot.

Suggested client-side use case:

- `ObserveCurrentTaskSyncSnapshotUseCase`

### 3. Background ownership

Best owner for live upload lifecycle:

- foreground service side
- or repository/service started from the same lifecycle that owns live sensor/session state

Avoid:

- direct ViewModel networking
- UI-owned retry loops
- Composable-owned session mutation

### 4. Retry policy

Client should batch locally and retry safely because the server endpoint is idempotent by `(sessionId, sequenceNo)`.

### 5. Minimal client payload fields

For Phase 1, the client does not need to ship every internal sensor detail.
Send only what followers or future history need.

Minimum position fields:

- `sequenceNo`
- `clientWallTimeMs`
- `latDeg`
- `lonDeg`
- `gpsAltMeters` or `pressureAltMeters` if available
- `groundSpeedMs`
- `trackDeg`
- `verticalSpeedMs` if available

Recommended extras:

- `teVarioMs`
- `windSpeedMs`
- `windFromDeg`
- `tasMs`
- `iasMs`
- `gpsAccuracyMeters`
- `flyingState`

---

## OGN and ADS-B handling for this phase

Keep this blunt and simple.

### OGN

XCPro already has OGN client-side overlay work. Do not force that through the new backend in Phase 1.

#### Phase 1 rule

- ownship live session -> your backend
- OGN overlay -> keep existing path
- no server-side OGN merge yet

#### When to add server-side OGN later

Add it only when you need:

- public web follower maps that cannot connect to OGN directly
- server-side trail/history consolidation
- normalized event/competition view across many pilots
- canonical OGN/FLARM identity fusion and duplicate suppression for the followed glider

When that work starts, follow the **Future OGN / FLARM fusion requirements** addendum below.

### ADS-B

Same story. Leave ADS-B out of the first server.

## Future OGN / FLARM fusion requirements (post-Phase 1 addendum)

This is intentionally **not** Phase S1/S2 backend scope.
Do not block the current relay-first backend on this work.
This section becomes mandatory only when you add **server-side OGN ingest / FLARM-aware fusion**.

### Core decision

When Glider A can appear through multiple sources, the system must behave like this:

- **XCPro relay is the primary / canonical source** for the followed glider while relay data is fresh
- **OGN is traffic overlay** for everyone else
- **OGN is fallback / backfill** for Glider A only when relay freshness degrades or goes stale
- relay + OGN + FLARM identity paths must collapse into **one aircraft object**, not two
- XCPro must **not** create a second independent aircraft identity in OGN if the glider already has onboard FLARM / ICAO identity

### Canonical aircraft identity model

Introduce one stable internal aircraft identity before attempting any relay/OGN fusion.

Recommended shape:

```yaml
glider_uuid: internal stable UUID

radio_identity:
  type: icao24 | flarm_id
  value: "6-hex-chars"

known_aliases:
  - type: icao24
    value: "optional"
  - type: flarm_id
    value: "optional"
  - type: registration
    value: "optional"
  - type: comp_no
    value: "optional"

source_bindings:
  relay_pilot_id: "optional"
  relay_device_id: "optional"
  ogn_address: "optional"
```

Rules:

- `glider_uuid` is the stable internal key used by the backend, follower model, and trail state
- `radio_identity.type + radio_identity.value` is the primary dedupe key for cross-source clustering
- store **ID type + ID value**, not one ambiguous string
- store both ICAO and FLARM IDs when known
- registration / comp number are aliases only; they must **not** become the sole canonical key
- random FLARM IDs must **not** be treated as durable canonical identity

### Alias mapping model

A future fusion layer needs an explicit alias mapping table, not ad hoc string comparison.

Recommended fields:

- `alias_type`
- `alias_value`
- `glider_uuid`
- `confidence` or `verification_state`
- `source` (manual, imported, OGN DDB, admin override, competition list, etc.)
- `created_at` / `updated_at`

Required behavior:

- support manual ICAO / FLARM entry when XCPro or the pilot knows the aircraft identity
- support registration-based lookup only when verified
- support explicit backend override / admin mapping
- allow relay pilot/device binding to a canonical `glider_uuid`

Trust order:

1. exact `icao24`
2. exact `flarm_id`
3. explicit backend alias mapping
4. registration + confirmed alias mapping

Guardrails:

- do **not** use loose spatial proximity alone to dedupe the followed glider
- do **not** let unverified registration text auto-merge live aircraft tracks
- if relay identity and OGN identity disagree with no trusted alias path, treat it as a configuration issue, not an auto-merge

### Relay-primary / OGN-fallback state machine

For the followed glider only, use an explicit freshness state machine.

Starting proposal:

- **Fresh:** relay age `<= 12s`
  - relay fully authoritative
  - duplicate OGN representation hidden
- **Degraded:** relay age `> 12s` and `<= 30s`
  - keep one Glider A object
  - relay still authoritative for task semantics
  - optionally use newer matching OGN positions for smoothing only if you still preserve one object
- **Stale:** relay age `> 30s`
  - if matching OGN is fresher, switch the **same internal Glider A object** to OGN-backed positioning
- **Lost:** relay stale and OGN stale
  - keep last known position briefly, then mark Glider A unavailable/lost

Rules:

- freshness is based on source receipt/update time, not only the client timestamp
- relay stays the source of truth for Glider A task state and task-aware semantics while it is fresh or degraded
- OGN should not create a second ownship/followed-aircraft object
- source switching must be internal; the user should not see one glider disappear and another appear

### Duplicate suppression rules for Glider A

If an OGN target resolves to Glider A's canonical identity, then:

- do **not** render a second aircraft icon
- do **not** create a second followed-glider object
- treat the OGN target as the **same aircraft**
- use it only as fallback / backfill / redundancy when relay freshness requires it

Trusted identity paths for clustering, in order:

1. exact `icao24`
2. exact `flarm_id`
3. explicit backend alias mapping
4. verified registration mapping

Guardrails:

- do **not** dedupe the followed glider using loose spatial proximity alone
- do **not** assume two close tracks in the same gaggle or thermal are the same aircraft
- do **not** rely on OGN alone to solve the duplicate-ownship UX problem
- do **not** let OGN overwrite relay task-state semantics while relay is healthy

### Continuous trail rules

Glider A's snail trail must be keyed by **canonical glider identity**, not by source type.

Rules:

- trail storage key = `glider_uuid`
- append relay points normally while relay is healthy
- if relay goes stale and matching OGN data exists, append fallback/backfill OGN points to the **same trail**
- when relay resumes, keep appending to the **same trail**
- do **not** split trail history into separate `relay trail` and `OGN trail` objects
- internal trail points may carry source metadata such as `source=relay|ogn` and `confidence=normal|degraded`

The user should never perceive:

- relay glider disappears
- OGN glider appears as a different object
- task context resets
- trail continuity breaks

### Explicit â€œdo not infer same-task from OGN aloneâ€ rule

This must be a hard rule in the future fusion design:

- **Do not infer "same task" from OGN alone.**

Same-task tagging may come from:

- XCPro / relay competition data
- verified task-sharing data
- another trusted competition/event source

It must **not** be inferred solely because:

- two gliders are nearby
- two gliders have similar headings
- an OGN track looks like it might be on the same course line

OGN can contribute traffic identity, positions, and fallback trail points, but it is not enough to assign shared task semantics by itself.

### Future transmission-path rule

When you add the future OGN publishing path, keep this explicit:

- XCPro should always send enriched task-aware ownship data to the relay
- onboard FLARM should continue normal RF broadcasting
- XCPro may also publish to OGN only under the **same aircraft radio identity** as the onboard FLARM / ICAO identity
- do **not** let XCPro create an independent second aircraft identity in OGN when the glider already has onboard FLARM

### Future implementation checklist

Before claiming server-side OGN/FLARM fusion is done, the design must answer **yes** to all of these:

- relay is primary for the followed glider
- OGN is overlay + fallback, not the main followed-glider source
- there is one canonical aircraft identity across relay / FLARM / OGN
- the model stores ID type + ID value
- duplicate Glider A icons are suppressed
- source switches preserve one continuous Glider A trail
- same-task is not inferred from OGN alone
- manual / verified ICAO/FLARM mapping is supported

### Phase placement

Treat this as **Phase S4+** work:

1. add OGN ingest worker
2. normalize external OGN traffic records
3. introduce canonical aircraft identity tables / alias mappings
4. implement relay-primary / OGN-fallback fusion
5. suppress duplicate Glider A objects
6. preserve one continuous Glider A trail across source switches

Until then, keep OGN out of the server-side live-follow fusion path.

---

## Alembic migration plan

Create at least these migrations:

1. `001_initial_core_tables`
2. `002_position_indexes`
3. `003_task_revisions`
4. `004_session_events`
5. `005_public_snapshot_fields` if later needed

Do not hand-edit production tables directly. Use migrations.

---

## Testing plan

### Unit tests

- task hash stable for same logical payload
- duplicate position sequence ignored
- out-of-order duplicate retry is safe
- stale session transition works
- auto-end transition works
- SI-unit field validation
- invalid coordinates rejected
- task validation rules

### Integration tests

- start session -> ingest positions -> public snapshot returns latest data
- task update mid-session -> latest task revision visible publicly
- Redis cache rebuild from PostgreSQL works
- follower delta cursor advances as expected
- explicit end closes the session
- offline batch replay accepted in order

### Failure-mode tests

- DB down -> ingest fails and Redis not updated
- Redis down -> ingest still persists to DB and public reads fall back gracefully
- duplicate batch retry after timeout -> acceptedCount stays deterministic
- stale cleanup runs twice -> idempotent state transitions

### Manual smoke test

1. Start stack.
2. Register device.
3. Start session.
4. Send one task snapshot.
5. Send five positions.
6. Fetch public snapshot by share code.
7. Poll delta.
8. End session.
9. Confirm status becomes `ENDED`.

---

## Suggested first implementation order

### Phase S1 - Thin end-to-end

Build this first and nothing more:

- compose stack
- health endpoints
- device register
- session start
- position batch ingest
- task snapshot upsert
- public snapshot read
- session end

### Phase S2 - Hardening

Then add:

- Redis hot cache
- delta endpoint
- stale/auto-end cleanup worker
- validation hardening
- error envelope
- structured logging
- better tests

### Phase S3 - Ops polish

Then add:

- backups
- metrics
- deploy script
- admin inspection endpoint
- retention cleanup

### Phase S4 - Optional advanced features

Later only:

- WebSockets/SSE
- OGN ingest worker with identity-fusion / duplicate-suppression layer
- public web follower UI
- competition scoring
- multi-pilot event view

---

## Backup plan

At minimum, back up PostgreSQL daily.

### Cheap first version

Use a host cron or a simple one-shot script that runs `pg_dump` from the compose network and stores compressed dumps under `/opt/backups`, then sync them off-box.

Example shape:

```bash
#!/usr/bin/env bash
set -euo pipefail

STAMP=$(date -u +%Y%m%dT%H%M%SZ)
mkdir -p /opt/backups

docker compose exec -T db \
  pg_dump -U "$DB_USER" "$DB_NAME" \
  | gzip > "/opt/backups/xcpro_${STAMP}.sql.gz"
```

Then push those backups to another storage target.
Do not keep your only backup on the same VM forever.

---

## Deploy commands

### First deploy

```bash
git clone YOUR_BACKEND_REPO xcpro-server
cd xcpro-server

cp .env.example .env
# edit .env

docker compose build
docker compose up -d
docker compose ps
docker compose logs -f api
```

### After changing code

```bash
cd xcpro-server
git pull
docker compose build api
docker compose up -d api
docker compose logs -f api
```

### Inspect health

```bash
curl -I https://api.xcpro.app/health/live
curl -I https://api.xcpro.app/health/ready
```

---

## Example Codex task list

If you hand this to Codex, give it these concrete phases.

### Phase 0 - Repo skeleton

- create backend repo layout
- compose stack
- FastAPI app scaffold
- settings, logging, health endpoints

Exit:
- stack boots
- `/health/live` works

### Phase 1 - Core persistence and auth

- SQLAlchemy models
- Alembic initial migration
- device register endpoint
- token helper
- session start/end endpoints

Exit:
- session lifecycle works end to end

### Phase 2 - Position ingest

- position batch schema
- dedupe by `(sessionId, sequenceNo)`
- PostgreSQL writes
- Redis latest state write-through
- public snapshot endpoint

Exit:
- positions visible publicly by share code

### Phase 3 - Task revisions

- task payload schema
- canonical task hashing
- task revision persistence
- latest task pointer on session
- public task read endpoint

Exit:
- task updates visible publicly

### Phase 4 - Hardening

- stale cleanup worker
- delta endpoint
- error envelope
- more tests
- deploy docs

Exit:
- required smoke tests pass
- retries and stale sessions behave correctly

---

## Definition of done for the first backend

The first backend is done when all of this is true:

- a fresh XCPro device can register and start a live session
- the app can upload position batches with retries
- the app can upload full task snapshots and task changes
- followers can load current live state by share code
- duplicate retries do not corrupt history
- a dead session becomes stale, then ended
- deployment survives container restarts
- no raw DB/Redis ports are exposed publicly
- normal production logs do not dump raw GPS traces
- the system is documented well enough that another engineer can deploy it cold

---

## Known assumptions

These are reasonable starting assumptions. Adjust later if the product changes.

1. One mobile device is the authoritative ownship source for a session.
2. Task edits happen from the pilot's XCPro client, not from follower devices.
3. Public follower access is link/share-code based.
4. The backend is a live relay/store first, not a scorer.
5. OGN/ADS-B stay outside this backend in Phase 1; later fusion must follow the future OGN / FLARM rules above.
6. The current Hetzner VM is enough for dev/test and early field validation.

---

## Blunt recommendation

Do not try to build scoring, OGN federation, WebSockets, and a giant account system right now.

Build this first:

- HTTPS
- session start/end
- position ingest
- task revisions
- public snapshot read
- stale cleanup
- Redis hot state
- PostgreSQL durable history

That is the real foundation.

Everything else is noise until this works.

---

## Optional next file to generate

Once this document is accepted, the next useful files are:

- `XCPro_Server_Change_Plan.md`
- `compose.yml`
- `deploy/Caddyfile`
- `server/app/main.py`
- `server/app/api/ingest.py`
- `server/app/domain/usecase/ingest_position_batch.py`
- `server/alembic/versions/001_initial_core_tables.py`
