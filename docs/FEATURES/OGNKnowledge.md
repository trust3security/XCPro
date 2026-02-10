# OGNKnowledge.md
_A practical, implementation-oriented briefing for a Codex agent to add an OGN (Open Glider Network) APRS-IS client + parser to XCPro (Kotlin/Android)._

## 1) What you are building
You are consuming **OGN's live tracking stream** over **APRS-IS-style TCP**, then parsing APRS packets (TNC2 text format) into structured aircraft/receiver updates.

OGN "extends" APRS primarily by putting extra data into the **APRS comment field** (commonly referred to as **OGN-flavoured APRS**). The payload is still a normal APRS packet; the extension is mostly **extra tokens after the normal APRS position/altitude fields**.

### Compliance / usage rules (don't skip)
OGN data is free to use but you must follow their usage rules:
- OGN data is under **ODbL**.
- If you re-distribute OGN data, you **must respect the OGN DDB privacy choices**.
- You **must not re-distribute data older than 24 hours**.

Source: https://www.glidernet.org/ogn-data-usage/

## 2) How to access OGN data (network)
### 2.1 OGN APRS servers and ports
OGN exposes APRS-IS-style TCP servers. Common entrypoint:
- `aprs.glidernet.org` on **TCP port 14580** (server-side filters).

OGN also documents multiple backbone APRS servers (examples seen in public docs include `glidern1.glidernet.org` ... `glidern5.glidernet.org`) used for redundancy/load distribution.

> Implementation note: treat the hostname as a load-balanced entry. Implement reconnect + backoff and DNS re-resolve.

### 2.2 Protocol: APRS-IS TCP session
APRS-IS is **line-based ASCII-ish text** over TCP:
1. Client opens TCP socket.
2. Server sends one or more `# ...` identification/comment lines.
3. Client sends a **login line**.
4. Server responds with `# ...` ack / info.
5. Server streams packets, each terminated by `\r\n`.

APRS-IS recommends disabling Nagle (`TCP_NODELAY`) for bidirectional clients to avoid delays.

APRS-IS "user-defined filter" port behavior is **additive**: you start with almost nothing, then receive what you subscribe to via `filter ...`.

Authoritative reference:
- https://www.aprs-is.net/connecting.aspx
- https://www.aprs-is.net/javAPRSFilter.aspx

### 2.3 Login line format
APRS-IS login format (general):
```
user <mycall[-ssid]> pass <passcode> vers <softwarename> <softwarevers> [filter <filter-specs>]
```

For **receive-only** clients, APRS-IS commonly uses:
- `pass -1`

Example (receive-only + filter):
```
user N0CALL pass -1 vers XCPro 1.0 filter r/-33.8688/151.2093/250
```

### 2.4 Filters (critical to avoid overload)
Do **not** try to consume the global feed unless you really mean it.

Key filters (javAPRSFilter):
- `r/lat/lon/distKm` : range filter around lat/lon (decimal degrees, km)
- `m/distKm` : range filter around **your own last known position**
- `t/<types>` : type filter (position/object/item/message/etc)
- `u/<destcall>` : destination ("to-call") / unproto filter (e.g., `u/OGNSDR`)
- `p/<prefix>` : filter by source callsign prefix

Filters are space-separated and additive. Exclusion uses `-` prefix.

Examples:
- "Everything within 250km of Sydney":
  - `filter r/-33.8688/151.2093/250`
- "Positions only within 250km":
  - `filter r/-33.8688/151.2093/250 t/p`
- "Receiver beacons only within 250km" (often `to-call` = `OGNSDR`):
  - `filter r/-33.8688/151.2093/250 u/OGNSDR`

> Expect the best results by combining a **range** + **type** + sometimes **destcall** filter.

## 3) The data you receive (packet format)
### 3.1 TNC2 line format
APRS-IS packets are "TNC2" textual lines:
```
<SRC>><DEST>,<PATH1>,<PATH2>,...:<INFO>
```

Notes:
- Lines starting with `#` are comments/keepalives -> ignore.
- Each line is CRLF terminated.
- Max line length is 512 bytes including CRLF (APRS-IS rule).

