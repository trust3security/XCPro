## Wind / IAS / TAS Notes

## Airspeed Definitions

XCPro does not measure Indicated Airspeed (IAS) directly on the phone because no pitot/static pressure data is available. Airspeed-dependent calculations therefore use a True Airspeed (TAS) proxy derived from GPS ground speed corrected by the wind estimator when wind is available, with a GPS-ground fallback when wind is not available.

This TAS proxy is suitable for:
- netto calculation,
- polar sink subtraction,
- wind-aware glide and performance metrics.

It must **not** be used for:
- stall margin assessment,
- maneuvering speed,
- overspeed or any airframe limitation calculations.

### Known Gaps / Next Steps
- Wind EKF still needs **real** TAS/IAS input (external vario). Phone-derived TAS must not feed EKF.
- Add external/manual wind override selection (EXTERNAL > MANUAL; AUTO only if newer than manual).
- Replay currently auto-navigates back when it starts; future polish could offer a dedicated "View on Map" action instead.
 - Implementation plan for future airspeed wiring: `docs/Cards/TAS-IAS-Wiring-Plan.md`.

### Implemented Since This Note
- G-load gating for EKF is live (raw accelerometer magnitude + smoothing).
- Wind-based TAS proxy uses wind-to vector math (air = ground - wind).
- EKF gating now requires updated GPS + airspeed samples and uses VTakeoff fallback 10 m/s.
- Replay now parses IAS/TAS from IGC I-record extensions and emits airspeed for replay fusion.
