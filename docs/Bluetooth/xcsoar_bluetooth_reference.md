# XCSoar Bluetooth Reference (Code Map)

This document is a code-level reference for the XCSoar Bluetooth and LXNAV
pipeline. It exists so future agents can find the exact files quickly and
avoid assumptions.

Source tree location: `C:\Users\Asus\AndroidStudioProjects\XCSoar`

Scope:
- Android Bluetooth discovery and transport
- JNI bridge into native code
- NMEA line splitting and checksum enforcement
- LXNAV (LX) driver parsing and device detection
- Port configuration and reconnect behavior

How to use this reference:
- Start with the Quick file index to locate entry points fast.
- Read the "Native data path" section to understand byte flow and parsing.
- Confirm port types and openers before changing Bluetooth behavior.
- Keep changes aligned with the LX driver sentences listed here.

## Quick file index (XCSoar)

Use this list to jump straight to the relevant code:

- `android/src/BluetoothHelper.java`: Bluetooth adapter, permissions, bonded devices, BLE scan, RFCOMM socket creation.
- `android/src/BluetoothClientPort.java`: async RFCOMM connect, hands off to `BluetoothPort`.
- `android/src/BluetoothPort.java`: RFCOMM InputStream/OutputStream wiring (serial byte stream).
- `android/src/InputThread.java` and `android/src/OutputThread.java`: read/write threads for RFCOMM stream.
- `android/src/HM10Port.java` and `android/src/HM10WriteBuffer.java`: BLE HM10 serial-like port.
- `android/src/BluetoothSensor.java`: BLE sensor GATT decoding (non-stream).
- `android/src/BluetoothUuids.java`: BLE service/characteristic UUIDs and scan filters.
- `src/Android/BluetoothHelper.{hpp,cpp}`: JNI wrapper to Java BluetoothHelper.
- `src/Android/PortBridge.{hpp,cpp}`: JNI bridge for Java AndroidPort -> native Port.
- `src/Device/Port/AndroidPort.{hpp,cpp}`: native Port implementation backed by AndroidPort.
- `src/Device/Descriptor.{hpp,cpp}`: port open, data flow, line handling, device parsing.
- `src/Device/Util/LineSplitter.{hpp,cpp}`: NMEA line splitting + sanitization.
- `src/NMEA/Checksum.{hpp,cpp}`: NMEA checksum calculation/validation.
- `src/Device/Parser.{hpp,cpp}`: generic NMEA parser for standard sentences.
- `src/Device/Driver/LX/*`: LXNAV driver (LXWP/PLXV parsing, NMEA setup).
- `src/Device/Port/ConfiguredPort.cpp`: port type -> openers (RFCOMM, BLE_HM10).
- `src/Device/Config.{hpp,cpp}`: port types, Bluetooth MAC handling, availability logic.
- `src/Dialogs/Device/PortPicker.cpp`: UI discovery list (RFCOMM vs BLE).

Suggested search terms:
- `LXWP0`, `LXWP1`, `PLXV0`, `PLXVC`
- `RFCOMM`, `BLE_HM10`, `BluetoothHelper`, `PortBridge`

## Android device discovery and port selection

- Discovery is UI-driven in `src/Dialogs/Device/PortPicker.cpp`.
- Classic Bluetooth devices are listed from bonded devices in
  `android/src/BluetoothHelper.java`.
- BLE scan results are filtered to known service UUIDs in
  `android/src/BluetoothUuids.java`, then mapped to port types in
  `PortPicker.cpp`:
  - HM10 -> `BLE_HM10`
  - Others -> `BLE_SENSOR`

## Android Bluetooth transport implementations

### Classic RFCOMM (SPP) stream

- RFCOMM uses the standard SPP UUID
  `00001101-0000-1000-8000-00805F9B34FB` in
  `android/src/BluetoothHelper.java`.
- Connection flow: `BluetoothClientPort` -> `BluetoothPort`
  (`android/src/BluetoothClientPort.java`, `android/src/BluetoothPort.java`).
- I/O is a raw byte stream via `InputThread` and `OutputThread`
  (`android/src/InputThread.java`, `android/src/OutputThread.java`).

### BLE HM10 (serial-like GATT stream)

- Implemented in `android/src/HM10Port.java` with write buffering in
  `android/src/HM10WriteBuffer.java`.
- Provides a bidirectional byte stream via GATT notifications and writes.

### BLE Sensor (non-stream)

