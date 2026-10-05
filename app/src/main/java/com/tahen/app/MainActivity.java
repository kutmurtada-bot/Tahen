package com.tahen.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintJob;
import android.print.PrintManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.tasks.Task;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private static final int RC_GOOGLE_SIGN_IN = 7001;
    private static final String DRIVE_FILE_SCOPE =
            "https://www.googleapis.com/auth/drive.file";
    private static final String TAG = "TahenGoogleDrive";

    private WebView webView;
    private WebView printWebView;
    private GoogleSignInAccount googleAccount;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                injectDeleteFamilyFix();
            }
        });
        webView.addJavascriptInterface(new DriveBridge(this), "AndroidGoogleDrive");
        webView.loadUrl("file:///android_asset/index.html");
        setContentView(webView);

        googleAccount = GoogleSignIn.getLastSignedInAccount(this);
    }

    private void injectDeleteFamilyFix() {
        try (InputStream in = getAssets().open("delete-fix.js")) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            String script = new String(out.toByteArray(), StandardCharsets.UTF_8);
            webView.evaluateJavascript(script, null);
        } catch (Exception e) {
            Log.e(TAG, "Failed to inject delete-family fix", e);
        }
    }

    private GoogleSignInOptions googleSignInOptions() {
        return new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestProfile()
                .requestScopes(new Scope(DRIVE_FILE_SCOPE))
                .build();
    }

    public void startGoogleDriveSignIn() {
        runOnUiThread(() -> {
            GoogleSignInClient client = GoogleSignIn.getClient(this, googleSignInOptions());
            client.silentSignIn().addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    GoogleSignInAccount account = task.getResult();
                    if (hasDriveScope(account)) {
                        completeGoogleSignIn(account);
                    } else {
                        startActivityForResult(client.getSignInIntent(), RC_GOOGLE_SIGN_IN);
                    }
                } else {
                    startActivityForResult(client.getSignInIntent(), RC_GOOGLE_SIGN_IN);
                }
            });
        });
    }

    private boolean hasDriveScope(GoogleSignInAccount account) {
        if (account == null || account.getGrantedScopes() == null) return false;
        for (Scope scope : account.getGrantedScopes()) {
            if (DRIVE_FILE_SCOPE.equals(scope.getScopeUri())) return true;
        }
        return false;
    }

    private void completeGoogleSignIn(GoogleSignInAccount account) {
        googleAccount = account;
        Log.d(TAG, "Google sign-in succeeded for " + safeAccountName(account));
        notifyWeb("signin", true, "تم ربط حساب Google: " + safeAccountName(account), "");
    }

    private String safeAccountName(GoogleSignInAccount account) {
        if (account == null) return "";
        if (account.getEmail() != null) return account.getEmail();
        if (account.getDisplayName() != null) return account.getDisplayName();
        return "";
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != RC_GOOGLE_SIGN_IN) return;

        Task<GoogleSignInAccount> task =
                GoogleSignIn.getSignedInAccountFromIntent(data);
        try {
            GoogleSignInAccount account = task.getResult(ApiException.class);
            if (account == null) {
                notifyWeb("signin", false, "Google لم يُرجع حساباً صالحاً.", "");
                return;
            }
            if (!hasDriveScope(account)) {
                notifyWeb("signin", false,
                        "تم اختيار الحساب لكن لم تُمنح صلاحية Google Drive للتطبيق. أعد المحاولة واسمح بالوصول إلى Drive.",
                        "");
                return;
            }
            completeGoogleSignIn(account);
        } catch (ApiException e) {
            int code = e.getStatusCode();
            String codeName = GoogleSignInStatusCodes.getStatusCodeString(code);
            Log.e(TAG, "Google sign-in failed: code=" + code + " (" + codeName + ")", e);
            String message;
            if (code == 10) {
                message = "Google رفض التطبيق بسبب إعداد OAuth. رمز الخطأ 10 (DEVELOPER_ERROR). يجب تسجيل package com.tahen.app مع SHA-1 الخاص بالـAPK في Google Cloud Console، ثم إعادة بناء التطبيق.";
            } else if (code == 12501) {
                message = "تم إلغاء تسجيل الدخول إلى Google.";
            } else if (code == 7) {
                message = "لا يوجد اتصال بالإنترنت. تحقق من الاتصال ثم حاول مرة أخرى.";
            } else {
                message = "فشل ربط Google. رمز الخطأ: " + code + " (" + codeName + ").";
            }
            notifyWeb("signin", false, message, "statusCode=" + code + ";status=" + codeName);
        }
    }

    public void signOutGoogleDrive() {
        GoogleSignInClient client = GoogleSignIn.getClient(
                this,
                new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
        );
        client.signOut().addOnCompleteListener(t -> {
            googleAccount = null;
            notifyWeb("signout", true, "تم فصل حساب Google.", "");
        });
    }

    public void backupToGoogleDrive(String data, String filename) {
        final String payload = data == null ? "" : data;
        final String name = (filename == null || filename.trim().isEmpty())
                ? "FlourDistributionManager_Backup.json" : filename.trim();

        if (googleAccount == null) {
            googleAccount = GoogleSignIn.getLastSignedInAccount(this);
        }
        if (googleAccount == null) {
            notifyWeb("backup", false, "اربط Google Drive أولاً.", "");
            return;
        }

        io.execute(() -> {
            try {
                String token = getAccessToken();
                String existingId = findFile(token, name);
                String id = uploadFile(token, name, payload, existingId);
                notifyWeb("backup", true,
                        "تم حفظ النسخة الاحتياطية في Google Drive.", id);
            } catch (Exception e) {
                notifyWeb("backup", false,
                        "تعذر النسخ إلى Google Drive: " + e.getMessage(), "");
            }
        });
    }

    public void restoreFromGoogleDrive(String filename) {
        final String name = (filename == null || filename.trim().isEmpty())
                ? "FlourDistributionManager_Backup.json" : filename.trim();

        if (googleAccount == null) {
            googleAccount = GoogleSignIn.getLastSignedInAccount(this);
        }
        if (googleAccount == null) {
            notifyWeb("restore", false, "اربط Google Drive أولاً.", "");
            return;
        }

        io.execute(() -> {
            try {
                String token = getAccessToken();
                String id = findFile(token, name);
                if (id == null) throw new Exception("لم يتم العثور على النسخة الاحتياطية.");
                String data = downloadFile(token, id);
                notifyWeb("restore", true, "تمت استعادة النسخة الاحتياطية.", data);
            } catch (Exception e) {
                notifyWeb("restore", false,
                        "تعذرت الاستعادة من Google Drive: " + e.getMessage(), "");
            }
        });
    }

    private String getAccessToken() throws Exception {
        if (googleAccount == null || googleAccount.getAccount() == null) {
            throw new Exception("حساب Google غير متاح.");
        }
        String scope = "oauth2:" + DRIVE_FILE_SCOPE;
        try {
            return GoogleAuthUtil.getToken(this, googleAccount.getAccount(), scope);
        } catch (Exception first) {
            GoogleSignInClient client = GoogleSignIn.getClient(this, googleSignInOptions());
            client.revokeAccess();
            throw first;
        }
    }

    private String findFile(String token, String filename) throws Exception {
        String q = "name='" + filename.replace("'", "\\'") + "' and trashed=false";
        String url = "https://www.googleapis.com/drive/v3/files?q=" +
                URLEncoder.encode(q, "UTF-8") +
                "&spaces=drive&pageSize=1&fields=files(id,name)";

        JSONObject response = requestJson("GET", url, token, null, null);
        JSONArray files = response.optJSONArray("files");
        if (files == null || files.length() == 0) return null;
        return files.getJSONObject(0).optString("id", null);
    }

    private String uploadFile(String token, String filename, String data,
                              String existingId) throws Exception {
        String boundary = "----Tahen" + UUID.randomUUID();
        String metadata = new JSONObject()
                .put("name", filename)
                .put("mimeType", "application/json")
                .toString();

        String endpoint;
        String method;
        if (existingId == null) {
            endpoint = "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart";
            method = "POST";
        } else {
            endpoint = "https://www.googleapis.com/upload/drive/v3/files/" +
                    URLEncoder.encode(existingId, "UTF-8") + "?uploadType=multipart";
            method = "PATCH";
        }

        ByteArrayOutputStream body = new ByteArrayOutputStream();
        writePart(body, boundary, "application/json; charset=UTF-8",
                metadata.getBytes(StandardCharsets.UTF_8));
        writePart(body, boundary, "application/json; charset=UTF-8",
                data.getBytes(StandardCharsets.UTF_8));
        body.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        JSONObject result = requestJson(method, endpoint, token,
                body.toByteArray(), "multipart/related; boundary=" + boundary);
        return result.optString("id", existingId);
    }

    private void writePart(OutputStream out, String boundary,
                           String contentType, byte[] bytes) throws Exception {
        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Type: " + contentType + "\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8));
        out.write(bytes);
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private String downloadFile(String token, String id) throws Exception {
        String url = "https://www.googleapis.com/drive/v3/files/" +
                URLEncoder.encode(id, "UTF-8") + "?alt=media";
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod("GET");
        c.setRequestProperty("Authorization", "Bearer " + token);
        c.setConnectTimeout(20000);
        c.setReadTimeout(30000);
        int code = c.getResponseCode();
        if (code < 200 || code >= 300) {
            throw new Exception(readStream(c.getErrorStream()));
        }
        return readStream(c.getInputStream());
    }

    private JSONObject requestJson(String method, String url, String token,
                                   byte[] body, String contentType) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod(method);
        c.setRequestProperty("Authorization", "Bearer " + token);
        c.setRequestProperty("Accept", "application/json");
        c.setConnectTimeout(20000);
        c.setReadTimeout(30000);

        if (body != null) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", contentType);
            c.setFixedLengthStreamingMode(body.length);
            try (OutputStream out = c.getOutputStream()) {
                out.write(body);
            }
        }

        int code = c.getResponseCode();
        String response = readStream(code >= 200 && code < 300
                ? c.getInputStream() : c.getErrorStream());
        if (code < 200 || code >= 300) {
            throw new Exception("Google Drive HTTP " + code + ": " + response);
        }
        return response.isEmpty() ? new JSONObject() : new JSONObject(response);
    }

    private String readStream(InputStream input) throws Exception {
        if (input == null) return "";
        StringBuilder out = new StringBuilder();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) out.append(line);
        }
        return out.toString();
    }

    private void notifyWeb(String action, boolean ok, String message, String data) {
        runOnUiThread(() -> {
            if (webView == null) return;
            String js = "window.onAndroidDriveResult && window.onAndroidDriveResult(" +
                    JSONObject.quote(action) + "," +
                    ok + "," +
                    JSONObject.quote(message == null ? "" : message) + "," +
                    JSONObject.quote(data == null ? "" : data) + ");";
            webView.evaluateJavascript(js, null);
        });
    }

    public void printHtml(String html, String title) {
        final String document = html == null ? "" : html;
        final String jobName = (title == null || title.trim().isEmpty())
                ? "برنامج توزيع الطحين" : title.trim();

        runOnUiThread(() -> {
            printWebView = new WebView(this);
            WebSettings s = printWebView.getSettings();
            s.setJavaScriptEnabled(false);
            printWebView.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageFinished(WebView view, String url) {
                    PrintManager printManager =
                            (PrintManager) getSystemService(PRINT_SERVICE);
                    if (printManager != null) {
                        PrintDocumentAdapter adapter =
                                view.createPrintDocumentAdapter(jobName);
                        printManager.print(
                                jobName, adapter, new PrintAttributes.Builder().build());
                    }
                }
            });
            printWebView.loadDataWithBaseURL(
                    null, document, "text/html", "UTF-8", null);
        });
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        if (printWebView != null) {
            printWebView.destroy();
            printWebView = null;
        }
        if (webView != null) webView.destroy();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
