> NOTICE (2026-02-06): Task refactor plan is documented in $plan. Review before implementing task-related changes.

# LXNAV NMEA Sentence Examples (Real-World)

This document contains **example NMEA strings** produced by LXNAV devices
(Hawk / S100 / S8x family) as seen in XCSoar logs, Condor LX emulation, user captures,
and XCSoar code comments (format references).

These examples are intended for:
- Parser implementation
- Unit testing
- Validation against XCSoar behavior

All strings below are representative of what you will actually see in the field.

---

## 1. `$LXWP0` -- Primary Flight Data

### Canonical / very common
```
$LXWP0,Y,0.1,504.7,0.00,,,,,,134,351,19.5*71
```

### Zero IAS, level flight
```
$LXWP0,Y,0.0,1234.5,0.00,,,,,,182,270,15.4*6A
```

### Climbing
```
$LXWP0,Y,85.3,812.2,2.45,,,,,,095,310,22.0*5F
```

### Sinking
```
$LXWP0,Y,92.1,765.8,-1.73,,,,,,101,298,18.6*43
```

### Missing checksum (observed in the wild)
```
$LXWP0,Y,88.4,654.1,1.12,,,,,,090,250,12.3
```

### Extra empty fields (tolerant parser case)
```
$LXWP0,Y,0.2,512.4,1.35,,,,,,,,182,270,21.3*6A
```

### Condor LX emulation example
```
$LXWP0,Y,0.1,998.6,0.85,,,,,,180,360,10.0*52
```

---

## 2. `$LXWP1` -- Device / Configuration / Info

### Common minimal form
```
$LXWP1,1,1,0,0,0,0,0*4E
```

### Variant
```
$LXWP1,1,0,1,0,0,0,1*48
```

### Without checksum
```
$LXWP1,1,1,1,0,0,0,0
```

Notes:
- Sent infrequently (often ~once per minute)
- Not flight-critical
- Should never block calculations
- XCSoar's LX driver expects LXWP1 fields as strings:
  product, serial, software version, hardware version, license.
Source: `C:\Users\Asus\AndroidStudioProjects\XCSoar\src\Device\Driver\LX\Parser.cpp:65`, `C:\Users\Asus\AndroidStudioProjects\XCSoar\src\NMEA\DeviceInfo.hpp:13`

---

## 3. `$GPRMC` -- GPS Position, Speed, Track

### Typical valid fix
```
$GPRMC,123519,A,4807.038,N,01131.000,E,22.4,84.4,230394,003.1,W*6A
```

### Invalid fix
```
$GPRMC,092751,V,,,,,,,230394,,,N*53
```

Notes:
- Speed is in **knots**
- Track is **degrees true**

---

## 4. `$GPGGA` -- GPS Fix Quality & Altitude

### Valid fix
```
$GPGGA,123520,4807.038,N,01131.000,E,1,08,0.9,545.4,M,46.9,M,,*47
```

### Poor / no fix
```
$GPGGA,123521,4807.038,N,01131.000,E,0,00,99.9,,,,,,*48
```

Notes:
- Altitude is in **meters**
- Often redundant if baro altitude comes from `$LXWP0`

---

## 5. Other Sentences (Ignore Safely)

Some setups may emit additional LX proprietary sentences:

```
$LXWP2,...
$LXWP3,...
```

XCSoar safely ignores these unless explicitly supported.

---

## 5A. XCSoar Code-Comment Examples (Reference)

These examples are taken from XCSoar source comments and are intended as
format references. Some omit checksums or use placeholders.

### `$LXWP0` (LX driver comment)
```
$LXWP0,Y,222.3,1665.5,1.71,,,,,,239,174,10.1
```
Source: `C:\Users\Asus\AndroidStudioProjects\XCSoar\src\Device\Driver\LX\Parser.cpp:19`

### `$LXWP0` subset (XCTracer LXWP0 mode)
```
$LXWP0,N,,119.9,0.16,,,,,,259,,*64
```
Source: `C:\Users\Asus\AndroidStudioProjects\XCSoar\src\Device\Driver\XCTracer\Parser.cpp:22`

### `$PLXVF` (LXNAV sVario example, checksum placeholder)
```
$PLXVF,,1.00,0.87,-0.12,-0.25,90.2,244.3,*CS
```
Source: `C:\Users\Asus\AndroidStudioProjects\XCSoar\src\Device\Driver\LX\Parser.cpp:226`

### `$PLXVS` (LXNAV sVario example, checksum placeholder)
```
$PLXVS,23.1,0,12.3,*CS
```
Source: `C:\Users\Asus\AndroidStudioProjects\XCSoar\src\Device\Driver\LX\Parser.cpp:268`

Notes:
- `*CS` indicates a checksum placeholder in the original comment.
- Use these for parser shape only, not as validated captures.

---

## 6. Parser Rules (XCSoar-Compatible)

To match XCSoar robustness:

1. Accept ASCII NMEA over Bluetooth SPP
2. Buffer until newline
3. Drop line if:
   - It does not start with `$`
   - A checksum is present and invalid
4. Split by comma
5. Never assume fields are populated
6. Parse only required fields
7. Ignore unknown sentences silently

---

## Final Note

These examples are sufficient to:
- Build a tolerant parser
- Write unit tests
- Implement autonomous Codex loops
- Match real LXNAV behavior without vendor specs


