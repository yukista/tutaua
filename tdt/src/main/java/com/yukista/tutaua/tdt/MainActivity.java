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
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final List<Channel> channels = new ArrayList<>();
    private final Map<String, List<Programme>> guide = new HashMap<>();
    private ExoPlayer player;
    private TextView channelName, programme, hint, guideView;
    private int selected = 0, source = 0;
    private String epgUrl = "";
    private boolean guideVisible;
    private ContentObserver observer;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        buildUi();
        player = new ExoPlayer.Builder(this).build();
        player.addListener(new Player.Listener() {
            @Override public void onPlayerError(PlaybackException error) { playNextSource(); }
        });
        ((PlayerView) findViewById(1)).setPlayer(player);
        observer = new ContentObserver(main) { @Override public void onChange(boolean selfChange) { loadRemoteConfiguration(); } };
        getContentResolver().registerContentObserver(BOX_CONFIG, false, observer);
        loadRemoteConfiguration();
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        PlayerView video = new PlayerView(this); video.setId(1); video.setUseController(false);
        root.addView(video, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout info = new LinearLayout(this); info.setOrientation(LinearLayout.VERTICAL); info.setPadding(36, 24, 36, 24);
        info.setBackgroundColor(0xB9000000);
        channelName = text(28, Color.WHITE); programme = text(19, 0xFFE5E5E5); hint = text(14, 0xFFF4C542);
        info.addView(channelName); info.addView(programme); info.addView(hint);
        FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP); root.addView(info, ip);
        guideView = text(22, Color.WHITE); guideView.setPadding(48, 36, 48, 36); guideView.setBackgroundColor(0xE91B2633); guideView.setVisibility(View.GONE);
        FrameLayout.LayoutParams gp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM); root.addView(guideView, gp);
        setContentView(root);
    }
    private TextView text(int size, int color) { TextView view = new TextView(this); view.setTextSize(size); view.setTextColor(color); view.setMaxLines(3); return view; }

    private void loadRemoteConfiguration() {
        String raw = "";
        try (Cursor cursor = getContentResolver().query(BOX_CONFIG, null, null, null, null)) {
            if (cursor != null) while (cursor.moveToNext()) if (CATALOG_KEY.equals(cursor.getString(0))) raw = cursor.getString(1);
        } catch (RuntimeException ignored) { }
        if (raw.isEmpty()) raw = getPreferences(0).getString(CATALOG_KEY, "");
        if (raw.isEmpty()) { showEmpty(); return; }
        try {
            JSONObject catalog = new JSONObject(raw);
            List<Channel> parsed = parseChannels(catalog.optJSONArray("channels"));
            if (parsed.isEmpty()) { showEmpty(); return; }
            channels.clear(); channels.addAll(parsed); epgUrl = catalog.optString("epg_url", "");
            getPreferences(0).edit().putString(CATALOG_KEY, raw).apply();
            selected = Math.min(selected, channels.size() - 1); source = 0; playSelected(); fetchGuide();
        } catch (Exception error) { showEmpty(); }
    }

    private List<Channel> parseChannels(JSONArray values) throws Exception {
        List<Channel> result = new ArrayList<>(); if (values == null) return result;
        for (int i=0;i<values.length();i++) {
            JSONObject item = values.getJSONObject(i); if (!item.optBoolean("visible", true)) continue;
            JSONArray streams = item.optJSONArray("streams"); if (streams == null) continue;
            Channel channel = new Channel(item.optString("id"), item.optString("name", "Canal"), item.optString("epg_id"), item.optInt("position", i));
            for (int j=0;j<streams.length();j++) { JSONObject stream=streams.getJSONObject(j); String url=stream.optString("url"); if (!url.isEmpty()) channel.sources.add(url); }
            if (!channel.sources.isEmpty()) result.add(channel);
        }
        Collections.sort(result, Comparator.comparingInt(value -> value.position)); return result;
    }

    private void fetchGuide() {
        if (epgUrl.isEmpty()) { updateLabels(); return; }
        worker.execute(() -> {
            try {
                HttpURLConnection connection = (HttpURLConnection) new URL(epgUrl).openConnection(); connection.setConnectTimeout(10000); connection.setReadTimeout(20000);
                StringBuilder body = new StringBuilder(); try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) { String line; while ((line=reader.readLine()) != null) body.append(line); }
                Map<String, List<Programme>> parsed = parseGuide(new JSONArray(body.toString()));
                main.post(() -> { guide.clear(); guide.putAll(parsed); updateLabels(); });
            } catch (Exception ignored) { main.post(this::updateLabels); }
        });
    }
    private Map<String, List<Programme>> parseGuide(JSONArray values) throws Exception {
        Map<String,List<Programme>> result = new HashMap<>();
        for (int i=0;i<values.length();i++) { JSONObject station=values.getJSONObject(i); List<Programme> events=new ArrayList<>(); JSONArray list=station.optJSONArray("events"); if(list != null) for(int j=0;j<list.length();j++) { JSONObject event=list.getJSONObject(j); events.add(new Programme(event.optLong("hi")*1000, event.optLong("hf")*1000, event.optString("t"))); } result.put(station.optString("name"), events); }
        return result;
    }

    private void playSelected() {
        if (channels.isEmpty()) return; Channel channel = channels.get(selected); source = Math.min(source, channel.sources.size()-1);
        player.setMediaItem(MediaItem.fromUri(channel.sources.get(source))); player.prepare(); player.play(); updateLabels();
    }
    private void playNextSource() { if (channels.isEmpty()) return; Channel channel=channels.get(selected); if (++source < channel.sources.size()) playSelected(); else { source=0; updateLabels(); } }
    private void updateLabels() {
        if (channels.isEmpty()) return; Channel channel=channels.get(selected); channelName.setText(channel.name);
        Programme now=null,next=null; long time=System.currentTimeMillis(); for(Programme item:guide.getOrDefault(channel.epgId, Collections.emptyList())) { if(item.start<=time && item.end>time) now=item; else if(item.start>time && next==null) next=item; }
        programme.setText(now == null ? "Sense guia disponible" : "Ara: " + now.title + (next == null ? "" : "  ·  Després: " + next.title));
        hint.setText("↑ ↓ Canvi de canal   ·   OK Guia   ·   " + (selected+1) + "/" + channels.size());
        if (guideVisible) showGuide();
    }
    private void showEmpty() { channelName.setText("Tutaua TDT"); programme.setText("Esperant la configuració remota de canals"); hint.setText("El gestor Tutaua ha de publicar el catàleg TDT."); }
    private void showGuide() { guideVisible=true; if(channels.isEmpty()) return; Channel channel=channels.get(selected); StringBuilder text=new StringBuilder(channel.name).append("\n\n"); for(Programme item:guide.getOrDefault(channel.epgId, Collections.emptyList())) if(item.end > System.currentTimeMillis()) text.append(android.text.format.DateFormat.format("HH:mm", item.start)).append("  ").append(item.title).append("\n"); guideView.setText(text.toString()); guideView.setVisibility(View.VISIBLE); }
    private void hideGuide() { guideVisible=false; guideView.setVisibility(View.GONE); }
    @Override public boolean dispatchKeyEvent(KeyEvent event) { if(event.getAction()!=KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(event); int key=event.getKeyCode(); if(key==KeyEvent.KEYCODE_DPAD_UP){hideGuide();if(!channels.isEmpty()){selected=(selected+channels.size()-1)%channels.size();source=0;playSelected();}return true;} if(key==KeyEvent.KEYCODE_DPAD_DOWN){hideGuide();if(!channels.isEmpty()){selected=(selected+1)%channels.size();source=0;playSelected();}return true;} if(key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER){if(guideVisible)hideGuide();else showGuide();return true;} if(key==KeyEvent.KEYCODE_BACK&&guideVisible){hideGuide();return true;} return super.dispatchKeyEvent(event); }
    @Override protected void onStop() { super.onStop(); if(player != null) player.pause(); }
    @Override protected void onStart() { super.onStart(); if(player != null && !channels.isEmpty()) player.play(); }
    @Override protected void onDestroy() { if(observer!=null)getContentResolver().unregisterContentObserver(observer); worker.shutdownNow(); if(player!=null)player.release(); super.onDestroy(); }
    private static final class Channel { final String id,name,epgId; final int position; final List<String> sources=new ArrayList<>(); Channel(String id,String name,String epgId,int position){this.id=id;this.name=name;this.epgId=epgId;this.position=position;} }
    private static final class Programme { final long start,end; final String title; Programme(long start,long end,String title){this.start=start;this.end=end;this.title=title;} }
}
