# How the working XCSoar path works

## The real data path

Your working setup is this:

```text
Condor on Windows PC
-> Condor NMEA output enabled
-> virtual COM port selected in Condor
-> HW VSP3 reads / exposes that virtual COM port
-> HW VSP3 forwards the bytes over TCP to the phone IP + port
-> XCSoar on Android listens on that TCP port
-> XCSoar parses the incoming NMEA / Condor feed
-> XCSoar updates ownship / nav state as if it were flying live
```

## Why this was needed

Condor outputs simulator navigation data like a serial/NMEA source.

The phone cannot directly open a Windows COM port. So something on the PC has to turn:

```text
Windows serial bytes
```

into:

```text
network bytes the phone can receive
```

HW VSP3 is doing that bridging job.

## What HW VSP3 is doing

In this use case, HW VSP3 is not the “flight computer”. It is just the bridge.

Conceptually it does this:

```text
COM5 on Windows <-> TCP connection to phone-ip:4353
```

So the Condor NMEA feed that would normally come out of a serial port is forwarded over local Wi-Fi/TCP instead.

## Roles on each side

### PC side
Owns:
- Condor simulator
- NMEA serial output
- COM-to-TCP bridge
- initiating the TCP connection toward the phone

### Phone side
Owns:
- listening TCP endpoint
- NMEA/Condor decoding
- flight-computer behavior

## Direction matters

This is not the phone dialing into the PC.

In the documented XCSoar + HW VSP3 setup, the **PC bridge is configured with the phone’s IP address and port**. That means the phone app is the side that must be reachable and ready to accept the incoming connection.

For XCPro, this means:

- the phone must expose a listening TCP socket
- the port must be configurable
- the app should show the current local IP address clearly

## Why port 4353 keeps showing up

`4353` is just a convenient port choice used in the documented XCSoar setup. It is not magical.

XCPro should:
- default to `4353`
- allow the user to change it
- show the effective bind state in the UI

## What is and is not part of XCPro

### XCPro should own
- TCP listen mode on Android
- stream lifecycle
- line buffering
- checksum validation
- Condor sentence parsing
- source selection / simulator mode activation
- connection state UX
- feeding normalized simulator ownship data into flight-runtime

### XCPro should not own
- Windows virtual COM creation
- HW VSP3 installation
- PC firewall configuration
- Condor-side COM routing

Those are external setup concerns.

## What XCPro must emulate from XCSoar

To behave like the working XCSoar setup, XCPro needs a mode that is effectively:

```text
Device source: TCP Port
Port: 4353
Parser / driver: Condor
```

That means the UI needs to let the user:

- enable Condor TCP mode
- choose the listen port
- see the phone IP address
- start listening
- see whether a client is connected
- see whether valid Condor data is arriving

## Recommended XCPro runtime UX

The app should expose a simple state machine like this:

```text
Disabled
Listening
Connected / waiting for valid sentences
Receiving Condor data
Stale / timed out
Disconnected
Error
```

## Failure modes XCPro must surface

### No connection ever arrives
Usually:
- wrong phone IP
- wrong port
- PC and phone not on same LAN
- firewall blocks the traffic
- XCPro not listening yet

### TCP connects but no valid flight data
Usually:
- wrong Condor COM port
- Condor NMEA output disabled
- malformed bridge setup
- parser only expects generic NMEA and misses Condor semantics

### Position moves but altitude / wind / vario look wrong
Usually:
- generic NMEA path only
- LXWP0 not parsed
- Condor-specific corrections not applied
- phone sensors still interfering with simulator authority

## What this means for implementation

The working XCSoar path is not evidence that XCPro needs a giant networking subsystem.

It is evidence that XCPro needs a **small, robust, simulator-owned incoming TCP/NMEA intake path** with **Condor-specific semantics** and **clean runtime source ownership**.
