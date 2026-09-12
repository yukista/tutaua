package com.yukista.tutaua.box;

import android.content.BroadcastReceiver;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONObject;

import java.util.Map;

public final class RemoteConfigReceiver extends BroadcastReceiver {
    private static final String ACTION = "com.yukista.tutaua.box.action.APPLY_REMOTE_CONFIG";
    @Override public void onReceive(Context context, Intent intent) {
        if (!ACTION.equals(intent.getAction())) return;
        try {
            JSONObject incoming = new JSONObject(intent.getStringExtra("config"));
            Map<String, String> current = BoxConfig.values(context);
            String fleet = value(incoming, BoxConfig.FLEET_URL, current);
            String fleetLan = value(incoming, BoxConfig.FLEET_LAN, current);
            String jellyfin = value(incoming, BoxConfig.JELLYFIN_URL, current);
            String tvApi = value(incoming, BoxConfig.TV_API_URL, current);
            String tvStream = value(incoming, BoxConfig.TV_STREAM_URL, current);
            String tdtCatalog = catalog(incoming, current);
            String updates = value(incoming, BoxConfig.UPDATE_MANIFEST_URL, current);
            String channel = value(incoming, BoxConfig.UPDATE_CHANNEL, current);
            int timeout = Integer.parseInt(value(incoming, BoxConfig.SCREEN_TIMEOUT_MINUTES, current));
            int library = Integer.parseInt(value(incoming, BoxConfig.LIBRARY_KEYCODE, current));
            int liveTv = Integer.parseInt(value(incoming, BoxConfig.LIVE_TV_KEYCODE, current));
            int games = Integer.parseInt(value(incoming, BoxConfig.GAMES_KEYCODE, current));
            boolean gamesEnabled = Boolean.parseBoolean(value(incoming, BoxConfig.GAMES_ENABLED, current));
            if (!BoxConfig.validFleetUrl(fleet) || !BoxConfig.validLanAddress(fleetLan) || !BoxConfig.validUrl(jellyfin, false) || !BoxConfig.validUrl(tvApi, false)
                    || !BoxConfig.validUrl(tvStream, true) || !BoxConfig.validUrl(updates, true)
                    || !BoxConfig.validChannel(channel) || !BoxConfig.validTimeoutMinutes(timeout)
                    || !BoxConfig.validKeyCode(library) || !BoxConfig.validKeyCode(liveTv) || !BoxConfig.validKeyCode(games)
                    || duplicate(library, liveTv, games)) throw new IllegalArgumentException("invalid configuration");
            SharedPreferences.Editor editor = BoxConfig.preferences(context).edit()
                    .putString(BoxConfig.FLEET_URL, BoxConfig.trimUrl(fleet))
                    .putString(BoxConfig.FLEET_LAN, fleetLan.trim())
                    .putString(BoxConfig.JELLYFIN_URL, BoxConfig.trimUrl(jellyfin))
                    .putString(BoxConfig.TV_API_URL, BoxConfig.trimUrl(tvApi))
                    .putString(BoxConfig.TV_STREAM_URL, tvStream.trim())
                    .putString(BoxConfig.TDT_CATALOG, tdtCatalog)
                    .putString(BoxConfig.UPDATE_MANIFEST_URL, updates.trim())
                    .putString(BoxConfig.UPDATE_CHANNEL, channel)
                    .putString(BoxConfig.SCREEN_TIMEOUT_MINUTES, String.valueOf(timeout))
                    .putString(BoxConfig.LIBRARY_KEYCODE, String.valueOf(library))
                    .putString(BoxConfig.LIVE_TV_KEYCODE, String.valueOf(liveTv))
                    .putString(BoxConfig.GAMES_KEYCODE, String.valueOf(games))
                    .putBoolean(BoxConfig.GAMES_ENABLED, gamesEnabled);
            if (!editor.commit() || !DeviceSettings.applyScreenTimeoutMinutes(timeout))
                throw new IllegalStateException("apply failed");
            BoxConfig.notifyConfiguration(context);
            setResultCode(Activity.RESULT_OK);
            Log.i("TUTAUA_BOX_REMOTE", "configuration SUCCESS version=" + intent.getIntExtra("version", 0));
        } catch (Exception error) {
            setResultCode(Activity.RESULT_CANCELED);
            Log.e("TUTAUA_BOX_REMOTE", "configuration ERROR " + error.getClass().getSimpleName());
        }
        finally { intent.replaceExtras(new android.os.Bundle()); }
    }
    private static String value(JSONObject source, String key, Map<String, String> fallback) {
        return source.has(key) ? source.optString(key, "") : fallback.get(key);
    }
    private static String catalog(JSONObject source, Map<String, String> fallback) {
        if (!source.has("tutaua_tdt")) return fallback.get(BoxConfig.TDT_CATALOG);
        JSONObject catalog = source.optJSONObject("tutaua_tdt");
        if (catalog == null) throw new IllegalArgumentException("invalid_tdt_catalog");
        return catalog.toString();
    }
    private static boolean duplicate(int first, int second, int third) {
        return first != 0 && (first == second || first == third) || second != 0 && second == third;
    }
}
