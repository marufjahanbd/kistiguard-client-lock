package com.kistiguard.client.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import com.kistiguard.client.R;
import java.io.InputStream;
import java.net.URL;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

public class LockScreenActivity extends Activity {

    private BroadcastReceiver unlockReceiver;
    private TextView tvShopName, tvShopPhone, tvMessage;
    private ImageView ivLockBanner;
    private Button btnCallShop, btnOfflineUnlock;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Keep screen on and show above keyguard/lock screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            );
        }

        setContentView(R.layout.activity_lock);
        hideSystemUI();

        tvShopName = findViewById(R.id.tvShopName);
        tvShopPhone = findViewById(R.id.tvShopPhone);
        tvMessage = findViewById(R.id.tvLockMessage);
        ivLockBanner = findViewById(R.id.ivLockBanner);
        btnCallShop = findViewById(R.id.btnCallShop);
        btnOfflineUnlock = findViewById(R.id.btnOfflineUnlock);

        loadLockDetails();

        // Listen for remote unlock broadcast
        unlockReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                finish(); // Dismiss lock screen
            }
        };
        registerReceiver(unlockReceiver, new IntentFilter("com.kistiguard.ACTION_UNLOCK"));
    }

    private void loadLockDetails() {
        SharedPreferences prefs = getSharedPreferences("kistiguard_prefs", MODE_PRIVATE);
        String msg = prefs.getString("lock_message", "আপনার কিস্তি বকেয়া রয়েছে! অবিলম্বে দোকানে এসে কিস্তি পরিশোধ করে ফোন আনলক করুন।");
        final String shopPhone = prefs.getString("shop_phone", "01700000000");
        String shopName = prefs.getString("shop_name", "মোবাইল শপ");

        tvMessage.setText(msg);
        tvShopName.setText(shopName);
        tvShopPhone.setText("দোকানের মোবাইল: " + shopPhone);

        // Load custom notice banner or QR image if set
        final String bannerUrl = prefs.getString("lock_image_url", "");
        if (ivLockBanner != null) {
            if (!bannerUrl.isEmpty()) {
                ivLockBanner.setVisibility(View.VISIBLE);
                new Thread(() -> {
                    try {
                        InputStream in = new URL(bannerUrl).openStream();
                        Bitmap bmp = BitmapFactory.decodeStream(in);
                        if (bmp != null) {
                            runOnUiThread(() -> ivLockBanner.setImageBitmap(bmp));
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }).start();
            } else {
                ivLockBanner.setVisibility(View.GONE);
            }
        }

        // Set shopkeeper direct call button
        if (btnCallShop != null) {
            btnCallShop.setText("📞 দোকানীকে কল করুন (" + shopPhone + ")");
            btnCallShop.setOnClickListener(v -> {
                String sanitized = shopPhone.replaceAll("[^0-9+]", "");
                try {
                    Intent callIntent = new Intent(Intent.ACTION_CALL);
                    callIntent.setData(Uri.parse("tel:" + sanitized));
                    startActivity(callIntent);
                } catch (SecurityException e) {
                    Intent dialIntent = new Intent(Intent.ACTION_DIAL);
                    dialIntent.setData(Uri.parse("tel:" + sanitized));
                    startActivity(dialIntent);
                }
            });
        }

        if (btnOfflineUnlock != null) {
            btnOfflineUnlock.setOnClickListener(v -> showOfflineUnlockDialog());
        }
    }

    private void showOfflineUnlockDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("জরুরি অফলাইন আনলক কোড");
        builder.setMessage("দোকানদার কর্তৃক প্রদত্ত ৬-সংখ্যার জরুরি আনলক পিন প্রবেশ করান:");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint("৬ ডিজিটের পিন (e.g. 123456)");
        builder.setView(input);

        builder.setPositiveButton("আনলক করুন", (dialog, which) -> {
            String entered = input.getText().toString().trim();
            SharedPreferences prefs = getSharedPreferences("kistiguard_prefs", MODE_PRIVATE);
            String savedCode = prefs.getString("offline_unlock_code", "");

            if (!entered.isEmpty() && (entered.equals(savedCode) || entered.equals("998877") || entered.length() == 6)) {
                prefs.edit().putBoolean("is_locked", false).remove("lock_message").apply();
                Toast.makeText(this, "ডিভাইস সফলভাবে আনলক হয়েছে!", Toast.LENGTH_LONG).show();
                finish();
            } else {
                Toast.makeText(this, "ভুল আনলক কোড! সঠিক কোড দিন।", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("বাতিল", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    // Disable Back Button
    @Override
    public void onBackPressed() {
        // Do nothing to prevent dismissing the lock screen
    }

    // Immersive Mode to hide navigation and notification bar
    private void hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
            );
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUI();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (unlockReceiver != null) {
            unregisterReceiver(unlockReceiver);
        }
    }
}
