package com.kistiguard.client.service;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import androidx.core.app.NotificationCompat;
import com.kistiguard.client.KistiGuardApp;
import com.kistiguard.client.ui.LockScreenActivity;

public class HeartbeatService extends Service {

    private Handler handler;
    private Runnable runnable;
    private static final long CHECK_INTERVAL_MS = 60000; // Check every 60s

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());

        Notification notification = new NotificationCompat.Builder(this, KistiGuardApp.CHANNEL_ID)
            .setContentTitle("Device Protection Engine")
            .setContentText("KistiGuard security monitor active")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build();

        startForeground(101, notification);

        runnable = new Runnable() {
            @Override
            public void run() {
                checkLockStatus();
                handler.postDelayed(this, CHECK_INTERVAL_MS);
            }
        };
        handler.post(runnable);
    }

    private void checkLockStatus() {
        SharedPreferences prefs = getSharedPreferences("kistiguard_prefs", Context.MODE_PRIVATE);
        boolean isLocked = prefs.getBoolean("is_locked", false);

        if (isLocked) {
            Intent lockIntent = new Intent(this, LockScreenActivity.class);
            lockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(lockIntent);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY; // Auto-restart if killed by system
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (handler != null && runnable != null) {
            handler.removeCallbacks(runnable);
        }
    }
}
