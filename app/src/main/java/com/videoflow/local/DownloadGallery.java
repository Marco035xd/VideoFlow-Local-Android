package com.videoflow.local;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.util.Size;
import android.webkit.WebResourceResponse;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/** Exposes only local video files registered by the download queue. */
final class DownloadGallery {
    static final String THUMBNAILS = "/download-thumbnails/";
    private final ContentResolver resolver;
    private final Store store;

    DownloadGallery(Context context, Store store) {
        resolver = context.getContentResolver();
        this.store = store;
    }

    JSONObject list() {
        List<JSONObject> registered = store.galleryVideos();
        JSONArray available = new JSONArray();
        for (JSONObject video : registered) {
            Uri uri = Uri.parse(video.optString("uri"));
            if (!"content".equals(uri.getScheme())) continue;
            try (AssetFileDescriptor file = resolver.openAssetFileDescriptor(uri, "r")) {
                if (file != null) available.put(video);
            } catch (IOException | SecurityException ignored) {
                // The video may have been moved or deleted outside the app.
            }
        }
        JSONObject result = new JSONObject();
        Store.put(result, "size", registered.size());
        Store.put(result, "videos", available);
        return result;
    }

    WebResourceResponse thumbnail(String id) {
        String uri = store.galleryUri(id);
        if (!uri.isEmpty()) {
            try {
                Bitmap image = resolver.loadThumbnail(Uri.parse(uri), new Size(480, 270), null);
                try {
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    if (image.compress(Bitmap.CompressFormat.JPEG, 84, bytes)) {
                        return new WebResourceResponse("image/jpeg", null, 200, "OK",
                                Map.of("Cache-Control", "private, max-age=600"),
                                new ByteArrayInputStream(bytes.toByteArray()));
                    }
                } finally { image.recycle(); }
            } catch (IOException | RuntimeException ignored) {
                // The card can still use the play icon as a placeholder.
            }
        }
        return new WebResourceResponse("image/jpeg", null, 404, "Not found", Map.of(), new ByteArrayInputStream(new byte[0]));
    }
}
