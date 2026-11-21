# Proguard rules for KistiGuard Client
-keep class com.kistiguard.client.** { *; }
-keepclassmembers class * extends android.app.admin.DeviceAdminReceiver { *; }
-keepclassmembers class * extends com.google.firebase.messaging.FirebaseMessagingService { *; }
