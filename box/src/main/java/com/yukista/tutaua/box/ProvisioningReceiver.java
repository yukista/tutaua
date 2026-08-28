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
            int timeoutMinutes = timeoutValue.isEmpty() ? 10 : Integer.parseInt(timeoutValue);
            if (!validUrl(jellyfin, false) || !validUrl(tvApi, false)
                    || !validUrl(tvStream, true) || !validUrl(updates, true)
                    || !channel.matches("[A-Za-z0-9._-]{1,32}")
                    || timeoutMinutes < 1 || timeoutMinutes > 120)
                throw new IllegalArgumentException("invalid_configuration");
            SharedPreferences.Editor editor = BoxConfig.preferences(context).edit()
                    .putString(BoxConfig.JELLYFIN_URL, trim(jellyfin))
                    .putString(BoxConfig.TV_API_URL, trim(tvApi))
                    .putString(BoxConfig.TV_STREAM_URL, tvStream.trim())
                    .putString(BoxConfig.UPDATE_MANIFEST_URL, updates.trim())
                    .putString(BoxConfig.UPDATE_CHANNEL, channel)
                    .putString(BoxConfig.SCREEN_TIMEOUT_MINUTES, String.valueOf(timeoutMinutes));
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
    private static boolean validUrl(String value, boolean emptyAllowed) {
        value = value.trim(); if (value.isEmpty()) return emptyAllowed;
        android.net.Uri uri = android.net.Uri.parse(value);
        return uri.getHost() != null && ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()));
    }
    private static String trim(String value) { return value.trim().replaceAll("/+$", ""); }
    private static String safe(String value) { return value != null && value.matches("[A-Za-z0-9-]{8,64}") ? value : "unknown"; }
}
