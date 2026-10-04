package com.example.CrashGuard;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.PhoneNumberFormattingTextWatcher;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;


import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class Add_EmergencyActivity extends AppCompatActivity {

    EditText editText1, editText10;
    TextView btn1;
    DBEmergency db;
    FirebaseAuth firebaseAuth;
    String email = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contact_add);

        firebaseAuth = FirebaseAuth.getInstance();
        FirebaseUser user = firebaseAuth.getCurrentUser();

        if (user != null) {
            email = user.getEmail();
        }

        editText1 = findViewById(R.id.add_name);
        editText10 = findViewById(R.id.add_phone);
        btn1 = findViewById(R.id.text_add);

        editText10.addTextChangedListener(new PhoneNumberFormattingTextWatcher());

        db = new DBEmergency(this);

        btn1.setOnClickListener(v -> {

            if (editText1.getText().toString().trim().isEmpty()) {
                Toast.makeText(this, "Enter Name", Toast.LENGTH_SHORT).show();

            } else if (editText10.getText().toString().trim().isEmpty()) {
                Toast.makeText(this, "Enter phone number", Toast.LENGTH_SHORT).show();

            } else {
                String normalizedPhone = PhoneUtils.normalize(editText10.getText().toString());
                if (!PhoneUtils.isValid(normalizedPhone)) {
                    Toast.makeText(this, "Enter a valid phone number", Toast.LENGTH_SHORT).show();
                    return;
                }

                String text = db.addContact(
                        new EmerContact(
                                editText1.getText().toString(),
                                normalizedPhone,
                                email
                        )
                );

                Toast.makeText(this, text, Toast.LENGTH_SHORT).show();

                Intent intent = new Intent(Add_EmergencyActivity.this, MyEmerContActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                finish();
            }
        });
    }
}