package com.kistiguard.client.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import com.kistiguard.client.ui.LockScreenActivity;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            SharedPreferences prefs = context.getSharedPreferences("kistiguard_prefs", Context.MODE_PRIVATE);
            boolean isLocked = prefs.getBoolean("is_locked", false);

            if (isLocked) {
                // If phone was locked before restart, launch lock screen immediately
                Intent lockIntent = new Intent(context, LockScreenActivity.class);
                lockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                context.startActivity(lockIntent);
            }
        }
    }
}
