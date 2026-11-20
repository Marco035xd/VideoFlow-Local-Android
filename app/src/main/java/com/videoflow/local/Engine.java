package com.videoflow.local;

import android.content.*;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import com.yausername.youtubedl_android.*;
import com.yausername.ffmpeg.FFmpeg;
import kotlin.Unit;
import kotlin.jvm.functions.Function3;
import org.json.*;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Android binaries run locally; no PC or remote download server is involved. */
public final class Engine {
    private static final Map<String, List<String>> PLATFORM_DOMAINS = Map.of(
            "YouTube", List.of("youtube.com", "youtu.be", "youtube-nocookie.com"),
            "Facebook", List.of("facebook.com", "fb.watch", "fb.com"),
            "TikTok", List.of("tiktok.com"),
            "Reddit", List.of("reddit.com", "redd.it", "redditmedia.com"),
            "Instagram", List.of("instagram.com", "instagr.am"),
            "X", List.of("x.com", "twitter.com"),
            "Twitch", List.of("twitch.tv"));
    private static final Set<String> SINGLE_VIDEO_POSTS = Set.of("Reddit", "Instagram", "X", "Twitch");
    private static volatile boolean initialized;
    private static volatile String version = "", initializationError = "";
    private static final Map<String, Process> conversions = new ConcurrentHashMap<>();
    private static final Set<String> cancelled = ConcurrentHashMap.newKeySet();
    private static final Object sessionLock = new Object();
    public interface Progress { void changed(float percent, String phase); }
    public static synchronized void init(Context context) throws Exception {
        if (initialized) return;
        try {
            YoutubeDL.getInstance().init(context); FFmpeg.getInstance().init(context);
            File plugin = new File(context.getNoBackupFilesDir(), "plugins/videoflow/yt_dlp_plugins/postprocessor/videoflow_cdn.py"); plugin.getParentFile().mkdirs();
            try (InputStream in = context.getAssets().open("plugins/yt_dlp_plugins/postprocessor/videoflow_cdn.py"); OutputStream out = new FileOutputStream(plugin)) { copy(in, out); }
            YoutubeDLRequest request = new YoutubeDLRequest(Collections.emptyList()); request.addOption("--version");
            version = YoutubeDL.getInstance().execute(request, null, null).getOut().trim();
            initialized = true; initializationError = "";
        } catch (Exception e) { initializationError = e.getMessage() == null ? e.toString() : e.getMessage(); throw e; }
    }
    public static JSONObject status() {
        JSONObject o = new JSONObject(); Store.put(o, "ready", initialized); Store.put(o, "version", version); Store.put(o, "error", initializationError); return o;
    }
    public static synchronized void update(Context context) throws Exception {
        init(context); YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel._STABLE);
        YoutubeDLRequest request = new YoutubeDLRequest(Collections.emptyList()); request.addOption("--version");
        version = YoutubeDL.getInstance().execute(request, null, null).getOut().trim();
    }
    public static String checkedUrl(String raw) {
        String value = raw.trim();
        source(value);
        return value;
    }
    private static boolean matchesDomain(String host, String domain) {
        return host.equals(domain) || host.endsWith("." + domain);
    }
    private static String sourceForHost(String host) {
        for (var platform : PLATFORM_DOMAINS.entrySet()) {
            for (String domain : platform.getValue()) {
                if (matchesDomain(host, domain)) return platform.getKey();
            }
        }
        return null;
    }
    static boolean supportsPlatform(String platform) { return PLATFORM_DOMAINS.containsKey(platform); }

    private static boolean isTwitchClip(String host, String path) {
        if (host.equals("clips.twitch.tv")) {
            return !path.equals("/embed") && path.matches("/(?:[A-Za-z0-9_-]+/)*[A-Za-z0-9_-]+/?");
        }
        return Set.of("twitch.tv", "www.twitch.tv", "go.twitch.tv", "m.twitch.tv").contains(host)
                && path.matches("/(?:[^/]+/)?clip/[A-Za-z0-9_-]+/?");
    }
    public static String source(String url) {
        String value = url.trim();
        if (value.length() > 4096) throw new IllegalArgumentException("invalid_url");
        try {
            URI uri = new URI(value);
            String host = uri.getHost();
            if (host != null && uri.getUserInfo() == null
                    && ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))) {
                host = host.toLowerCase(Locale.ROOT).replaceFirst("\\.$", "");
                String platform = sourceForHost(host);
                String path = uri.getPath();
                if ("Twitch".equals(platform) && !isTwitchClip(host, path)) {
                    throw new IllegalArgumentException("twitch_clip_required");
                }
                if ("X".equals(platform) && !path.matches("/(?:(?:i/web|[^/]+)/status|statuses)/[0-9]+(?:/(?:video|photo)/[0-9]+)?/?")) {
                    throw new IllegalArgumentException("x_post_required");
                }
                if (platform != null) return platform;
            }
        } catch (URISyntaxException ignored) { }
        throw new IllegalArgumentException("invalid_url");
    }
    private static YoutubeDLRequest base(Context context, String url) {
        YoutubeDLRequest request = new YoutubeDLRequest(checkedUrl(url));
        if (SINGLE_VIDEO_POSTS.contains(source(url))) request.addOption("--playlist-items", "1");
        request.addOption("--ignore-config"); request.addOption("--no-warnings"); request.addOption("--socket-timeout", 20);
        request.addOption("--plugin-dirs", new File(context.getNoBackupFilesDir(), "plugins").getAbsolutePath());
        request.addOption("--use-postprocessor", "VideoFlowCDN:when=pre_process");
        request.addOption("--retries", 3); request.addOption("--fragment-retries", 3);
        request.addOption("--retry-sleep", "http:exp=1:8"); request.addOption("--concurrent-fragments", 1);
        request.addOption("--abort-on-unavailable-fragments");
        String connection = Store.get(context).settings().optString("connection");
        if (connection.equals("ipv4")) request.addOption("--force-ipv4"); else if (connection.equals("ipv6")) request.addOption("--force-ipv6");
        return request;
    }
    private static YoutubeDLResponse execute(Context context, YoutubeDLRequest request, String id, Function3<Float, Long, String, Unit> callback) throws Exception {
        File copy = null;
        synchronized (sessionLock) {
            File master = new File(context.getNoBackupFilesDir(), "session.txt");
            if (master.isFile()) {
                copy = File.createTempFile("videoflow-session-", ".txt", context.getCacheDir());
                Files.copy(master.toPath(), copy.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                request.addOption("--cookies", copy.getAbsolutePath());
            }
        }
        // Each subprocess gets its own writable jar. The imported master is never overwritten by yt-dlp.
        try { return YoutubeDL.getInstance().execute(request, id, callback); }
        finally { if (copy != null) copy.delete(); }
    }
    public static JSONObject analyze(Context context, String url) throws Exception {
        String platform = source(url);
        init(context);
        YoutubeDLRequest request = base(context, url); request.addOption("--no-playlist"); request.addOption("--skip-download"); request.addOption("--dump-single-json");
        String out = execute(context, request, "analysis-" + UUID.randomUUID(), null).getOut();
        JSONObject info = new JSONObject(out);
        if (SINGLE_VIDEO_POSTS.contains(platform)) {
            while (info != null && info.optJSONArray("entries") != null) info = info.optJSONArray("entries").optJSONObject(0);
            if (info == null) throw new IllegalArgumentException("post_without_video");
        }
        JSONObject result = new JSONObject();
        for (String key : List.of("title", "duration", "uploader", "thumbnail", "id", "webpage_url")) if (info.has(key)) Store.put(result, key, info.opt(key));
        Store.put(result, "url", url.trim()); Store.put(result, "source", platform);
        JSONArray formats = info.optJSONArray("formats"), tracks = new JSONArray();
        Map<String, JSONObject> groups = new LinkedHashMap<>(); TreeSet<Integer> heights = new TreeSet<>(Comparator.reverseOrder());
        boolean hasVideo = false;
        if (formats != null) for (int i = 0; i < formats.length(); i++) {
            JSONObject f = formats.optJSONObject(i); if (f == null || f.optBoolean("has_drm") || f.optString("url").isEmpty()) continue;
            String codec = f.isNull("vcodec") ? "" : f.optString("vcodec");
            boolean video = !codec.equals("none") && (!codec.isEmpty() || Set.of("mp4", "mov", "mkv", "webm").contains(f.optString("ext")));
            hasVideo |= video;
            int height = f.optInt("height"); if (video && height > 0) heights.add(height);
            if (f.optString("acodec", "none").equals("none")) continue;
            String lang = f.optString("language", ""), note = f.optString("format_note", "").split(",", 2)[0].trim();
            if (note.matches("(?i)(audio[ _-]*)?(low|medium|high|best)|dash audio|audio only|hls|dash|\\d+p(\\d+)?|\\d+(\\.\\d+)?k|drc")) note = "";
            String key = lang + "|" + note;
            JSONObject group = groups.get(key);
            if (group == null) {
                group = new JSONObject(); Store.put(group, "label", note.isEmpty() ? (lang.isEmpty() ? "Audio" : lang) : note + (lang.isEmpty() ? "" : " · " + lang));
                Store.put(group, "language", lang); Store.put(group, "ids", new JSONArray()); groups.put(key, group);
            }
            String id = f.optString("format_id"); if (id.matches("[A-Za-z0-9_.+-]{1,100}")) group.optJSONArray("ids").put(id);
        }
        if (SINGLE_VIDEO_POSTS.contains(platform) && !hasVideo) throw new IllegalArgumentException("post_without_video");
        for (JSONObject group : groups.values()) if (group.optJSONArray("ids").length() > 0) tracks.put(group);
        JSONArray resolutions = new JSONArray(); for (Integer h : heights) resolutions.put(h);
        Store.put(result, "tracks", tracks); Store.put(result, "heights", resolutions); return result;
    }
    public static JSONArray collection(Context context, JSONObject job) throws Exception {
        String url = job.optString("url");
        if (!"YouTube".equals(source(url))) throw new IllegalArgumentException("invalid_collection");
        init(context); YoutubeDLRequest request = base(context, url);
        request.addOption("--yes-playlist"); request.addOption("--flat-playlist"); request.addOption("--skip-download");
        request.addOption("--ignore-errors"); request.addOption("--dump-single-json");
        JSONObject info = new JSONObject(execute(context, request, job.optString("id"), null).getOut());
        JSONArray entries = info.optJSONArray("entries");
        if (entries == null || entries.length() == 0) throw new IllegalArgumentException("empty_collection");
        return entries;
    }
    public static JSONArray download(Context context, JSONObject job, Progress progress) throws Exception {
        init(context); String id = job.optString("id"); File work = workDir(context, id);
        String url = job.optString("url");
        YoutubeDLRequest request = base(context, url);
        if (cancelled.contains(id)) throw new InterruptedException("cancelled");
        request.addOption("--no-playlist"); request.addOption("--newline"); request.addOption("--progress"); request.addOption("--no-quiet"); request.addOption("--no-mtime");
        request.addOption("--no-overwrites"); request.addOption("--restrict-filenames"); request.addOption("--trim-filenames", 160);
        request.addOption("-o", new File(work, "%(title).120B [%(id)s].%(ext)s").getAbsolutePath());
        request.addCommands(List.of("--print-to-file", "after_move:%(filepath)s", new File(work, "outputs.txt").getAbsolutePath()));
        String format = job.optString("format", "mp4"), mode = job.optString("mode", "video"), quality = job.optString("quality", "720");
        if (!Set.of("mp4", "mkv", "webm", "mov", "avi", "mp3", "ogg", "m4a", "flac", "wav", "opus").contains(format)) throw new IllegalArgumentException("invalid_format");
        String height = quality.matches("\\d{2,4}") ? "[height<=" + quality + "]" : "";
        JSONArray ids = job.optJSONArray("formatIds"); String track = "";
        if (ids != null && ids.length() > 0) {
            List<String> valid = new ArrayList<>();
            for (int i = 0; i < ids.length(); i++) { String fid = ids.optString(i); if (fid.matches("[A-Za-z0-9_.+-]{1,100}")) valid.add(fid.replace(".", "\\.").replace("+", "\\+")); }
            if (valid.isEmpty()) throw new IllegalArgumentException("invalid_track");
            track = "[format_id~='^(" + String.join("|", valid) + ")$']";
        }
        boolean audio = mode.equals("audio");
        String selector;
        if (audio) selector = "bestaudio" + track + "/best" + track;
        else if (mode.equals("silent")) selector = "bestvideo" + height + "/best" + height;
        else if (!track.isEmpty()) selector = "bestvideo" + height + "+bestaudio" + track + "/best" + height + track;
        else if (format.equals("mp4")) selector = "bestvideo" + height + "[ext=mp4]+bestaudio[ext=m4a]/best" + height + "[ext=mp4]/bestvideo" + height + "+bestaudio/best" + height;
        else selector = "bestvideo" + height + "+bestaudio/best" + height;
        if (mode.equals("video") && track.isEmpty() && SINGLE_VIDEO_POSTS.contains(source(url))) selector += "/bestvideo" + height;
        request.addOption("-f", selector);
        if (audio) {
            request.addOption("-x"); request.addOption("--audio-format", format.equals("ogg") ? "vorbis" : format);
            String bitrate = job.optString("bitrate", "192"); if (!Set.of("128", "192", "256", "320").contains(bitrate)) bitrate = "192";
            request.addOption("--audio-quality", bitrate + "K");
        } else {
            String container = Set.of("mov", "avi").contains(format) ? "mkv" : format;
            request.addOption("--merge-output-format", container);
            request.addOption("--recode-video", format);
        }
        request.addOption("--no-simulate");
        try {
            execute(context, request, id, (percent, eta, line) -> { progress.changed(Math.max(0, Math.min(99, percent)), line.startsWith("[videoflow]") ? "reconnecting" : line.startsWith("[download]") ? "downloading" : "processing"); return Unit.INSTANCE; });
        } catch (Exception e) {
            if (cancelled.contains(id)) throw new InterruptedException("cancelled");
            String msg = String.valueOf(e.getMessage()).toLowerCase(Locale.ROOT);
            if (Store.get(context).settings().optString("connection").equals("auto") && (msg.contains("timed out") || msg.contains("connection reset") || msg.contains("connection refused") || msg.contains("unreachable"))) {
                progress.changed(0, "reconnecting"); request.addOption("--force-ipv4");
                execute(context, request, id, (percent, eta, line) -> { progress.changed(Math.max(0, Math.min(99, percent)), line.startsWith("[videoflow]") ? "reconnecting" : "downloading"); return Unit.INSTANCE; });
            } else throw e;
        }
        if (cancelled.contains(id)) throw new InterruptedException("cancelled");
        List<File> outputs = new ArrayList<>(); File list = new File(work, "outputs.txt");
        if (list.isFile()) for (String line : Files.readAllLines(list.toPath(), StandardCharsets.UTF_8)) {
            File f = new File(line.trim()).getCanonicalFile();
            if (f.getParentFile().equals(work.getCanonicalFile()) && f.isFile() && f.length() > 0 && !outputs.contains(f)) outputs.add(f);
        }
        if (outputs.isEmpty()) throw new IOException("no_output");
        JSONArray result = new JSONArray();
        for (File output : outputs) {
            if (mode.equals("silent")) {
                File silent = new File(work, "videoflow-silent." + format);
                runFfmpeg(context, id, List.of("-y", "-nostdin", "-hide_banner", "-i", output.getAbsolutePath(), "-map", "0:v:0", "-c:v", "copy", "-an", "-progress", "pipe:1", "-nostats", silent.getAbsolutePath()), 1, progress);
                if (!silent.isFile() || silent.length() == 0) throw new IOException("no_output");
                if (!output.delete() || !silent.renameTo(output)) throw new IOException("save_failed");
            }
            double duration = mode.equals("audio") ? 0 : videoDuration(output);
            JSONObject published = publish(context, output, id);
            if (duration > 0) Store.put(published, "duration", duration);
            result.put(published);
        }
        return result;
    }
    private static double videoDuration(File file) {
        try (MediaMetadataRetriever retriever = new MediaMetadataRetriever()) {
            retriever.setDataSource(file.getAbsolutePath());
            return Long.parseLong(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)) / 1000.0;
        } catch (IOException | RuntimeException ignored) {
            return 0;
        }
    }
    public static File workDir(Context context, String id) {
        if (!id.matches("[a-f0-9-]{36}")) throw new IllegalArgumentException("invalid_id");
        File dir = new File(context.getNoBackupFilesDir(), "work/" + id); dir.mkdirs(); return dir;
    }
    public static JSONObject importMedia(Context context, Uri uri) throws Exception {
        String name = "media";
        try (Cursor c = context.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) { if (c != null && c.moveToFirst()) name = c.getString(0); }
        String safe = name.replaceAll("[^\\p{L}\\p{N} ._()-]", "_"); if (safe.length() > 140) safe = safe.substring(safe.length() - 140);
        File dir = new File(context.getNoBackupFilesDir(), "inputs/" + UUID.randomUUID()); dir.mkdirs(); File file = new File(dir, safe);
        try (InputStream in = context.getContentResolver().openInputStream(uri); OutputStream out = new FileOutputStream(file)) { if (in == null) throw new IOException("input_unavailable"); copy(in, out); }
        if (file.length() == 0) throw new IOException("input_empty");
        JSONObject item = new JSONObject(); Store.put(item, "name", name); Store.put(item, "sourcePath", file.getAbsolutePath()); Store.put(item, "bytes", file.length());
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try { retriever.setDataSource(file.getAbsolutePath()); Store.put(item, "duration", Long.parseLong(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)) / 1000.0); }
        catch (Exception ignored) {} finally { retriever.release(); }
        return item;
    }
    public static JSONArray convert(Context context, JSONObject job, Progress progress) throws Exception {
        init(context); String id = job.optString("id"); File input = new File(job.optString("sourcePath")).getCanonicalFile();
        if (cancelled.contains(id)) throw new InterruptedException("cancelled");
        File allowed = new File(context.getNoBackupFilesDir(), "inputs").getCanonicalFile();
        if (!input.getPath().startsWith(allowed.getPath() + File.separator) || !input.isFile()) throw new IOException("input_unavailable");
        String format = job.optString("format", "mp4"), kind = job.optString("kind");
        if (!Set.of("mp4", "mkv", "mov", "webm", "avi", "mp3", "ogg", "m4a", "flac", "wav", "opus").contains(format)) throw new IllegalArgumentException("invalid_format");
        String title = job.optString("title", "VideoFlow").replaceAll("[\\\\/:*?\"<>|]", "_").replaceFirst("\\.[^.]+$", "");
        if (title.length() > 100) title = title.substring(0, 100);
        File output = new File(workDir(context, id), title + (kind.equals("compress") ? " - VideoFlow" : " - " + format.toUpperCase(Locale.ROOT)) + "." + format);
        List<String> command = new ArrayList<>(List.of("-y", "-nostdin", "-hide_banner", "-i", input.getAbsolutePath(), "-map_metadata", "-1"));
        boolean audio = Set.of("mp3", "ogg", "m4a", "flac", "wav", "opus").contains(format);
        if (audio) {
            command.addAll(List.of("-vn", "-map", "0:a:0", "-ac", "2"));
            String codec = Map.of("mp3", "libmp3lame", "ogg", "libvorbis", "m4a", "aac", "flac", "flac", "wav", "pcm_s16le", "opus", "libopus").get(format);
            command.addAll(List.of("-c:a", codec));
            if (!Set.of("wav", "flac").contains(format)) command.addAll(List.of("-b:a", Integer.toString(Math.max(64, Math.min(320, job.optInt("bitrate", 192)))) + "k"));
        } else {
            command.addAll(List.of("-map", "0:v:0", "-map", "0:a:0?", "-c:v", format.equals("webm") ? "libvpx-vp9" : format.equals("avi") ? "mpeg4" : "libx264"));
            if (format.equals("webm")) command.addAll(List.of("-b:v", "0", "-crf", "34", "-deadline", "realtime", "-cpu-used", "5"));
            else if (format.equals("avi")) command.addAll(List.of("-q:v", "5"));
            else command.addAll(List.of("-preset", "veryfast", "-crf", kind.equals("compress") && Set.of("24", "28", "32").contains(job.optString("level")) ? job.optString("level") : "22", "-pix_fmt", "yuv420p"));
            command.addAll(List.of("-c:a", format.equals("webm") ? "libopus" : format.equals("avi") ? "libmp3lame" : "aac", "-b:a", "128k"));
            String height = job.optString("height", "keep");
            if (Set.of("1080", "720", "480").contains(height)) command.addAll(List.of("-vf", "scale=-2:'min(" + height + ",ih)':force_divisible_by=2"));
            if (Set.of("mp4", "mov").contains(format)) command.addAll(List.of("-movflags", "+faststart"));
        }
        command.addAll(List.of("-progress", "pipe:1", "-nostats", output.getAbsolutePath()));
        runFfmpeg(context, id, command, Math.max(1, job.optDouble("duration", 1)), progress);
        if (cancelled.contains(id)) throw new InterruptedException("cancelled");
        if (!output.isFile() || output.length() == 0) throw new IOException("no_output");
        if (kind.equals("compress") && output.length() >= input.length()) { output.delete(); throw new IOException("already_compressed"); }
        JSONArray media = new JSONArray(); media.put(publish(context, output, id)); return media;
    }
    private static void runFfmpeg(Context context, String id, List<String> arguments, double duration, Progress progress) throws Exception {
        List<String> command = new ArrayList<>(); command.add(new File(context.getApplicationInfo().nativeLibraryDir, "libffmpeg.so").getAbsolutePath()); command.addAll(arguments);
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
        File packages = new File(context.getNoBackupFilesDir(), "youtubedl-android/packages");
        builder.environment().put("LD_LIBRARY_PATH", new File(packages, "ffmpeg/usr/lib").getAbsolutePath() + ":" + new File(packages, "python/usr/lib").getAbsolutePath());
        builder.environment().put("TMPDIR", context.getCacheDir().getAbsolutePath());
        Process process = builder.start(); conversions.put(id, process); StringBuilder errors = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (cancelled.contains(id)) { process.destroy(); throw new InterruptedException("cancelled"); }
                if (line.startsWith("out_time_us=")) try { progress.changed((float)Math.min(99, Long.parseLong(line.substring(12)) / 1000000.0 / duration * 100), "processing"); } catch (NumberFormatException ignored) {}
                else { errors.append(line).append('\n'); if (errors.length() > 6000) errors.delete(0, errors.length() - 6000); }
            }
            if (process.waitFor() != 0) throw new IOException(errors.toString());
        } finally { conversions.remove(id); if (process.isAlive()) process.destroy(); }
    }
    public static void cancel(String id) { cancelled.add(id); YoutubeDL.getInstance().destroyProcessById(id); Process p = conversions.get(id); if (p != null) p.destroy(); }
    public static void prepare(String id) { cancelled.remove(id); }
    public static boolean isCancelled(String id) { return cancelled.contains(id); }
    private static JSONObject publish(Context context, File file, String id) throws Exception {
        if (cancelled.contains(id)) throw new InterruptedException("cancelled");
        ContentResolver resolver = context.getContentResolver(); ContentValues values = new ContentValues();
        String ext = file.getName().substring(file.getName().lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        String mime = Map.ofEntries(Map.entry("mp4", "video/mp4"), Map.entry("mkv", "video/x-matroska"), Map.entry("mov", "video/quicktime"), Map.entry("avi", "video/x-msvideo"), Map.entry("webm", "video/webm"), Map.entry("mp3", "audio/mpeg"), Map.entry("ogg", "audio/ogg"), Map.entry("m4a", "audio/mp4"), Map.entry("flac", "audio/flac"), Map.entry("wav", "audio/wav"), Map.entry("opus", "audio/ogg")).getOrDefault(ext, "application/octet-stream");
        values.put(MediaStore.Downloads.DISPLAY_NAME, file.getName()); values.put(MediaStore.Downloads.MIME_TYPE, mime);
        values.put(MediaStore.Downloads.RELATIVE_PATH, "Download/VideoFlow"); values.put(MediaStore.Downloads.IS_PENDING, 1);
        Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values); if (uri == null) throw new IOException("save_failed");
        try {
            try (InputStream in = new FileInputStream(file); OutputStream out = resolver.openOutputStream(uri)) { if (out == null) throw new IOException("save_failed"); copy(in, out); }
            values.clear(); values.put(MediaStore.Downloads.IS_PENDING, 0); resolver.update(uri, values, null, null);
        } catch (Exception e) { resolver.delete(uri, null, null); throw e; }
        JSONObject result = new JSONObject(); Store.put(result, "uri", uri.toString()); Store.put(result, "name", file.getName()); Store.put(result, "bytes", file.length()); Store.put(result, "mime", mime);
        file.delete(); return result;
    }
    public static void importCookies(Context context, Uri uri) throws Exception {
        String text;
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IOException("cookie_invalid"); ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int n;
            while ((n = in.read(buffer)) > 0) { out.write(buffer, 0, n); if (out.size() > 2097152) throw new IOException("cookie_invalid"); }
            text = out.toString(StandardCharsets.UTF_8.name()).replace("\ufeff", "");
        }
        if (!text.startsWith("# Netscape HTTP Cookie File") && !text.startsWith("# HTTP Cookie File")) throw new IOException("cookie_invalid");
        StringBuilder accepted = new StringBuilder("# Netscape HTTP Cookie File\n"); int count = 0;
        for (String line : text.split("\\r?\\n")) {
            String item = line.startsWith("#HttpOnly_") ? line.substring(10) : line;
            if (item.startsWith("#") || item.trim().isEmpty()) continue;
            String[] fields = item.split("\\t", -1); if (fields.length != 7) throw new IOException("cookie_invalid");
            String host = fields[0].replaceFirst("^\\.", "").toLowerCase(Locale.ROOT);
            if (!host.matches("[a-z0-9.-]+") || !Set.of("TRUE", "FALSE").contains(fields[1]) || !Set.of("TRUE", "FALSE").contains(fields[3])) throw new IOException("cookie_invalid");
            if (!fields[4].isEmpty() && !fields[4].matches("\\d{1,12}")) throw new IOException("cookie_invalid");
            if (!fields[4].isEmpty() && !fields[4].equals("0") && Long.parseLong(fields[4]) < System.currentTimeMillis() / 1000) continue;
            if (sourceForHost(host) != null || matchesDomain(host, "googlevideo.com")) {
                accepted.append(line).append('\n');
                count++;
            }
        }
        if (count == 0) throw new IOException("cookie_invalid");
        synchronized (sessionLock) { Files.write(new File(context.getNoBackupFilesDir(), "session.txt").toPath(), accepted.toString().getBytes(StandardCharsets.UTF_8)); }
    }
    public static boolean hasCookies(Context context) { return new File(context.getNoBackupFilesDir(), "session.txt").isFile(); }
    public static void forgetCookies(Context context) { synchronized (sessionLock) { new File(context.getNoBackupFilesDir(), "session.txt").delete(); } }
    public static String error(Exception exception) {
        String text = String.valueOf(exception.getMessage()), lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("no video in this post") || lower.contains("no media found")) return "post_without_video";
        if (lower.contains("sign in") || lower.contains("not a bot") || lower.contains("login required")
                || lower.contains("authentication is required") || lower.contains("requires authentication")
                || lower.contains("rate-limit for accessing posts anonymously") || lower.contains("logged-in")
                || lower.contains("unable to access the reddit api")) return "session_required";
        if (lower.contains("timed out") || lower.contains("network is unreachable") || lower.contains("connection refused") || lower.contains("connection reset") || lower.contains("unable to resolve")) return "network_error";
        if (lower.contains("requested format is not available")) return "format_unavailable";
        if (lower.contains("cannot parse data")) return "session_required";
        if (lower.contains("private video") || lower.contains("video unavailable") || lower.contains("not available")) return "video_unavailable";
        if (lower.contains("no space left")) return "no_space";
        if (text.matches("[a-z_]{3,40}")) return text;
        return text.length() > 900 ? text.substring(text.length() - 900) : text;
    }
    private static void copy(InputStream in, OutputStream out) throws IOException { byte[] buffer = new byte[262144]; int n; while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n); }
}
