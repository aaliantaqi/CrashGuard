package com.example.CrashGuard;

import android.app.*;
import android.content.*;
import android.hardware.*;
import android.os.*;
import android.content.Context;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import android.provider.Settings;
import android.widget.Toast;

public abstract class SensorService extends Service implements SensorEventListener {

    private static final String TAG = "SensorService";
    private PowerManager.WakeLock wakeLock;
    private static final int NOTIFICATION_ID = 7;
    private static final int ALERT_NOTIFICATION_ID = 8;
    private static final String CHANNEL_ID = "crashguard_sensor";
    private static final String ALERT_CHANNEL_ID = "crashguard_alert";
    private static final long TRIGGER_COOLDOWN_MS = 10_000L;
    private static final String PREFS_NAME = "crashguard_prefs";
    private static final String PREF_TRACKING = "tracking";

    private boolean isAlertRunning = false;
    private boolean isForegroundStarted = false;

    private Sensor accelerometer;
    private Sensor gyroscope;
    private SensorManager mSensorManager;
    private AccidentDetector accidentDetector;
    private boolean hasGyro = false;

    private long lastTriggeredAtMs = 0L;
    private boolean isTracking = true;
    private boolean sensorsRegistered = false;
    private long lastSensorLogAtMs = 0L;


    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        createAlertChannel();

        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (mSensorManager != null) {
            accelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            gyroscope     = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
            hasGyro       = (gyroscope != null);
        }

        accidentDetector = new AccidentDetector(hasGyro, this::triggerAccidentFlow);
        Log.d(TAG, "onCreate: hasAccel=" + (accelerometer != null) + ", hasGyro=" + hasGyro);

