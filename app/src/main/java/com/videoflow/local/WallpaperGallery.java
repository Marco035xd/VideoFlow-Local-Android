package com.videoflow.local;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;
import android.provider.OpenableColumns;
import android.util.AtomicFile;
import android.webkit.WebResourceResponse;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Set;

/** Wallpaper files and preferences are private to VideoFlow and survive app updates. */
final class WallpaperGallery {
    private static final Object LOCK = new Object();
    private static final int LIMIT = 15 * 1024 * 1024;
    private static final int DEFAULT_OPACITY = 54;

    private final Context context;
    private final File images;
    private final SharedPreferences preferences;

    WallpaperGallery(Context context) {
        this.context = context.getApplicationContext();
        images = new File(this.context.getFilesDir(), "wallpaper-images");
        preferences = this.context.getSharedPreferences("wallpapers", Context.MODE_PRIVATE);
    }

    private JSONObject load() throws Exception {
        JSONObject gallery = new JSONObject(preferences.getString("gallery", "{}"));
        if (!gallery.has("photos")) gallery.put("photos", new JSONArray());
        if (!gallery.has("active")) gallery.put("active", "");
        return gallery;
    }

    private void save(JSONObject gallery) throws Exception {
        if (!preferences.edit().putString("gallery", gallery.toString()).commit()) {
            throw new IOException("wallpaper_save_failed");
        }
    }

    JSONObject snapshot() throws Exception {
        synchronized (LOCK) { return load(); }
    }

    JSONObject select(String id) throws Exception {
        synchronized (LOCK) {
            JSONObject gallery = load();
            if (!id.isEmpty()) findPhoto(gallery, id);
            gallery.put("active", id);
            save(gallery);
            return gallery;
        }
    }

    JSONObject opacity(String id, Object value) throws Exception {
        if (!(value instanceof Integer) || (int) value < 25 || (int) value > 100) {
            throw new IllegalArgumentException("wallpaper_opacity_invalid");
        }
        synchronized (LOCK) {
            JSONObject gallery = load();
            findPhoto(gallery, id).put("opacity", value);
            save(gallery);
            return gallery;
        }
    }

    private JSONObject findPhoto(JSONObject gallery, String id) throws Exception {
        JSONArray photos = gallery.getJSONArray("photos");
        for (int i = 0; i < photos.length(); i++) {
            JSONObject photo = photos.getJSONObject(i);
            if (id.equals(photo.getString("id"))) return photo;
        }
        throw new IllegalArgumentException("wallpaper_missing");
    }

    JSONObject importPhoto(Uri uri) throws Exception {
        if (!"content".equals(uri.getScheme())) throw new IOException("wallpaper_invalid");
        byte[] photo;
        byte[] thumbnail;
        Bitmap bitmap = decode(uri);
        try {
            photo = encode(bitmap, 86);
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            double scale = Math.min(1, 360.0 / Math.max(width, height));
            Bitmap preview = Bitmap.createScaledBitmap(bitmap, scaled(width, scale), scaled(height, scale), true);
            try { thumbnail = encode(preview, 80); }
            finally { if (preview != bitmap) preview.recycle(); }
        } finally { bitmap.recycle(); }

        StringBuilder id = new StringBuilder();
        for (byte part : MessageDigest.getInstance("SHA-256").digest(photo)) {
            id.append(String.format(Locale.ROOT, "%02x", part & 255));
        }
        String key = id.toString();
        String name = nameOf(uri);
        synchronized (LOCK) {
            JSONObject gallery = load();
            JSONArray previous = gallery.getJSONArray("photos");
            JSONArray updated = new JSONArray();
            JSONObject existing = null;
            for (int i = 0; i < previous.length(); i++) {
                JSONObject entry = previous.getJSONObject(i);
                if (key.equals(entry.getString("id"))) existing = entry;
            }
            if (!images.isDirectory() && !images.mkdirs()) throw new IOException("wallpaper_save_failed");
            File original = new File(images, key + ".webp");
            File thumb = new File(images, key + "-thumb.webp");
            if (!original.isFile()) write(original, photo);
            if (!thumb.isFile()) write(thumb, thumbnail);
            if (existing == null) {
                updated.put(new JSONObject().put("id", key).put("name", name).put("opacity", DEFAULT_OPACITY));
                for (int i = 0; i < previous.length(); i++) updated.put(previous.getJSONObject(i));
                gallery.put("photos", updated);
            }
            gallery.put("active", key);
            save(gallery);
            return gallery;
        }
    }

    private Bitmap decode(Uri uri) throws IOException {
        byte[] data;
        try (InputStream input = context.getContentResolver().openInputStream(uri);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (input == null) throw new IOException("wallpaper_invalid");
            byte[] chunk = new byte[16384];
            int count;
            while ((count = input.read(chunk)) != -1) {
                if (output.size() + count > LIMIT) throw new IOException("wallpaper_too_large");
                output.write(chunk, 0, count);
            }
            data = output.toByteArray();
        }
        try {
            return ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(data)), (decoder, info, source) -> {
                int width = info.getSize().getWidth();
                int height = info.getSize().getHeight();
                if (!Set.of("image/jpeg", "image/png", "image/webp").contains(info.getMimeType())) {
                    throw new IllegalArgumentException("wallpaper_invalid");
                }
                if ((long) width * height > 40_000_000) throw new IllegalArgumentException("wallpaper_too_large");
                double scale = Math.min(1, 2560.0 / Math.max(width, height));
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                decoder.setTargetSize(scaled(width, scale), scaled(height, scale));
            });
        } catch (ImageDecoder.DecodeException error) { throw new IOException("wallpaper_invalid", error); }
    }

    private String nameOf(Uri uri) {
        try (Cursor cursor = context.getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst() && cursor.getString(0) != null) {
                String name = cursor.getString(0).replaceFirst("\\.[^.]+$", "").trim();
                return name.length() > 160 ? name.substring(0, 160) : name;
            }
        }
        return "";
    }

    private static int scaled(int original, double scale) {
        return Math.max(1, (int) Math.round(original * scale));
    }

    private static byte[] encode(Bitmap bitmap, int quality) throws IOException {
        Bitmap.CompressFormat format = Build.VERSION.SDK_INT >= 30
                ? Bitmap.CompressFormat.WEBP_LOSSY : Bitmap.CompressFormat.WEBP;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!bitmap.compress(format, quality, output)) throw new IOException("wallpaper_save_failed");
        return output.toByteArray();
    }

    private static void write(File file, byte[] contents) throws IOException {
        AtomicFile atomic = new AtomicFile(file);
        FileOutputStream stream = atomic.startWrite();
        try {
            stream.write(contents);
            atomic.finishWrite(stream);
        } catch (IOException error) {
            atomic.failWrite(stream);
            throw error;
        }
    }

    WebResourceResponse image(String name) {
        if (name.matches("[a-f0-9]{64}(-thumb)?\\.webp")) {
            try { return new WebResourceResponse("image/webp", null, new FileInputStream(new File(images, name))); }
            catch (IOException ignored) {}
        }
        return new WebResourceResponse(null, null, null);
    }
}