### 3.2 APRS payload types you'll see
In `<INFO>` the first character indicates payload type, e.g.:
- `!` or `=` : position without timestamp
- `/` or `@` : position with timestamp
- `>` : status
- `:` : message
- `;` : object
- `_` : weather report (often uses positions too)

OGN traffic is commonly:
- **Aircraft position reports** (timestamped)
- **Receiver station beacons** (position + receiver status in comment)
- Other device families (FANET etc.) may appear with different destination callsigns.

## 4) OGN-flavoured APRS (the OGN-specific part)
### 4.1 Typical aircraft beacon example
Example aircraft beacon seen in public OGN parsing docs:
```
FLRDDDEAD>APRS,qAS,EDER:/114500h5029.86N/00956.98E'342/049/A=005524 id0ADDDEAD -454fpm -1.1rot 8.8dB 0e +51.2kHz gps4x5
```

Breakdown:
- `FLRDDDEAD` = source callsign (often prefix indicates device family, e.g. FLR = FLARM).
- `>APRS` = destination/to-call (standard APRS destcall).
- `qAS,EDER` = APRS-IS "q construct" + receiving iGate/server path info.
- `:/114500h...` = APRS position with timestamp (`/HHMMSS[h|z|/]` style).
- `5029.86N/00956.98E` = lat/lon in APRS degrees+minutes format.
- `'` after lon = APRS symbol (symbol table + symbol code are embedded around the lon separator).
- `342/049` = course/speed.
- `/A=005524` = altitude in feet (standard APRS "A=" altitude extension).
- After that: **OGN comment tokens**.

### 4.2 Comment token pattern (robust parsing strategy)
OGN-specific data is mostly **space-separated tokens** appended after normal APRS fields.

Common observed token shapes:
- `id[0-9A-F]{8}`  
  - 8 hex digits. Practical extraction:
    - last 6 hex chars often correspond to the "device_id" used in the OGN DDB.
    - first 2 hex chars look like a type/flags byte (exact bit meanings vary by device family).
- `[+-]?[0-9]+(\.[0-9]+)?fpm`  
  - vertical speed in **feet per minute** (typical interpretation)
- `[+-]?[0-9]+(\.[0-9]+)?rot`  
  - turn rate (units depend on sender; treat as numeric "rot")
- `[0-9]+(\.[0-9]+)?dB`  
  - signal quality (receiver-reported)
- `[0-9]+e`  
  - "e" count (receiver-reported; exact meaning can vary; keep raw)
- `[+-]?[0-9]+(\.[0-9]+)?kHz`  
  - frequency offset (receiver-reported)
- `gps[0-9]+x[0-9]+`  
  - GPS quality indicator (e.g. `gps4x5`); keep raw and/or split into two ints.

**Important:** do not hard-fail on unknown tokens. Store:
- a structured map for recognized tokens
- an "extraCommentTokens" list for everything else

This lets you survive new device families without breaking.

### 4.3 Other message families you may encounter
OGN carries multiple upstream data families. You will see different destination/to-calls.
Examples from public OGN protocol discussions include:
- `OGNSDR` (receiver beacons / receiver status)
- `OGNFNT` (FANET-related traffic)
- `OGFLYM` is commonly used for model airplanes (some viewers have a "hide model airplanes (OGFLYM)" toggle)

You should not assume only `>APRS` exists.

## 5) Receiver beacons (OGNSDR) and status parsing
Receiver beacons typically look like APRS position reports plus a richer comment string containing station stats.

Examples seen in public "valid messages" sets include:
- Position-style receiver beacon:
  ```
  LILH>OGNSDR,TCPIP*,qAC,GLIDERN2:/132201h4457.61NI00900.58E&/A=000423
  ```
- Receiver status beacon with metrics:
  ```
  LKHE>OGNSDR,qAS,glidern5:>164507h CpuLoad=0.10 ... GPS: -0.4 / 6 satellites ...
  ```

Implementation tips:
- Parse receiver beacons just like aircraft beacons:
  - TNC2 header -> source/dest/path
  - APRS payload -> timestamp/pos/symbol/alt
  - The remaining comment line is a free-form metrics string. Parse selectively with regexes, keep the full raw comment as fallback.

## 6) Device identification & privacy: OGN Device Database (DDB)
OGN provides a device database (DDB) to map device IDs to aircraft metadata and privacy flags.

