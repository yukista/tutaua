package com.yukista.tutaua.manager;

import android.content.Context;

import org.json.JSONObject;

import java.util.Map;

/**
 * Snapshot of the configuration the Box is actually using.
 *
 * The values are read from the Box content provider, so Fleet sees the real
 * applied state instead of an echo of the last desired configuration.
 * Local-only and large keys (admin pin, TDT catalog) are never reported.
 */
final class ReportedConfig {
    private static final String[] KEYS = {
            "jellyfin.base_url",
            "jellyfin.username",
            "tv.api_base_url",
            "tv.stream_url",
            "manager.base_url",
            "manager.lan_address",
            "updates.channel",
            "updates.manifest_url",
            "device.screen_timeout_minutes",
            "remote.library_keycode",
            "remote.live_tv_keycode",
            "remote.games_keycode",
            "games.enabled",
    };

    static JSONObject collect(Context context, ManagerStore store) throws Exception {
        Map<String, String> box = BoxConfigClient.read(context);
        JSONObject config = new JSONObject();
        for (String key : KEYS) {
            String value = box.get(key);
            if (value != null) config.put(key, value);
        }
        int heartbeat = store.heartbeatSeconds();
        if (heartbeat >= 30) config.put("manager.heartbeat_seconds", heartbeat);
        return config;
    }

    private ReportedConfig() { }
}
