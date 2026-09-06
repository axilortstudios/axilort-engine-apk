package com.axilort.engine;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private WebView webView;
    private View splash;
    private ValueCallback<Uri[]> filePathCallback;
    private static final int FILE_CHOOSER_REQUEST = 4101;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);

        splash = createSplash();
        root.addView(splash, new LinearLayout.LayoutParams(-1, -1));

        webView = new WebView(this);
        webView.setVisibility(View.GONE);
        root.addView(webView, new LinearLayout.LayoutParams(-1, -1));
        setContentView(root);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        s.setLoadsImagesAutomatically(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);

        webView.addJavascriptInterface(new AxilortStorage(), "AxilortStorage");
        webView.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                new Handler().postDelayed(() -> {
                    splash.animate().alpha(0f).setDuration(350).withEndAction(() -> {
                        splash.setVisibility(View.GONE);
                        webView.setVisibility(View.VISIBLE);
                    }).start();
                }, 450);
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = callback;
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                startActivityForResult(intent, FILE_CHOOSER_REQUEST);
                return true;
            }
        });
        webView.loadUrl("file:///android_asset/index.html");
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != FILE_CHOOSER_REQUEST || filePathCallback == null) return;
        Uri[] results = null;
        if (resultCode == RESULT_OK && data != null) {
            if (data.getClipData() != null) {
                int n = data.getClipData().getItemCount();
                results = new Uri[n];
                for (int i = 0; i < n; i++) results[i] = data.getClipData().getItemAt(i).getUri();
            } else if (data.getData() != null) {
                results = new Uri[]{data.getData()};
            }
        }
        filePathCallback.onReceiveValue(results);
        filePathCallback = null;
    }

    private File storageRoot() {
        File base = getExternalFilesDir(null);
        if (base == null) base = getFilesDir();
        File root = new File(base, "AxilortEngine");
        if (!root.exists()) root.mkdirs();
        return root;
    }

    private File safeFile(String relative) throws Exception {
        File root = storageRoot().getCanonicalFile();
        File file = new File(root, relative == null ? "" : relative).getCanonicalFile();
        String rp = root.getPath();
        if (!file.getPath().equals(rp) && !file.getPath().startsWith(rp + File.separator)) throw new SecurityException("Invalid path");
        return file;
    }

    public class AxilortStorage {
        @JavascriptInterface public String rootPath() { return storageRoot().getAbsolutePath(); }
        @JavascriptInterface public String list(String relative) {
            try {
                File dir = safeFile(relative);
                JSONArray out = new JSONArray();
                File[] files = dir.listFiles();
                if (files == null) return out.toString();
                for (File f : files) {
                    JSONObject o = new JSONObject();
                    o.put("name", f.getName());
                    o.put("path", relative == null || relative.isEmpty() ? f.getName() : relative + "/" + f.getName());
                    o.put("folder", f.isDirectory());
                    o.put("size", f.isFile() ? f.length() : 0);
                    out.put(o);
                }
                return out.toString();
            } catch (Exception e) { return "[]"; }
        }
        @JavascriptInterface public boolean mkdir(String relative) {
            try { return safeFile(relative).mkdirs(); } catch (Exception e) { return false; }
        }
        @JavascriptInterface public boolean delete(String relative) {
            try {
                File f = safeFile(relative);
                deleteRecursive(f);
                return !f.exists();
            } catch (Exception e) { return false; }
        }
        private void deleteRecursive(File f) {
            if (f.isDirectory()) {
                File[] children = f.listFiles();
                if (children != null) for (File c : children) deleteRecursive(c);
            }
            f.delete();
        }
        @JavascriptInterface public boolean writeBase64(String relative, String data) {
            try {
                File f = safeFile(relative);
                File parent = f.getParentFile();
                if (parent != null) parent.mkdirs();
                byte[] bytes = Base64.decode(data, Base64.DEFAULT);
                try (FileOutputStream out = new FileOutputStream(f)) { out.write(bytes); }
                return true;
            } catch (Exception e) { return false; }
        }
        @JavascriptInterface public String readBase64(String relative) {
            try {
                File f = safeFile(relative);
                if (!f.isFile()) return "";
                try (FileInputStream in = new FileInputStream(f); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    byte[] buf = new byte[8192]; int n;
                    while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                    return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
                }
            } catch (Exception e) { return ""; }
        }
    }

    private View createSplash() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setBackgroundColor(Color.rgb(5,5,5));

        ImageView logo = new ImageView(this);
        try (InputStream input = getAssets().open("images/logo.png")) {
            logo.setImageBitmap(BitmapFactory.decodeStream(input));
        } catch (Exception ignored) {
            logo.setImageResource(com.axilort.engine.R.drawable.axilort_logo);
        }
        logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(180, 180);
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        box.addView(logo, lp);

        TextView title = new TextView(this);
        title.setText("AXILORT ENGINE");
        title.setTextColor(Color.WHITE);
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        box.addView(title, new LinearLayout.LayoutParams(-1, 55));

        TextView studio = new TextView(this);
        studio.setText("AXILORT STUDIOS");
        studio.setTextColor(Color.rgb(150,150,150));
        studio.setTextSize(12);
        studio.setGravity(Gravity.CENTER);
        box.addView(studio, new LinearLayout.LayoutParams(-1, 35));

        ProgressBar progress = new ProgressBar(this);
        progress.setIndeterminate(true);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(42,42);
        pp.gravity = Gravity.CENTER_HORIZONTAL;
        pp.topMargin = 28;
        box.addView(progress, pp);
        return box;
    }

    @Override public void onBackPressed() {
        if (webView != null && webView.getVisibility() == View.VISIBLE && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}
