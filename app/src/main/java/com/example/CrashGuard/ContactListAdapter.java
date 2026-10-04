//Is file ka main purpose
//        👉 Simple:
//        ContactListAdapter = RecyclerView ka bridge (data ↔ UI)
//        👉 Ye:
//
//        database se aaye data ko screen pe show karta hai
//        har contact ka design apply karta hai
//        edit & delete actions handle karta hai

package com.example.CrashGuard;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;


import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

public class ContactListAdapter extends RecyclerView.Adapter<ContactListAdapter.ViewHolder> {

    String email;
    Context context;
    DBEmergency db;
    int layoutResourceId;
    ArrayList<EmerContact> data = new ArrayList<>();

    public ContactListAdapter(ArrayList<EmerContact> data,
                              int layoutResourceId,
                              Context context,
                              String email) {

        this.layoutResourceId = layoutResourceId;
        this.context = context;
        this.data = data;
        this.email = email;
        db = new DBEmergency(context);
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
        View v = LayoutInflater.from(viewGroup.getContext())
                .inflate(layoutResourceId, viewGroup, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        EmerContact contact = data.get(position);
        holder.textName.setText(contact._name);
        holder.textContact.setText(contact._phone);
    }

    @Override
    public int getItemCount() {
        return data == null ? 0 : data.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

        public TextView textName;
        public TextView textContact;
        public TextView btnEdit, btnDelete;

        public ViewHolder(View row) {
            super(row);

            textName = row.findViewById(R.id.cont_name);
            textContact = row.findViewById(R.id.cont_number);
            btnEdit = row.findViewById(R.id.btn_edit);
            btnDelete = row.findViewById(R.id.btn_delete);

            btnDelete.setOnClickListener(this);
            btnEdit.setOnClickListener(this);
        }

        @Override
        public void onClick(View v) {

            int position = getAdapterPosition();
            if (position == RecyclerView.NO_POSITION) return;

            if (v.getId() == R.id.btn_edit) {

                Intent intent = new Intent(context, Edit_ContactActivity.class);
                Bundle b = new Bundle();

                b.putString("Name", textName.getText().toString());
                b.putString("Phone", textContact.getText().toString());
                int slot = data.get(position).getSlotIndex();
                if (slot < 1) slot = position + 1;
                b.putInt("ContactSlot", slot);

                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                intent.putExtras(b);
                context.startActivity(intent);

            } else if (v.getId() == R.id.btn_delete) {

                AlertDialog.Builder builder = new AlertDialog.Builder(context);
                builder.setTitle("Are you sure you want to delete?")
                        .setPositiveButton("Yes", (dialog, which) -> {

                            int pos = getAdapterPosition();
                            if (pos == RecyclerView.NO_POSITION) return;

                            int slot = data.get(pos).getSlotIndex();
                            if (slot < 1) slot = pos + 1; // fallback
                            db.updatecontact("cont" + slot, null, email);
                            removeAt(pos);
                        })
                        .setNegativeButton("No", null)
                        .show();
            }
        }

        private void removeAt(int position) {
            data.remove(position);
            notifyItemRemoved(position);
            notifyItemRangeChanged(position, data.size());
        }
    }
}