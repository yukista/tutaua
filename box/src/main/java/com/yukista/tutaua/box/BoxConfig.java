package com.yukista.tutaua.box;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.LinkedHashMap;
import java.util.Map;

final class BoxConfig {
    static final String JELLYFIN_URL = "jellyfin.base_url";
    static final String TV_API_URL = "tv.api_base_url";
    static final String TV_STREAM_URL = "tv.stream_url";
    static final String UPDATE_MANIFEST_URL = "updates.manifest_url";
    static final String UPDATE_CHANNEL = "updates.channel";
    static final String SCREEN_TIMEOUT_MINUTES = "device.screen_timeout_minutes";
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
        return values;
    }
}
