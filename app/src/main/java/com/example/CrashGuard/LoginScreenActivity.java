package com.example.CrashGuard;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class LoginScreenActivity extends AppCompatActivity {

    private EditText editTextEmail, editTextPassword;
    private LinearLayout contentLayout;
    private ProgressBar progressBar;
    private TextView textViewResendVerification;

    private FirebaseAuth firebaseAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login_screen);

        contentLayout = findViewById(R.id.contentLayout);
        editTextEmail    = findViewById(R.id.editTextEmail);
        editTextPassword = findViewById(R.id.editTextPassword);
        textViewResendVerification = findViewById(R.id.textViewResendVerification);

        progressBar      = findViewById(R.id.progressBar);
        progressBar.setVisibility(View.GONE);

        firebaseAuth = FirebaseAuth.getInstance();

        // ✅ Already logged in AND verified → go to Dashboard
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (currentUser != null && currentUser.isEmailVerified()) {
            startActivity(new Intent(this, DashboardActivity.class));
            finish();
            return;
        }

        boolean justSignedUp = getIntent().getBooleanExtra("justSignedUp", false);
        if (justSignedUp) {
            textViewResendVerification.setVisibility(View.VISIBLE);

            String signupEmail = getIntent().getStringExtra("signupEmail");
            if (signupEmail != null) {
                editTextEmail.setText(signupEmail);
            }
        }

        // ── Spannable text for register redirect ─────────────────────────
        TextView registerText = findViewById(R.id.textViewRegisterRedirect);
        String text = "New user? Create your profile";
        SpannableString spannable = new SpannableString(text);

        spannable.setSpan(
                new ForegroundColorSpan(Color.GRAY),
                0, 9,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );
        spannable.setSpan(
                new ForegroundColorSpan(Color.parseColor("#FF3B30")),
                10, text.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );
        registerText.setText(spannable);
    }

    public void login(View view) {
        String email    = editTextEmail.getText().toString().trim();
        String password = editTextPassword.getText().toString().trim();

        if (TextUtils.isEmpty(email)) {
            Toast.makeText(this, "No Email Entered", Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(password)) {
            Toast.makeText(this, "No Password Entered", Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        contentLayout.setVisibility(View.GONE);

        firebaseAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {

                    progressBar.setVisibility(View.GONE);
                    contentLayout.setVisibility(View.VISIBLE);

                    if (task.isSuccessful()) {

                        FirebaseUser user = firebaseAuth.getCurrentUser();

                        // ✅ Email verified check
                        if (user != null && !user.isEmailVerified()) {
                            firebaseAuth.signOut();
                            textViewResendVerification.setVisibility(View.VISIBLE);
                            Toast.makeText(this,
                                    "Please verify your email first. Check your inbox.",
                                    Toast.LENGTH_LONG).show();
                            return;
                        }
                        // ✅ Verified — go to Dashboard
                        Toast.makeText(this, "Logged In!", Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(this, DashboardActivity.class));
                        finish();

                    } else {
                        // ✅ Login failed
                        Exception e = task.getException();
                        if (e != null) {
                            Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
                            e.printStackTrace();
                        }
                    }
                });
    }

    // ✅ Resend verification email
    public void resendVerification(View view) {
        String email    = editTextEmail.getText().toString().trim();
        String password = editTextPassword.getText().toString().trim();

        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            Toast.makeText(this,
                    "Enter your email and password first, then tap Resend.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        // Sign in silently to get the user object, then send verification
        firebaseAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = firebaseAuth.getCurrentUser();
                        if (user != null && !user.isEmailVerified()) {
                            user.sendEmailVerification()
                                    .addOnCompleteListener(verifyTask -> {
                                        firebaseAuth.signOut(); // sign out again
                                        if (verifyTask.isSuccessful()) {
                                            Toast.makeText(this,
                                                    "Verification email sent! Check your inbox.",
                                                    Toast.LENGTH_LONG).show();
                                        } else {
                                            Toast.makeText(this,
                                                    "Failed to send. Try again later.",
                                                    Toast.LENGTH_SHORT).show();
                                        }
                                    });
                        } else {
                            Toast.makeText(this,
                                    "Email is already verified. Please login.",
                                    Toast.LENGTH_SHORT).show();
                            firebaseAuth.signOut();
                        }
                    } else {
                        Toast.makeText(this,
                                "Invalid credentials. Cannot resend.",
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    public void forgotPassword(View view) {
        String email = editTextEmail.getText().toString().trim();

        if (TextUtils.isEmpty(email)) {
            Toast.makeText(this, "Enter Your Email First", Toast.LENGTH_SHORT).show();
            return;
        }

        firebaseAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(this, "Password Reset Email Sent", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Failed to Send Reset Email", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    public void goToRegister(View view) {
        startActivity(new Intent(this, SignUpActivity.class));
    }
}