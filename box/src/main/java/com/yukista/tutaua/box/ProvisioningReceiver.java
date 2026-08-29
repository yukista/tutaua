package com.yukista.tutaua.box;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;

import java.nio.charset.StandardCharsets;

public final class ProvisioningReceiver extends BroadcastReceiver {
    private static final String ACTION = "com.yukista.tutaua.box.action.PROVISION";
    private static final String TAG = "TUTAUA_BOX_PROVISION";

    @Override public void onReceive(Context context, Intent intent) {
        if (!ACTION.equals(intent.getAction())) return;
        String requestId = intent.getStringExtra("request_id");
        try {
            if (requestId == null || !requestId.matches("[A-Za-z0-9-]{8,64}"))
                throw new IllegalArgumentException("invalid_request");
            String jellyfin = decode(intent, "jellyfin_b64");
            String tvApi = decode(intent, "tv_api_b64");
            String tvStream = decode(intent, "tv_stream_b64");
            String updates = decode(intent, "updates_b64");
            String channel = decode(intent, "channel_b64");
            String timeoutValue = decode(intent, "screen_timeout_b64");
            String libraryKeyValue = decode(intent, "library_keycode_b64");
            String liveTvKeyValue = decode(intent, "live_tv_keycode_b64");
            String gamesKeyValue = decode(intent, "games_keycode_b64");
            String gamesEnabledValue = decode(intent, "games_enabled_b64");
            int libraryKeyCode = libraryKeyValue.isEmpty() ? BoxConfig.DEFAULT_LIBRARY_KEYCODE : Integer.parseInt(libraryKeyValue);
            int liveTvKeyCode = liveTvKeyValue.isEmpty() ? BoxConfig.DEFAULT_LIVE_TV_KEYCODE : Integer.parseInt(liveTvKeyValue);
            int gamesKeyCode = gamesKeyValue.isEmpty() ? BoxConfig.DEFAULT_GAMES_KEYCODE : Integer.parseInt(gamesKeyValue);
            boolean gamesEnabled = gamesEnabledValue.isEmpty() || Boolean.parseBoolean(gamesEnabledValue);
            int timeoutMinutes = timeoutValue.isEmpty() ? 10 : Integer.parseInt(timeoutValue);
            if (!BoxConfig.validUrl(jellyfin, false) || !BoxConfig.validUrl(tvApi, false)
                    || !BoxConfig.validUrl(tvStream, true) || !BoxConfig.validUrl(updates, true)
                    || !BoxConfig.validChannel(channel)
                    || !BoxConfig.validKeyCode(libraryKeyCode) || !BoxConfig.validKeyCode(liveTvKeyCode) || !BoxConfig.validKeyCode(gamesKeyCode)
                    || hasDuplicateNonZero(libraryKeyCode, liveTvKeyCode, gamesKeyCode)
                    || !BoxConfig.validTimeoutMinutes(timeoutMinutes))
                throw new IllegalArgumentException("invalid_configuration");
            SharedPreferences.Editor editor = BoxConfig.preferences(context).edit()
                    .putString(BoxConfig.JELLYFIN_URL, BoxConfig.trimUrl(jellyfin))
                    .putString(BoxConfig.TV_API_URL, BoxConfig.trimUrl(tvApi))
                    .putString(BoxConfig.TV_STREAM_URL, tvStream.trim())
                    .putString(BoxConfig.UPDATE_MANIFEST_URL, updates.trim())
                    .putString(BoxConfig.UPDATE_CHANNEL, channel)
                    .putString(BoxConfig.SCREEN_TIMEOUT_MINUTES, String.valueOf(timeoutMinutes))
                    .putString(BoxConfig.LIBRARY_KEYCODE, String.valueOf(libraryKeyCode))
                    .putString(BoxConfig.LIVE_TV_KEYCODE, String.valueOf(liveTvKeyCode))
                    .putString(BoxConfig.GAMES_KEYCODE, String.valueOf(gamesKeyCode));
            editor.putBoolean(BoxConfig.GAMES_ENABLED, gamesEnabled);
            if (!editor.commit()) throw new IllegalStateException("write_failed");
            if (!DeviceSettings.applyScreenTimeoutMinutes(timeoutMinutes))
                throw new IllegalStateException("screen_timeout_failed");
            context.getContentResolver().notifyChange(ConfigProvider.URI, null);
            Log.i(TAG, requestId + " SUCCESS");
        } catch (Exception error) {
            Log.e(TAG, safe(requestId) + " ERROR " + error.getClass().getSimpleName());
        } finally { intent.replaceExtras(new android.os.Bundle()); }
    }

    private static String decode(Intent intent, String key) {
        String value = intent.getStringExtra(key);
        return value == null || "-".equals(value) ? ""
                : new String(Base64.decode(value, Base64.NO_WRAP), StandardCharsets.UTF_8);
    }
    private static String safe(String value) { return value != null && value.matches("[A-Za-z0-9-]{8,64}") ? value : "unknown"; }
    private static boolean hasDuplicateNonZero(int first, int second, int third) {
        return first != 0 && (first == second || first == third) || second != 0 && second == third;
    }
}
