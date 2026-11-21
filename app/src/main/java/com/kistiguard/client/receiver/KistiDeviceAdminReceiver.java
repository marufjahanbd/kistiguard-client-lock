package com.kistiguard.client.receiver;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

public class KistiDeviceAdminReceiver extends DeviceAdminReceiver {

    @Override
    public void onEnabled(Context context, Intent intent) {
        super.onEnabled(context, intent);
        Toast.makeText(context, "KistiGuard Protection Activated", Toast.LENGTH_SHORT).show();
    }

    @Override
    public CharSequence onDisableRequested(Context context, Intent intent) {
        // Warning shown if user attempts to remove device admin
        return "সতর্কতা: KistiGuard আনইনস্টল বা ডিসেবল করার অনুমতি নেই! কিস্তি পরিশোধ না করা পর্যন্ত এটি সক্রিয় থাকবে।";
    }

    @Override
    public void onDisabled(Context context, Intent intent) {
        super.onDisabled(context, intent);
    }
}
