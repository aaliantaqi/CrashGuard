package com.example.CrashGuard;

import android.Manifest;
import android.app.Activity;
import android.content.DialogInterface;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PermissionHandler {

    private Activity mActivity;

    public PermissionHandler(Activity activity) {
        mActivity = activity;
    }

    public boolean requestPermissions(int code, List<String> permissions, List<String> rationale) {
        final int MY_PERMISSION_REQUEST_CODE = code;

        List<String> permissionsNeeded = new ArrayList<>();
        final List<String> permissionList = new ArrayList<>();

        for (int ii = 0; ii < permissions.size(); ++ii) {
            if (addPermission(permissionList, permissions.get(ii))) {
                permissionsNeeded.add(rationale.get(ii)); // only permissions actually needing rationale
            }
        }

        if (!permissionList.isEmpty()) {
            if (!permissionsNeeded.isEmpty()) {
                StringBuilder message = new StringBuilder("You need to grant access to " + permissionsNeeded.get(0));
                for (int ii = 1; ii < permissionsNeeded.size(); ii++) {
                    message.append(", ").append(permissionsNeeded.get(ii));
                }

                showRationaleMessage(
                        "App needs access to: " + message,
                        (dialogInterface, i) ->
                                ActivityCompat.requestPermissions(
                                        mActivity,
                                        permissionList.toArray(new String[0]),
                                        MY_PERMISSION_REQUEST_CODE
                                )
                );
                return false;
            }

            ActivityCompat.requestPermissions(
                    mActivity,
                    permissionList.toArray(new String[0]),
                    MY_PERMISSION_REQUEST_CODE
            );
            return false;
        }

        return true;
    }

    private void showRationaleMessage(String message, DialogInterface.OnClickListener okListener) {
        new AlertDialog.Builder(mActivity)
                .setMessage(message)
                .setPositiveButton("OK", okListener)
                .setNegativeButton("Cancel", null)
                .create()
                .show();
    }

    private boolean addPermission(List<String> permissionList, String permission) {
        if (ContextCompat.checkSelfPermission(mActivity, permission)
                != PackageManager.PERMISSION_GRANTED) {
            permissionList.add(permission);
            // true = user has denied before, we SHOULD show rationale
            return ActivityCompat.shouldShowRequestPermissionRationale(mActivity, permission);
        }
        return false; // already granted -> no rationale needed
    }
    public boolean handleRequestResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults
    ) {
        Map<String, Integer> perms = new HashMap<>();

        perms.put(Manifest.permission.ACCESS_FINE_LOCATION, PackageManager.PERMISSION_GRANTED);
        perms.put(Manifest.permission.SEND_SMS, PackageManager.PERMISSION_GRANTED);
        perms.put(Manifest.permission.POST_NOTIFICATIONS, PackageManager.PERMISSION_GRANTED);

        for (int ii = 0; ii < permissions.length; ii++) {
            perms.put(permissions[ii], grantResults[ii]);
        }
        boolean baseGranted =
                perms.get(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        && perms.get(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED;

        // On Android 13+ notification permission is required for the foreground service notification
        // to be visible to the user.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            return baseGranted
                    && perms.get(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        }

        return baseGranted;
    }
}