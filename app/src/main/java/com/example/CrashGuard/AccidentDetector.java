package com.example.CrashGuard;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.os.SystemClock;

/**
 * AccidentDetector implements a multi-stage pipeline:
 * - Stage A: Impact detection (accelerometer linear acceleration magnitude spike)
 * - Stage B: Confirmation (gyroscope angular-rate spike if available; otherwise stronger accel pattern)
 * - Stage C: Post-impact inactivity (device becomes relatively still for a short window)
 * This design reduces false positives from walking/pocket motion by requiring multiple conditions
 * and a post-impact stillness phase.
 */
public final class AccidentDetector implements SensorEventListener {

    public interface OnAccidentListener {
        void onAccident();
    }

    // -----------------------------
    // Tuning knobs (adjust if needed)
    // -----------------------------
    private static final boolean DEBUG_LOG = false;

    // Filtering
    private static final float GRAVITY_LP_ALPHA = 0.90f;      // Low-pass gravity estimator
    private static final float SMOOTHING_ALPHA = 0.65f;       // Exponential smoothing for magnitudes

    // Stage A: impact (linear acceleration magnitude, m/s^2)
    private static final float IMPACT_THRESHOLD_WITH_GYRO = 22.0f;
    private static final float IMPACT_THRESHOLD_NO_GYRO = 25.0f;

    // "Jerk" (rate of change of linear magnitude), m/s^3 (approx)
    private static final float JERK_THRESHOLD_NO_GYRO = 120.0f;

    // Stage B: gyro confirmation (angular speed magnitude, rad/s)
    private static final float GYRO_THRESHOLD_RAD_S = 3.5f;
    private static final long GYRO_CONFIRM_WINDOW_MS = 450L;  // gyro spike must occur within this time of impact

    // Stage C: inactivity
    private static final long INACTIVITY_MIN_MS = 2_000L;
    private static final long INACTIVITY_MAX_MS = 5_000L;
    private static final float STILL_LINEAR_THRESHOLD = 1.2f; // m/s^2 (linear accel magnitude after filtering)
    private static final float STILL_GYRO_THRESHOLD = 0.8f;   // rad/s
    private static final long STILL_HOLD_MS = 900L;           // must remain "still" for this long inside inactivity window

    // Anti-repeat guard (separate from service cooldown)
    private static final long INTERNAL_REFRACTORY_MS = 5_000L;

    // -----------------------------
    // State
    // -----------------------------
    private enum State { ARMED, IMPACT_SEEN, CONFIRMED_WAIT_STILL }

    private final OnAccidentListener listener;
    private final boolean hasGyro;

    private State state = State.ARMED;

    // accel filtering
    private final float[] gravity = new float[] {0f, 0f, 0f};
    private final float[] linear = new float[] {0f, 0f, 0f};
    private float smoothLinearMag = 0f;
    private float lastLinearMag = 0f;
    private long lastAccelTsMs = 0L;

    // gyro
    private float smoothGyroMag = 0f;
    private long lastGyroTsMs = 0L;

    // timing
    private long impactAtMs = 0L;
    private long confirmedAtMs = 0L;
    private long stillSinceMs = 0L;
    private long lastFiredAtMs = 0L;

    // derived evidence flags
    private boolean gyroConfirmed = false;
    private boolean accelPatternConfirmed = false;

    public AccidentDetector(boolean hasGyro, OnAccidentListener listener) {
        this.hasGyro = hasGyro;
        this.listener = listener;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event == null || event.sensor == null) return;

        final int type = event.sensor.getType();
        if (type == Sensor.TYPE_ACCELEROMETER) {
            onAccelerometer(event);
        } else if (type == Sensor.TYPE_GYROSCOPE) {
            onGyroscope(event);
        }

