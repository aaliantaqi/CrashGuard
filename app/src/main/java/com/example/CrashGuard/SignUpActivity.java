package com.example.CrashGuard;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.telephony.PhoneNumberFormattingTextWatcher;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.app.DatePickerDialog;

import java.util.Calendar;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class SignUpActivity extends AppCompatActivity {

    private EditText editTextFirstName, editTextLastName, editTextdateOfBirth, editTextPhoneNumber, editTextBloodGroup, editTextEmail, editTextPassword;
    private Button btnPersonal;
    EditText editTextDOB;
    private FirebaseAuth firebaseAuth;
    private DatabaseReference databaseReference;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        TextView loginRedirect = findViewById(R.id.textViewLoginRedirect);
        loginRedirect.setOnClickListener(v -> {
            startActivity(new Intent(SignUpActivity.this, LoginScreenActivity.class));
            finish();
        });

        editTextDOB = findViewById(R.id.editTextDOB);
        editTextDOB.setOnClickListener(v -> showDatePickerDialog());

        editTextFirstName   = findViewById(R.id.editTextFirstName);
        editTextLastName    = findViewById(R.id.editTextLastName);
        editTextdateOfBirth = findViewById(R.id.editTextDOB);
        editTextPhoneNumber = findViewById(R.id.editTextPhoneNumber);
        editTextBloodGroup  = findViewById(R.id.editTextBloodGroup);
        editTextEmail       = findViewById(R.id.editTextEmail);
        editTextPassword    = findViewById(R.id.editTextPassword);
        btnPersonal         = findViewById(R.id.btnPersonal);

        editTextPhoneNumber.addTextChangedListener(new PhoneNumberFormattingTextWatcher());

        firebaseAuth      = FirebaseAuth.getInstance();
        databaseReference = FirebaseDatabase.getInstance().getReference();

        AutoCompleteTextView bloodGroup = findViewById(R.id.editTextBloodGroup);
        String[] bloodGroups = { "A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-" };

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_dropdown_item_1line,
                bloodGroups
        );
        bloodGroup.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) bloodGroup.showDropDown();
        });
        bloodGroup.setAdapter(adapter);
    }

    public void goToHome(View view) {
        String firstName    = editTextFirstName.getText().toString().trim();
        String lastName     = editTextLastName.getText().toString().trim();
        String dateOfBirth  = editTextdateOfBirth.getText().toString().trim();
        String phoneNumber  = editTextPhoneNumber.getText().toString().trim();
        String bloodGroup   = editTextBloodGroup.getText().toString().trim();
        String email        = editTextEmail.getText().toString().trim();
        String password     = editTextPassword.getText().toString().trim();

        // ── Validations ──────────────────────────────────────────────────
        if (TextUtils.isEmpty(firstName)) {
            Toast.makeText(this, "Please enter your first name", Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(lastName)) {
            Toast.makeText(this, "Please enter your last name", Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(dateOfBirth)) {
            Toast.makeText(this, "Please enter your date of birth", Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(phoneNumber)) {
            Toast.makeText(this, "Please enter your phone number", Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(bloodGroup)) {
            Toast.makeText(this, "Please enter your blood group", Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(email) || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Please enter a valid email", Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(password) || password.length() < 8) {
            Toast.makeText(this, "Password must be at least 8 characters", Toast.LENGTH_SHORT).show();
            return;
        }

        btnPersonal.setEnabled(false);
        Toast.makeText(this, "Creating account...", Toast.LENGTH_SHORT).show();

        // ── Create Firebase Account ───────────────────────────────────────
        firebaseAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {

                    if (!task.isSuccessful()) {
                        btnPersonal.setEnabled(true);
                        Exception e = task.getException();
                        Toast.makeText(this,
                                e != null ? e.getMessage() : "Signup failed. Try again.",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    FirebaseUser user = firebaseAuth.getCurrentUser();
                    if (user == null) {
                        btnPersonal.setEnabled(true);
                        Toast.makeText(this, "User creation failed", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // ── Save profile to Firebase DB ───────────────────────
                    UserInformation userInformation = new UserInformation(
                            firstName, lastName, dateOfBirth, phoneNumber, bloodGroup.toUpperCase());

                    databaseReference.child(user.getUid())
                            .setValue(userInformation)
                            .addOnCompleteListener(dbTask -> {

                                btnPersonal.setEnabled(true);

                                if (dbTask.isSuccessful()) {
                                    // Save locally
                                    saveUserLocal(firstName, lastName, phoneNumber, bloodGroup.toUpperCase());

                                    // Send verification email
                                    user.sendEmailVerification()
                                            .addOnCompleteListener(verifyTask -> {
                                                if (verifyTask.isSuccessful()) {
                                                    Toast.makeText(this,
                                                            "Account created! Please verify your email before logging in.",
                                                            Toast.LENGTH_LONG).show();
                                                } else {
                                                    Toast.makeText(this,
                                                            "Account created but verification email failed. Try resending from login.",
                                                            Toast.LENGTH_LONG).show();
                                                }
                                            });

                                    // ✅ Sign out — user must verify email first
                                    firebaseAuth.signOut();

                                    // ✅ Go to Login — NOT Dashboard
                                    Intent intent = new Intent(SignUpActivity.this, LoginScreenActivity.class);
                                    intent.putExtra("justSignedUp", true);
                                    intent.putExtra("signupEmail", email); // optional: prefill email for convenience
                                    startActivity(intent);
                                    finish();

                                } else {
                                    user.delete(); // roll back the orphaned Auth account
                                    Toast.makeText(this, "Failed to save profile. Try again.", Toast.LENGTH_SHORT).show();
                                }
                            });
                });
    }

    private void saveUserLocal(String firstName, String lastName, String phoneNumber, String bloodGroup) {
        SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        prefs.edit()
                .putString("firstName", firstName)
                .putString("lastName", lastName)
                .putString("phoneNumber", phoneNumber)
                .putString("bloodGroup", bloodGroup)
                .apply();
    }

    private void showDatePickerDialog() {
        final Calendar calendar = Calendar.getInstance();

        int defaultYear  = calendar.get(Calendar.YEAR) - 26;
        int defaultMonth = calendar.get(Calendar.MONTH);
        int defaultDay   = calendar.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog datePickerDialog = new DatePickerDialog(this,
                (view, selectedYear, selectedMonth, selectedDay) -> {
                    String dob = selectedDay + "/" + (selectedMonth + 1) + "/" + selectedYear;
                    editTextDOB.setText(dob);
                }, defaultYear, defaultMonth, defaultDay);

        datePickerDialog.getDatePicker().setMaxDate(calendar.getTimeInMillis());

        Calendar minDate = Calendar.getInstance();
        minDate.set(1900, 0, 1);
        datePickerDialog.getDatePicker().setMinDate(minDate.getTimeInMillis());

        datePickerDialog.show();
    }
}