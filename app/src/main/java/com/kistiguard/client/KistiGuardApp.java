package com.kistiguard.client;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.os.Build;
import com.kistiguard.client.service.HeartbeatService;

public class KistiGuardApp extends Application {

    public static final String CHANNEL_ID = "kistiguard_protection_channel";

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();

        // Start silent background security heartbeat service
        try {
            Intent serviceIntent = new Intent(this, HeartbeatService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Device Protection Service",
                NotificationManager.IMPORTANCE_MIN
            );
            channel.setDescription("Silent background integrity and anti-tamper monitor");
            channel.setShowBadge(false);

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
}