        // Show gyro toast only once
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean gyroToastShown = prefs.getBoolean("gyro_toast_shown", false);
        if (!gyroToastShown) {
            prefs.edit().putBoolean("gyro_toast_shown", true).apply();
            new Handler(Looper.getMainLooper()).post(() -> {
                if (getApplicationContext() == null) return;
                Toast.makeText(getApplicationContext(),
                        hasGyro ? "Gyroscope Detected"
                                : "No Gyroscope",
                        Toast.LENGTH_LONG).show();
            });
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "CrashGuard Tracking",
                    // ✅ FIX 3 — LOW for tracking, not HIGH
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Background accident detection service");
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private void createAlertChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel alertChannel = new NotificationChannel(
                    ALERT_CHANNEL_ID,
                    "CrashGuard Accident Alert",
                    NotificationManager.IMPORTANCE_HIGH
            );
            alertChannel.setDescription("Shows emergency alert when accident detected");
            alertChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            alertChannel.enableVibration(true);
            alertChannel.enableLights(true);
            // ✅ Allow this channel to bypass DND for emergencies
            alertChannel.setBypassDnd(true);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(alertChannel);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mSensorManager != null) mSensorManager.unregisterListener(this);
        releaseWakeLock();
        sensorsRegistered = false;
        Log.d(TAG, "onDestroy: sensors unregistered");
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!isTracking) return;
        long now = SystemClock.elapsedRealtime();
        if (now - lastSensorLogAtMs > 5000L) {
            lastSensorLogAtMs = now;
            Log.d(TAG, "onSensorChanged: type=" + event.sensor.getType());
        }
        if (accidentDetector != null) accidentDetector.onSensorChanged(event);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private void registerSensor() {
        if (sensorsRegistered) {
            Log.d(TAG, "registerSensor: already registered");
            return;
        }
        if (mSensorManager != null && accelerometer != null) {

            // ✅ KEY FIX — maxReportLatencyUs = 0 forces immediate delivery
            // even when screen is off. Without this, Android batches sensor
            // events and delivers them late or not at all on lock screen.
            mSensorManager.registerListener(this, accelerometer,
                    SensorManager.SENSOR_DELAY_GAME, 0);

            if (gyroscope != null) {
                mSensorManager.registerListener(this, gyroscope,
                        SensorManager.SENSOR_DELAY_GAME, 0);
            }
            sensorsRegistered = true;
            Log.d(TAG, "registerSensor: registered (gyro=" + (gyroscope != null) + ")");
        } else {
            Log.d(TAG, "registerSensor: FAILED");
        }
    }

    private void unregisterSensor() {
        if (mSensorManager != null) mSensorManager.unregisterListener(this);
        sensorsRegistered = false;
        Log.d(TAG, "unregisterSensor: done");
    }

    private void sendStateToUI(boolean state) {
        Intent intent = new Intent("TRACKING_STATE");
        intent.putExtra("state", state);
        sendBroadcast(intent);
    }

    private boolean readSavedTrackingState() {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean(PREF_TRACKING, false);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        final String action = (intent != null) ? intent.getAction() : null;
        Log.d(TAG, "onStartCommand: action=" + action);

        if (action == null) {
            isTracking = readSavedTrackingState();
            ensureForegroundNotification();
            if (isTracking) registerSensor(); else unregisterSensor();
            sendStateToUI(isTracking);
            return START_STICKY;
        }

        switch (action) {
            case "ACTION_START":
                isTracking = true;
                saveState(true);
                ensureForegroundNotification();
                registerSensor();
                acquireWakeLock(); // ✅ hold CPU awake during tracking
                sendStateToUI(true);
                break;

            case "ACTION_STOP":
                isTracking = false;
                saveState(false);
                ensureForegroundNotification();
                unregisterSensor();
                releaseWakeLock(); // ✅ release when stopped
                cancelAlertNotification();
                sendStateToUI(false);
                break;

            case "ACTION_CLOSE":
                isTracking = false;
                saveState(false);
                unregisterSensor();
                releaseWakeLock(); // ✅ release
                cancelAlertNotification();
                stopForeground(true);
                isForegroundStarted = false;
                stopSelf();
                sendStateToUI(false);
                break;

            case "ACTION_RESET_ALERT":
                isAlertRunning = false;
                cancelAlertNotification();
                break;
        }
        return START_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        boolean tracking = readSavedTrackingState();
        if (tracking) {
            isTracking = true;
            unregisterSensor();
            registerSensor();
            ensureForegroundNotification();
            Intent restartIntent = new Intent(getApplicationContext(), SensorService.class);
            restartIntent.setAction("ACTION_START");
            startService(restartIntent);
        }
    }

    private void saveState(boolean state) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putBoolean(PREF_TRACKING, state).apply();
    }

    private Notification buildNotification() {
        Intent closeIntent = new Intent(this, SensorService.class);
        closeIntent.setAction("ACTION_CLOSE");
        PendingIntent closePending = PendingIntent.getService(this, 2, closeIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Intent openApp = new Intent(this, DashboardActivity.class);
        PendingIntent openPending = PendingIntent.getActivity(this, 0, openApp,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Intent actionIntent = new Intent(this, SensorService.class);
        actionIntent.setAction(isTracking ? "ACTION_STOP" : "ACTION_START");
        int actionRequestCode = isTracking ? 11 : 10;
        PendingIntent actionPending = PendingIntent.getService(this, actionRequestCode, actionIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.alarm)
                .setContentTitle("CrashGuard Monitoring")
                .setContentText(isTracking ? "Tracking Active" : "Tracking Paused")
                .setContentIntent(openPending)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .addAction(0, isTracking ? "Stop" : "Start", actionPending)
                .addAction(0, "Close", closePending)
                .build();
    }

    private void ensureForegroundNotification() {
        Notification n = buildNotification();
        if (!isForegroundStarted) {
            startForeground(NOTIFICATION_ID, n);
            isForegroundStarted = true;
            return;
        }
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIFICATION_ID, n);
    }

    private void showFullScreenAlert() {
        Intent fullScreenIntent = new Intent(this, SendSMSActivity.class);
        fullScreenIntent.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK |
                        Intent.FLAG_ACTIVITY_CLEAR_TOP |
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
        );
        PendingIntent fullScreenPendingIntent = PendingIntent.getActivity(
                this, 99, fullScreenIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Notification alertNotification = new NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
                .setSmallIcon(R.drawable.alarm)
                .setContentTitle("🚨 Accident Detected!")
                .setContentText("Emergency Alert")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(false)
                .setOngoing(true)
                // ✅ This is the KEY line — triggers full screen intent
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .setContentIntent(fullScreenPendingIntent)
                .build();

        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(ALERT_NOTIFICATION_ID, alertNotification);
    }

    private void cancelAlertNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(ALERT_NOTIFICATION_ID);
    }

    private void triggerAccidentFlow() {
        long now = SystemClock.elapsedRealtime();
        if (now - lastTriggeredAtMs < TRIGGER_COOLDOWN_MS || isAlertRunning) return;

        lastTriggeredAtMs = now;
        isAlertRunning = true;

        // ✅ FIX 1 — PARTIAL_WAKE_LOCK (not deprecated, keeps CPU alive)
        acquireWakeLock();

        // ✅ FIX 2 — Show notification AND try startActivity together
        // Notification handles lock screen. startActivity handles foreground case.
        // Both are instant — no delay needed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && Settings.canDrawOverlays(this)) {
            // Has overlay permission — launch directly
            Intent intent = new Intent(this, SendSMSActivity.class);
            intent.setFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK |
                            Intent.FLAG_ACTIVITY_CLEAR_TOP |
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
            );
            startActivity(intent);
        }

        // ✅ ALWAYS show full screen notification too — works on lock screen
        // even if startActivity is blocked by Android
        showFullScreenAlert();

        // ✅ Safety reset after 30 seconds
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            isAlertRunning = false;
            releaseWakeLock();
        }, 30_000L);
    }

    // ✅ FIX 1 — PARTIAL_WAKE_LOCK keeps CPU alive without screen
    // Screen waking is handled by setTurnScreenOn() in SendSMSActivity
    private void acquireWakeLock() {
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm == null) return;
            releaseWakeLock();
            wakeLock = pm.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "CrashGuard::EmergencyWakeLock"
            );
            wakeLock.acquire(4 * 60 * 60 * 1000L); // 4 hours max
            Log.d(TAG, "WakeLock acquired");
        } catch (Exception e) {
            Log.e(TAG, "WakeLock acquire failed: " + e.getMessage());
        }
    }

    private void releaseWakeLock() {
        try {
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
                Log.d(TAG, "WakeLock released");
            }
            wakeLock = null;
        } catch (Exception e) {
            Log.e(TAG, "WakeLock release failed: " + e.getMessage());
        }
    }
}