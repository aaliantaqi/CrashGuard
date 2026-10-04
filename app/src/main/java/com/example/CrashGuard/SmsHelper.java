package com.example.CrashGuard;

import android.content.Context;
import android.telephony.SmsManager;


import java.util.ArrayList;
import java.util.List;

public final class SmsHelper {
    private SmsHelper() {}

    public static List<String> normalizeNumbers(List<String> rawNumbers) {
        ArrayList<String> out = new ArrayList<>();
        if (rawNumbers == null) return out;

        for (String raw : rawNumbers) {
            String n = PhoneUtils.normalize(raw);
            if (!n.isEmpty() && PhoneUtils.isValid(n)) out.add(n);
        }
        return out;
    }

    public static void sendToMany(Context context, List<String> rawNumbers, String message) {
        if (message == null) message = "";
        List<String> numbers = normalizeNumbers(rawNumbers);
        if (numbers.isEmpty()) return;

        SmsManager sms = SmsManager.getDefault();
        ArrayList<String> parts = sms.divideMessage(message);

        for (String number : numbers) {
            sms.sendMultipartTextMessage(number, null, parts, null, null);
        }
    }
}