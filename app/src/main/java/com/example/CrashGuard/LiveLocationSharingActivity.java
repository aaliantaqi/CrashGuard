package com.example.CrashGuard;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.List;

public class LiveLocationSharingActivity extends AppCompatActivity {

    private static final int REQ_CODE = 4101;

    private TextView tvStatus;
    private Spinner spinnerContacts;
    private ArrayList<EmerContact> contactList;
    private Button btnStartStop;
    private FirebaseUser firebaseUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_live_location_sharing);

        spinnerContacts = findViewById(R.id.spinnerContacts);
        tvStatus        = findViewById(R.id.tvLiveStatus);
        btnStartStop    = findViewById(R.id.btnLiveStartStop);

        firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null) {
            finish();
            return;
        }

        loadContacts();

        btnStartStop.setOnClickListener(v -> startSharing());

        updateUi();

        MaterialToolbar toolbar = findViewById(R.id.toolbarLive);
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void loadContacts() {
        DBEmergency db = new DBEmergency(this);
        contactList = new ArrayList<>(db.getContact(firebaseUser.getEmail()));

        List<String> names = new ArrayList<>();
        names.add("Select Contact"); // default hint

        for (EmerContact c : contactList) {
            names.add(c._name);
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                names
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerContacts.setAdapter(adapter);
        spinnerContacts.setSelection(0);
    }

    private boolean hasRequiredPermissions() {
        boolean loc = ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean sms = ContextCompat.checkSelfPermission(this,
                Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED;
        return loc && sms;
    }

    private void requestRequiredPermissions() {
        ActivityCompat.requestPermissions(
                this,
                new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.SEND_SMS
                },
                REQ_CODE
        );
    }

    private void startSharing() {
        if (contactList == null || contactList.isEmpty()) {
            Toast.makeText(this,
                    "Add at least 1 emergency contact first", Toast.LENGTH_SHORT).show();
            return;
        }

        int position = spinnerContacts.getSelectedItemPosition();
        if (position == 0) {
            Toast.makeText(this, "Please select a contact", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!hasRequiredPermissions()) {
            requestRequiredPermissions();
            return;
        }

        // ✅ SAFETY FIX: Live location sharing makes no sense without internet.
        // A stale cached GPS fix could send the wrong location entirely.
        if (!LocationHelper.isInternetAvailable(this)) {
            Toast.makeText(this,
                    "No internet connection. Cannot share location.",
                    Toast.LENGTH_LONG).show();
            tvStatus.setText("Location sharing requires an internet connection.");
            return;
        }

        sendCurrentLocationOnce();
    }

    private void sendToSelectedContact(String message) {
        if (contactList == null || contactList.isEmpty()) {
            Toast.makeText(this, "No contacts available", Toast.LENGTH_SHORT).show();
            return;
        }

        int position = spinnerContacts.getSelectedItemPosition();
        if (position == 0) {
            Toast.makeText(this, "Please select a contact", Toast.LENGTH_SHORT).show();
            return;
        }

        // position - 1 because index 0 is the "Select Contact" hint
        EmerContact selected = contactList.get(position - 1);

        List<String> single = new ArrayList<>();
        single.add(selected._phone);

        SmsHelper.sendToMany(this, single, message);

        tvStatus.setText("Location sent to " + selected._name);
    }

    private void sendCurrentLocationOnce() {
        LocationHelper.getOneShotLocation(this, 8000L, location -> {
            if (location == null) {
                Toast.makeText(this,
                        "Could not get location. Try again.",
                        Toast.LENGTH_SHORT).show();
                tvStatus.setText("Location unavailable. Please try again.");
                return;
            }
            String msg = buildMessage(location);
            sendToSelectedContact(msg);
            Toast.makeText(this, "Location sent", Toast.LENGTH_SHORT).show(); // ← YAHAN ✅
        });
    }

    private String buildMessage(Location location) {
        String link = "https://maps.google.com/?q="
                + location.getLatitude() + "," + location.getLongitude();

        // Accuracy add
        String accuracy = "~" + Math.round(location.getAccuracy()) + " meters";

        return "Live Location Update:\n"
                + link + "\n"
                + "Accuracy: " + accuracy;
    }

    private void updateUi() {
        if (btnStartStop != null) btnStartStop.setText("Send Location");
        if (tvStatus != null) tvStatus.setText("Send your current location instantly");
    }

    public void onBack(View view) {
        finish();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CODE) {
            if (hasRequiredPermissions()) {
                startSharing();
            } else {
                Toast.makeText(this,
                        "Permissions required for live sharing",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }
}