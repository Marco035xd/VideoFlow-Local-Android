package com.videoflow.local;

import android.app.*;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.*;
import android.util.Rational;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.*;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import java.util.*;

@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public class PlayerActivity extends AppCompatActivity {
    private ExoPlayer player;
    private PlayerView playerView;
    private LinearLayout chrome;
    private boolean leavingForPip;
    private long position;
    private boolean play = true;
    private BroadcastReceiver receiver;
    @Override protected void attachBaseContext(Context base) {
        Configuration config = new Configuration(base.getResources().getConfiguration());
        config.setLocale(Locale.forLanguageTag(Store.get(base).settings().optString("language", "es")));
        super.attachBaseContext(base.createConfigurationContext(config));
    }
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); if (state != null) { position = state.getLong("position"); play = state.getBoolean("play", true); }
        FrameLayout frame = new FrameLayout(this); frame.setBackgroundColor(Color.BLACK); setContentView(frame);
        playerView = new PlayerView(this); frame.addView(playerView, new FrameLayout.LayoutParams(-1, -1));
        playerView.setControllerAutoShow(true); playerView.setKeepScreenOn(true);
        playerView.setShowNextButton(false); playerView.setShowPreviousButton(false);
        chrome = new LinearLayout(this); chrome.setPadding(dp(12), dp(9), dp(12), dp(9)); chrome.setGravity(Gravity.CENTER_VERTICAL); chrome.setBackgroundColor(0xaa0c111c);
        FrameLayout.LayoutParams top = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP); frame.addView(chrome, top);
        Button back = control("‹"); back.setContentDescription(Store.get(this).text("Volver", "Back", "Voltar")); chrome.addView(back, new LinearLayout.LayoutParams(dp(45), dp(42))); back.setOnClickListener(v -> finish());
        TextView title = new TextView(this); title.setText(getIntent().getStringExtra("title") == null ? "VideoFlow Local" : getIntent().getStringExtra("title")); title.setTextColor(Color.WHITE); title.setTextSize(14); title.setPadding(dp(13), 0, dp(10), 0); title.setMaxLines(1); title.setEllipsize(android.text.TextUtils.TruncateAt.END); chrome.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        Button pip = control("PiP"); chrome.addView(pip, new LinearLayout.LayoutParams(dp(62), dp(42))); pip.setOnClickListener(v -> enterPip());
        playerView.setControllerVisibilityListener((PlayerView.ControllerVisibilityListener) visibility -> { if (!isInPictureInPictureMode()) chrome.setVisibility(visibility); });
        receiver = new BroadcastReceiver() { public void onReceive(Context c, Intent i) { if (player != null) { if (player.getPlaybackState() == Player.STATE_ENDED) player.seekTo(0); if (player.isPlaying()) player.pause(); else player.play(); updatePip(); } } };
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, new IntentFilter(getPackageName() + ".PLAY_PAUSE"), Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(receiver, new IntentFilter(getPackageName() + ".PLAY_PAUSE"));
    }
    private int dp(int value) { return (int)(getResources().getDisplayMetrics().density * value + 0.5f); }
    private Button control(String label) {
        Button button = new Button(this); button.setText(label); button.setAllCaps(false); button.setTextSize(14); button.setTextColor(0xffb4caff); button.setMinimumWidth(0); button.setMinWidth(0); button.setPadding(0, 0, 0, 0);
        android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable(); shape.setColor(0xff182131); shape.setCornerRadius(dp(12)); shape.setStroke(dp(1), 0xff354156); button.setBackground(shape); return button;
    }
    @Override protected void onStart() { super.onStart(); if (player == null) {
        player = new ExoPlayer.Builder(this).build(); playerView.setPlayer(player);
        player.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true);
        player.addListener(new Player.Listener() {
            @Override public void onIsPlayingChanged(boolean playing) { updatePip(); }
            @Override public void onVideoSizeChanged(VideoSize size) { updatePip(); }
            @Override public void onPlayerError(PlaybackException error) { Toast.makeText(PlayerActivity.this, Store.get(PlayerActivity.this).text("No se puede reproducir este formato. Prueba convertirlo a MP4.", "Cannot play this format. Try converting it to MP4.", "Não é possível reproduzir este formato. Converta para MP4."), Toast.LENGTH_LONG).show(); }
        });
        player.setMediaItem(MediaItem.fromUri(getIntent().getData())); player.seekTo(position); player.prepare(); player.setPlayWhenReady(play); updatePip();
    } }
    private PictureInPictureParams params() {
        int width = player == null ? 16 : player.getVideoSize().width, height = player == null ? 9 : player.getVideoSize().height;
        float ratio = height > 0 ? width / (float)height : 16f / 9; ratio = Math.max(0.42f, Math.min(2.38f, ratio));
        boolean playing = player != null && player.isPlaying();
        PendingIntent intent = PendingIntent.getBroadcast(this, 2, new Intent(getPackageName() + ".PLAY_PAUSE").setPackage(getPackageName()), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String label = playing ? Store.get(this).text("Pausar", "Pause", "Pausar") : Store.get(this).text("Reproducir", "Play", "Reproduzir");
        RemoteAction action = new RemoteAction(android.graphics.drawable.Icon.createWithResource(this, playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play), label, label, intent);
        PictureInPictureParams.Builder builder = new PictureInPictureParams.Builder().setAspectRatio(new Rational((int)(ratio * 1000), 1000)).setActions(Collections.singletonList(action));
        android.graphics.Rect source = new android.graphics.Rect();
        if (playerView != null && playerView.getGlobalVisibleRect(source)) builder.setSourceRectHint(source);
        if (Build.VERSION.SDK_INT >= 31) builder.setAutoEnterEnabled(playing).setSeamlessResizeEnabled(true);
        return builder.build();
    }
    private void updatePip() { if (!isFinishing()) try { setPictureInPictureParams(params()); } catch (Exception ignored) {} }
    private void enterPip() {
        if (!getPackageManager().hasSystemFeature("android.software.picture_in_picture")) { Toast.makeText(this, Store.get(this).text("PiP no está disponible en este dispositivo.", "PiP is unavailable on this device.", "PiP não está disponível neste dispositivo."), Toast.LENGTH_SHORT).show(); return; }
        try { leavingForPip = enterPictureInPictureMode(params()); } catch (Exception ignored) {}
    }
    @Override protected void onUserLeaveHint() { super.onUserLeaveHint(); if (player != null && player.isPlaying() && Build.VERSION.SDK_INT < 31) enterPip(); }
    @Override public void onPictureInPictureModeChanged(boolean pip, Configuration config) {
        super.onPictureInPictureModeChanged(pip, config); chrome.setVisibility(pip ? View.GONE : View.VISIBLE); playerView.setUseController(!pip); leavingForPip = pip;
    }
    @Override protected void onStop() { super.onStop(); if (!isInPictureInPictureMode()) release(); }
    private void release() { if (player != null) { position = player.getCurrentPosition(); play = player.getPlayWhenReady(); playerView.setPlayer(null); player.release(); player = null; } }
    @Override protected void onSaveInstanceState(Bundle out) { if (player != null) { position = player.getCurrentPosition(); play = player.getPlayWhenReady(); } out.putLong("position", position); out.putBoolean("play", play); super.onSaveInstanceState(out); }
    @Override protected void onDestroy() { release(); unregisterReceiver(receiver); super.onDestroy(); }
}
