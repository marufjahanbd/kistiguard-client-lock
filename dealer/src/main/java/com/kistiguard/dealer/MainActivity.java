package com.kistiguard.dealer;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.util.UUID;
import java.util.concurrent.Executor;

public class MainActivity extends AppCompatActivity {

    public static final String TARGET_URL = "https://kistiguard.bartamanict.com/dealer";
    private static final int FILE_CHOOSER_REQUEST_CODE = 2001;
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 1001;
    private static final String PREFS_NAME = "kistiguard_dealer_prefs";
    private static final String KEY_BIO_TOKEN = "device_bio_token";

    private WebView webView;
    private SwipeRefreshLayout swipeRefresh;
    private ProgressBar progressBar;
    private View layoutError;
    private Button btnRetry;

    private ValueCallback<Uri[]> filePathCallback;
    private PermissionRequest pendingPermissionRequest;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webView);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        progressBar = findViewById(R.id.progressBar);
        layoutError = findViewById(R.id.layoutError);
        btnRetry = findViewById(R.id.btnRetry);

        swipeRefresh.setColorSchemeColors(0xFF2563EB);
        swipeRefresh.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                webView.reload();
            }
        });

        btnRetry.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                layoutError.setVisibility(View.GONE);
                webView.setVisibility(View.VISIBLE);
                webView.reload();
            }
        });

        initWebView();
        checkAndRequestCameraPermission();
        webView.loadUrl(TARGET_URL);
    }

    private void checkAndRequestCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    CAMERA_PERMISSION_REQUEST_CODE);
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void initWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);

        // =====================================================================
        // FULLSCREEN PAYMENT GATEWAY FIX (bKash / SSLCOMMERZ)
        // setUseWideViewPort(true) + setLoadWithOverviewMode(true) ensures the
        // payment page fills the entire WebView without grey side-bars.
        // setSupportMultipleWindows(true) allows bKash window.open() popup to work.
        // =====================================================================
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.NORMAL);
        settings.setSupportMultipleWindows(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        webView.setBackgroundColor(0xFFFFFFFF);

        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setMediaPlaybackRequiresUserGesture(false);

        // Session Cookie persistence across restarts
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(webView, true);
        }

        // Native Android Biometric Javascript Interface
        webView.addJavascriptInterface(new BiometricBridge(), "AndroidBiometric");

        // =====================================================================
        // APK DOWNLOAD FIX
        // Android WebView cannot download files on its own. setDownloadListener
        // intercepts <a href="...apk" download> clicks and routes them through
        // the system DownloadManager, which saves the file to Downloads/ and
        // shows a notification when complete.
        // =====================================================================
        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent,
                                        String contentDisposition, String mimeType, long contentLength) {
                handleApkDownload(url);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress < 100) {
                    progressBar.setVisibility(View.VISIBLE);
                    progressBar.setProgress(newProgress);
                } else {
                    progressBar.setVisibility(View.GONE);
                    swipeRefresh.setRefreshing(false);
                }
            }

            // Camera / Microphone permission from HTML5 (html5-qrcode / getUserMedia)
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (ContextCompat.checkSelfPermission(MainActivity.this,
                                    Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                request.grant(request.getResources());
                            } else {
                                pendingPermissionRequest = request;
                                ActivityCompat.requestPermissions(MainActivity.this,
                                        new String[]{Manifest.permission.CAMERA},
                                        CAMERA_PERMISSION_REQUEST_CODE);
                            }
                        }
                    });
                }
            }

            // =====================================================================
            // PAYMENT POPUP FULLSCREEN FIX
            // bKash and SSLCOMMERZ open their confirmation pages via window.open()
            // or target="_blank". Without onCreateWindow the popup is silently
            // dropped. Here we show it fullscreen inside a FrameLayout overlay.
            // =====================================================================
            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog,
                                          boolean isUserGesture, android.os.Message resultMsg) {
                WebView popupWebView = new WebView(MainActivity.this);
                WebSettings ps = popupWebView.getSettings();
                ps.setJavaScriptEnabled(true);
                ps.setDomStorageEnabled(true);
                ps.setUseWideViewPort(true);
                ps.setLoadWithOverviewMode(true);
                ps.setSupportZoom(false);
                ps.setBuiltInZoomControls(false);
                CookieManager.getInstance().setAcceptCookie(true);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    CookieManager.getInstance().setAcceptThirdPartyCookies(popupWebView, true);
                }
                popupWebView.setBackgroundColor(0xFFFFFFFF);

                popupWebView.setWebViewClient(new WebViewClient() {
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView v, String url) {
                        return handleSpecialUrls(url);
                    }
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            return handleSpecialUrls(req.getUrl().toString());
                        }
                        return false;
                    }
                });

                // Full-screen container
                FrameLayout container = new FrameLayout(MainActivity.this);
                container.setBackgroundColor(0xFFFFFFFF);
                FrameLayout.LayoutParams fill = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT);
                container.addView(popupWebView, fill);

                // Close / back button at bottom-left
                Button closeBtn = new Button(MainActivity.this);
                closeBtn.setText("← ফিরে যান");
                closeBtn.setTextColor(0xFFFFFFFF);
                closeBtn.setBackgroundColor(0xCC1e293b);
                closeBtn.setTextSize(13f);
                FrameLayout.LayoutParams closeBtnParams = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                closeBtnParams.gravity = Gravity.BOTTOM | Gravity.START;
                closeBtnParams.leftMargin = 16;
                closeBtnParams.bottomMargin = 24;
                container.addView(closeBtn, closeBtnParams);

                closeBtn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        // Restore normal view
                        setContentView(R.layout.activity_main);
                        webView = findViewById(R.id.webView);
                        swipeRefresh = findViewById(R.id.swipeRefresh);
                        progressBar = findViewById(R.id.progressBar);
                        layoutError = findViewById(R.id.layoutError);
                        btnRetry = findViewById(R.id.btnRetry);
                        swipeRefresh.setColorSchemeColors(0xFF2563EB);
                        swipeRefresh.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
                            @Override
                            public void onRefresh() { webView.reload(); }
                        });
                        btnRetry.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View vv) {
                                layoutError.setVisibility(View.GONE);
                                webView.setVisibility(View.VISIBLE);
                                webView.reload();
                            }
                        });
                        initWebView();
                        webView.loadUrl(TARGET_URL);
                    }
                });

                setContentView(container);

                WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                transport.setWebView(popupWebView);
                resultMsg.sendToTarget();
                return true;
            }

            // File Chooser for Image/Document Uploads
            @Override
            public boolean onShowFileChooser(WebView wv, ValueCallback<Uri[]> cb,
                                             FileChooserParams params) {
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = cb;

                Intent intent;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    intent = params.createIntent();
                } else {
                    intent = new Intent(Intent.ACTION_GET_CONTENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("*/*");
                }
                try {
                    startActivityForResult(intent, FILE_CHOOSER_REQUEST_CODE);
                } catch (ActivityNotFoundException e) {
                    filePathCallback = null;
                    Toast.makeText(MainActivity.this, "ফাইল সিলেক্টর খুঁজে পাওয়া যায়নি", Toast.LENGTH_SHORT).show();
                    return false;
                }
                return true;
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleSpecialUrls(url);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    return handleSpecialUrls(request.getUrl().toString());
                }
                return false;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                layoutError.setVisibility(View.GONE);
                webView.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                swipeRefresh.setRefreshing(false);
                progressBar.setVisibility(View.GONE);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (request.isForMainFrame()) {
                        showOfflineError();
                    }
                }
            }
        });
    }

    /**
     * Routes APK download URLs to Android DownloadManager.
     * WebView cannot download files on its own — DownloadManager handles it
     * natively, shows a system notification, and saves to Downloads/.
     */
    private void handleApkDownload(String url) {
        try {
            Toast.makeText(this, "APK ডাউনলোড শুরু হচ্ছে...", Toast.LENGTH_SHORT).show();
            DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
            req.setTitle("KistiGuard Client APK");
            req.setDescription("ডাউনলোড হচ্ছে, অনুগ্রহ করে অপেক্ষা করুন...");
            req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "kistiguard-client.apk");
            req.setMimeType("application/vnd.android.package-archive");
            String cookie = CookieManager.getInstance().getCookie(url);
            if (cookie != null) req.addRequestHeader("Cookie", cookie);
            req.addRequestHeader("User-Agent", "Mozilla/5.0 (Android; Mobile)");
            DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            if (dm != null) {
                dm.enqueue(req);
                Toast.makeText(this, "✓ ডাউনলোড শুরু হয়েছে! নোটিফিকেশন বারে দেখুন।", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            // Fallback to external browser
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (Exception ignored) {}
        }
    }

    /**
     * Native Android Biometric Prompt Bridge — exposed to WebView JS as window.AndroidBiometric
     */
    public class BiometricBridge {

        @JavascriptInterface
        public boolean isAvailable() {
            try {
                BiometricManager bm = BiometricManager.from(MainActivity.this);
                int can = bm.canAuthenticate(
                        BiometricManager.Authenticators.BIOMETRIC_STRONG |
                        BiometricManager.Authenticators.BIOMETRIC_WEAK);
                return can == BiometricManager.BIOMETRIC_SUCCESS;
            } catch (Exception e) { return false; }
        }

        @JavascriptInterface
        public void enroll() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    showNativeBiometricPrompt(
                            "বায়োমেট্রিক যোগ করুন",
                            "আপনার ফোনের ফিঙ্গারপ্রিন্ট স্পর্শ করে একাউন্ট লিংক করুন",
                            true);
                }
            });
        }

        @JavascriptInterface
        public void authenticate() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    showNativeBiometricPrompt(
                            "কিসতিগার্ড লগইন",
                            "ফিঙ্গারপ্রিন্ট স্পর্শ করে একাউন্টে প্রবেশ করুন",
                            false);
                }
            });
        }
    }

    private void showNativeBiometricPrompt(String title, String subtitle, final boolean isEnrollment) {
        Executor executor = ContextCompat.getMainExecutor(this);
        BiometricPrompt prompt = new BiometricPrompt(this, executor,
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationError(int code, @NonNull CharSequence err) {
                        super.onAuthenticationError(code, err);
                        String msg = err.toString().replace("'", "\\'");
                        webView.evaluateJavascript(
                                "if(window.onAndroidBiometricError) window.onAndroidBiometricError('" + msg + "');", null);
                    }

                    @Override
                    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                        super.onAuthenticationSucceeded(result);
                        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                        String bioToken = prefs.getString(KEY_BIO_TOKEN, null);
                        if (bioToken == null || isEnrollment) {
                            String aid = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
                            bioToken = "bio_native_" + (aid != null ? aid : UUID.randomUUID().toString().replace("-", ""));
                            prefs.edit().putString(KEY_BIO_TOKEN, bioToken).apply();
                        }
                        final String token = bioToken;
                        if (isEnrollment) {
                            webView.evaluateJavascript(
                                    "if(window.onAndroidBiometricEnrolled) window.onAndroidBiometricEnrolled('" + token + "');", null);
                        } else {
                            webView.evaluateJavascript(
                                    "if(window.onAndroidBiometricLogin) window.onAndroidBiometricLogin('" + token + "');", null);
                        }
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        super.onAuthenticationFailed();
                    }
                });

        BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setNegativeButtonText("বাতিল")
                .setAllowedAuthenticators(
                        BiometricManager.Authenticators.BIOMETRIC_STRONG |
                        BiometricManager.Authenticators.BIOMETRIC_WEAK)
                .build();
        prompt.authenticate(info);
    }

    private boolean handleSpecialUrls(String url) {
        if (url == null) return false;

        // Phone dialer
        if (url.startsWith("tel:")) {
            startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse(url)));
            return true;
        }

        // WhatsApp / Mail
        if (url.startsWith("mailto:") || url.contains("wa.me") || url.startsWith("whatsapp:")) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                return true;
            } catch (Exception ignored) {}
        }

        // APK download — route to DownloadManager
        if (url.endsWith(".apk") || url.contains("kistiguard-client.apk")
                || url.contains("/download/kistiguard") || url.contains("/downloads/kistiguard")) {
            handleApkDownload(url);
            return true;
        }

        return false;
    }

    private void showOfflineError() {
        swipeRefresh.setRefreshing(false);
        progressBar.setVisibility(View.GONE);
        webView.setVisibility(View.GONE);
        layoutError.setVisibility(View.VISIBLE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && pendingPermissionRequest != null) {
                    pendingPermissionRequest.grant(pendingPermissionRequest.getResources());
                    pendingPermissionRequest = null;
                }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && pendingPermissionRequest != null) {
                    pendingPermissionRequest.deny();
                    pendingPermissionRequest = null;
                }
                Toast.makeText(this, "ক্যামেরা ব্যবহারের জন্য পারমিশন প্রয়োজন", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == FILE_CHOOSER_REQUEST_CODE) {
            if (filePathCallback == null) {
                super.onActivityResult(requestCode, resultCode, data);
                return;
            }
            Uri[] results = null;
            if (resultCode == RESULT_OK && data != null) {
                if (data.getData() != null) {
                    results = new Uri[]{data.getData()};
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && data.getClipData() != null) {
                    int count = data.getClipData().getItemCount();
                    results = new Uri[count];
                    for (int i = 0; i < count; i++) {
                        results[i] = data.getClipData().getItemAt(i).getUri();
                    }
                }
            }
            filePathCallback.onReceiveValue(results);
            filePathCallback = null;
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
