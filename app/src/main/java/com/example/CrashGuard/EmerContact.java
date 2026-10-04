package com.example.CrashGuard;

public class EmerContact {

    String _name, _phone, _email;
    int _slotIndex = -1; // 1..5 (DB slot), -1 when not set

    // Empty constructor
    public EmerContact() {
        // No initialization needed
    }
    // Constructor for name, phone, and email
    public EmerContact(final String name, final String phone, final String email) {
        this._name = name;
        this._phone = phone;
        this._email = email;
    }

    // Getter and setter for email
    public String getEmail() {
        return _email;
    }

    public void setEmail(final String email) {
        this._email = email;
    }

    // Getter and setter for name
    public String getName() {
        return _name;
    }

    public void setName(final String name) {
        this._name = name;
    }

    // Getter and setter for phone
    public String getPhone() {
        return _phone;
    }

    public void setPhone(final String phone) {
        this._phone = phone;
    }

    public int getSlotIndex() {
        return _slotIndex;
    }

    public void setSlotIndex(int slotIndex) {
        this._slotIndex = slotIndex;
    }
}
