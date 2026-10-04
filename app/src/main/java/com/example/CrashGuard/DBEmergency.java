
package com.example.CrashGuard;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

public class DBEmergency extends SQLiteOpenHelper {
    private Context context;
    String contindex = "";
    private static final int DATABASE_VERSION = 2;
    private static final String DATABASE_NAME = "EmergencyDatabaseMain";
    private static final String TABLE_CONTACTS = "EmerContact";
    private static final String CUSTOMER_EMAIL = "email";
    private static final String E_CONTACT1 = "cont1";
    private static final String E_CONTACT2 = "cont2";
    private static final String E_CONTACT3 = "cont3";
    private static final String E_CONTACT4 = "cont4";
    private static final String E_CONTACT5 = "cont5";

    public DBEmergency(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        this.context = context;
    }
    @Override
    public void onCreate(SQLiteDatabase db) {
        String CREATE_CUSTOMER_TABLE = "CREATE TABLE IF NOT EXISTS " + TABLE_CONTACTS + "("
                + CUSTOMER_EMAIL + " VARCHAR(30),"
                + E_CONTACT1 + " VARCHAR(30),"
                + E_CONTACT2 + " VARCHAR(30),"
                + E_CONTACT3 + " VARCHAR(30),"
                + E_CONTACT4 + " VARCHAR(30),"
                + E_CONTACT5 + " VARCHAR(30)"
                + ")";
        db.execSQL(CREATE_CUSTOMER_TABLE);
    }
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Preserve existing users/contacts where possible.
        if (oldVersion < 2) {
            try {
                db.execSQL("ALTER TABLE " + TABLE_CONTACTS + " ADD COLUMN " + E_CONTACT4 + " VARCHAR(30)");
            } catch (Exception ignored) {}
            try {
                db.execSQL("ALTER TABLE " + TABLE_CONTACTS + " ADD COLUMN " + E_CONTACT5 + " VARCHAR(30)");
            } catch (Exception ignored) {}
            return;
        }

        db.execSQL("DROP TABLE IF EXISTS " + TABLE_CONTACTS);
        onCreate(db);
    }
    public String addContact(EmerContact contact) {
        String toast = "";

        try (SQLiteDatabase db = this.getWritableDatabase()) {

            contindex = exist(db, contact);
            String s = "Name=" + contact.getName() + " Phone=" + contact.getPhone();

            if (contindex.equals("First")) {
                ContentValues values = new ContentValues();
                values.put(CUSTOMER_EMAIL, contact.getEmail());
                values.put(E_CONTACT1, s);   // first contact always goes in cont1
                db.insert(TABLE_CONTACTS, null, values);
                toast = "Contact saved";

            } else if (contindex.equals("")) {
                toast = "You can save upto 5 emergency contacts only.";

            } else {
                // contindex will now be cont1 / cont2 / cont3 correctly
                String query = "UPDATE " + TABLE_CONTACTS
                        + " SET " + contindex + "=? WHERE " + CUSTOMER_EMAIL + "=?";
                db.execSQL(query, new Object[]{s, contact.getEmail()});
                toast = "Contact saved";
            }
        }

        return toast;
    }
    public List<EmerContact> getContact(String email) {
        List<EmerContact> contactList = new ArrayList<>();
        try (SQLiteDatabase db = getWritableDatabase()) {
            String rowQuery = "SELECT * FROM " + TABLE_CONTACTS + " WHERE " + CUSTOMER_EMAIL + "=?";
            try (Cursor cursor = db.rawQuery(rowQuery, new String[]{email})) {
                if (cursor.moveToFirst()) {
                    for (int i = 1; i <= 5; i++) {
                        String colName = "cont" + i;
                        String contactValue = cursor.getString(cursor.getColumnIndexOrThrow(colName));
                        if (contactValue != null && !contactValue.trim().isEmpty()) {
                            int n = contactValue.indexOf("Name=");
                            int p = contactValue.indexOf("Phone=");
                            if (n != -1 && p != -1 && p > n) {
                                String name = contactValue.substring(n + 5, p - 1);
                                String phone = contactValue.substring(p + 6);
                                EmerContact contact = new EmerContact();
                                contact.setName(name);
                                contact.setPhone(phone);
                                contact.setSlotIndex(i);
                                contactList.add(contact);
                            }
                        }
                    }
                }
            }
        }
        return contactList;
    }
    public String exist(SQLiteDatabase db, EmerContact contact) {
        contindex = "";
        String emailsearchQuery = "SELECT * FROM " + TABLE_CONTACTS + " WHERE " + CUSTOMER_EMAIL + "=?";
        try (Cursor e = db.rawQuery(emailsearchQuery, new String[]{contact.getEmail()})) {
            if (e.getCount() == 0) {
                contindex = "First";
            } else {
                for (int i = 1; i <= 5; i++) {
                    String emptysearchQuery = "SELECT cont" + i + " FROM " + TABLE_CONTACTS + " WHERE " + CUSTOMER_EMAIL + "=? AND cont" + i + " IS NULL";
                    try (Cursor c = db.rawQuery(emptysearchQuery, new String[]{contact.getEmail()})) {
                        if (c.getCount() != 0) {
                            contindex = "cont" + i;
                            break;
                        }
                    }
                }
            }
        }
        return contindex;
    }
    public String updatecontact(String conindex, String value, String email) {

        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();

        if (value == null) {
            values.putNull(conindex);  // REAL SQL NULL
        } else {
            values.put(conindex, value);
        }
        db.update(TABLE_CONTACTS, values, CUSTOMER_EMAIL + "=?",
                new String[]{email});

        db.close();
        return "Successfully updated";
    }
}