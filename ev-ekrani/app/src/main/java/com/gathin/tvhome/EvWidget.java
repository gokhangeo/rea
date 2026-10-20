package com.gathin.tvhome;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.recyclerview.widget.RecyclerView;

import com.gokhan.evekrani.Services;

/**
 * Ev Ekranı bölümü: saatin altı ile dock arasındaki boşlukta Fenerbahçe kartı, 3D aile fotoğrafı ve Mersin
 * hava durumu. Kutunun içindeki sunucudan açılan saydam bir WebView (assets/ui/widget.html); ana ekranın
 * geri kalanı cleanyourtv'nin kendi Java kodu olarak kalır.
 *
 * Odak: dock'tan Yukarı ya da üst çubuktan Aşağı basınca bölüme geçer. Bölümün içindeki oklar sayfada
 * işlenir; sayfa kenara gelince Tv.ust() / Tv.dock() ile odağı geri verir. Geri tuşu sayfaya gider.
 */
final class EvWidget {

    /** MainActivity'nin odak yönetimi. */
    interface Host {
        /** Bölüm odağı aldı: dock'un cam vurgusu ve başlığı gizlenir. */
        void onWidgetFocused();
        void focusTopBar();
        void focusDock();
    }

    private static final String TAG = "EvEkrani";
    private static final String YOUTUBE_TV = "com.google.android.youtube.tv";

    private final Activity activity;
    private final FrameLayout root;
    private final Host host;
    private final float density;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private WebView web;
    /** Izgara kaydırılınca bölüm söner; dock yerine dönünce geri gelir. */
    private float scrollAlpha = 1f;
    private int lastTop = -1, lastHeight = -1, lastShelf = -1;

    EvWidget(Activity activity, FrameLayout root, float density, Host host) {
        this.activity = activity;
        this.root = root;
        this.density = density;
        this.host = host;
        create();
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void create() {
        WebView w = new WebView(activity);
        w.setBackgroundColor(Color.TRANSPARENT);
        w.getSettings().setJavaScriptEnabled(true);
        w.getSettings().setDomStorageEnabled(true);
        // CSS pikseli = dp; WebView'ın kendi yakınlaştırması ve yazı büyütmesi kapalı.
        w.getSettings().setUseWideViewPort(false);
        w.getSettings().setLoadWithOverviewMode(false);
        w.getSettings().setTextZoom(100);
        w.setVerticalScrollBarEnabled(false);
        w.setHorizontalScrollBarEnabled(false);
        w.setOverScrollMode(View.OVER_SCROLL_NEVER);
        w.setFocusable(false);
        w.setFocusableInTouchMode(false);
        w.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        w.addJavascriptInterface(new Bridge(), "Tv");
        w.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return true;   // bölüm hiçbir yere gitmez; YouTube Tv.ac ile uygulamada açılır
            }

            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                Log.w(TAG, "bölüm çöktü, yeniden kuruluyor");
                handler.post(() -> {
                    boolean hadFocus = view.hasFocus();
                    root.removeView(view);
                    view.destroy();
                    if (web == view) web = null;
                    create();
                    if (hadFocus) host.focusDock();
                });
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                lastShelf = -1;
                handler.post(EvWidget.this::pushLayout);
            }

