# Garmin GLO 2 Note

Research date: 2026-04-11

## Purpose

This note records what Garmin GLO 2 appears to do over Bluetooth, what XCPro
currently does with Bluetooth and GPS ownership, and what future implementation
work should preserve.

This is a repo note, not a claim that GLO 2 support already exists in XCPro.

## Short Answer

When connected to a phone, Garmin GLO 2 acts like an external Bluetooth Classic
GNSS receiver. The useful product-level behavior is:

- the device connects over Bluetooth Classic
- the blue LED goes solid when connected to the phone
- the green LED flashes while searching for satellites
- the green LED goes solid when it has a GPS fix
- the device streams NMEA position data over the Bluetooth serial link
- Garmin documents a 10 Hz update rate, with the note that not all mobile
  devices expose all 10 updates per second

For XCPro UI, the most useful top-of-screen string is the connected device
identity plus status, for example:

- `Active GPS: Garmin GLO 2 #12345`
- `Status: Connected`
- `Fix: GPS fix acquired`
- `Stream: NMEA alive (RMC/GGA)`

Do not use a raw live NMEA sentence as the primary header text. It changes
rapidly and contains live coordinates and time.

## Confirmed From Official Garmin Sources

Official Garmin docs support these points:

- GLO / GLO 2 use Bluetooth Classic.
- The unit pairs to a mobile device over Bluetooth.
- Blue LED states:
  - slow flash = searching for mobile devices
  - rapid flash = pairing
  - solid blue = connected to mobile device
- Status LED states:
  - flashing green = searching for GPS satellites
  - solid green = GPS satellite fix
- The device turns off automatically after several minutes if it does not
  establish a Bluetooth connection.
- Garmin documents a 10 Hz update rate for GLO 2.
- Garmin support also describes GLO-family position data as NMEA data streamed
  over a serial link.

## Observed Or Inferred Details

These points are useful but are not all directly stated in official Garmin
documentation:

- Likely paired device name:
  - a secondary pairing guide shows the name as `Garmin GLO 2 #12345`
  - treat that as an observed example, not a hardcoded guarantee
- Likely NMEA sentence family:
  - older GLO-family captures show continuous `GPRMC` and `GPGGA`
  - the same captures also show `GPVTG`
  - periodic satellite/status sentences include `GPGSA` and `GPGSV`
- Parser tolerance:
  - a safe XCPro parser should not hardcode only `GP` talker IDs
  - future support should tolerate `GPxxx` and `GNxxx` style talker prefixes
    when matching sentence families

These are reasonable implementation inferences, but they should still be
verified on real hardware before hardcoding UI or parser assumptions.

## Example Observed NMEA Lines

These lines are from a secondary GLO-family capture, not from official Garmin
documentation and not guaranteed to be byte-for-byte identical on every GLO 2:

```text
$GPRMC,230655.9,A,3520.14407,N,13924.42820,E,000.03,344.7,241115,007.0,W,A*28
$GPGGA,230655.9,3520.14407,N,13924.42820,E,1,20,0.6,18.7,M,35.5,M,,*68
$GPVTG,344.7,T,351.8,M,000.03,N,0000.05,K,A*1E
$GPGSA,A,3,11,01,08,84,72,92,30,74,19,32,83,28,1.0,0.6,0.8*3E
$GPGSV,6,1,21,50,00,000,00,11,41,067,28,01,49,041,40,08,17,114,40*75
```

For XCPro planning, the initial parser target should stay narrow:

- required first: `GGA`, `RMC`
- optional later: `VTG`, `GSA`, `GSV`

## Current XCPro Repo Status On 2026-04-11

GLO 2 is not implemented today.

What exists now:

- General settings still expose a single `Bluetooth Vario` entry.
- Bluetooth transport and LXNAV Bluetooth runtime currently live under
  `feature:variometer`.
- The existing Bluetooth parser/runtime is LX-specific.
- The current live GPS truth seam is phone GPS via `UnifiedSensorManager`.
- `feature:flight-runtime` consumes GPS through `SensorDataSource.gpsFlow`.
- `feature:map` is still the owner of live phone sensor ingestion.

What does not exist yet:

- no Garmin GLO 2 Bluetooth runtime
- no GLO 2 NMEA parser
- no external GNSS arbitration policy
- no GPS input-mode screen for `PHONE_ONLY`, `EXTERNAL_ONLY`,
  `AUTO_PREFER_EXTERNAL`
- no role-based multi-device Bluetooth ownership

## Current Code Seams To Read Before Implementing

These files are the main current anchors:

- `app/src/main/java/com/trust3/xcpro/appshell/settings/GeneralSettingsCategoryGrid.kt`
  - current General settings entry still says `Bluetooth Vario`
- `feature/profile/src/main/java/com/trust3/xcpro/screens/navdrawer/BluetoothVarioSettingsScreen.kt`
  - current top-of-screen `Selected` and `Active` device labels
