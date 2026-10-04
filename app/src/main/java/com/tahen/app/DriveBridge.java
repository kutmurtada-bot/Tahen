package com.tahen.app;

import android.webkit.JavascriptInterface;

public class DriveBridge {
    private final MainActivity activity;

    public DriveBridge(MainActivity activity) {
        this.activity = activity;
    }

    @JavascriptInterface
    public void signIn() {
        activity.startGoogleDriveSignIn();
    }

    @JavascriptInterface
    public void signOut() {
        activity.signOutGoogleDrive();
    }

    @JavascriptInterface
    public void saveBackup(String data, String filename) {
        activity.backupToGoogleDrive(data, filename);
    }

    @JavascriptInterface
    public void restoreBackup(String filename) {
        activity.restoreFromGoogleDrive(filename);
    }

    @JavascriptInterface
    public void printHtml(String html, String title) {
        activity.printHtml(html, title);
    }
}
