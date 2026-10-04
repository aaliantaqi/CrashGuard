package com.example.CrashGuard;
import com.google.firebase.database.IgnoreExtraProperties;
@IgnoreExtraProperties
public class UserInformation {

    public String firstName;
    public String lastName;
    public String dateOfBirth;
    public String phoneNumber;
    public String bloodGroup;

    // Required empty constructor for Firebase
    public UserInformation() {
    }
    public UserInformation(String firstName, String lastName, String dateOfBirth, String phoneNumber, String bloodGroup) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.dateOfBirth = dateOfBirth;
        this.phoneNumber = phoneNumber;
        this.bloodGroup = bloodGroup;
    }
}
