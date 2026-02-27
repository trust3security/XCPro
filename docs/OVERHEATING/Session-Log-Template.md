# Overheating Session Log Template

Use one copy of this template per profiling session.

## Session Metadata

- Date:
- Engineer/Agent:
- Device model:
- Android version:
- Build variant:
- App commit/branch:
- Ambient temperature estimate:
- Battery start %:
- Battery end %:
- Session duration:

## Scenario Definition

- Scenario ID:
- Flight/replay mode:
- Map visible state:
- Audio enabled:
- OGN enabled:
- OGN display mode:
- OGN SCIA enabled:
- OGN thermals enabled:
- ADS-B enabled:
- Weather rain enabled:
- Weather animation enabled:
- SkySight satellite/radar/lightning enabled:

## Instrumentation Used

- Android Studio CPU profiler: yes/no
- Android Studio Energy profiler: yes/no
- Perfetto trace: yes/no
- `dumpsys thermalservice`: yes/no
- `dumpsys gfxinfo`: yes/no
- `dumpsys batterystats`: yes/no

## Key Measurements

- Estimated drain rate (%/hour):
- Thermal status transitions:
- CPU hot threads/process notes:
- GPU/jank notes:
- Memory churn notes:
- UI symptom notes (stutter/lag):

## Findings

1) Finding:
- Evidence:
- Code path(s):
- Confidence:

2) Finding:
- Evidence:
- Code path(s):
- Confidence:

## Interpretation

- Most likely dominant contributor in this session:
- Secondary contributors:
- What changed compared with prior sessions:

## Decisions

- Mitigation selected:
- Mitigation deferred:
- Required follow-up runs:

## Next Actions

1.
2.
3.
