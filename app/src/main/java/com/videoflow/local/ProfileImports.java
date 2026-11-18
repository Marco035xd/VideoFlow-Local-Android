package com.videoflow.local;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.webkit.WebResourceResponse;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Exposes only user-selected media, until the local profile editor finishes importing it. */
final class ProfileImports {
    static final String PATH = "/profile-import/";
    private final Context context;
    private final Map<String, File> files = new ConcurrentHashMap<>();
    private boolean closed;

    ProfileImports(Context context) {
        this.context = context.getApplicationContext();
    }

    JSONObject copy(Uri uri, boolean avatar) throws Exception {
        if (!"content".equals(uri.getScheme())) throw new IOException("mediaInvalid");
        var resolver = context.getContentResolver();
        long limit = (avatar ? 8 : 25) * 1024L * 1024L;
        String name = "";
        try (Cursor cursor = resolver.query(uri, new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                name = cursor.getString(0);
                if (!cursor.isNull(1) && cursor.getLong(1) > limit) throw new IOException("size");
            }
        }
        File file = File.createTempFile("vf-profile-", ".bin", context.getCacheDir());
        boolean retained = false;
        try {
            try (InputStream input = resolver.openInputStream(uri); FileOutputStream output = new FileOutputStream(file)) {
                if (input == null) throw new IOException("mediaInvalid");
                byte[] buffer = new byte[16384];
                int read;
                long bytes = 0;
                while ((read = input.read(buffer)) != -1) {
                    if ((bytes += read) > limit) throw new IOException("size");
                    output.write(buffer, 0, read);
                }
            }
            String token = java.util.UUID.randomUUID().toString();
            JSONObject result = new JSONObject().put("path", PATH + token)
                    .put("name", name == null ? "" : name).put("mime", resolver.getType(uri));
            synchronized (this) {
                if (closed) throw new IOException("mediaInvalid");
                files.put(token, file);
                retained = true;
            }
            return result;
        } finally {
            if (!retained) file.delete();
        }
    }

    WebResourceResponse open(String token) {
        File file = files.get(token);
        if (file != null) {
            try {
                return new WebResourceResponse("application/octet-stream", null, 200, "OK",
                        Map.of("Cache-Control", "no-store", "X-Content-Type-Options", "nosniff",
                                "Content-Disposition", "attachment"), new FileInputStream(file));
            } catch (IOException ignored) { }
        }
        return new WebResourceResponse("text/plain", null, 404, "Not Found", Map.of(), null);
    }

    void release(String path) {
        if (!path.startsWith(PATH)) return;
        File file = files.remove(path.substring(PATH.length()));
        if (file != null) file.delete();
    }

    synchronized void close() {
        closed = true;
        for (File file : files.values()) file.delete();
        files.clear();
    }
}
