package com.videoflow.local;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.webkit.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.webkit.WebViewAssetLoader;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends AppCompatActivity {
    private static final String ORIGIN = "https://appassets.androidplatform.net/";
    private WebView web;
    private Store store;
    private WallpaperGallery wallpapers;
    private ProfileImports profileImports;
    private final ExecutorService io = Executors.newFixedThreadPool(2);
    private final Handler main = new Handler(Looper.getMainLooper());
    private String pickerId, pickerKind, sharedText = "";
    private boolean loaded;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); store = Store.get(this);
        wallpapers = new WallpaperGallery(this);
        profileImports = new ProfileImports(this);
        web = new WebView(this); setContentView(web);
        if ((getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) WebView.setWebContentsDebuggingEnabled(true);
        // Keep system bar insets outside the web UI, including Android 15 edge-to-edge.
        web.setOnApplyWindowInsetsListener((v, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            } else v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        web.setBackgroundColor(0xff0c111c);
        WebSettings settings = web.getSettings(); settings.setJavaScriptEnabled(true); settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false); settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setMediaPlaybackRequiresUserGesture(false); // The local editor controls its muted banner loop.
        WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .addPathHandler("/wallpaper-images/", wallpapers::image)
                .addPathHandler(ProfileImports.PATH, profileImports::open).build();
        web.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) { return loader.shouldInterceptRequest(request.getUrl()); }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (request.getUrl().toString().startsWith(ORIGIN + "assets/")) return false;
                if (request.isForMainFrame() && request.getUrl().getScheme().equals("https")) startActivity(new Intent(Intent.ACTION_VIEW, request.getUrl()));
                return true;
            }
            @Override public void onPageFinished(WebView view, String url) { loaded = true; if (!sharedText.isEmpty()) sendShared(); }
        });
        web.setWebChromeClient(new WebChromeClient());
        web.addJavascriptInterface(new Bridge(), "Native");
        web.loadUrl(ORIGIN + "assets/index.html");
        acceptIntent(getIntent());
        io.submit(() -> {
            try {
                Engine.init(this);
                if (store.settings().optBoolean("autoUpdate", true)) {
                    try { Engine.update(this); } catch (Exception ignored) { /* Bundled engine remains usable offline. */ }
                }
            } catch (Exception ignored) { /* Status is exposed in the UI with a retry action. */ }
        });
    }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); acceptIntent(intent); if (loaded) sendShared(); }
    private void acceptIntent(Intent intent) { if (Intent.ACTION_SEND.equals(intent.getAction())) sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) == null ? "" : intent.getStringExtra(Intent.EXTRA_TEXT); }
    private void sendShared() { String value = sharedText; sharedText = ""; web.evaluateJavascript("window.receiveShared&&window.receiveShared(" + JSONObject.quote(value) + ")", null); }
    private final class Bridge {
        @JavascriptInterface public void request(String id, String method, String payload) {
            main.post(() -> {
                if (web == null || web.getUrl() == null || !web.getUrl().startsWith(ORIGIN + "assets/")) return;
                JSONObject data;
                try { data = new JSONObject(payload); } catch (Exception e) { reply(id, null, "invalid_request"); return; }
                if (Set.of("pick", "play", "share", "open", "clipboard", "cancel", "enqueue", "retry", "confirmClearRemoved", "confirmClearHistory", "forgetCookies").contains(method)) {
                    try { uiRequest(id, method, data); } catch (Exception e) { reply(id, null, Engine.error(e)); }
                } else io.submit(() -> {
                    try { reply(id, background(method, data), null); } catch (Exception e) { reply(id, null, Engine.error(e)); }
                });
            });
        }
    }
    private Object background(String method, JSONObject data) throws Exception {
        switch (method) {
            case "state": {
                JSONObject state = store.snapshot(); Store.put(state, "engine", Engine.status()); Store.put(state, "session", Engine.hasCookies(this)); return state;
            }
            case "analyze": return Engine.analyze(this, data.optString("url"));
            case "settings": store.configure(data); return store.settings();
            case "soundSettings": return store.soundSettings(data);
            case "releaseProfileMedia": profileImports.release(data.optString("path")); return true;
            case "wallpapers": return wallpapers.snapshot();
            case "wallpaperSelect": return wallpapers.select(data.getString("id"));
            case "wallpaperOpacity": return wallpapers.opacity(data.getString("id"), data.get("opacity"));
            case "remove": store.remove(data.optString("id")); return true;
            case "restore": store.restore(data.optString("id")); return true;
            case "clearCompleted": store.clearCompleted(); return true;
            case "update":
                if (store.jobs().stream().anyMatch(j -> Set.of("running", "queued").contains(j.optString("status")))) throw new IllegalArgumentException("wait_jobs");
                Engine.update(this); return Engine.status();
            default: throw new IllegalArgumentException("invalid_request");
        }
    }
    private void uiRequest(String id, String method, JSONObject data) throws Exception {
        switch (method) {
            case "pick": {
                if (pickerId != null) throw new IllegalArgumentException("picker_busy");
                if (data.optString("kind").equals("cookies") && hasActiveJobs()) throw new IllegalArgumentException("wait_jobs");
                pickerId = id; pickerKind = data.optString("kind");
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE);
                if (pickerKind.equals("wallpaper")) {
                    intent.setType("image/*");
                    intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/jpeg", "image/png", "image/webp"});
                } else if (pickerKind.equals("profileAvatar") || pickerKind.equals("profileBanner")) {
                    boolean avatar = pickerKind.equals("profileAvatar");
                    intent.setType(avatar ? "image/*" : "*/*");
                    intent.putExtra(Intent.EXTRA_MIME_TYPES, avatar
                            ? new String[]{"image/jpeg", "image/png", "image/webp", "image/gif"}
                            : new String[]{"image/jpeg", "image/png", "image/webp", "image/gif", "video/mp4", "video/webm"});
                } else {
                    intent.setType(pickerKind.equals("cookies") ? "text/*" : "*/*");
                    if (!pickerKind.equals("cookies")) intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"video/*", "audio/*", "application/ogg"});
                }
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, pickerKind.equals("media"));
                startActivityForResult(intent, 100); return;
            }
            case "clipboard": {
                ClipboardManager manager = getSystemService(ClipboardManager.class);
                reply(id, manager.hasPrimaryClip() ? manager.getPrimaryClip().getItemAt(0).coerceToText(this).toString() : "", null); return;
            }
            case "enqueue": {
                JSONArray items = data.optJSONArray("jobs"); if (items == null || items.length() == 0 || items.length() > 1000) throw new IllegalArgumentException("invalid_request");
                // Validate the full batch before modifying the queue.
                for (int i = 0; i < items.length(); i++) {
                    JSONObject j = items.getJSONObject(i); String kind = j.optString("kind");
                    if (!Set.of("download", "collection", "convert", "compress").contains(kind)) throw new IllegalArgumentException("invalid_request");
                    if (kind.equals("download") || kind.equals("collection")) {
                        String url = Engine.checkedUrl(j.optString("url"));
                        String source = Engine.source(url);
                        if (kind.equals("collection") && !source.equals("YouTube")) throw new IllegalArgumentException("invalid_collection");
                        Store.put(j, "url", url);
                        Store.put(j, "source", source);
                    }
                    else {
                        File input = new File(j.optString("sourcePath")).getCanonicalFile();
                        File allowed = new File(getNoBackupFilesDir(), "inputs").getCanonicalFile();
                        if (!input.getPath().startsWith(allowed.getPath() + File.separator) || !input.isFile()) throw new IllegalArgumentException("input_unavailable");
                    }
                }
                for (int i = 0; i < items.length(); i++) store.add(items.getJSONObject(i));
                requestNotifications(); JobService.start(this); reply(id, items.length(), null); return;
            }
            case "cancel": {
                String task = data.optString("id"); JSONObject j = store.job(task);
                if (j != null && Set.of("queued", "running").contains(j.optString("status"))) {
                    Engine.cancel(task);
                    if (j.optString("status").equals("queued")) store.update(task, "status", "cancelled");
                    else store.update(task, "phase", "cancelled");
                    store.save();
                }
                reply(id, true, null); return;
            }
            case "retry": {
                String task = data.optString("id"); JSONObject j = store.job(task);
                if (j == null || !Set.of("error", "cancelled", "interrupted").contains(j.optString("status"))) throw new IllegalArgumentException("invalid_request");
                store.update(task, "status", "queued"); store.update(task, "error", ""); store.update(task, "progress", 0); store.save();
                requestNotifications(); JobService.start(this); reply(id, true, null); return;
            }
            case "play": case "open": case "share": {
                Uri uri = Uri.parse(data.optString("uri"));
                // Only selected files or media owned by this app may cross the native boundary.
                if (!ownedUri(uri)) throw new IllegalArgumentException("file_unavailable");
                if (method.equals("play")) startActivity(new Intent(this, PlayerActivity.class).setData(uri).putExtra("title", data.optString("name", "VideoFlow")));
                else if (method.equals("share")) startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType(data.optString("mime", "video/*")).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "VideoFlow"));
                else startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(uri, data.optString("mime", "video/*")).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
                reply(id, true, null); return;
            }
            case "confirmClearRemoved": case "confirmClearHistory": {
                boolean removed = method.equals("confirmClearRemoved");
                new AlertDialog.Builder(this).setTitle(store.text(removed ? "¿Vaciar las retiradas?" : "¿Limpiar el historial?", removed ? "Clear removed downloads?" : "Clear history?", removed ? "Esvaziar removidos?" : "Limpar histórico?"))
                    .setMessage(store.text("Solo se borrarán estos registros. Los archivos guardados se conservan." + (removed ? " El historial también se conserva." : ""), "Only these records will be cleared. Saved files are kept." + (removed ? " History is also kept." : ""), "Somente estes registros serão apagados. Os arquivos salvos serão mantidos." + (removed ? " O histórico também será mantido." : "")))
                    .setNegativeButton(store.text("Cancelar", "Cancel", "Cancelar"), (d,w) -> reply(id, false, null))
                    .setPositiveButton(store.text("Vaciar", "Clear", "Esvaziar"), (d,w) -> { if (removed) store.clearRemoved(); else store.clearHistory(); reply(id, true, null); })
                    .setOnCancelListener(d -> reply(id, false, null)).show(); return;
            }
            case "forgetCookies": if (hasActiveJobs()) throw new IllegalArgumentException("wait_jobs"); Engine.forgetCookies(this); reply(id, true, null); return;
            default: throw new IllegalArgumentException("invalid_request");
        }
    }
    private final Set<String> pickedUris = new HashSet<>();
    private boolean hasActiveJobs() { return store.jobs().stream().anyMatch(j -> Set.of("queued", "running").contains(j.optString("status"))); }
    private boolean ownedUri(Uri uri) {
        if (pickedUris.contains(uri.toString())) return true;
        if (!"content".equals(uri.getScheme())) return false;
        for (JSONObject j : store.jobs()) if (containsUri(j.optJSONArray("outputs"), uri.toString())) return true;
        JSONArray history = store.snapshot().optJSONArray("history"); for (int i = 0; i < history.length(); i++) if (containsUri(history.optJSONObject(i).optJSONArray("outputs"), uri.toString())) return true;
        return false;
    }
    private boolean containsUri(JSONArray files, String uri) { if (files != null) for (int i = 0; i < files.length(); i++) if (uri.equals(files.optJSONObject(i).optString("uri"))) return true; return false; }
    private void requestNotifications() { if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101); }
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data); if (requestCode != 100 || pickerId == null) return;
        String id = pickerId, kind = pickerKind; pickerId = null; pickerKind = null;
        if (resultCode != RESULT_OK || data == null) { reply(id, new JSONArray(), null); return; }
        ArrayList<Uri> uris = new ArrayList<>();
        if (data.getClipData() != null) for (int i = 0; i < data.getClipData().getItemCount(); i++) uris.add(data.getClipData().getItemAt(i).getUri());
        else if (data.getData() != null) uris.add(data.getData());
        if (kind.equals("player")) {
            if (!uris.isEmpty()) { pickedUris.add(uris.get(0).toString()); startActivity(new Intent(this, PlayerActivity.class).setData(uris.get(0)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)); }
            reply(id, true, null); return;
        }
        io.submit(() -> {
            try {
                if (kind.equals("wallpaper")) { reply(id, uris.isEmpty() ? null : wallpapers.importPhoto(uris.get(0)), null); }
                else if (kind.equals("profileAvatar") || kind.equals("profileBanner")) {
                    reply(id, uris.isEmpty() ? null : profileImports.copy(uris.get(0), kind.equals("profileAvatar")), null);
                }
                else if (kind.equals("cookies")) { if (!uris.isEmpty()) Engine.importCookies(this, uris.get(0)); reply(id, true, null); }
                else { JSONArray items = new JSONArray(); for (Uri uri : uris) items.put(Engine.importMedia(this, uri)); reply(id, items, null); }
            } catch (Exception e) { reply(id, null, Engine.error(e)); }
        });
    }
    private void reply(String id, Object result, String error) {
        main.post(() -> {
            if (web == null || isFinishing()) return;
            web.evaluateJavascript("window.nativeReply&&window.nativeReply(" + JSONObject.quote(id) + "," + (result == null ? "null" : result instanceof JSONObject || result instanceof JSONArray || result instanceof Boolean || result instanceof Number ? result.toString() : JSONObject.quote(result.toString())) + "," + (error == null ? "null" : JSONObject.quote(error)) + ")", null);
        });
    }
    @Override public void onBackPressed() { if (web != null) web.evaluateJavascript("window.goBack&&window.goBack()", null); else super.onBackPressed(); }
    @Override protected void onDestroy() { if (web != null) { web.removeJavascriptInterface("Native"); web.destroy(); web = null; } profileImports.close(); io.shutdown(); super.onDestroy(); }
}
