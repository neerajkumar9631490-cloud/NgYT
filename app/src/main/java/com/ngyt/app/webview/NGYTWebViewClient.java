package com.ngyt.app.webview;

import android.app.Activity;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Dedicated WebViewClient owning NGYT's ad-block injection.
 *
 * <p>Mirrors YTPro's architecture ({@code YTProWebViewClient}): page behavior
 * lives here instead of an anonymous class in the activity. Ad-blocking only —
 * none of YTPro's download / background-play / Gemini features are included.
 *
 * <p>Loads {@code assets/adblock.js} lazily and injects it on page start and
 * finish so YouTube video ads, banners and overlays are skipped/hidden. A
 * TrustedTypes default policy is installed first (technique borrowed from
 * YTPro, MIT licensed) so the injection survives YouTube's
 * Content-Security-Policy.
 *
 * <p>Unlike YTPro, the script is bundled locally instead of fetched from a
 * remote CDN, so ad-blocking works offline and needs no extra permissions.
 * For the same reason there is no {@code shouldInterceptRequest} override —
 * no request proxying or CSP stripping is required.
 */
public class NGYTWebViewClient extends WebViewClient {

    /** TrustedTypes passthrough policy, same approach as YTPro's client. */
    private static final String TRUSTED_TYPES_POLICY =
            "if (window.trustedTypes && window.trustedTypes.createPolicy && !window.trustedTypes.defaultPolicy) {"
            + "window.trustedTypes.createPolicy('default',"
            + " {createHTML: (s) => s, createScriptURL: (s) => s, createScript: (s) => s});}";

    private final Activity activity;
    private final WebView web;
    /** Cached ad-block JS, loaded lazily from assets on first injection. */
    private String adBlockJs;

    public NGYTWebViewClient(Activity activity, WebView web) {
        this.activity = activity;
        this.web = web;
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        // Keep all navigation inside this WebView.
        return false;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean shouldOverrideUrlLoading(WebView view, String url) {
        // Old-API fallback: keep navigation inside the WebView.
        return false;
    }

    @Override
    public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
        super.onPageStarted(view, url, favicon);
        injectAdBlock(view);
    }

    @Override
    public void onPageFinished(WebView view, String url) {
        // Install the TrustedTypes policy first (YTPro technique), then the blocker.
        view.evaluateJavascript(TRUSTED_TYPES_POLICY, null);
        injectAdBlock(view);
        super.onPageFinished(view, url);
    }

    /**
     * Injects the cached ad-block script into the page. Safe to call
     * repeatedly; the script guards against double-install, which is what
     * makes YouTube's single-page-app navigation work.
     */
    public void injectAdBlock(WebView view) {
        if (view == null) {
            return;
        }
        if (adBlockJs == null) {
            adBlockJs = loadAssetText("adblock.js");
        }
        if (adBlockJs.isEmpty()) {
            return;
        }
        // Min SDK 21 is above KITKAT, so evaluateJavascript is always available.
        view.evaluateJavascript(adBlockJs, null);
    }

    /** Reads a text file from src/main/assets into a String. */
    private String loadAssetText(String assetName) {
        StringBuilder sb = new StringBuilder();
        try (InputStream is = activity.getAssets().open(assetName);
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        } catch (IOException e) {
            // Leave empty; injection becomes a no-op so the app still runs.
            e.printStackTrace();
        }
        return sb.toString();
    }
}
