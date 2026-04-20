# References

These are the source references behind this pack.

## 1) XCSoar user manual
**Why it matters:** confirms XCSoar is intended to connect a smartphone device to a PC flight simulator that outputs NMEA, and that the TCP Port setting is useful for Condor.

- Title: `XCSoar User Manual`
- URL: `https://download.xcsoar.org/releases/7.43/XCSoar-manual.pdf`

Relevant points:
- smartphone device connected to a PC flight simulator outputting NMEA
- suitable simulators include Condor
- TCP Port setting is useful to connect to Condor

## 2) Condor manual: using Condor with an external flight computer
**Why it matters:** documents the exact HW VSP3 + XCSoar workflow, including use of the phone IP, TCP port 4353, and the Condor driver.

- Title: `Condor Soaring Simulator Version 3 User Guide`
- URL: `https://static1.squarespace.com/static/61deff61d06db05354560a40/t/6720fb1b8f66ae284fce041b/1730214688737/Condor%2B3%2Bmanual_en.pdf`

Relevant points:
- install HW VSP3
- same Wi-Fi network
- note XCSoar device IP
- XCSoar Device A -> TCP Port -> 4353 -> Driver: Condor Soaring Simulator
- select Condor driver for correct altitude readings
- configure HW VSP3 with the phone/device IP and matching port
- configure Condor NMEA output to the created virtual COM port

## 3) Condor official site: NMEA output
**Why it matters:** confirms the sentence set exposed by Condor.

- Title: `About Condor`
- URL: `https://www.condorsoaring.com/about/`

Relevant points:
- real-time NMEA output through serial port
- `GPGGA`, `GPRMC`, and `LXWP0` are supported

Also visible on:
- Title: `V3 Discover – Condor Soaring`
- URL: `https://www.condorsoaring.com/v3discover/`

## 4) HW VSP3 product page
**Why it matters:** confirms HW VSP3 is a virtual COM to TCP/IP bridge.

- Title: `HW VSP3 - Virtual Serial Port`
- URL: `https://www.hw-group.com/software/hw-vsp3-virtual-serial-port`

Relevant point:
- adds a virtual serial port and redirects data from that port via TCP/IP

## 5) XCSoar Condor driver source
**Why it matters:** shows the actual Condor-specific semantics XCSoar applies.

- Title: `XCSoar/src/Device/Driver/Condor.cpp`
- URL: `https://raw.githubusercontent.com/XCSoar/XCSoar/master/src/Device/Driver/Condor.cpp`

Relevant points:
- dedicated driver name: `Condor Soaring Simulator`
- parses `LXWP0`
- uses baro altitude, true airspeed, TE vario, and external wind
- comments state Condor uses TAS in the airspeed field
- comments state Condor 1.1.4 / Condor 2 output wind direction “to”, so XCSoar applies reciprocal wind correction

## 6) XCSoar LX parser source
**Why it matters:** useful contrast showing the generic LX path differs from the Condor path.

- Title: `XCSoar/src/Device/Driver/LX/Parser.cpp`
- URL: `https://raw.githubusercontent.com/XCSoar/XCSoar/master/src/Device/Driver/LX/Parser.cpp`

Relevant point:
- generic LX handling differs from the dedicated Condor handling, reinforcing that Condor should not be treated as generic LX/NMEA if the target is parity

## 7) Android networking permissions
**Why it matters:** confirms the baseline Android manifest permissions needed for socket operations.

- Title: `Connect to the network | Android Developers`
- URL: `https://developer.android.com/develop/connectivity/network-ops/connecting`

Relevant points:
- manifest should include `android.permission.INTERNET`
- manifest should include `android.permission.ACCESS_NETWORK_STATE`
- network operations must not run on the main thread

## 8) Android local-network watchlist
**Why it matters:** future-proofing for local-LAN TCP connectivity.

- Title: `Local network permission | Android Developers`
- URL: `https://developer.android.com/privacy-and-security/local-network-permission`

Relevant points:
- local network protections are being introduced
- accepting an incoming TCP connection on a local network is part of the impacted operations
- Android 17 and target SDK 37+ will require local-network permission handling for affected apps

## Practical interpretation

The source set above is enough to justify the core implementation decisions:

1. XCPro needs **incoming TCP listen mode** on the phone.
2. XCPro must support the **Condor sentence mix**, not just plain GPS.
3. XCPro needs **Condor-specific normalization** if the target is XCSoar-equivalent behavior.
4. XCPro should remain **bridge-agnostic** on the PC side.
5. The feature should be implemented as a **simulator-owned runtime source**, not a map hack.
