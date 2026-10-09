package com.example.CrashGuard;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.PhoneNumberFormattingTextWatcher;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class Edit_ContactActivity extends Activity {

    EditText editText1, editText2;
    String name, phone;
    int cont;
    TextView btn1;
    DBEmergency db;
    private FirebaseAuth firebaseAuth;
    String email = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contact_edit);

        firebaseAuth = FirebaseAuth.getInstance();
        final FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user != null) email = user.getEmail();

        final Bundle b = getIntent().getExtras();
        if (b != null) {
            name = b.getString("Name");
            phone = b.getString("Phone");
            int slot = b.getInt("ContactSlot", -1);
            cont = (slot >= 1) ? slot : 1;
        }

        // EditTexts and Button
        editText1 = findViewById(R.id.edit_name);
        editText2 = findViewById(R.id.edit_phone);
        editText2.addTextChangedListener(new PhoneNumberFormattingTextWatcher());
        btn1 = findViewById(R.id.text_edit);
        db = new DBEmergency(this);

        editText1.setText(name);
        editText2.setText(phone);

        // Button click listener (modernized)
        btn1.setOnClickListener(v -> {
            final String enteredName = editText1.getText().toString();
            final String enteredPhone = editText2.getText().toString();

            if (enteredName.isEmpty() || enteredPhone.isEmpty()) {
                Toast.makeText(Edit_ContactActivity.this, "Enter Details", Toast.LENGTH_SHORT).show();
            } else {
                String normalizedPhone = PhoneUtils.normalize(enteredPhone);
                if (!PhoneUtils.isValid(normalizedPhone)) {
                    Toast.makeText(Edit_ContactActivity.this, "Enter a Valid Phone Number", Toast.LENGTH_SHORT).show();
                    return;
                }

                final String s = "Name=" + enteredName + " Phone=" + normalizedPhone;
                db.updatecontact("cont" + cont, s, email);
                Toast.makeText(Edit_ContactActivity.this, "Contact updated successfully", Toast.LENGTH_SHORT).show();

                Intent intent = new Intent(Edit_ContactActivity.this, MyEmerContActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
            }
        });
    }
}