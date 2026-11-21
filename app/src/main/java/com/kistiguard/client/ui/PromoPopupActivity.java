package com.kistiguard.client.ui;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import com.kistiguard.client.R;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class PromoPopupActivity extends Activity {

    private CountDownTimer countDownTimer;
    private String dealerPhone = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Turn screen on and show above keyguard if screen was locked
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            );
        }

        setContentView(R.layout.activity_promo_popup);

        TextView tvShopName = findViewById(R.id.tvPromoShopName);
        TextView tvTitle = findViewById(R.id.tvPromoTitle);
        TextView tvMessage = findViewById(R.id.tvPromoMessage);
        TextView tvCountdown = findViewById(R.id.tvCountdownText);
        TextView btnClose = findViewById(R.id.btnClosePromo);
        Button btnCall = findViewById(R.id.btnPromoCall);
        ImageView ivBanner = findViewById(R.id.ivPromoBanner);

        Intent intent = getIntent();
        String title = intent.getStringExtra("title");
        String message = intent.getStringExtra("message");
        String shopName = intent.getStringExtra("dealer_shop");
        dealerPhone = intent.getStringExtra("dealer_phone");
        String imageUrl = intent.getStringExtra("image_url");
        String durationStr = intent.getStringExtra("duration");

        int durationSeconds = 5;
        try {
            if (durationStr != null && !durationStr.isEmpty()) {
                durationSeconds = Integer.parseInt(durationStr);
            }
        } catch (Exception e) {
            durationSeconds = 5;
        }

        if (shopName != null && !shopName.isEmpty()) {
            tvShopName.setText("📣 " + shopName + " - বিশেষ অফার");
        }
        if (title != null && !title.isEmpty()) {
            tvTitle.setText(title);
        }
        if (message != null && !message.isEmpty()) {
            tvMessage.setText(message);
        }

        if (dealerPhone != null && !dealerPhone.isEmpty()) {
            btnCall.setText("📞 দোকানে কল করুন (" + dealerPhone + ")");
            btnCall.setVisibility(View.VISIBLE);
            btnCall.setOnClickListener(v -> {
                if (countDownTimer != null) countDownTimer.cancel();
                try {
                    Intent callIntent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + dealerPhone));
                    startActivity(callIntent);
                } catch (Exception e) {
                    Intent dialIntent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + dealerPhone));
                    startActivity(dialIntent);
                }
                finish();
            });
        } else {
            btnCall.setVisibility(View.GONE);
        }

        // Close Cross button
        btnClose.setOnClickListener(v -> {
            if (countDownTimer != null) countDownTimer.cancel();
            finish();
        });

        // Load banner image on background thread
        if (imageUrl != null && !imageUrl.isEmpty()) {
            new Thread(() -> {
                try {
                    URL url = new URL(imageUrl);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setDoInput(true);
                    connection.connect();
                    InputStream input = connection.getInputStream();
                    final Bitmap bitmap = BitmapFactory.decodeStream(input);
                    runOnUiThread(() -> {
                        if (bitmap != null) {
                            ivBanner.setImageBitmap(bitmap);
                            ivBanner.setVisibility(View.VISIBLE);
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();
        }

        // 5-Second Auto-Dismiss Countdown
        long totalMillis = (long) durationSeconds * 1000;
        countDownTimer = new CountDownTimer(totalMillis, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                long sec = Math.round((double) millisUntilFinished / 1000);
                tvCountdown.setText("⏱️ এই বিজ্ঞাপনটি " + sec + " সেকেন্ড পর স্বয়ংক্রিয়ভাবে বন্ধ হবে...");
            }

            @Override
            public void onFinish() {
                finish();
            }
        }.start();
    }

    @Override
    protected void onDestroy() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
        super.onDestroy();
    }
}
