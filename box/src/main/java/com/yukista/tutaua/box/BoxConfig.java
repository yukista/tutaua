package com.yukista.tutaua.box;

import android.content.Context;
import android.content.SharedPreferences;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

final class BoxConfig {
    static final String JELLYFIN_URL = "jellyfin.base_url";
    static final String TV_API_URL = "tv.api_base_url";
    static final String TV_STREAM_URL = "tv.stream_url";
    static final String UPDATE_MANIFEST_URL = "updates.manifest_url";
    static final String UPDATE_CHANNEL = "updates.channel";
    static final String SCREEN_TIMEOUT_MINUTES = "device.screen_timeout_minutes";
    static final String LIBRARY_KEYCODE = "remote.library_keycode";
    static final String LIVE_TV_KEYCODE = "remote.live_tv_keycode";
    static final String GAMES_KEYCODE = "remote.games_keycode";
    static final String GAMES_ENABLED = "games.enabled";
    static final int DEFAULT_LIBRARY_KEYCODE = 132; // KEYCODE_F2 on the validated TX5 remote.
    static final int DEFAULT_LIVE_TV_KEYCODE = 134; // KEYCODE_F4: physical YouTube button on the validated TX5 remote.
    static final int DEFAULT_GAMES_KEYCODE = 0;
    static final boolean DEFAULT_GAMES_ENABLED = true;
    private static final String STORE = "box_config_v1";

    private BoxConfig() {}

    static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(STORE, Context.MODE_PRIVATE);
    }

    static Map<String, String> values(Context context) {
        SharedPreferences preferences = preferences(context);
        Map<String, String> values = new LinkedHashMap<>();
        values.put(JELLYFIN_URL, preferences.getString(JELLYFIN_URL, ""));
        values.put(TV_API_URL, preferences.getString(TV_API_URL, ""));
        values.put(TV_STREAM_URL, preferences.getString(TV_STREAM_URL, ""));
        values.put(UPDATE_MANIFEST_URL, preferences.getString(UPDATE_MANIFEST_URL, ""));
        values.put(UPDATE_CHANNEL, preferences.getString(UPDATE_CHANNEL, "stable"));
        values.put(SCREEN_TIMEOUT_MINUTES, preferences.getString(SCREEN_TIMEOUT_MINUTES, "10"));
        values.put(LIBRARY_KEYCODE, preferences.getString(LIBRARY_KEYCODE, String.valueOf(DEFAULT_LIBRARY_KEYCODE)));
        values.put(LIVE_TV_KEYCODE, preferences.getString(LIVE_TV_KEYCODE, String.valueOf(DEFAULT_LIVE_TV_KEYCODE)));
        values.put(GAMES_KEYCODE, preferences.getString(GAMES_KEYCODE, String.valueOf(DEFAULT_GAMES_KEYCODE)));
        values.put(GAMES_ENABLED, String.valueOf(preferences.getBoolean(GAMES_ENABLED, DEFAULT_GAMES_ENABLED)));
        return values;
    }

    static boolean validUrl(String value, boolean emptyAllowed) {
        String candidate = value == null ? "" : value.trim();
        if (candidate.isEmpty()) return emptyAllowed;
        try {
            URI uri = URI.create(candidate);
            return uri.getHost() != null
                    && ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()));
        } catch (IllegalArgumentException ignored) { return false; }
    }

    static boolean validChannel(String value) {
        return value != null && value.matches("[A-Za-z0-9._-]{1,32}");
    }

    static boolean validTimeoutMinutes(int minutes) { return minutes >= 1 && minutes <= 120; }

    static boolean validKeyCode(int value) { return value >= 0 && value <= 400; }

    static int keyCodeOrDefault(String value, int defaultValue) {
        try { int parsed = Integer.parseInt(value); return validKeyCode(parsed) ? parsed : defaultValue; }
        catch (Exception ignored) { return defaultValue; }
    }

    static String trimUrl(String value) { return value.trim().replaceAll("/+$", ""); }
}