- `feature/profile/src/main/java/com/trust3/xcpro/screens/navdrawer/BluetoothVarioSettingsUseCase.kt`
  - current Bluetooth settings UI text mapping
- `feature/variometer/src/main/java/com/trust3/xcpro/variometer/bluetooth/AndroidBluetoothTransport.kt`
  - current Bluetooth Classic RFCOMM transport using the SPP UUID
- `feature/variometer/src/main/java/com/trust3/xcpro/variometer/bluetooth/NmeaLineFramer.kt`
  - reusable ASCII newline-delimited line framing
- `feature/variometer/src/main/java/com/trust3/xcpro/variometer/bluetooth/lxnav/LxSentenceParser.kt`
  - current LX-only parser
- `feature/variometer/src/test/java/com/trust3/xcpro/variometer/bluetooth/lxnav/runtime/LxExternalRuntimeRepositoryTest.kt`
  - shows that `GPRMC` is currently ignored by the LX runtime path
- `feature/map/src/main/java/com/trust3/xcpro/sensors/UnifiedSensorManager.kt`
  - current phone GPS producer
- `feature/flight-runtime/src/main/java/com/trust3/xcpro/sensors/SensorDataSource.kt`
  - current GPS read seam into flight runtime

## Ownership Guidance For Future Work

Important repo boundary reminder:

- GLO 2 Bluetooth runtime should not be owned by `feature:map`
- map should stay a consumer
- flight truth should stay in `feature:flight-runtime`
- UI and ViewModel should not own Bluetooth socket lifecycle

As of this research pass, the repo still has Bluetooth transport/runtime under
`feature:variometer`, but that is a current-state fact, not a recommendation
that future external-device ownership must stay there forever.

If future work adds GLO 2, the implementation should preserve:

- one fused flight pipeline only
- one authoritative GPS read seam into `feature:flight-runtime`
- explicit GNSS arbitration policy instead of blind phone+external merge
- role-based device ownership rather than one global active Bluetooth device

## Recommended UI Text When GLO 2 Is Connected

Best top-of-screen summary:

- `Active GPS: <bonded Bluetooth display name>`
- `Status: Connecting` / `Connected` / `Disconnected`
- `Fix: Searching satellites` / `GPS fix acquired`
- `Stream: NMEA alive (RMC/GGA)` or `Waiting for first sentence`

Do not use:

- the full raw NMEA sentence as the primary title
- vendor/protocol noise as the only status
- a single app-global `active Bluetooth device` concept

If protocol visibility is wanted, show a small diagnostic line such as:

- `Last sentence: RMC`
- `NMEA alive`
- `10 Hz source detected`

## Recommended Initial Parser Contract

For future GLO 2 support:

- transport:
  - Bluetooth Classic RFCOMM / SPP
- parser:
  - newline-delimited NMEA lines
  - tolerate partial lines
  - tolerate malformed lines
  - tolerate unsupported sentence types
  - parser should classify, not decide final source truth
- initial sentences:
  - `GGA`
  - `RMC`
- later optional:
  - `VTG`
  - `GSA`
  - `GSV`

`GGA` and `RMC` are enough for the first useful external GNSS slice.

## Source Quality Notes

Use these source tiers:

- highest confidence:
  - official Garmin manual and support pages
- medium confidence:
  - secondary guides describing pairing name or UI behavior
- lower confidence:
  - older GLO-family capture logs used only to guide parser tolerance

Do not hardcode the exact paired Bluetooth name format without checking a real
device first.

## Sources

Official Garmin:

- GLO 2 instructions PDF:
  - `https://static.garmin.com/pumac/GLO_2_Instruc_Web_ML.pdf`
- Garmin support: GLO series Bluetooth version:
  - `https://support.garmin.com/en-IN/?faq=wAJs9UQuKf90wPiJ8a3Vi7`
- Garmin support: NMEA data streamed over serial link:
  - `https://support.garmin.com/en-US/aviation/faq/mkuJnIsr4A0d3ug27OUJa9/`
- Garmin support: GLO series 10 Hz note:
  - `https://support.garmin.com/en-PH/?faq=8iZtul4NVO4RLCB5bWioIA`

Secondary references used only for implementation guidance:

- observed pairing-name example:
  - `https://eu.nvcharts.com/31618/garmin-glo-2-gps-receiver-with-bluetooth-connection/`
- older GLO-family capture and sentence examples:
  - `https://4river.a.la9.jp/gps/report/GLO.htm`

## Practical Takeaway

If a future Codex session asks "what does Garmin GLO 2 do when connected?",
the concise repo-local answer is:

- it connects as a Bluetooth Classic external GNSS receiver
- it reports connection and GPS-fix state through LEDs
- it streams NMEA location data
- XCPro does not consume that stream yet
- the right XCPro UI header is the connected device name plus GPS/fix/stream
  status, not a raw NMEA sentence
