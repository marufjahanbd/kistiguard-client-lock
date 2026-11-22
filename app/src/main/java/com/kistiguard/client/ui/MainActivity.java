package com.kistiguard.client.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.UserManager;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.kistiguard.client.R;
import com.kistiguard.client.receiver.KistiDeviceAdminReceiver;
import com.kistiguard.client.service.HeartbeatService;

import org.json.JSONObject;

public class MainActivity extends Activity {

    private static final int REQUEST_CODE_ENABLE_ADMIN = 1001;

    private ImageView ivDeviceQr;
    private TextView tvDeviceBrandModel;
    private TextView tvDeviceId;
    private TextView tvDeviceOs;
    private TextView tvAdminStatus;
    private Button btnActivateAdmin;
    private Button btnHideAndProtect;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences("kistiguard_prefs", Context.MODE_PRIVATE);
        boolean isEnrolled = prefs.getBoolean("is_enrolled", false);

        // If device is already enrolled and stealth active, keep icon hidden and finish
        if (isEnrolled) {
            startHeartbeatService();
            hideLauncherIcon();
            finish();
            return;
        }

        setContentView(R.layout.activity_main);

        ivDeviceQr = findViewById(R.id.ivDeviceQr);
        tvDeviceBrandModel = findViewById(R.id.tvDeviceBrandModel);
        tvDeviceId = findViewById(R.id.tvDeviceId);
        tvDeviceOs = findViewById(R.id.tvDeviceOs);
        tvAdminStatus = findViewById(R.id.tvAdminStatus);
        btnActivateAdmin = findViewById(R.id.btnActivateAdmin);
        btnHideAndProtect = findViewById(R.id.btnHideAndProtect);

        initDevicePairingQr();
        checkAdminStatus();

        btnActivateAdmin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                promptDeviceAdmin();
            }
        });

        btnHideAndProtect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                completeEnrollment();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkAdminStatus();
    }

    /**
     * Inspect phone hardware and render pairing QR Code
     */
    @SuppressLint("HardwareIds")
    private void initDevicePairingQr() {
        String brand = capitalize(Build.MANUFACTURER != null ? Build.MANUFACTURER : Build.BRAND);
        String model = Build.MODEL != null ? Build.MODEL : "Android Device";
        String os = "Android " + Build.VERSION.RELEASE;

        String deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        String imei1 = deviceId;
        String imei2 = "";

        // Attempt Telephony IMEI if permission or privileged access exists
        try {
            TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
            if (tm != null && checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    String primaryImei = tm.getImei(0);
                    if (primaryImei != null && !primaryImei.isEmpty()) {
                        imei1 = primaryImei;
                    }
                    String secondaryImei = tm.getImei(1);
                    if (secondaryImei != null && !secondaryImei.isEmpty()) {
                        imei2 = secondaryImei;
                    }
                } else {
                    String devId = tm.getDeviceId();
                    if (devId != null && !devId.isEmpty()) {
                        imei1 = devId;
                    }
                }
            }
        } catch (Exception ignored) {}

        tvDeviceBrandModel.setText("মডেল: " + brand + " " + model);
        tvDeviceId.setText("IMEI / ID: " + imei1);
        tvDeviceOs.setText("অ্যান্ড্রয়েড: " + os);

        // Build Payload JSON for Dealer App Auto-Fill
        try {
            JSONObject json = new JSONObject();
            json.put("type", "kistiguard_device");
            json.put("brand", brand);
            json.put("model", model);
            json.put("imei_1", imei1);
            if (imei2 != null && !imei2.isEmpty()) {
                json.put("imei_2", imei2);
            }
            json.put("os", os);
            json.put("android_id", deviceId);

            generateQrBitmap(json.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void generateQrBitmap(String content) {
        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, 512, 512);
            int width = bitMatrix.getWidth();
            int height = bitMatrix.getHeight();
            Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    bmp.setPixel(x, y, bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }
            ivDeviceQr.setImageBitmap(bmp);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void checkAdminStatus() {
        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName adminComponent = new ComponentName(this, KistiDeviceAdminReceiver.class);

        if (dpm != null && dpm.isAdminActive(adminComponent)) {
            tvAdminStatus.setText("✅ ডিভাইস অ্যাডমিন সক্রিয় রয়েছে (সফল)");
            tvAdminStatus.setTextColor(Color.parseColor("#10b981"));
            btnActivateAdmin.setVisibility(View.GONE);
        } else {
            tvAdminStatus.setText("⚠️ ডিভাইস অ্যাডমিন পারমিশন প্রয়োজন");
            tvAdminStatus.setTextColor(Color.parseColor("#f59e0b"));
            btnActivateAdmin.setVisibility(View.VISIBLE);
        }
    }

    private void promptDeviceAdmin() {
        ComponentName adminComponent = new ComponentName(this, KistiDeviceAdminReceiver.class);
        Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent);
        intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
            "KistiGuard ডিভাইস প্রোটেকশন সক্রিয় করতে ডিভাইস অ্যাডমিন পারমিশন দিন।");
        startActivityForResult(intent, REQUEST_CODE_ENABLE_ADMIN);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_ENABLE_ADMIN) {
            checkAdminStatus();
            DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
            ComponentName adminComponent = new ComponentName(this, KistiDeviceAdminReceiver.class);
            if (dpm != null && dpm.isAdminActive(adminComponent)) {
                applyAntiTamperPolicies(dpm, adminComponent);
                Toast.makeText(this, "ডিভাইস অ্যাডমিন সক্রিয় হয়েছে!", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void completeEnrollment() {
        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName adminComponent = new ComponentName(this, KistiDeviceAdminReceiver.class);

        if (dpm != null && !dpm.isAdminActive(adminComponent)) {
            promptDeviceAdmin();
            return;
        }

        applyAntiTamperPolicies(dpm, adminComponent);
        startHeartbeatService();

        SharedPreferences prefs = getSharedPreferences("kistiguard_prefs", Context.MODE_PRIVATE);
        prefs.edit().putBoolean("is_enrolled", true).apply();

        Toast.makeText(this, "🔒 কিস্তি প্রোটেকশন সক্রিয় করা হয়েছে। আইকন হাইড হচ্ছে...", Toast.LENGTH_LONG).show();
        hideLauncherIcon();
        finish();
    }

    private void startHeartbeatService() {
        Intent serviceIntent = new Intent(this, HeartbeatService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    private void applyAntiTamperPolicies(DevicePolicyManager dpm, ComponentName adminComponent) {
        if (dpm == null) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && dpm.isDeviceOwnerApp(getPackageName())) {
                dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_FACTORY_RESET);
                dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_DEBUGGING_FEATURES);
                dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_SAFE_BOOT);
                dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_MODIFY_ACCOUNTS);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    dpm.setUninstallBlocked(adminComponent, getPackageName(), true);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

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

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return "";
        return Character.toUpperCase(str.charAt(0)) + str.substring(1).toLowerCase();
    }
}
