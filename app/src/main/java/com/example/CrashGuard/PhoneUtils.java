package com.example.CrashGuard;
public final class PhoneUtils {
    private PhoneUtils() {}
    public static String normalize(String raw) {
        if (raw == null) return "";
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return "";
        // Keep leading + if present; strip other non-digits
        boolean hasPlus = trimmed.startsWith("+");
        String digits = trimmed.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return "";
        return hasPlus ? ("+" + digits) : digits;
    }
    public static boolean isValid(String raw) {
        String n = normalize(raw);
        if (n.isEmpty()) return false;
        // Basic sanity: digits length 10..15 (common phone ranges), optional leading +
        String digits = n.startsWith("+") ? n.substring(1) : n;
        return digits.length() >= 10 && digits.length() <= 15;
    }
}