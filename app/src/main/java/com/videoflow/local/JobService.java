package com.videoflow.local;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.media.MediaPlayer;
import android.os.*;
import androidx.core.app.NotificationCompat;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

/** Owns active work independently of the WebView/activity lifecycle. */
public class JobService extends Service {
    private static final String CHANNEL = "videoflow_jobs";
    private static final Set<MediaPlayer> SOUND_PLAYERS = new HashSet<>();
    private final ExecutorService workers = Executors.newFixedThreadPool(3);
    private final Set<String> active = ConcurrentHashMap.newKeySet();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Store store;
    private PowerManager.WakeLock wakeLock;
    private boolean closing;
    public static void start(Context context) { context.startForegroundService(new Intent(context, JobService.class)); }
    @Override public void onCreate() {
        super.onCreate(); store = Store.get(this);
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL, "VideoFlow", NotificationManager.IMPORTANCE_LOW));
        startForeground(10, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        PowerManager pm = getSystemService(PowerManager.class);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "VideoFlow:Jobs"); wakeLock.setReferenceCounted(false); wakeLock.acquire(6 * 60 * 60 * 1000L);
        handler.post(tick);
    }
    private Notification notification() {
        String text = active.isEmpty() ? store.text("Preparando trabajos…", "Preparing jobs…", "Preparando tarefas…") : active.size() + store.text(" trabajos en curso", " jobs running", " tarefas em andamento");
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_download).setContentTitle("VideoFlow Local")
            .setContentText(text).setOngoing(true).setOnlyAlertOnce(true).setContentIntent(open).setProgress(0, 0, true).build();
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) { schedule(); return START_NOT_STICKY; }
    private final Runnable tick = new Runnable() {
        public void run() {
            if (closing) return;
            schedule();
            if (!closing) { getSystemService(NotificationManager.class).notify(10, notification()); handler.postDelayed(this, 1200); }
        }
    };
    private synchronized void schedule() {
        if (closing) return;
        int limit = store.settings().optInt("concurrent", 2);
        for (JSONObject j : store.jobs()) {
            String id = j.optString("id");
            if (active.size() >= limit) break;
            if (!j.optString("status").equals("queued") || j.optLong("removedAt") != 0 || active.contains(id)) continue;
            active.add(id); store.update(id, "status", "running"); store.update(id, "phase", "preparing"); store.save();
            JSONObject snapshot = Store.cloneJson(j); Engine.prepare(id); workers.submit(() -> work(snapshot));
        }
        boolean queued = store.jobs().stream().anyMatch(j -> j.optString("status").equals("queued") && j.optLong("removedAt") == 0);
        if (active.isEmpty() && !queued) { closing = true; stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); }
    }
    private void work(JSONObject job) {
        String id = job.optString("id"); final long[] lastSave = {0};
        Engine.Progress progress = (percent, phase) -> {
            store.update(id, "progress", Math.round(percent)); store.update(id, "phase", phase);
            if (System.currentTimeMillis() - lastSave[0] > 1200) { store.save(); lastSave[0] = System.currentTimeMillis(); }
        };
        try {
            if (job.optString("kind").equals("collection")) {
                List<Engine.CollectionVideo> videos = Engine.collection(this, job.optString("url"), id, false);
                int added = 0;
                for (Engine.CollectionVideo video : videos) {
                    if (Engine.isCancelled(id)) throw new InterruptedException("cancelled");
                    JSONObject child = Store.cloneJson(job); child.remove("id"); child.remove("formatIds");
                    Store.put(child, "kind", "download"); Store.put(child, "url", video.url()); Store.put(child, "title", video.title());
                    Store.put(child, "source", Engine.source(video.url())); Store.put(child, "thumbnail", video.thumbnail()); store.add(child); added++;
                }
                if (added == 0) throw new IllegalArgumentException("empty_collection");
                store.update(id, "count", added); store.finish(id, new JSONArray());
            } else {
                JSONArray result = job.optString("kind").equals("download") ? Engine.download(this, job, progress) : Engine.convert(this, job, progress);
                store.finish(id, result);
            }
            if (!job.optString("kind").equals("collection")) playSound("complete", R.raw.sound_complete);
        } catch (Exception e) {
            boolean cancelled = Engine.isCancelled(id) || e instanceof InterruptedException || e instanceof com.yausername.youtubedl_android.YoutubeDL.CanceledException;
            store.update(id, "status", cancelled ? "cancelled" : "error");
            store.update(id, "error", Engine.error(e)); store.save();
            if (!cancelled) playSound("error", R.raw.sound_error);
        } finally { active.remove(id); handler.post(this::schedule); }
    }
    private void playSound(String key, int resource) {
        handler.post(() -> {
            if (!store.soundSettings(new JSONObject()).optBoolean(key)) return;
            MediaPlayer player = MediaPlayer.create(getApplicationContext(), resource);
            if (player == null) return;
            SOUND_PLAYERS.add(player);
            player.setOnCompletionListener(JobService::releaseSound);
            player.setOnErrorListener((failed, reason, extra) -> { releaseSound(failed); return true; });
            try { player.start(); } catch (IllegalStateException error) { releaseSound(player); }
        });
    }
    private static void releaseSound(MediaPlayer player) {
        if (SOUND_PLAYERS.remove(player)) player.release();
    }
    @Override public void onTimeout(int startId, int fgsType) {
        for (String id : active) { Engine.cancel(id); store.update(id, "status", "interrupted"); store.update(id, "error", "android_timeout"); }
        for (JSONObject j : store.jobs()) if (j.optString("status").equals("queued")) store.update(j.optString("id"), "status", "interrupted");
        store.save(); closing = true; stopForeground(STOP_FOREGROUND_REMOVE); stopSelf();
    }
    @Override public void onDestroy() {
        closing = true; handler.removeCallbacks(tick);
        for (String id : active) Engine.cancel(id);
        workers.shutdown(); if (wakeLock != null && wakeLock.isHeld()) wakeLock.release(); super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
