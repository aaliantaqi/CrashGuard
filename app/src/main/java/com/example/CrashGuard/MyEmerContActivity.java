package com.example.CrashGuard;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.widget.Button;
import com.google.android.material.appbar.MaterialToolbar;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import java.util.ArrayList;
import java.util.List;

public class MyEmerContActivity extends AppCompatActivity {

    private FirebaseAuth firebaseAuth;
    String email = "";
    Button btn_add;
    RecyclerView dataList;
    List<EmerContact> contact;
    ArrayList<EmerContact> add = new ArrayList<>();
    DBEmergency db;
    private ContactListAdapter adapter;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_contacts);

        // Firebase
        firebaseAuth = FirebaseAuth.getInstance();
        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user != null) email = user.getEmail();

        // Toolbar
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        // RecyclerView and Button
        btn_add = findViewById(R.id.btn_add);
        dataList = findViewById(R.id.listView);
        db = new DBEmergency(this);

        contact = db.getContact(email);
        add.addAll(contact);

        adapter = new ContactListAdapter(add, R.layout.emercontact_list_item, this, email);

        dataList.setLayoutManager(new LinearLayoutManager(this));
        dataList.setItemAnimator(new DefaultItemAnimator());
        dataList.setAdapter(adapter);

        btn_add.setOnClickListener(v -> startActivity(new Intent(this, Add_EmergencyActivity.class)));
    }
}