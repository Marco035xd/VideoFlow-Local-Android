package com.videoflow.local;

import android.content.Context;
import android.os.SystemClock;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.util.*;

/** Estimates saved file sizes using collection durations and a bounded sample of selected formats. */
final class CollectionEstimate {
    private static final int SAMPLE_LIMIT = 6;
    private static final long CACHE_MS = 300_000;
    private static final Set<String> BITRATE_FORMATS = Set.of("mp3", "ogg", "m4a", "opus");
    private record Catalog(String url, String connection, long sessionChanged, long expires,
                           List<Engine.CollectionVideo> videos) { }
    private Catalog catalog;

    JSONObject calculate(Context context, JSONObject options) throws Exception {
        String url = Engine.checkedUrl(options.optString("url"));
        if (!Engine.source(url).equals("YouTube")) throw new IllegalArgumentException("invalid_collection");
        String mode = options.optString("mode"), format = options.optString("format");
        boolean audio = mode.equals("audio");
        if (!Set.of("audio", "video", "silent").contains(mode)
                || !(audio ? Set.of("mp3", "ogg", "m4a", "opus", "wav", "flac") : Set.of("mp4", "mkv", "webm", "mov", "avi")).contains(format))
            throw new IllegalArgumentException("invalid_format");
        String connection = Store.get(context).settings().optString("connection");
        long sessionChanged = new File(context.getNoBackupFilesDir(), "session.txt").lastModified();
        String process = "estimate-" + UUID.randomUUID();
        if (catalog == null || !url.equals(catalog.url()) || !connection.equals(catalog.connection())
                || sessionChanged != catalog.sessionChanged() || SystemClock.elapsedRealtime() >= catalog.expires()) {
            var videos = Engine.collection(context, url, process, true);
            catalog = new Catalog(url, connection, sessionChanged, SystemClock.elapsedRealtime() + CACHE_MS, videos);
        }
        List<Engine.CollectionVideo> videos = catalog.videos();
        if (audio && BITRATE_FORMATS.contains(format)) {
            double seconds = 0;
            int count = 0;
            for (Engine.CollectionVideo video : videos) {
                double duration = positive(video.duration());
                if (duration > 0) {
                    seconds += duration;
                    count++;
                }
            }
            return result(seconds * Engine.audioBitrate(options) * 125, count, videos.size(), 0);
        }

        int sampleCount = Math.min(videos.size(), SAMPLE_LIMIT);
        Map<Integer, Engine.CollectionVideo> sample = new LinkedHashMap<>();
        for (int i = 0; i < sampleCount; i++) {
            int index = (int) ((long) i * (videos.size() - 1) / Math.max(1, sampleCount - 1));
            sample.put(index, videos.get(index));
        }
        Map<String, JSONObject> formats = Engine.sampleCollection(context, new ArrayList<>(sample.values()), options, process);
        double sampleBytes = 0, sampleSeconds = 0, remainingSeconds = 0;
        int measured = 0, remaining = 0;
        for (int i = 0; i < videos.size(); i++) {
            Engine.CollectionVideo video = videos.get(i);
            double duration = positive(video.duration());
            if (sample.containsKey(i)) {
                JSONObject info = video.id().isEmpty() ? formats.get(video.url()) : formats.get(video.id());
                if (info == null) continue;
                double measuredDuration = positive(info.optDouble("duration", 0));
                if (measuredDuration > 0) duration = measuredDuration;
                double rate = bytesPerSecond(info, duration, mode, format);
                if (duration > 0 && rate > 0) {
                    sampleSeconds += duration;
                    sampleBytes += duration * rate;
                    measured++;
                }
            } else if (duration > 0) {
                remainingSeconds += duration;
                remaining++;
            }
        }
        if (sampleSeconds == 0 || remaining == 0) return result(sampleBytes, measured, videos.size(), 0);
        return result(sampleBytes + remainingSeconds * sampleBytes / sampleSeconds, measured + remaining, videos.size(), measured);
    }

    private static JSONObject result(double bytes, int estimated, int total, int projectedFrom) {
        JSONObject result = new JSONObject();
        Store.put(result, "bytes", Math.round(bytes));
        Store.put(result, "estimated", estimated);
        Store.put(result, "total", total);
        Store.put(result, "projectedFrom", projectedFrom);
        return result;
    }

    private static double bytesPerSecond(JSONObject info, double duration, String mode, String format) {
        if (mode.equals("audio")) {
            double sampleRate = positive(info.optDouble("asr", 0));
            double channels = positive(info.optDouble("audio_channels", 0));
            double pcm = (sampleRate > 0 ? sampleRate : 48000) * (channels > 0 ? channels : 2) * 2;
            // FLAC size varies by content. Approximate it as 60% of 16-bit PCM.
            return format.equals("wav") ? pcm : pcm * 0.6;
        }
        JSONArray tracks = info.optJSONArray("requested_formats");
        if (tracks == null || tracks.length() == 0) return streamRate(info, duration, mode.equals("silent"));
        double sum = 0;
        for (int i = 0; i < tracks.length(); i++) {
            JSONObject track = tracks.optJSONObject(i);
            if (track == null) return 0;
            double rate = streamRate(track, duration, false);
            if (rate <= 0) return 0;
            sum += rate;
        }
        return sum;
    }

    private static double streamRate(JSONObject stream, double duration, boolean silent) {
        double total = positive(stream.optDouble("tbr", 0));
        double video = positive(stream.optDouble("vbr", 0));
        double audio = positive(stream.optDouble("abr", 0));
        if (silent && !stream.optString("acodec").equals("none")) {
            if (video > 0) return video * 125;
            if (total > audio && audio > 0) return (total - audio) * 125;
        }
        double size = positive(stream.optDouble("filesize", 0));
        if (size == 0) size = positive(stream.optDouble("filesize_approx", 0));
        if (size > 0 && duration > 0) return size / duration;
        return (total > 0 ? total : video + audio) * 125;
    }

    private static double positive(double value) {
        return Double.isFinite(value) && value > 0 ? value : 0;
    }
}
