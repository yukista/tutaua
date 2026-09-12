package com.yukista.tutaua.tdt;

import android.app.Activity;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Android-TV-only, remotely configured TDT player for Tutaua Box. */
public final class MainActivity extends Activity {
    private static final Uri BOX_CONFIG = Uri.parse("content://com.yukista.tutaua.box.config/config");
    private static final String CATALOG_KEY = "tdt.catalog";
    private static final long HEADER_TIMEOUT_MS = 4500;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final List<Channel> channels = new ArrayList<>();
    private final Map<String, List<Programme>> guide = new HashMap<>();
    private ExoPlayer player;
    private FrameLayout root;
    private PlayerView playerView;
    private LinearLayout info;
    private TextView channelName, programme, hint, guideView;
    private int selected = 0, source = 0;
    private String epgUrl = "", playbackStatus = "";
    private boolean guideVisible;
    private ContentObserver observer;
    private final Runnable hideHeader = () -> { if (!guideVisible) info.setVisibility(View.GONE); };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        buildUi();
        player = new ExoPlayer.Builder(this).build();
        player.addListener(new Player.Listener() {
            @Override public void onPlayerError(PlaybackException error) { playbackStatus = "Aquesta font no està disponible"; playNextSource(); }
            @Override public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_BUFFERING) { playbackStatus = "Carregant…"; showHeader(); updateLabels(); }
                if (state == Player.STATE_READY) { playbackStatus = ""; updateLabels(); scheduleHeaderHide(); }
            }
        });
        playerView.setPlayer(player);
        observer = new ContentObserver(main) { @Override public void onChange(boolean selfChange) { loadRemoteConfiguration(); } };
        getContentResolver().registerContentObserver(BOX_CONFIG, false, observer);
        loadRemoteConfiguration();
    }

    private void buildUi() {
        root = new FrameLayout(this);
        playerView = new PlayerView(this); playerView.setUseController(false); root.addView(playerView, fullScreen());
        guideView = text(16, Color.WHITE); guideView.setGravity(Gravity.TOP); guideView.setPadding(dp(48), dp(38), dp(660), dp(38));
        guideView.setBackgroundColor(0xED0B1520); guideView.setVisibility(View.GONE); guideView.setMaxLines(Integer.MAX_VALUE); root.addView(guideView, fullScreen());
        info = new LinearLayout(this); info.setOrientation(LinearLayout.VERTICAL); info.setPadding(dp(36), dp(24), dp(36), dp(24)); info.setBackgroundColor(0xC9000000);
        channelName = text(28, Color.WHITE); programme = text(19, 0xFFE5E5E5); hint = text(14, 0xFFF4C542);
        info.addView(channelName); info.addView(programme); info.addView(hint); root.addView(info, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP)); setContentView(root);
    }
    private FrameLayout.LayoutParams fullScreen() { return new FrameLayout.LayoutParams(-1, -1); }
    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + .5f); }
    private TextView text(int size, int color) { TextView view = new TextView(this); view.setTextSize(size); view.setTextColor(color); view.setMaxLines(3); return view; }

    private void loadRemoteConfiguration() {
        String raw = "";
        try (Cursor cursor = getContentResolver().query(BOX_CONFIG, null, null, null, null)) { if (cursor != null) while (cursor.moveToNext()) if (CATALOG_KEY.equals(cursor.getString(0))) raw = cursor.getString(1); } catch (RuntimeException ignored) { }
        if (raw.isEmpty()) raw = getPreferences(0).getString(CATALOG_KEY, ""); if (raw.isEmpty()) { showEmpty(); return; }
        try {
            JSONObject catalog = new JSONObject(raw); List<Channel> parsed = parseChannels(catalog.optJSONArray("channels")); if (parsed.isEmpty()) { showEmpty(); return; }
            channels.clear(); channels.addAll(parsed); epgUrl = catalog.optString("epg_url", ""); getPreferences(0).edit().putString(CATALOG_KEY, raw).apply();
            selected = Math.min(selected, channels.size() - 1); source = 0; playSelected(); fetchGuide();
        } catch (Exception error) { showEmpty(); }
    }
    private List<Channel> parseChannels(JSONArray values) throws Exception {
        List<Channel> result = new ArrayList<>(); if (values == null) return result;
        for (int i=0;i<values.length();i++) { JSONObject item = values.getJSONObject(i); if (!item.optBoolean("visible", true)) continue; JSONArray streams = item.optJSONArray("streams"); if (streams == null) continue;
            Channel channel = new Channel(item.optString("id"), item.optString("name", "Canal"), item.optString("epg_id"), item.optInt("position", i));
            for (int j=0;j<streams.length();j++) { String url=streams.getJSONObject(j).optString("url"); if (!url.isEmpty()) channel.sources.add(url); } if (!channel.sources.isEmpty()) result.add(channel); }
        Collections.sort(result, Comparator.comparingInt(value -> value.position)); return result;
    }
    private void fetchGuide() {
        if (epgUrl.isEmpty()) { updateLabels(); return; }
        worker.execute(() -> { try { HttpURLConnection connection = (HttpURLConnection) new URL(epgUrl).openConnection(); connection.setConnectTimeout(10000); connection.setReadTimeout(20000);
            StringBuilder body = new StringBuilder(); try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) { String line; while ((line=reader.readLine()) != null) body.append(line); }
            Map<String, List<Programme>> parsed = parseGuide(new JSONArray(body.toString())); main.post(() -> { guide.clear(); guide.putAll(parsed); updateLabels(); }); } catch (Exception ignored) { main.post(this::updateLabels); } });
    }
    private Map<String, List<Programme>> parseGuide(JSONArray values) throws Exception {
        Map<String,List<Programme>> result = new HashMap<>();
        for (int i=0;i<values.length();i++) { JSONObject station=values.getJSONObject(i); List<Programme> events=new ArrayList<>(); JSONArray list=station.optJSONArray("events"); if(list != null) for(int j=0;j<list.length();j++) { JSONObject event=list.getJSONObject(j); events.add(new Programme(event.optLong("hi")*1000, event.optLong("hf")*1000, event.optString("t"))); } result.put(station.optString("name"), events); } return result;
    }
    private void playSelected() { if (channels.isEmpty()) return; Channel channel = channels.get(selected); source = Math.min(source, channel.sources.size()-1); playbackStatus = "Carregant…"; player.setMediaItem(MediaItem.fromUri(channel.sources.get(source))); player.prepare(); player.play(); showHeader(); updateLabels(); }
    private void playNextSource() { if (channels.isEmpty()) return; Channel channel=channels.get(selected); if (++source < channel.sources.size()) playSelected(); else { source=0; playbackStatus="No s'ha pogut reproduir aquest canal"; showHeader(); updateLabels(); } }
    private Programme currentProgramme(Channel channel) { long time = System.currentTimeMillis(); for (Programme item : guide.getOrDefault(channel.epgId, Collections.emptyList())) if (item.start <= time && item.end > time) return item; return null; }
    private Programme nextProgramme(Channel channel) { long time = System.currentTimeMillis(); for (Programme item : guide.getOrDefault(channel.epgId, Collections.emptyList())) if (item.start > time) return item; return null; }
    private void updateLabels() { if (channels.isEmpty()) return; Channel channel=channels.get(selected); Programme now=currentProgramme(channel), next=nextProgramme(channel); channelName.setText(channel.name); programme.setText(now == null ? "Sense guia disponible" : "Ara: " + now.title + (next == null ? "" : "  ·  Després: " + next.title)); hint.setText(playbackStatus.isEmpty() ? "↑ ↓ Canvi de canal   ·   OK Guia   ·   " + (selected+1) + "/" + channels.size() : playbackStatus); if (guideVisible) renderGuide(); }
    private void showHeader() { if (guideVisible) return; info.setVisibility(View.VISIBLE); info.bringToFront(); main.removeCallbacks(hideHeader); }
    private void scheduleHeaderHide() { main.removeCallbacks(hideHeader); if (!guideVisible) main.postDelayed(hideHeader, HEADER_TIMEOUT_MS); }
    private void showEmpty() { showHeader(); channelName.setText("Tutaua TDT"); programme.setText("Esperant la configuració remota de canals"); hint.setText("El gestor Tutaua ha de publicar el catàleg TDT."); }
    private void showGuide() { if (channels.isEmpty()) return; guideVisible=true; main.removeCallbacks(hideHeader); info.setVisibility(View.GONE); guideView.setVisibility(View.VISIBLE); FrameLayout.LayoutParams preview = new FrameLayout.LayoutParams(dp(620), dp(349), Gravity.TOP | Gravity.RIGHT); preview.setMargins(0, dp(34), dp(34), 0); playerView.setLayoutParams(preview); guideView.bringToFront(); playerView.bringToFront(); renderGuide(); }
    private void renderGuide() { if (!guideVisible || channels.isEmpty()) return; StringBuilder value = new StringBuilder("GUIA TUTAUA TDT").append("                         ").append(selected + 1).append("/").append(channels.size()).append("\n"); value.append("↑ ↓ selecciona i reprodueix · OK o Enrere tanca\n\n"); int first = Math.max(0, selected - 4), last = Math.min(channels.size(), selected + 5); if (first > 0) value.append("  …\n"); for (int i=first; i<last; i++) { Channel channel=channels.get(i); Programme now=currentProgramme(channel), next=nextProgramme(channel); value.append(i == selected ? "▶  " : "   ").append(channel.name); if (now != null) value.append("\n     Ara: ").append(now.title); if (next != null) value.append("\n     Després: ").append(next.title); value.append("\n\n"); } if (last < channels.size()) value.append("  …"); guideView.setText(value.toString()); }
    private void hideGuide() { guideVisible=false; guideView.setVisibility(View.GONE); playerView.setLayoutParams(fullScreen()); scheduleHeaderHide(); }
    private void selectOffset(int offset) { if(channels.isEmpty()) return; selected=(selected+offset+channels.size())%channels.size(); source=0; playSelected(); if(guideVisible) renderGuide(); }
    @Override public boolean dispatchKeyEvent(KeyEvent event) { if(event.getAction()!=KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(event); int key=event.getKeyCode(); if(key==KeyEvent.KEYCODE_DPAD_UP){ selectOffset(-1); return true; } if(key==KeyEvent.KEYCODE_DPAD_DOWN){ selectOffset(1); return true; } if(key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER){ if(guideVisible) hideGuide(); else showGuide(); return true; } if(key==KeyEvent.KEYCODE_BACK&&guideVisible){ hideGuide(); return true; } showHeader(); scheduleHeaderHide(); return super.dispatchKeyEvent(event); }
    @Override protected void onStop() { super.onStop(); if(player != null) player.pause(); }
    @Override protected void onStart() { super.onStart(); if(player != null && !channels.isEmpty()) player.play(); }
    @Override protected void onDestroy() { main.removeCallbacks(hideHeader); if(observer!=null)getContentResolver().unregisterContentObserver(observer); worker.shutdownNow(); if(player!=null)player.release(); super.onDestroy(); }
    private static final class Channel { final String id,name,epgId; final int position; final List<String> sources=new ArrayList<>(); Channel(String id,String name,String epgId,int position){this.id=id;this.name=name;this.epgId=epgId;this.position=position;} }
    private static final class Programme { final long start,end; final String title; Programme(long start,long end,String title){this.start=start;this.end=end;this.title=title;} }
}
