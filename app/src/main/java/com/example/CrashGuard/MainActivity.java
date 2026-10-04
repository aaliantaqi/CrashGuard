package com.example.CrashGuard;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class MainActivity extends AppCompatActivity {

    private static final String KEY   = "com.crashguard.secret";
    private static final String STATE = "com.crashguard.state";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Use Light Mode Only
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);

        // If user is already logged in, go directly to Dashboard
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            if (currentUser.isEmailVerified()) {
                startActivity(new Intent(this, DashboardActivity.class));
                finish();
                return;
            } else {
                FirebaseAuth.getInstance().signOut(); // kick out unverified sessions
            }
        }

        // Check if first time or returning user
        SharedPreferences prefs = getSharedPreferences(KEY, MODE_PRIVATE);
        String state = prefs.getString(STATE, null);

        if (state == null) {
            // First time ever opening the app → go to Sign Up
            prefs.edit().putString(STATE, "signUp").apply();
            startActivity(new Intent(this, SignUpActivity.class));
        } else {
            // Returning user who logged out → go to Login
            startActivity(new Intent(this, LoginScreenActivity.class));
        }

        finish();
    }
}