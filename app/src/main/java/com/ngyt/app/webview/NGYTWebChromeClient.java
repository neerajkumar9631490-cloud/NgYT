package com.ngyt.app.webview;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.FrameLayout;

/**
 * Dedicated WebChromeClient for NGYT (YTPro architecture, ad-blocking only).
 *
 * <p>Two jobs, mirroring {@code YTProWebChromeClient}:
 * <ol>
 *   <li>Re-inject the ad-block script as YouTube navigates its single-page
 *       app (via {@link #onProgressChanged}), delegating to
 *       {@link NGYTWebViewClient} which owns the script.</li>
 *   <li>Host HTML5 full-screen video (the YouTube full-screen button) on top
 *       of the window in immersive landscape, with notch-cutout support on
 *       Android P+ — same approach as YTPro.</li>
 * </ol>
 */
public class NGYTWebChromeClient extends WebChromeClient {

    private final Activity activity;
    private final WebView web;
    private final NGYTWebViewClient viewClient;

    private View customView;
    private CustomViewCallback customViewCallback;
    private int originalSystemUiVisibility;
    private int originalOrientation;

    public NGYTWebChromeClient(Activity activity, WebView web, NGYTWebViewClient viewClient) {
        this.activity = activity;
        this.web = web;
        this.viewClient = viewClient;
    }

    @Override
    public void onProgressChanged(WebView view, int newProgress) {
        // YouTube is a SPA: re-inject while navigating between videos.
        if (newProgress > 50) {
            viewClient.injectAdBlock(view);
        }
    }

    @Override
    public void onShowCustomView(View view, CustomViewCallback callback) {
        // Called when the YouTube full-screen button is tapped.
        if (customView != null) {
            callback.onCustomViewHidden();
            return;
        }
        // YTPro-style: allow drawing into the notch cutout on Android P+.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
            WindowManager.LayoutParams params = activity.getWindow().getAttributes();
            params.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            activity.getWindow().setAttributes(params);
        }
        // Remember orientation, then go immersive landscape like the YouTube app.
        originalOrientation = activity.getRequestedOrientation();
        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);

        customView = view;
        customViewCallback = callback;
        originalSystemUiVisibility = activity.getWindow().getDecorView().getSystemUiVisibility();
        FrameLayout decor = (FrameLayout) activity.getWindow().getDecorView();
        decor.addView(customView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        activity.getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    @Override
    public void onHideCustomView() {
        // Called on video exit / back-press: remove the video view.
        if (customView == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
            WindowManager.LayoutParams params = activity.getWindow().getAttributes();
            params.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT;
            activity.getWindow().setAttributes(params);
        }
        FrameLayout decor = (FrameLayout) activity.getWindow().getDecorView();
        decor.removeView(customView);
        customView = null;
        activity.getWindow().getDecorView().setSystemUiVisibility(originalSystemUiVisibility);
        activity.setRequestedOrientation(originalOrientation);
        if (customViewCallback != null) {
            customViewCallback.onCustomViewHidden();
            customViewCallback = null;
        }
        web.clearFocus();
    }

    /** True while a full-screen video is showing. */
    public boolean isCustomViewShown() {
        return customView != null;
    }
}
