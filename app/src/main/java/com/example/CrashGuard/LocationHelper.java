package com.example.CrashGuard;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

public final class LocationHelper {
    private LocationHelper() {}

    public interface OneShotCallback {
        void onResult(Location location);
    }

    // ── Internet check ────────────────────────────────────────────────────
    /**
     * Returns true only if there is an active network connection.
     * When offline, location should NOT be included in the SMS because
     * the cached GPS fix may be stale and dangerously inaccurate.
     */
    public static boolean isInternetAvailable(Context context) {
        ConnectivityManager cm =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.net.Network network = cm.getActiveNetwork();
            if (network == null) return false;
            NetworkCapabilities caps = cm.getNetworkCapabilities(network);
            return caps != null && (
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                            || caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                            || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            );
        } else {
            // API < 23 fallback
            android.net.NetworkInfo info = cm.getActiveNetworkInfo();
            return info != null && info.isConnected();
        }
    }

    // ── Permission check ──────────────────────────────────────────────────
    public static boolean hasLocationPermission(Context context) {
        return ContextCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    // ── One-shot location ─────────────────────────────────────────────────
    /**
     * Fetches a one-shot GPS location ONLY when internet is available.
     * If internet is off, calls back with null immediately so the caller
     * can send an SMS without any location link.
     *
     * This prevents sending a stale cached location that could mislead
     * rescuers to the wrong place.
     */
    @SuppressLint("MissingPermission")
    public static void getOneShotLocation(Context context, long timeoutMs, OneShotCallback cb) {
        if (cb == null) return;

        // ✅ Internet check FIRST — no internet = no location in SMS
        if (!isInternetAvailable(context)) {
            cb.onResult(null);
            return;
        }

        if (!hasLocationPermission(context)) {
            cb.onResult(null);
            return;
        }

        FusedLocationProviderClient client =
                LocationServices.getFusedLocationProviderClient(context);

        client.getLastLocation()
                .addOnSuccessListener(loc -> {
                    if (loc != null) {

                        // ✅ Check — location 30 second se zyada purani toh nahi?
                        long ageMs = System.currentTimeMillis() - loc.getTime();

                        if (ageMs > 30_000L) {
                            // Purani hai — fresh lo
                            requestSingleUpdate(context, client, timeoutMs, cb);
                        } else {
                            // Fresh hai — use karo
                            cb.onResult(loc);
                        }

                    } else {
                        requestSingleUpdate(context, client, timeoutMs, cb);
                    }
                })
                .addOnFailureListener(e -> requestSingleUpdate(context, client, timeoutMs, cb));
    }

    // ── Request a fresh GPS update ─────────────────────────────────────────
    @SuppressLint("MissingPermission")
    private static void requestSingleUpdate(Context context,
                                            FusedLocationProviderClient client,
                                            long timeoutMs,
                                            OneShotCallback cb) {
        Handler handler = new Handler(Looper.getMainLooper());

        LocationRequest req = LocationRequest.create();
        req.setInterval(1000);
        req.setFastestInterval(500);
        req.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        req.setNumUpdates(1);

        final boolean[] delivered = {false};

        LocationCallback callback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult result) {
                if (delivered[0]) return;
                delivered[0] = true;
                try {
                    client.removeLocationUpdates(this);
                } catch (Exception ignored) {}
                cb.onResult(result == null ? null : result.getLastLocation());
            }
        };

        try {
            client.requestLocationUpdates(req, callback, Looper.getMainLooper());
        } catch (Exception e) {
            cb.onResult(null);
            return;
        }

        handler.postDelayed(() -> {
            if (delivered[0]) return;
            delivered[0] = true;
            try {
                client.removeLocationUpdates(callback);
            } catch (Exception ignored) {}
            cb.onResult(null);
        }, Math.max(1000L, timeoutMs));
    }
}