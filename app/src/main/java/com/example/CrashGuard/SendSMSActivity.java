package com.example.CrashGuard;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.app.KeyguardManager;
import android.os.CountDownTimer;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;

public class SendSMSActivity extends AppCompatActivity {

    private static final int SMS_PERMISSION_CODE = 2001;
    private MediaPlayer alarm;
    private CountDownTimer timer;
    private ProgressBar progressBar;
    private boolean smsSent = false;
    private FirebaseAuth firebaseAuth;
    private FirebaseUser firebaseUser;
    private boolean pendingAutoSend = false; // was the countdown already at 0 when perms were requested?
    private TextView timerText, phoneText, nameText, messageText;
    private ArrayList<EmerContact> contacts = new ArrayList<>();
    private String[] phoneNumbers;
    private String username = "";
    private String bloodGroup = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Show over lock screen and turn screen on
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            );
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
            if (km != null) {
                km.requestDismissKeyguard(this, null);
            }
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_send_sms);

        initViews();
        loadUserFromLocal();

        firebaseAuth = FirebaseAuth.getInstance();
        firebaseUser = firebaseAuth.getCurrentUser();

        loadContacts();
        initFirebase();

        // Blink animation on timer text
        Animation blink = new AlphaAnimation(0.4f, 1.0f);
        blink.setDuration(600);
        blink.setRepeatMode(Animation.REVERSE);
        blink.setRepeatCount(Animation.INFINITE);
        timerText.startAnimation(blink);

    }

    private boolean checkPermissions() {
        boolean sms = ContextCompat.checkSelfPermission(this,
                Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED;

        if (!sms) {
            ActivityCompat.requestPermissions(this,
                    new String[]{ Manifest.permission.SEND_SMS, Manifest.permission.ACCESS_FINE_LOCATION },
                    SMS_PERMISSION_CODE);
            return false;
        }
        return true; // location is optional — attemptSend()/LocationHelper already handle it being absent
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == SMS_PERMISSION_CODE) {
            boolean smsGranted = false, locGranted = false;
            for (int i = 0; i < permissions.length; i++) {
                if (Manifest.permission.SEND_SMS.equals(permissions[i])
                        && grantResults[i] == PackageManager.PERMISSION_GRANTED) smsGranted = true;
                if (Manifest.permission.ACCESS_FINE_LOCATION.equals(permissions[i])
                        && grantResults[i] == PackageManager.PERMISSION_GRANTED) locGranted = true;
            }

            if (smsGranted) {
                // Location is nice-to-have (for the maps link) but SMS is mandatory.
                // Retry the send regardless of location permission — attemptSend()
                // already falls back to buildMessageWithoutLocation() if location fails.
                if (pendingAutoSend) {
                    pendingAutoSend = false;
                    attemptSend(false);
                }
            } else {
                Toast.makeText(this,
                        "SMS permission is required to send the emergency alert!",
                        Toast.LENGTH_LONG).show();
                // Don't leave the user stuck — let them retry manually.
            }
        }
    }

    private void initViews() {
        try {
            alarm = MediaPlayer.create(this, R.raw.alarm_sound);
            if (alarm != null) {
                alarm.setLooping(true);
            }
        } catch (Exception e) {
            alarm = null;
        }

        progressBar = findViewById(R.id.progressBar);
        timerText   = findViewById(R.id.timer);
        phoneText   = findViewById(R.id.phone1);
        nameText    = findViewById(R.id.name1);
        messageText = findViewById(R.id.emergencymessage);
    }

    private void loadUserFromLocal() {
        SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        String first = prefs.getString("firstName", null);
        String last  = prefs.getString("lastName",  null);
        bloodGroup   = prefs.getString("bloodGroup", "");
        if (first != null && last != null) {
            username = first + " " + last;
        }
    }

    private void initFirebase() {
        firebaseAuth = FirebaseAuth.getInstance();
        firebaseUser = firebaseAuth.getCurrentUser();

        if (firebaseUser == null) return;

        DatabaseReference ref = FirebaseDatabase.getInstance()
                .getReference(firebaseUser.getUid());

        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String first = snapshot.child("firstName").getValue(String.class);
                    String last  = snapshot.child("lastName").getValue(String.class);
                    String bg    = snapshot.child("bloodGroup").getValue(String.class);

                    if (first != null && last != null) {
                        username = first + " " + last;
                    } else {
                        loadUserFromLocal();
                    }
                    if (bg != null) bloodGroup = bg;
                } else {
                    loadUserFromLocal();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(SendSMSActivity.this,
                        "Failed to load user", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadContacts() {
        contacts.clear();

        if (firebaseUser == null || firebaseUser.getEmail() == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show();
            return;
        }

        DBEmergency db = new DBEmergency(this);
        contacts.addAll(db.getContact(firebaseUser.getEmail()));

        phoneNumbers = new String[contacts.size()];
        StringBuilder names  = new StringBuilder();
        StringBuilder phones = new StringBuilder();

        for (int i = 0; i < contacts.size(); i++) {
            phoneNumbers[i] = contacts.get(i)._phone;
            names.append(contacts.get(i)._name).append("\n");
            phones.append(contacts.get(i)._phone).append("\n");
        }

        nameText.setText(names.toString());
        phoneText.setText(phones.toString());
        messageText.setText(buildMessageWithoutLocation());
        startTimer();
    }

    private void startTimer() {
        progressBar.setProgress(0);

        timer = new CountDownTimer(20000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int seconds = (int) (millisUntilFinished / 1000);
                timerText.setText("Sending alert in " + seconds + " seconds...");
                int progress = (int) ((20000 - millisUntilFinished) / 200);
                progressBar.setProgress(progress);
                if (alarm != null && !alarm.isPlaying()) alarm.start();
            }

            @Override
            public void onFinish() {
                attemptSend(false);
            }
        };

        timer.start();
    }

    public void cancelAlarm(View view) {
        performCancel();
    }

    private void performCancel() {
        if (alarm != null && alarm.isPlaying()) alarm.stop();
        if (timer != null) timer.cancel();

        Intent serviceIntent = new Intent(this, SensorService.class);
        serviceIntent.setAction("ACTION_STOP");
        ContextCompat.startForegroundService(this, serviceIntent);

        Intent broadcast = new Intent("TRACKING_STATE");
        broadcast.putExtra("state", false);
        sendBroadcast(broadcast);

        Intent dashIntent = new Intent(this, DashboardActivity.class);
        dashIntent.setFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP |
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
        );
        startActivity(dashIntent);
        finish();
    }

    public void sendButtonPress(View view) {
        attemptSend(true);
    }

    private void attemptSend(boolean isManual) {
        if (smsSent) return;
        if (!checkPermissions()) {
            pendingAutoSend = true; // remember we still need to send once granted
            return;
            }
        // ✅ SAFETY FIX: Only include location if internet is available.
        // When offline, getLastLocation() may return a stale cached fix
        // that could send rescuers to the wrong place.
        if (!LocationHelper.isInternetAvailable(this)) {
            // No internet — send SMS immediately without location link
            String message = buildMessageWithoutLocation();
            if (messageText != null) messageText.setText(message);
            sendSMS(message);
            return;
        }

        // Internet is available — fetch fresh GPS then send
        LocationHelper.getOneShotLocation(this, 5000L, location -> {
            String message;
            if (location != null) {
                double lat = location.getLatitude();
                double lng = location.getLongitude();
                String locationLink = "https://maps.google.com/?q=" + lat + "," + lng;
                String hospitalLink = "https://www.google.com/maps/search/hospital/@"
                        + lat + "," + lng + ",15z";
                message = buildMessageWithLocation(locationLink, hospitalLink);
            } else {
                // Internet was on but GPS timed out — still no location link
                message = buildMessageWithoutLocation();
            }

            if (messageText != null) messageText.setText(message);
            sendSMS(message);
        });
    }

    private void sendSMS(String message) {
        if (smsSent) return;

        if (phoneNumbers == null || phoneNumbers.length == 0) { //n this code donest use becoz emregency contact see at start.
            Toast.makeText(this,
                    "Emergency contacts not available", Toast.LENGTH_SHORT).show();
            return;
        }

        smsSent = true;

        if (alarm != null && alarm.isPlaying()) alarm.stop();
        if (timer != null) timer.cancel();

        ArrayList<String> nums = new ArrayList<>();
        for (String n : phoneNumbers) nums.add(n);
        SmsHelper.sendToMany(this, nums, message);

        Toast.makeText(this, "Emergency SMS sent", Toast.LENGTH_LONG).show();

        Intent stopIntent = new Intent(this, SensorService.class);
        stopIntent.setAction("ACTION_CLOSE");
        ContextCompat.startForegroundService(this, stopIntent);

        Intent broadcast = new Intent("TRACKING_STATE");
        broadcast.putExtra("state", false);
        sendBroadcast(broadcast);

        finish();
    }

    private String buildMessageWithLocation(String locationLink, String hospitalLink) {
        return "Accident detected! Please help!\n"
                + "Name: " + username + "\n"
                + "Blood Group: " + (bloodGroup.isEmpty() ? "Not set" : bloodGroup) + "\n"
                + "Location: " + locationLink + "\n\n"
                + "Nearest hospitals:\n" + hospitalLink;
    }

    private String buildMessageWithoutLocation() {
        return "Accident detected! Please Help!\n"
                + "Name: " + username + "\n"
                + "Blood Group: " + (bloodGroup.isEmpty() ? "Not set" : bloodGroup) + "\n"
                + "Location: Not Available";
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (alarm != null) {
            try { alarm.release(); } catch (Exception ignored) {}
            alarm = null;
        }
        // ✅ startService use karo — safer in onDestroy
        try {
            Intent intent = new Intent(this, SensorService.class);
            intent.setAction("ACTION_RESET_ALERT");
            startService(intent);
        } catch (Exception ignored) {}
    }


    @Override
    public void onBackPressed() {
        if (!smsSent) {
            performCancel();
        } else {
            super.onBackPressed();
        }
    }
}