### 6.1 DDB download endpoint
From the DDB project documentation, `/download` can return either:
- CSV-ish (quoted fields, `#` comments), or
- JSON with `j=1`

Key fields include:
- `device_type` (regex `^[FIO]$`)
- `device_id` (6 hex chars)
- `aircraft_model`, `registration`, `cn`
- `tracked` (`Y/N`)
- `identified` (`Y/N`)
- optional `aircraft_type` (enable with `t=1`)

Example:
- All devices as JSON:
  - `https://ddb.glidernet.org/download/?j=1`
- Only some devices:
  - `https://ddb.glidernet.org/download/?j=1&device_id=DDDEAD,484D20`

### 6.2 How to use DDB in XCPro
Suggested approach:
- Maintain an in-memory cache keyed by `device_id` (6 hex).
- Refresh periodically (e.g., every few hours) or on-demand for unknown IDs.
- Respect privacy:
  - If `tracked == 'N'`: do not show track history / do not re-broadcast.
  - If `identified == 'N'`: do not display tail number / registration / CN, even if you know it.

## 7) Kotlin implementation notes (XCPro)
### 7.1 Networking
- Use a background coroutine (Dispatchers.IO).
- `Socket(host, port)` with:
  - connect timeout
  - `tcpNoDelay = true`
  - read timeout (optional)
- Read bytes and split on `\n` (be tolerant of `\r\n`).
- Ignore lines starting with `#`.

**Charset:** APRS-IS can contain non-UTF-8 bytes; safest is to treat input as bytes and decode with ISO-8859-1 (lossless 1:1 mapping) when needed.

### 7.2 Reconnect and keepalive
- If `readLine()` returns null or throws, reconnect with exponential backoff.
- Consider sending a periodic keepalive/comment line only if required by server (usually reading is enough because the server sends keepalive `#` lines).

### 7.3 Parsing architecture
Make parsing stages explicit:
1. `parseTnc2(line)` -> `src`, `dst`, `path[]`, `info`
2. `parseAprsInfo(info)` -> typed payload
3. `parseOgnComment(comment)` -> map of known tokens + raw/extra tokens
4. Merge -> `OgnEvent` model (aircraft/receiver/etc)

### 7.4 Skeleton Kotlin pseudocode
```kotlin
val host = "aprs.glidernet.org"
val port = 14580

val socket = Socket()
socket.tcpNoDelay = true
socket.connect(InetSocketAddress(host, port), 10_000)

val reader = socket.getInputStream().bufferedReader(Charsets.ISO_8859_1)
val writer = socket.getOutputStream().bufferedWriter(Charsets.ISO_8859_1)

// Wait for server greeting lines (optional: just start reading)
val login = "user N0CALL pass -1 vers XCPro 1.0 filter r/-33.8688/151.2093/250"
writer.write(login)
writer.write("\r\n")
writer.flush()

while (true) {
    val line = reader.readLine() ?: break
    if (line.startsWith("#")) continue
    val pkt = parseTnc2(line)            // src/dst/path/info
    val aprs = parseAprsInfo(pkt.info)   // position/status/message/object...
    val ogn = parseOgnComment(aprs.commentOrNull)
    emitEvent(pkt, aprs, ogn)
}
```

## 8) Quick test plan
- Start with a small range filter over an active area.
- Log raw lines to file for later regression tests.
- Build unit tests for:
  - TNC2 parsing edge cases (no path, multiple commas, malformed lines)
  - APRS position formats you actually see (`/` + timestamp, `!` no timestamp)
  - OGN token parsing:
    - id token present/absent
    - missing altitude
    - missing course/speed
    - weird ordering of tokens

## 9) Reference URLs (for the agent)
OGN & usage:
- https://www.glidernet.org/ogn-data-usage/
- https://github.com/glidernet/ogn-aprs-protocol
- https://github.com/glidernet/python-ogn-client

APRS-IS protocol:
- https://www.aprs-is.net/connecting.aspx
- https://www.aprs-is.net/javAPRSFilter.aspx

OGN Device Database:
- https://github.com/glidernet/ogn-ddb
- https://ddb.glidernet.org/download/?j=1


