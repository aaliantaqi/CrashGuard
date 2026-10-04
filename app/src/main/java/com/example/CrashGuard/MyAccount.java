package com.example.CrashGuard;
import com.google.android.material.textfield.TextInputLayout;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

import android.telephony.PhoneNumberFormattingTextWatcher;
import android.text.TextUtils;
import android.view.View;
import androidx.activity.OnBackPressedCallback;
import android.content.SharedPreferences;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.app.DatePickerDialog;
import java.util.Calendar;
import androidx.appcompat.widget.Toolbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class MyAccount extends AppCompatActivity {

    private FirebaseAuth firebaseAuth;
    private TextInputLayout bloodGroupLayout;
    private DatabaseReference databaseReference;
    private ProgressBar progressDialog;
    private SharedPreferences sharedPreferences;
    private static final String PREFS_NAME = "UserPrefs";
    String email="";
    private EditText editFirstName, editLastName, editPhoneNumber,editDateOfBirth;
    private AutoCompleteTextView editBloodGroup;
    private Button btnSave;
    private boolean isEditing = false;
    private Button btnEdit;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_myaccount);
        editDateOfBirth = findViewById(R.id.editDateOfBirth);
        bloodGroupLayout = findViewById(R.id.bloodGroupLayout);
        editBloodGroup = findViewById(R.id.editBloodGroup);
        editFirstName = findViewById(R.id.editFirstName);
        editLastName = findViewById(R.id.editLastName);
        editPhoneNumber = findViewById(R.id.editPhoneNumber);
        firebaseAuth = FirebaseAuth.getInstance();
        final FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user == null) {
            finish();
            startActivity(new Intent(this, LoginScreenActivity.class));
        }
        databaseReference = FirebaseDatabase.getInstance().getReference();
        btnEdit = findViewById(R.id.btnEdit);

        btnSave = findViewById(R.id.btnSave);
        editPhoneNumber.addTextChangedListener(new PhoneNumberFormattingTextWatcher());

        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        editBloodGroup = findViewById(R.id.editBloodGroup);

        String[] bloodGroups = {"A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-"};

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_dropdown_item_1line,
                bloodGroups
        );
        editBloodGroup.setAdapter(adapter);
        editBloodGroup.setOnClickListener(v -> {
            if (isEditing) {
                editBloodGroup.showDropDown();
            }
        });
        editBloodGroup.setKeyListener(null);
        editDateOfBirth.setOnClickListener(v -> {

            Calendar calendar = Calendar.getInstance();

            // 👉 Set default DOB (e.g., year 2000)
            int defaultYear = 2000;
            int defaultMonth = calendar.get(Calendar.MONTH);
            int defaultDay = calendar.get(Calendar.DAY_OF_MONTH);

            DatePickerDialog datePickerDialog = new DatePickerDialog(
                    MyAccount.this,
                    (view, selectedYear, selectedMonth, selectedDay) -> {

                        String date = selectedDay + "/" + (selectedMonth + 1) + "/" + selectedYear;
                        editDateOfBirth.setText(date);

                    },
                    defaultYear, defaultMonth, defaultDay
            );

            // ❌ Prevent future dates
            datePickerDialog.getDatePicker().setMaxDate(calendar.getTimeInMillis());

            // ✅ Optional: minimum date (1900)
            Calendar minDate = Calendar.getInstance();
            minDate.set(1900, 0, 1);
            datePickerDialog.getDatePicker().setMinDate(minDate.getTimeInMillis());

            datePickerDialog.show();
        });

        editDateOfBirth.setFocusable(false);
        editDateOfBirth.setClickable(true);
// Load locally saved values
        String firstName = sharedPreferences.getString("firstName", "");
        String lastName = sharedPreferences.getString("lastName", "");
        String phoneNumber = sharedPreferences.getString("phoneNumber", "");
        String dob = sharedPreferences.getString("dateOfBirth", "");
        String blood = sharedPreferences.getString("bloodGroup", "");

