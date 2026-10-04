package com.tahen.app;

import android.app.Activity;
import android.content.Intent;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

/**
 * Google Drive bridge placeholder.
 * The web app can call this interface; OAuth/Drive API implementation
 * can be added without changing the HTML interface.
 */
public class DriveBridge {
    private final Activity activity;

    public DriveBridge(Activity activity) {
        this.activity = activity;
    }

    @JavascriptInterface
    public void signIn() {
        Toast.makeText(activity, "سيتم فتح تسجيل الدخول إلى Google عند تجهيز OAuth", Toast.LENGTH_SHORT).show();
        // OAuth flow must be added with a Google Cloud client ID / consent configuration.
    }

    @JavascriptInterface
    public void signOut() {
        Toast.makeText(activity, "تم طلب فصل حساب Google", Toast.LENGTH_SHORT).show();
    }

    @JavascriptInterface
    public String saveBackup(String data, String filename) {
        Toast.makeText(activity, "تم تجهيز ملف النسخ الاحتياطي: " + filename, Toast.LENGTH_SHORT).show();
        return "pending_oauth";
    }

    @JavascriptInterface
    public String restoreBackup(String filename) {
        Toast.makeText(activity, "استعادة النسخة تحتاج تسجيل دخول Google أولاً", Toast.LENGTH_SHORT).show();
        return "";
    }
}
