## Wind / IAS / TAS Notes

## Airspeed Definitions

XCPro does not measure Indicated Airspeed (IAS) because no pitot/static pressure data is available. All airspeed-dependent calculations therefore use a True Airspeed (TAS) proxy derived from GPS ground speed corrected by the wind estimator.

This TAS proxy is suitable for:
- netto calculation,
- polar sink subtraction,
- wind-aware glide and performance metrics.

It must **not** be used for:
- stall margin assessment,
- maneuvering speed,
- overspeed or any airframe limitation calculations.

### Known Gaps / Next Steps
- TAS proxy still mirrors ground speed; need XCSoar's polar/density TAS estimator to tighten EKF quality.
- G-load gating is still disabled until we expose accelerometer feeds.
- Replay currently auto-navigates back when it starts; future polish could offer a dedicated "View on Map" action instead.
