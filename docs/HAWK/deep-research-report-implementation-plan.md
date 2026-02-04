Here’s the blunt recommendation: ship “HAWK‑like” without adding raw gyro and without replay IMU.
Use what XCPro already has (earth‑frame vertical accel + attitude + baro) and build the “fast but not spiky” behavior with baro‑gated fusion + aggressive outlier handling + adaptive trust in accel. Add gyro/replay IMU later only if you prove you need it.

Why I’m confident that’s the right call (based on your findings)

No gyro feed: adding it forces you to widen the core sensor contract + plumbing + testing. That’s a big blast radius for a feature that might not materially improve vario feel because…

You already have attitude + earth‑frame vertical acceleration via OrientationProcessor / AccelData.verticalAcceleration with isReliable. That’s the key ingredient for a HAWK‑style “quick response without false lift spikes.”

Replay has no IMU. Any “replay IMU” you invent will lie to you and will absolutely poison tuning.

XCPro’s vario loop is baro‑gated. That’s exactly how you avoid “IMU‑only rocket spikes” and it matches the feel you’re trying to replicate.

So: don’t boil the ocean. Build the HAWK feel inside the constraints of the existing pipeline first.

What you should do (practical plan)
Phase 1 (MVP that can genuinely feel “HAWK‑inspired”)

Goal: fast, stable, low false‑lift spikes with current sensors.

1) Make a dedicated HAWK vario engine that is baro‑clocked

Trigger the filter only on new baro samples (like you noted is “non‑negotiable”).

Treat accel as a helper input that can speed response, not as a driver that can create lift on its own.

Key behavior:
If baro didn’t change, vario shouldn’t jump just because accel twitched.

2) Use the existing earth‑frame vertical acceleration and gate it hard

Consume AccelData.verticalAcceleration

Only use it when isReliable == true

When it’s unreliable or stale: fall back to baro‑only mode.

This single decision is a massive part of “gust spike suppression.”

3) Implement “gust filtering” as adaptive distrust of accel (not as fancy wind estimation)

Most “false lift spikes” on phones come from turbulence/handling/rotation artifacts in accel and small pressure transients. A HAWK‑like feel comes from not believing accel too much when the world is messy.

Concrete mechanism (works well in practice):

Maintain a short rolling window of vertical accel (e.g. last 300–700 ms).

Compute something like aRms or variance.

Map that to an adaptive accel noise value:

When accel is smooth → trust it more (faster response).
When accel is chattery → trust it less (stability, fewer spikes).

So gusts get “filtered” because they increase accel variance, which automatically turns accel’s influence down.

4) Use a small Kalman filter (or complementary filter) that fuses baro + accel

You don’t need a huge EKF. A simple 2‑state filter works:

State:

h = altitude (or pressure-altitude)

v = vertical speed

Model update at each baro tick:

Predict using accel as input (when reliable)

Correct using baro altitude

The gust suppression happens via:

adaptive accel noise (described above)

baro innovation gating (reject pressure blips)

no IMU-only update (baro-gated loop)

5) Handle QNH jumps cleanly (or avoid them)

Your note is important: TE resets on QNH jumps; HAWK must not explode when someone changes QNH.

Best approach for a vario engine:

Drive the filter from raw pressure / pressure altitude with a fixed reference (QNE) for vario physics

Let display altitude do whatever it wants with QNH, but keep the vario filter decoupled

If you must use a QNH-coupled altitude stream, then implement:

Detect step changes (big instantaneous altitude delta with no physical acceleration support)

Reset / re-seed the filter when that happens

6) Integrate it where it will actually run

You already found the truth: VarioServiceManager owns lifecycle.
So:

Start/stop HAWK pipeline there (or via a parallel manager invoked there)

Don’t bury it somewhere that dies when UI sleeps

7) Add a real settings/flag surface (don’t ship “magic”)

You need:

A feature flag: HAWK_ENABLED

A small config model: response feel, sink/lift smoothing, accel trust baseline, etc.

Even if you keep UI minimal, you need a way to tune without code changes.

8) Reuse existing wind fusion (don’t duplicate)

Your existing WindSensorFusionRepository already does circling wind + confidence decay. Great.
Do not entangle wind into gust filtering in v1. Keep HAWK focused: vertical channel quality.

Phase 1.5 (Make tuning possible without lying to yourself)

This is the part most teams skip and then wonder why it never matches the “feel.”

Add a “truthful replay” path for tuning

Since replay has no IMU:

In replay mode: run baro-only and label it as such (internally via confidence)

For real tuning/regression: create a separate sensor log format captured during live flights:

baro pressure + timestamp

verticalAcceleration + reliability + timestamp

attitude (optional)

GPS (optional)

Then you can replay real IMU later without inventing physics.

You already have hawk_vario_logs*.txt. Lean into that: make it a first-class regression input.

Should you add gyro now?

No. Not for a HAWK‑like vario feel.

Add raw gyro only if you hit one of these:

isReliable is false too often in real use (magnetic interference / phone placement) and the accel gating causes too much baro-only sluggishness

You want to replace Android’s rotation vector with your own attitude estimation (bigger project)

You want consistent behavior across weird devices whose rotation vector implementation is bad

Otherwise, gyro is complexity without guaranteed payoff.

If you do add gyro later: make it optional, and don’t make the HAWK engine depend on it.

Should you add replay IMU now?

Also no.

IGC replay won’t have it.

Synthetic IMU derived from GPS/pressure is basically “make up data that feels plausible,” and you’ll tune against nonsense.

Do replay IMU only if you control the recording format and actually recorded IMU live.

Exactly what I’d implement into XCPro next (in order)

HawkVarioEngine (new)

Inputs: baro samples, accel samples (+reliability), clock

Output: vario, smoothedVario, confidence, debug metrics (innovation, accelVariance)

Barycentric / Kalman fusion with adaptive accel trust

Adaptive accel noise from accel RMS window

Innovation gating on baro

Baro-gated stepping only

Fallback modes

If accel unreliable → baro-only

If baro missing → hold last output / decay confidence (do not integrate IMU alone)

QNH jump guard

Prefer pressure/QNE inside filter

Otherwise reset on detected step

Service integration

Start/stop inside VarioServiceManager

Respect background execution realities

Preferences + feature flag

Minimal UI, but tunable constants via prefs

Regression harness

Use those existing hawk log files

Add a way to dump state for A/B comparisons vs current vario

The “gust filtering” story you can honestly claim

If you do the above, your marketing claim can be true without hand-waving:

Fast because accel contributes to prediction when it’s clean

Stable because baro is the governor and accel gets downweighted during turbulence

Low false-lift spikes because:

no IMU-only stepping

accel reliability gating

adaptive accel noise in bumps

baro outlier rejection

That’s basically the whole recipe.

If you want, paste the existing vario output interface(s) XCPro expects (the class/type that ultimately feeds audio/UI), and I’ll map exactly where HawkVarioEngine should plug in (what to replace vs wrap) and the cleanest way to A/B test it against the current Levo/TE path without breaking replay.