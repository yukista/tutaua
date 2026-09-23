package com.yukista.tutaua.box;

import android.content.Context;
import android.content.SharedPreferences;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

final class BoxConfig {
    static final String FLEET_URL = "manager.base_url";
    static final String FLEET_LAN = "manager.lan_address";
    static final String LOCAL_JELLYFIN = "http://192.168.1.139:8096";
    static final String LOCAL_TV = "http://192.168.1.139:8092";
    static final String REMOTE_JELLYFIN = "https://tutaua-demo.duckdns.org";
    static final String REMOTE_TV = "https://tutaua-app.duckdns.org/tv";
    static final String DEFAULT_FLEET = "https://tutaua-app.duckdns.org/control";
    static final String JELLYFIN_URL = "jellyfin.base_url";
    static final String JELLYFIN_USER = "jellyfin.username";
    static final String TV_API_URL = "tv.api_base_url";
    static final String TV_STREAM_URL = "tv.stream_url";
    static final String TDT_CATALOG = "tdt.catalog";
    static final String UPDATE_MANIFEST_URL = "updates.manifest_url";
    static final String UPDATE_CHANNEL = "updates.channel";
    static final String SCREEN_TIMEOUT_MINUTES = "device.screen_timeout_minutes";
    static final String LIBRARY_KEYCODE = "remote.library_keycode";
    static final String LIVE_TV_KEYCODE = "remote.live_tv_keycode";
    static final String GAMES_KEYCODE = "remote.games_keycode";
    static final String GAMES_ENABLED = "games.enabled";
    static final String ADMIN_PIN = "admin.pin";
    static final int DEFAULT_LIBRARY_KEYCODE = 132; // KEYCODE_F2 on the validated TX5 remote.
    static final int DEFAULT_LIVE_TV_KEYCODE = 134; // KEYCODE_F4: physical YouTube button on the validated TX5 remote.
    static final int DEFAULT_GAMES_KEYCODE = 0;
    static final boolean DEFAULT_GAMES_ENABLED = true;
    static final String DEFAULT_ADMIN_PIN = "0000";
    private static final String STORE = "box_config_v1";

    private BoxConfig() {}

    static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(STORE, Context.MODE_PRIVATE);
    }

    static Map<String, String> values(Context context) {
        SharedPreferences preferences = preferences(context);
        Map<String, String> values = new LinkedHashMap<>();
        values.put(FLEET_URL, preferences.getString(FLEET_URL, ""));
        values.put(FLEET_LAN, preferences.getString(FLEET_LAN, ""));
        values.put(JELLYFIN_URL, preferences.getString(JELLYFIN_URL, ""));
        values.put(JELLYFIN_USER, preferences.getString(JELLYFIN_USER, ""));
        values.put(TV_API_URL, preferences.getString(TV_API_URL, ""));
        values.put(TV_STREAM_URL, preferences.getString(TV_STREAM_URL, ""));
        values.put(TDT_CATALOG, preferences.getString(TDT_CATALOG, ""));
        values.put(UPDATE_MANIFEST_URL, preferences.getString(UPDATE_MANIFEST_URL, ""));
        values.put(UPDATE_CHANNEL, preferences.getString(UPDATE_CHANNEL, "stable"));
        values.put(SCREEN_TIMEOUT_MINUTES, preferences.getString(SCREEN_TIMEOUT_MINUTES, "10"));
        values.put(LIBRARY_KEYCODE, preferences.getString(LIBRARY_KEYCODE, String.valueOf(DEFAULT_LIBRARY_KEYCODE)));
        values.put(LIVE_TV_KEYCODE, preferences.getString(LIVE_TV_KEYCODE, String.valueOf(DEFAULT_LIVE_TV_KEYCODE)));
        values.put(GAMES_KEYCODE, preferences.getString(GAMES_KEYCODE, String.valueOf(DEFAULT_GAMES_KEYCODE)));
        values.put(GAMES_ENABLED, String.valueOf(preferences.getBoolean(GAMES_ENABLED, DEFAULT_GAMES_ENABLED)));
        values.put(ADMIN_PIN, preferences.getString(ADMIN_PIN, DEFAULT_ADMIN_PIN));
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

    static boolean validFleetUrl(String value) {
        if (value.trim().isEmpty()) return true; // Preserve the enrolled server.
        try {
            URI uri = URI.create(value.trim());
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                    && uri.getUserInfo() == null && uri.getQuery() == null && uri.getFragment() == null;
        } catch (IllegalArgumentException error) { return false; }
    }

    static boolean validLanAddress(String value) {
        if (value.trim().isEmpty()) return true;
        String[] parts = value.trim().split("\\.");
        if (parts.length != 4) return false;
        try {
            int[] octets = new int[4];
            for (int i=0;i<4;i++) { octets[i]=Integer.parseInt(parts[i]); if(octets[i]<0||octets[i]>255)return false; }
            return octets[0]==10 || octets[0]==192&&octets[1]==168 || octets[0]==172&&octets[1]>=16&&octets[1]<=31;
        } catch (NumberFormatException error) { return false; }
    }

    static void notifyConfiguration(android.content.Context context) {
        context.getContentResolver().notifyChange(ConfigProvider.URI, null);
        context.sendBroadcast(new android.content.Intent("com.yukista.tutaua.manager.action.CONFIG_CHANGED")
                .setPackage("com.yukista.tutaua.manager"), "com.yukista.tutaua.permission.MANAGE_BOX");
    }

    static boolean validChannel(String value) {
        return value != null && value.matches("[A-Za-z0-9._-]{1,32}");
    }

    static boolean validUsername(String value) {
        String candidate = value == null ? "" : value.trim();
        return candidate.length() <= 64 && !candidate.matches(".*[\\p{Cntrl}].*");
    }

    static boolean validPassword(String value) {
        return value == null || value.length() <= 256;
    }

    static boolean validTimeoutMinutes(int minutes) { return minutes >= 1 && minutes <= 120; }

    static boolean validKeyCode(int value) { return value >= 0 && value <= 400; }

    static boolean validAdminPin(String value) {
        return value != null && value.matches("\\d{4,8}");
    }

    static int keyCodeOrDefault(String value, int defaultValue) {
        try { int parsed = Integer.parseInt(value); return validKeyCode(parsed) ? parsed : defaultValue; }
        catch (Exception ignored) { return defaultValue; }
    }

    static String trimUrl(String value) { return value.trim().replaceAll("/+$", ""); }
}