// Display immediately
        editFirstName.setText(firstName);
        editLastName.setText(lastName);
        editPhoneNumber.setText(phoneNumber);
        editDateOfBirth.setText(dob);
        editBloodGroup.setText(blood, false);

        setFieldsEditable(false);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        progressDialog = findViewById(R.id.progressBar);
        progressDialog.setVisibility(View.VISIBLE);

        databaseReference.child(user.getUid())
                .addListenerForSingleValueEvent(new com.google.firebase.database.ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            String firstName = snapshot.child("firstName").getValue(String.class);
                            String lastName = snapshot.child("lastName").getValue(String.class);
                            String phone = snapshot.child("phoneNumber").getValue(String.class);
                            String dob = snapshot.child("dateOfBirth").getValue(String.class);
                            String blood = snapshot.child("bloodGroup").getValue(String.class);

                            if (firstName != null) editFirstName.setText(firstName);
                            if (lastName != null) editLastName.setText(lastName);
                            if (phone != null) editPhoneNumber.setText(phone);
                            if (dob != null) editDateOfBirth.setText(dob);
                            if (blood != null) editBloodGroup.setText(blood, false);

                            // Also save fresh data locally
                            SharedPreferences.Editor editor = sharedPreferences.edit();
                            editor.putString("firstName", firstName);
                            editor.putString("lastName", lastName);
                            editor.putString("phoneNumber", phone);
                            editor.putString("dateOfBirth", dob);
                            editor.putString("bloodGroup", blood);

                            editor.apply();

                        } else {
                            Toast.makeText(MyAccount.this, "No Data Found for this account.", Toast.LENGTH_SHORT).show();
                        }

                        progressDialog.setVisibility(View.GONE);
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        progressDialog.setVisibility(View.GONE);
                        Toast.makeText(MyAccount.this, "Failed to load data", Toast.LENGTH_SHORT).show();
                    }
                });
             getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    finish();
            }
        });
    }
    public void goToLogout(View view) {
        FirebaseAuth.getInstance().signOut();

        // Clear user session or shared preferences
        SharedPreferences preferences = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.clear();
        editor.apply();

        // Redirect to login screen
        Intent intent = new Intent(this, LoginScreenActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
    private void setFieldsEditable(boolean enabled) {

        if (enabled) {
            editFirstName.setBackgroundResource(R.drawable.edit_enabled_bg);
            editLastName.setBackgroundResource(R.drawable.edit_enabled_bg);
            editPhoneNumber.setBackgroundResource(R.drawable.edit_enabled_bg);
            editDateOfBirth.setBackgroundResource(R.drawable.edit_enabled_bg);
            bloodGroupLayout.setEndIconMode(TextInputLayout.END_ICON_DROPDOWN_MENU);

        } else {
            editFirstName.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            editLastName.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            editPhoneNumber.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            editDateOfBirth.setBackgroundColor(android.graphics.Color.TRANSPARENT);
//            editBloodGroup.setBackground(null);
            bloodGroupLayout.setEndIconMode(TextInputLayout.END_ICON_NONE);

        }

        editFirstName.setEnabled(enabled);
        editLastName.setEnabled(enabled);
        editPhoneNumber.setEnabled(enabled);
        editDateOfBirth.setEnabled(enabled);
        editDateOfBirth.setClickable(enabled);
        editBloodGroup.setClickable(enabled);
        editBloodGroup.setFocusable(enabled);
        isEditing = enabled;
    }
    public void goToSave(View view) {
        if (!isEditing) {
            Toast.makeText(this, "Press Edit first to make changes", Toast.LENGTH_SHORT).show();
            return;
        }
        String firstName = editFirstName.getText().toString().trim();
        String lastName = editLastName.getText().toString().trim();
        String phoneNumber = editPhoneNumber.getText().toString().trim();
        String dateOfBirth = editDateOfBirth.getText().toString().trim();
        String bloodGroup = editBloodGroup.getText().toString().trim();

        if (TextUtils.isEmpty(firstName)) {
            Toast.makeText(this, "Please enter your first name", Toast.LENGTH_SHORT).show();
            return;
        } else if (TextUtils.isEmpty(lastName)) {
            Toast.makeText(this, "Please enter your last name", Toast.LENGTH_SHORT).show();

            return;
        } else if (TextUtils.isEmpty(phoneNumber)) {
            Toast.makeText(this, "Please enter your phone number name", Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(dateOfBirth)) {
            Toast.makeText(this, "Enter date of birth", Toast.LENGTH_SHORT).show();
            return;
        }

        if (TextUtils.isEmpty(bloodGroup)) {
            Toast.makeText(this, "Enter blood group", Toast.LENGTH_SHORT).show();
            return;
        }

        UserInformation userInformation = new UserInformation(firstName, lastName, dateOfBirth, phoneNumber, bloodGroup);
        FirebaseUser user = firebaseAuth.getCurrentUser();

        if (user != null) {
            progressDialog.setVisibility(View.VISIBLE);
            databaseReference.child(user.getUid()).setValue(userInformation).addOnCompleteListener(task -> {
                progressDialog.setVisibility(View.GONE);
                Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show();
                // Save locally
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString("firstName", firstName);
                editor.putString("lastName", lastName);
                editor.putString("phoneNumber", phoneNumber);
                editor.putString("dateOfBirth", dateOfBirth);
                editor.putString("bloodGroup", bloodGroup);
                editor.apply();
                setFieldsEditable(false);
                btnEdit.setText("Edit");
            });
        }
    }
    public void enableEditing(View view) {

        if (!isEditing) {

            // Enable editing
            isEditing = true;
            setFieldsEditable(true);

            btnEdit.setText("Cancel");

            // Focus first field for smooth Material animation
            editFirstName.requestFocus();
            editFirstName.setSelection(editFirstName.getText().length());

        } else {

            // Disable editing
            isEditing = false;
            setFieldsEditable(false);

            btnEdit.setText("Edit");

            // Remove focus from fields
            editFirstName.clearFocus();
            editLastName.clearFocus();
            editPhoneNumber.clearFocus();
        }
    }
    @Override
    public boolean onSupportNavigateUp() {
        finish(); // goes back to previous activity (Dashboard)
        return true;
    }
}