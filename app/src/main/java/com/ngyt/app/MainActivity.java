package com.ngyt.app;

import android.annotation.SuppressLint;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.ngyt.app.webview.NGYTWebChromeClient;
import com.ngyt.app.webview.NGYTWebViewClient;

/**
 * NGYT single-activity app — wiring only (YTPro architecture, ad-blocking only).
 *
 * <p>Like YTPro's MainActivity, this class only configures the WebView and
 * attaches dedicated clients from the {@code webview} package:
 * {@link NGYTWebViewClient} owns page/ad-block injection and
 * {@link NGYTWebChromeClient} owns SPA re-injection plus full-screen video.
 *
 * <p>Loads https://m.youtube.com and blocks video ads, banners and overlays.
 * No downloads, background play, Gemini or other YTPro features are included.
 */
public class MainActivity extends AppCompatActivity {

    /** Mobile YouTube URL loaded on startup. */
    private static final String HOME_URL = "https://m.youtube.com";

    private WebView webView;
    private NGYTWebChromeClient chromeClient;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Full-screen: no title bar, hide status bar.
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_main);

        // Short disclaimer shown once at startup.
        Toast.makeText(
                this,
                "NGYT loads YouTube in a WebView. Ads are blocked locally for a cleaner view.",
                Toast.LENGTH_LONG).show();

        webView = findViewById(R.id.webview);
        configureWebView(webView);

        // YTPro-style wiring: page behavior lives in the dedicated clients.
        NGYTWebViewClient viewClient = new NGYTWebViewClient(this, webView);
        webView.setWebViewClient(viewClient);
        chromeClient = new NGYTWebChromeClient(this, webView, viewClient);
        webView.setWebChromeClient(chromeClient);

        // About overlay button: opens the NGYT info dialog.
        ImageButton aboutButton = findViewById(R.id.btn_about);
        aboutButton.setOnClickListener(v -> showAboutDialog());

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
        } else {
            webView.loadUrl(HOME_URL);
        }
    }

    /**
     * Applies the WebView settings needed for modern YouTube mobile.
     */
    private void configureWebView(WebView view) {
        WebSettings settings = view.getSettings();
        settings.setJavaScriptEnabled(true); // Required for YouTube + ad-block injection.
        settings.setDomStorageEnabled(true); // Required for YouTube login/state.
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        }
        // Standard mobile UA keeps m.youtube.com layout; do not override.
        view.setOverScrollMode(View.OVER_SCROLL_NEVER);
    }

    /**
     * Shows the About dialog with the app name, creator and a short description.
     */
    private void showAboutDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.app_name)
                .setMessage(R.string.about_text)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (webView != null) {
            webView.saveState(outState);
        }
    }

    @Override
    public void onBackPressed() {
        // Order: exit full-screen video -> WebView history -> exit app.
        if (chromeClient != null && chromeClient.isCustomViewShown()) {
            chromeClient.onHideCustomView();
        } else if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (chromeClient != null && chromeClient.isCustomViewShown()) {
            chromeClient.onHideCustomView();
        }
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
