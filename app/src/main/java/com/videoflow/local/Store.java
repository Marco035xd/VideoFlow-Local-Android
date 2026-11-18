package com.videoflow.local;

import android.content.Context;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** All records are private to this Android app. Saved media is kept separately in MediaStore. */
public final class Store {
    private static Store instance;
    private static final List<String> SOUNDS = List.of("click", "complete", "error");
    private final AtomicFile file;
    private JSONObject root;
    public static synchronized Store get(Context context) {
        if (instance == null) instance = new Store(context.getApplicationContext());
        return instance;
    }
    private Store(Context context) {
        file = new AtomicFile(new File(context.getFilesDir(), "library.json"));
        try (InputStream stream = file.openRead(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int n; while ((n = stream.read(buffer)) != -1) bytes.write(buffer, 0, n);
            root = new JSONObject(bytes.toString(StandardCharsets.UTF_8.name()));
        }
        catch (Exception e) { root = new JSONObject(); }
        if (!root.has("settings")) put(root, "settings", new JSONObject());
        if (!root.has("jobs")) put(root, "jobs", new JSONArray());
        if (!root.has("history")) put(root, "history", new JSONArray());
        JSONObject settings = root.optJSONObject("settings");
        defaults(settings, "language", "es"); defaults(settings, "theme", "videoflow");
        defaults(settings, "mode", "dark"); defaults(settings, "accent", "theme");
        defaults(settings, "retention", 30); defaults(settings, "concurrent", 2);
        defaults(settings, "connection", "auto"); defaults(settings, "autoUpdate", true);
        defaults(settings, "format", "mp4"); defaults(settings, "quality", "720");
        // Android may have killed the process; never claim a partial file was completed.
        for (JSONObject job : jobs()) {
            if (Set.of("running", "queued").contains(job.optString("status"))) put(job, "status", "interrupted");
        }
        purgeExpired(); save();
    }
    public static void put(JSONObject object, String key, Object value) {
        try { object.put(key, value); } catch (JSONException e) { throw new IllegalArgumentException(e); }
    }
    private static void defaults(JSONObject object, String key, Object value) { if (!object.has(key)) put(object, key, value); }
    public static JSONObject cloneJson(JSONObject value) {
        try { return new JSONObject(value.toString()); } catch (JSONException e) { throw new IllegalArgumentException(e); }
    }
    public synchronized JSONObject settings() { return cloneJson(root.optJSONObject("settings")); }
    public synchronized JSONObject soundSettings(JSONObject changes) {
        for (Iterator<String> keys = changes.keys(); keys.hasNext();) {
            String key = keys.next();
            if (!SOUNDS.contains(key) || !(changes.opt(key) instanceof Boolean)) {
                throw new IllegalArgumentException("invalid_request");
            }
        }
        JSONObject settings = root.optJSONObject("settings");
        JSONObject previous = settings.optJSONObject("sounds");
        JSONObject result = new JSONObject();
        for (String key : SOUNDS) {
            put(result, key, changes.has(key) ? changes.optBoolean(key) : previous != null && previous.optBoolean(key));
        }
        if (changes.length() > 0) {
            put(settings, "sounds", result);
            save();
        }
        return result;
    }
    public synchronized void configure(JSONObject changes) {
        JSONObject settings = root.optJSONObject("settings");
        for (String key : List.of("language", "theme", "mode", "accent", "format", "quality", "connection", "autoUpdate", "concurrent", "retention")) {
            if (changes.has(key)) put(settings, key, changes.opt(key));
        }
        if (!Set.of("es", "en", "pt").contains(settings.optString("language"))) put(settings, "language", "es");
        put(settings, "retention", Set.of(7, 15, 30).contains(settings.optInt("retention")) ? settings.optInt("retention") : 30);
        put(settings, "concurrent", Math.max(1, Math.min(3, settings.optInt("concurrent", 2))));
        purgeExpired(); save();
    }
    public synchronized List<JSONObject> jobs() {
        List<JSONObject> list = new ArrayList<>(); JSONArray jobs = root.optJSONArray("jobs");
        for (int i = 0; i < jobs.length(); i++) if (jobs.optJSONObject(i) != null) list.add(jobs.optJSONObject(i));
        return list;
    }
    public synchronized JSONObject job(String id) { for (JSONObject j : jobs()) if (id.equals(j.optString("id"))) return j; return null; }
    public synchronized JSONObject add(JSONObject options) {
        JSONObject j = cloneJson(options);
        put(j, "id", UUID.randomUUID().toString()); put(j, "status", "queued"); put(j, "progress", 0);
        put(j, "created", System.currentTimeMillis()); put(j, "removedAt", 0);
        root.optJSONArray("jobs").put(j); save(); return cloneJson(j);
    }
    public synchronized void update(String id, String key, Object value) { JSONObject j = job(id); if (j != null) put(j, key, value); }
    public synchronized void finish(String id, JSONArray media) {
        JSONObject j = job(id); if (j == null) return;
        put(j, "status", "completed"); put(j, "progress", 100); put(j, "outputs", media);
        put(j, "error", ""); put(j, "finished", System.currentTimeMillis());
        // One history record per successful job, never per restore.
        JSONArray history = root.optJSONArray("history");
        boolean exists = false; for (int i = 0; i < history.length(); i++) if (id.equals(history.optJSONObject(i).optString("id"))) exists = true;
        if (!exists) { JSONObject h = cloneJson(j); h.remove("sourcePath"); h.remove("cookies"); h.remove("formatIds"); history.put(h); }
        save();
    }
    public synchronized void remove(String id) {
        JSONObject j = job(id);
        if (j != null && !Set.of("queued", "running").contains(j.optString("status"))) { put(j, "removedAt", System.currentTimeMillis()); save(); }
    }
    public synchronized void restore(String id) {
        for (JSONObject j : jobs()) if ((id.equals("all") || id.equals(j.optString("id"))) && j.optLong("removedAt") > 0) put(j, "removedAt", 0);
        save();
    }
    public synchronized void clearRemoved() {
        JSONArray keep = new JSONArray(); for (JSONObject j : jobs()) if (j.optLong("removedAt") == 0) keep.put(j);
        put(root, "jobs", keep); save();
    }
    public synchronized void clearCompleted() { for (JSONObject j : jobs()) if (j.optString("status").equals("completed") && j.optLong("removedAt") == 0) put(j, "removedAt", System.currentTimeMillis()); save(); }
    public synchronized void clearHistory() { put(root, "history", new JSONArray()); save(); }
    public synchronized void purgeExpired() {
        long cutoff = System.currentTimeMillis() - root.optJSONObject("settings").optInt("retention", 30) * 86400000L;
        JSONArray keep = new JSONArray();
        for (JSONObject j : jobs()) if (j.optLong("removedAt") == 0 || j.optLong("removedAt") > cutoff) keep.put(j);
        put(root, "jobs", keep);
    }
    public synchronized JSONObject snapshot() { purgeExpired(); return cloneJson(root); }
    public synchronized void save() {
        FileOutputStream output = null;
        try {
            output = file.startWrite(); output.write(root.toString().getBytes(StandardCharsets.UTF_8)); file.finishWrite(output);
        } catch (IOException e) { if (output != null) file.failWrite(output); throw new IllegalStateException("Cannot save local library", e); }
    }
    public String text(String es, String en, String pt) { String lang = settings().optString("language"); return lang.equals("en") ? en : lang.equals("pt") ? pt : es; }
}
