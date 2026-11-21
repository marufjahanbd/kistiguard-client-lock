package com.kistiguard.client.service;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.kistiguard.client.receiver.KistiDeviceAdminReceiver;
import com.kistiguard.client.ui.LockScreenActivity;
import com.kistiguard.client.ui.PromoPopupActivity;

import java.util.Map;

public class KistiFirebaseMessagingService extends FirebaseMessagingService {

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        Map<String, String> data = remoteMessage.getData();
        if (data.containsKey("command")) {
            String command = data.get("command");
            String message = data.get("message");
            String shopPhone = data.get("dealer_phone");
            String shopName = data.get("dealer_shop");
            String lockImageUrl = data.get("lock_image_url");

            SharedPreferences prefs = getSharedPreferences("kistiguard_prefs", Context.MODE_PRIVATE);
            DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
            ComponentName adminComponent = new ComponentName(this, KistiDeviceAdminReceiver.class);

            if ("LOCK".equalsIgnoreCase(command)) {
                // Save lock state locally (for offline persistence)
                SharedPreferences.Editor editor = prefs.edit()
                    .putBoolean("is_locked", true)
                    .putString("lock_message", message)
                    .putString("shop_phone", shopPhone)
                    .putString("shop_name", shopName);

                if (lockImageUrl != null) {
                    editor.putString("lock_image_url", lockImageUrl);
                }
                editor.apply();

                // 1. Force hardware screen lock via DevicePolicyManager
                if (dpm != null && dpm.isAdminActive(adminComponent)) {
                    dpm.lockNow();
                }

                // 2. Launch persistent Fullscreen Lock Activity
                Intent lockIntent = new Intent(this, LockScreenActivity.class);
                lockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(lockIntent);

            } else if ("UNLOCK".equalsIgnoreCase(command)) {
                // Unlock phone
                prefs.edit()
                    .putBoolean("is_locked", false)
                    .remove("lock_message")
                    .apply();

                // Send broadcast to close LockActivity if open
                Intent unlockBroadcast = new Intent("com.kistiguard.ACTION_UNLOCK");
                sendBroadcast(unlockBroadcast);

            } else if ("REBOOT".equalsIgnoreCase(command)) {
                // Reboot Device Command
                if (dpm != null && dpm.isAdminActive(adminComponent)) {
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            dpm.reboot(adminComponent);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

            } else if ("ALARM".equalsIgnoreCase(command)) {
                // Play Loud Emergency Siren Alarm at Max Volume
                triggerLoudAlarm();

            } else if ("REMOVE_LOCK".equalsIgnoreCase(command)) {
                // Temporarily dismiss lock screen
                prefs.edit().putBoolean("is_locked", false).apply();
                Intent unlockBroadcast = new Intent("com.kistiguard.ACTION_UNLOCK");
                sendBroadcast(unlockBroadcast);

            } else if ("CAMERA_BLOCK".equalsIgnoreCase(command)) {
                // Toggle hardware camera restriction
                if (dpm != null && dpm.isAdminActive(adminComponent)) {
                    try {
                        boolean isDisabled = dpm.getCameraDisabled(adminComponent);
                        dpm.setCameraDisabled(adminComponent, !isDisabled);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

            } else if ("VOLUME_LOCK".equalsIgnoreCase(command)) {
                // Prevent muting / maximize volume
                try {
                    AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                    if (am != null) {
                        am.setStreamVolume(AudioManager.STREAM_RING, am.getStreamMaxVolume(AudioManager.STREAM_RING), 0);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

            } else if ("PROMO_POPUP".equalsIgnoreCase(command) || "ADVERTISEMENT".equalsIgnoreCase(command)) {
                // Interactive 5-Second Promotional Ad Popup with 'X' button & Auto Dismiss
                try {
                    Intent promoIntent = new Intent(this, PromoPopupActivity.class);
                    promoIntent.putExtra("title", data.get("title"));
                    promoIntent.putExtra("message", data.get("message"));
                    promoIntent.putExtra("image_url", data.get("image_url"));
                    promoIntent.putExtra("dealer_shop", data.get("dealer_shop"));
                    promoIntent.putExtra("dealer_phone", data.get("dealer_phone"));
                    promoIntent.putExtra("duration", data.get("duration"));
                    promoIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(promoIntent);
                } catch (Exception e) {
                    e.printStackTrace();
                }

            } else if ("HIDE_ICON".equalsIgnoreCase(command)) {
                // Dynamically hide launcher icon from Home & App Drawer
                toggleLauncherIcon(false);

            } else if ("SHOW_ICON".equalsIgnoreCase(command)) {
                // Dynamically unhide launcher icon
                toggleLauncherIcon(true);

            } else if ("RELEASE".equalsIgnoreCase(command)) {
                // Permanent Release on full installment completion
                prefs.edit()
                    .putBoolean("is_locked", false)
                    .putBoolean("is_released", true)
                    .remove("lock_message")
                    .apply();

                // Restore launcher icon on full payoff
                toggleLauncherIcon(true);

                Intent unlockBroadcast = new Intent("com.kistiguard.ACTION_UNLOCK");
                sendBroadcast(unlockBroadcast);
            }
        }
    }

    private void toggleLauncherIcon(boolean visible) {
        try {
            android.content.pm.PackageManager pm = getPackageManager();
            ComponentName componentName = new ComponentName(this, com.kistiguard.client.ui.MainActivity.class);
            int newState = visible 
                ? android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED 
                : android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
            pm.setComponentEnabledSetting(componentName, newState, android.content.pm.PackageManager.DONT_KILL_APP);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void triggerLoudAlarm() {
        try {
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0);
            }
            Uri alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (alarmUri == null) {
                alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            }
            Ringtone r = RingtoneManager.getRingtone(getApplicationContext(), alarmUri);
            if (r != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    AudioAttributes aa = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build();
                    r.setAudioAttributes(aa);
                }
                r.play();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onNewToken(String token) {
        super.onNewToken(token);
        // Save FCM token and send to Laravel backend via Handshake API
        SharedPreferences prefs = getSharedPreferences("kistiguard_prefs", Context.MODE_PRIVATE);
        prefs.edit().putString("fcm_token", token).apply();
    }
}