            @SuppressWarnings("deprecation")
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                // Sunucu daha açılmadıysa kısa süre sonra yeniden dene.
                handler.postDelayed(() -> { if (web == view) view.loadUrl(Services.WIDGET_URL); }, 1500);
            }
        });
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0);
        lp.leftMargin = lp.rightMargin = (int) (56 * density);
        // Gökyüzü ve renkli parıltının üstünde, dock başlığının (heroName) altında.
        root.addView(w, Math.min(2, root.getChildCount()), lp);
        w.setVisibility(View.INVISIBLE);
        w.loadUrl(Services.WIDGET_URL);
        web = w;
        lastTop = lastHeight = -1;
    }

    /**
     * Bölümü saat satırının altından dock'un hemen üstüne yerleştirir. Sol taraftaki Fenerbahçe kartı dock
     * başlığının üstünde biter (shelf), sağdaki fotoğraf dock'a kadar iner.
     */
    void layout(View header, RecyclerView grid, View heroName, boolean heroShown) {
        if (web == null) return;
        int[] rootLoc = new int[2], loc = new int[2];
        root.getLocationInWindow(rootLoc);
        header.getLocationInWindow(loc);
        int top = loc[1] - rootLoc[1] + header.getHeight() + (int) (16 * density);
        grid.getLocationInWindow(loc);
        int dockTop = loc[1] - rootLoc[1] + grid.getPaddingTop();
        int bottom = dockTop - (int) (18 * density);
        int heroH = heroName.getHeight() > 0 ? heroName.getHeight() : (int) (56 * density);
        int shelf = heroShown ? dockTop - (int) (40 * density) - heroH - (int) (12 * density) : bottom;
        int height = Math.max(0, bottom - top);
        if (top != lastTop || height != lastHeight) {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) web.getLayoutParams();
            lp.topMargin = top;
            lp.height = height;
            web.setLayoutParams(lp);
            lastTop = top;
            lastHeight = height;
        }
        web.setVisibility(height > 140 * density ? View.VISIBLE : View.INVISIBLE);
        int shelfDp = Math.round(Math.max(0, shelf - top) / density);
        if (shelfDp != lastShelf) {
            lastShelf = shelfDp;
            pushLayout();
        }
    }

    private void pushLayout() {
        if (web != null && lastShelf >= 0) web.evaluateJavascript("window.yerlesim&&yerlesim(" + lastShelf + ")", null);
    }

    /** Izgara aşağı kaydıkça bölüm söner ki satırların arkasında kalmasın. */
    void onGridScrolled(RecyclerView grid) {
        if (web == null) return;
        float fade = 60 * density;
        float a = Math.max(0f, Math.min(1f, 1f - grid.computeVerticalScrollOffset() / fade));
        if (a == scrollAlpha) return;
        scrollAlpha = a;
        web.setAlpha(a);
    }

    private boolean usable() {
        return web != null && web.getVisibility() == View.VISIBLE && scrollAlpha > 0.9f && lastHeight > 0;
    }

    /** Odağı bölüme verir; bölüm görünmüyorsa false. */
    boolean focus() {
        if (!usable()) return false;
        host.onWidgetFocused();
        web.setFocusable(true);
        web.setFocusableInTouchMode(true);
        web.requestFocus(View.FOCUS_UP);
        web.evaluateJavascript("window.odaklan&&odaklan()", null);
        return true;
    }

    boolean hasFocus() {
        return web != null && web.hasFocus();
    }

    /** Odak başka yere geçti (Home, bir uygulamadan dönüş): sayfadaki vurgu kalkar. */
    void onFocusLeft() {
        if (web == null) return;
        web.evaluateJavascript("window.birak&&birak()", null);
        handler.post(() -> {
            if (web != null && !web.hasFocus()) {
                web.setFocusable(false);
                web.setFocusableInTouchMode(false);
            }
        });
    }

    /** Geri tuşu bölümdeyken: dönmüş kart varsa düzelir, yoksa odak dock'a iner (sayfa karar verir). */
    boolean handleBack(KeyEvent event) {
        int code = event.getKeyCode();
        if (code != KeyEvent.KEYCODE_BACK && code != KeyEvent.KEYCODE_ESCAPE) return false;
        if (!hasFocus()) return false;
        if (event.getAction() == KeyEvent.ACTION_UP) web.evaluateJavascript("window.geri&&geri()", null);
        return true;
    }

    void resume() {
        if (web != null) web.evaluateJavascript("window.gorundu&&gorundu()", null);
    }

    void destroy() {
        if (web != null) {
            root.removeView(web);
            web.destroy();
            web = null;
        }
    }

    /** YouTube'u kendi TV uygulamasında, yoksa bulunan ilk uygulamada açar. Yalnızca youtube.com adresleri. */
    private void openYoutube(String url) {
        if (url == null || !url.startsWith("https://www.youtube.com/")) return;
        Intent view = new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            activity.startActivity(new Intent(view).setPackage(YOUTUBE_TV));
        } catch (ActivityNotFoundException e) {
            try {
                activity.startActivity(view);
            } catch (ActivityNotFoundException e2) {
                Toast.makeText(activity, R.string.not_available, Toast.LENGTH_SHORT).show();
            }
        }
    }

    /** Sayfanın çağırdığı köprü (window.Tv). JavaScript iş parçacığından gelir; iş ana iş parçacığında yapılır. */
    private final class Bridge {
        @JavascriptInterface
        public void ust() {
            handler.post(() -> leave(host::focusTopBar));
        }

        @JavascriptInterface
        public void dock() {
            handler.post(() -> leave(host::focusDock));
        }

        @JavascriptInterface
        public void ac(String url) {
            handler.post(() -> openYoutube(url));
        }
    }

    private void leave(Runnable target) {
        if (web == null) return;
        target.run();
        if (!web.hasFocus()) {
            web.setFocusable(false);
            web.setFocusableInTouchMode(false);
        }
    }
}