        advanceStateMachine();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // no-op
    }

    private void onAccelerometer(SensorEvent event) {
        final float ax = event.values[0];
        final float ay = event.values[1];
        final float az = event.values[2];

        // Low-pass filter to estimate gravity, then high-pass to get linear acceleration.
        gravity[0] = GRAVITY_LP_ALPHA * gravity[0] + (1f - GRAVITY_LP_ALPHA) * ax;
        gravity[1] = GRAVITY_LP_ALPHA * gravity[1] + (1f - GRAVITY_LP_ALPHA) * ay;
        gravity[2] = GRAVITY_LP_ALPHA * gravity[2] + (1f - GRAVITY_LP_ALPHA) * az;

        linear[0] = ax - gravity[0];
        linear[1] = ay - gravity[1];
        linear[2] = az - gravity[2];

        final float linearMag = (float) Math.sqrt(
                linear[0] * linear[0] +
                        linear[1] * linear[1] +
                        linear[2] * linear[2]
        );

        // Smooth magnitude to reduce noise spikes.
        smoothLinearMag = (smoothLinearMag == 0f)
                ? linearMag
                : (SMOOTHING_ALPHA * smoothLinearMag + (1f - SMOOTHING_ALPHA) * linearMag);

        final long nowMs = SystemClock.elapsedRealtime();
        float jerk = 0f;
        if (lastAccelTsMs != 0L) {
            final long dtMs = Math.max(1L, nowMs - lastAccelTsMs);
            jerk = Math.abs((linearMag - lastLinearMag) / (dtMs / 1000f));
        }
        lastAccelTsMs = nowMs;
        lastLinearMag = linearMag;

        if (state == State.ARMED) {
            final float impactThreshold = hasGyro ? IMPACT_THRESHOLD_WITH_GYRO : IMPACT_THRESHOLD_NO_GYRO;
            if (smoothLinearMag >= impactThreshold) {
                impactAtMs = nowMs;
                gyroConfirmed = false;
                accelPatternConfirmed = false;
                stillSinceMs = 0L;
                state = State.IMPACT_SEEN;

                // No-gyro fallback: require a sharper accel pattern (jerk) in addition to a higher impact threshold.
                if (!hasGyro && jerk >= JERK_THRESHOLD_NO_GYRO) {
                    accelPatternConfirmed = true;
                }

                if (DEBUG_LOG) {
                    android.util.Log.d("AccidentDetector",
                            "Impact seen: smoothLin=" + smoothLinearMag + " jerk=" + jerk);
                }
            }
        } else if (state == State.IMPACT_SEEN) {
            // In no-gyro mode we can confirm via a strong short-lived motion pattern within the confirm window.
            if (!hasGyro && !accelPatternConfirmed) {
                final long sinceImpact = nowMs - impactAtMs;
                if (sinceImpact <= GYRO_CONFIRM_WINDOW_MS) {
                    if (jerk >= JERK_THRESHOLD_NO_GYRO) {
                        accelPatternConfirmed = true;
                        if (DEBUG_LOG) {
                            android.util.Log.d("AccidentDetector", "Accel pattern confirmed (no gyro)");
                        }
                    }
                }
            }
        }
    }

    private void onGyroscope(SensorEvent event) {
        // Gyro values are rad/s about each axis.
        final float gx = event.values[0];
        final float gy = event.values[1];
        final float gz = event.values[2];
        final float gyroMag = (float) Math.sqrt(gx * gx + gy * gy + gz * gz);

        smoothGyroMag = (smoothGyroMag == 0f)
                ? gyroMag
                : (SMOOTHING_ALPHA * smoothGyroMag + (1f - SMOOTHING_ALPHA) * gyroMag);

        lastGyroTsMs = SystemClock.elapsedRealtime();

        if (state == State.IMPACT_SEEN) {
            final long nowMs = lastGyroTsMs;
            final long sinceImpact = nowMs - impactAtMs;
            if (sinceImpact >= 0L && sinceImpact <= GYRO_CONFIRM_WINDOW_MS) {
                if (smoothGyroMag >= GYRO_THRESHOLD_RAD_S) {
                    gyroConfirmed = true;
                    if (DEBUG_LOG) {
                        android.util.Log.d("AccidentDetector", "Gyro confirmed: smoothGyro=" + smoothGyroMag);
                    }
                }
            }
        }
    }

    private void advanceStateMachine() {
        final long nowMs = SystemClock.elapsedRealtime();

        // Separate guard from the service-level cooldown: prevents repeated firing in one event burst.
        if (lastFiredAtMs != 0L && (nowMs - lastFiredAtMs) < INTERNAL_REFRACTORY_MS) {
            return;
        }

        switch (state) {
            case ARMED:
                return;

            case IMPACT_SEEN: {
                final long sinceImpact = nowMs - impactAtMs;

                final boolean confirmed = hasGyro ? gyroConfirmed : accelPatternConfirmed;
                if (confirmed) {
                    confirmedAtMs = nowMs;
                    stillSinceMs = 0L;
                    state = State.CONFIRMED_WAIT_STILL;
                    return;
                }

                // If we fail to confirm quickly, reset (likely a bump/pocket movement).
                if (sinceImpact > GYRO_CONFIRM_WINDOW_MS) {
                    resetToArmed();
                }
                return;
            }

            case CONFIRMED_WAIT_STILL: {
                final long sinceConfirm = nowMs - confirmedAtMs;

                // Only evaluate stillness inside the inactivity window.
                if (sinceConfirm < INACTIVITY_MIN_MS) {
                    return;
                }
                if (sinceConfirm > INACTIVITY_MAX_MS) {
                    // Too much ongoing motion: likely not a real accident stop pattern.
                    resetToArmed();
                    return;
                }

                final boolean linStill = smoothLinearMag <= STILL_LINEAR_THRESHOLD;
                final boolean gyroStill = !hasGyro || smoothGyroMag <= STILL_GYRO_THRESHOLD;

                if (linStill && gyroStill) {
                    if (stillSinceMs == 0L) stillSinceMs = nowMs;
                    if ((nowMs - stillSinceMs) >= STILL_HOLD_MS) {
                        fireAccident();
                        resetToArmed();
                    }
                } else {
                    stillSinceMs = 0L; // must be continuously still for hold duration
                }
                return;
            }
        }
    }

    private void fireAccident() {
        lastFiredAtMs = SystemClock.elapsedRealtime();
        if (DEBUG_LOG) {
            android.util.Log.d("AccidentDetector", "ACCIDENT TRIGGERED");
        }
        if (listener != null) listener.onAccident();
    }

    private void resetToArmed() {
        state = State.ARMED;
        impactAtMs = 0L;
        confirmedAtMs = 0L;
        stillSinceMs = 0L;
        gyroConfirmed = false;
        accelPatternConfirmed = false;
    }
}