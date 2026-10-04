package com.example.CrashGuard;

import android.Manifest;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.Toast;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.List;

public class DashboardActivity extends AppCompatActivity {

    private final int MY_PERMISSION_REQUEST_CODE = 1;
    private PermissionHandler mPermissionHandler;
    private FirebaseAuth firebaseAuth;
    private FirebaseUser firebaseUser;
    private boolean isTracking;
    private Button buttonToggleTracking;
    private DBEmergency mDatabase;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        // Ask the user to allow "Display over other apps" so our alert screen can always show
        // my app suppoprts andoird 8 or above so this actually needs this block of code
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Intent overlayIntent = new Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())
                );
                startActivity(overlayIntent);
            }
        }

        // ✅ Add in DashboardActivity onCreate
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String packageName = getPackageName();
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(packageName)) {
                Intent intent = new Intent();
                intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + packageName));
                startActivity(intent);
            }
        }

        mPermissionHandler = new PermissionHandler(this);
        firebaseAuth = FirebaseAuth.getInstance();
        buttonToggleTracking = findViewById(R.id.buttonToggleTracking);
        mDatabase = new DBEmergency(this);

        setupFirebase();
        wireDashboardCards();

        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // ✅ FIX 1 — read actual saved state, do NOT hardcode false
        SharedPreferences prefs = getSharedPreferences("crashguard_prefs", MODE_PRIVATE);
        boolean savedState = prefs.getBoolean("tracking", false);
        updateUIButton(savedState);
    }
    private void wireDashboardCards() {
        MaterialCardView cardContacts = findViewById(R.id.cardContacts);
        MaterialCardView cardAccount  = findViewById(R.id.cardAccount);
        MaterialCardView cardLiveShare = findViewById(R.id.cardLiveShare);

        if (cardContacts != null) {
            cardContacts.setOnClickListener(v -> {
                v.startAnimation(AnimationUtils.loadAnimation(this, R.anim.press_scale));
                startActivity(new Intent(this, MyEmerContActivity.class));
            });
        }
        if (cardAccount != null) {
            cardAccount.setOnClickListener(v -> {
                v.startAnimation(AnimationUtils.loadAnimation(this, R.anim.press_scale));
                startActivity(new Intent(this, MyAccount.class));
            });
        }
        if (cardLiveShare != null) {
            cardLiveShare.setOnClickListener(v -> {
                v.startAnimation(AnimationUtils.loadAnimation(this, R.anim.press_scale));
                startActivity(new Intent(this, LiveLocationSharingActivity.class));
            });
        }
    }

    private void updateUIButton(boolean tracking) {
        isTracking = tracking;
        if (tracking) {
            buttonToggleTracking.setBackgroundResource(R.drawable.button_tracking_stop);
            buttonToggleTracking.setText("Stop Tracking");
        } else {
            buttonToggleTracking.setBackgroundResource(R.drawable.button_tracking_start);
            buttonToggleTracking.setText("Start Tracking");
        }
    }

    private BroadcastReceiver trackingReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean state = intent.getBooleanExtra("state", false);
            updateUIButton(state);
        }
    };

    @Override
    protected void onResume() {
        super.onResume();

        ContextCompat.registerReceiver(
                this,
                trackingReceiver,
                new IntentFilter("TRACKING_STATE"),
                ContextCompat.RECEIVER_NOT_EXPORTED
        );

        // ✅ Always sync UI from SharedPreferences on resume
        SharedPreferences prefs = getSharedPreferences("crashguard_prefs", MODE_PRIVATE);
        boolean savedState = prefs.getBoolean("tracking", false);
        updateUIButton(savedState);
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            unregisterReceiver(trackingReceiver);
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        }
    }

    public void startTrackingWrapper(View view) {

        if (isTracking) {
            toggleTracking();
            return;
        }

        if (!hasContact()) {
            Toast.makeText(this,
                    "Please add at least 1 emergency contact first",
                    Toast.LENGTH_LONG).show();
            return;
        }



        List<String> permissionName = new ArrayList<>();
        List<String> permissionTag  = new ArrayList<>();

        permissionName.add(Manifest.permission.ACCESS_FINE_LOCATION);
        permissionName.add(Manifest.permission.SEND_SMS);
        permissionTag.add("Access Location");
        permissionTag.add("Send SMS");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionName.add(Manifest.permission.POST_NOTIFICATIONS);
            permissionTag.add("Post Notifications");
        }

        boolean permissionsOk = mPermissionHandler.requestPermissions(
                MY_PERMISSION_REQUEST_CODE, permissionName, permissionTag);

        if (!permissionsOk) return;

        if (!locationServicesStatusCheck()) return;

        toggleTracking();
    }

    private void toggleTracking() {
        Intent intent = new Intent(this, SensorService.class);
        intent.setAction(isTracking ? "ACTION_STOP" : "ACTION_START");
        ContextCompat.startForegroundService(this, intent);
    }

    private void setupFirebase() {
        firebaseAuth = FirebaseAuth.getInstance();
        firebaseUser = firebaseAuth.getCurrentUser();
        if (firebaseUser == null) {
            finish();
            startActivity(new Intent(this, LoginScreenActivity.class));
        }
    }
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
//        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
//        if (requestCode == SMS_PERMISSION_CODE) {
//            attemptSend(false); // retry now that the user has answered the prompt
//        }

        if (requestCode == MY_PERMISSION_REQUEST_CODE) {
            // ✅ FIX 2 — also check contact and GPS before starting
            if (mPermissionHandler.handleRequestResult(
                    requestCode, permissions, grantResults)) {
                if (hasContact() && locationServicesStatusCheck()) {
                    toggleTracking();
                }
            }
        } else {
            super.onRequestPermissionsResult(
                    requestCode, permissions, grantResults);
        }
    }

    private boolean locationServicesStatusCheck() {
        LocationManager lm =
                (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (lm != null &&
                lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) return true;

        new AlertDialog.Builder(this)
                .setTitle("Enable GPS")
                .setMessage("This function needs your GPS, do you want to enable it now?")
                .setIcon(android.R.drawable.ic_menu_mylocation)
                .setCancelable(false)
                .setPositiveButton("Yes", (d, w) ->
                        startActivity(new Intent(
                                Settings.ACTION_LOCATION_SOURCE_SETTINGS)))
                .show();

        return false;
    }

    private boolean hasContact() {
        if (firebaseUser == null || firebaseUser.getEmail() == null) return false;

        List<EmerContact> contacts = mDatabase.getContact(firebaseUser.getEmail());
        if (contacts == null || contacts.isEmpty()) return false;

        for (EmerContact c : contacts) {
            if (c != null
                    && c.getName() != null && !c.getName().trim().isEmpty()
                    && c.getPhone() != null && !c.getPhone().trim().isEmpty()) {
                return true;
            }
        }
        return false;
    }
}