- Implemented in `android/src/BluetoothSensor.java`.
- Decodes known BLE characteristics and forwards values to `SensorListener`;
  it does NOT provide a raw NMEA stream.

## JNI bridge into native (Android)

- Java `InputListener` delivers raw bytes to JNI in
  `android/src/NativeInputListener.java` and `src/Android/NativeInputListener.cpp`.
- `PortBridge` wraps the Java `AndroidPort` and is used by native `AndroidPort`
  (`src/Android/PortBridge.cpp`, `src/Device/Port/AndroidPort.cpp`).

## Native data path (byte stream -> NMEA lines)

### Raw bytes

- `AndroidPort` is a `BufferedPort` (`src/Device/Port/AndroidPort.cpp`,
  `src/Device/Port/BufferedPort.cpp`).
- Bytes are forwarded to `DeviceDescriptor::DataReceived()` which uses
  `PortLineSplitter` unless the driver uses raw data
  (`src/Device/Descriptor.cpp`, `src/Device/Util/LineSplitter.cpp`).

### Line splitting and sanitization

- Lines are split by newline, CR is stripped, control chars are replaced
  with spaces, and NUL bytes are skipped
  (`src/Device/Util/LineSplitter.cpp`, `src/util/TextFile.hxx`).

### Checksum enforcement

- Both the LX driver and generic `NMEAParser` require valid checksums
  (`src/Device/Driver/LX/Parser.cpp`, `src/Device/Parser.cpp`,
  `src/NMEA/Checksum.cpp`).

## LXNAV driver parsing and device detection

- Driver registration: `src/Device/Driver/LX/Register.cpp` (driver name "LX").
- Parsing: `src/Device/Driver/LX/Parser.cpp`.
  - `$LXWP0`: pressure altitude, TE vario, true airspeed, external wind.
  - `$LXWP1`: device info; product name is used to detect V7/S8x/NINC/NANO/1600/1606.
  - `$LXWP2`: MacCready, ballast, bugs, volume.
  - `$LXWP3`: altitude offset -> QNH.
  - `$PLXV0`: LXNAV vario settings map.
  - `$PLXVC`: Nano settings and forwarded Nano info.
  - `$PLXVF` / `$PLXVS`: sVario acceleration, vario, IAS, altitude, OAT, voltage.
- Device flags and pass-through logic are in `src/Device/Driver/LX/Internal.hpp`.

## LXNAV NMEA mode setup and command mode

- `LXDevice::EnableNMEA()` uses LXNAV vario commands and sets NMEA rates
  (`src/Device/Driver/LX/Mode.cpp`, `src/Device/Driver/LX/LXNAVVario.hpp`).
  - NMEA rate command: `PLXV0,NMEARATE,W,2,5,1,60,30,0,0`.
- Nano info is requested after setup (`src/Device/Driver/LX/NanoProtocol.hpp`).
- Logger/command mode uses binary LX protocol (SYN/ACK/CRC)
  (`src/Device/Driver/LX/Protocol.hpp`, `src/Device/Driver/LX/Logger.cpp`).

## Port configuration and selection

- Port types are defined in `src/Device/Config.hpp`:
  `RFCOMM`, `BLE_HM10`, `BLE_SENSOR`, `RFCOMM_SERVER`, etc.
- Port opening is in `src/Device/Port/ConfiguredPort.cpp`:
  - `RFCOMM` -> `OpenAndroidBluetoothPort`
  - `BLE_HM10` -> `OpenAndroidBleHm10Port`
  - `BLE_SENSOR` -> `OpenBluetoothSensor` via `DeviceDescriptor`.
- `DeviceConfig::BluetoothNameStartsWith()` is used to pre-identify
  LXNAV Nano devices on Android (`src/Device/Config.cpp`).

## Error handling and reconnect behavior

- Port state changes propagate from Java to native via `PortBridge`
  (`src/Android/PortBridge.cpp`, `android/src/BluetoothClientPort.java`).
- `DeviceDescriptor::OnSysTicker()` detects failures and triggers reopen
  (`src/Device/Descriptor.cpp`).
- For RFCOMM/BLE, `DeviceConfig::ShouldReopenOnTimeout()` returns false;
  recovery is driven by explicit port failure, not idle timeout
  (`src/Device/Config.cpp`).

## NMEA output to the device

- Outbound commands use `PortWriteNMEA()` which prepends `$` and appends
  checksum + CRLF (`src/Device/Util/NMEAWriter.cpp`).
