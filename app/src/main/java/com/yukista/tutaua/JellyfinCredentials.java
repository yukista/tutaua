package com.yukista.tutaua;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Applies the Jellyfin credentials managed remotely by the Box.
 *
 * Credentials are used once to obtain a session and are never persisted by the
 * app; only the encrypted access token inside {@link SecureTokenStore} remains.
 */
final class JellyfinCredentials {
    static final String ACTION_APPLIED = "com.yukista.tutaua.action.CREDENTIALS_APPLIED";
    private static final String PREFS = "finity";
    private static final String TAG = "TUTAUA_CREDENTIALS";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    interface Callback { void onResult(boolean applied); }

    private JellyfinCredentials() {}

    static void apply(Context context, String server, String username, String password, Callback callback) {
        final Context app = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            boolean applied = false;
            try { applied = authenticate(app, server, username, password); }
            catch (Exception error) { Log.e(TAG, "apply ERROR " + error.getClass().getSimpleName()); }
            final boolean result = applied;
            MAIN.post(() -> { if (callback != null) callback.onResult(result); });
            if (result) app.sendBroadcast(new Intent(ACTION_APPLIED).setPackage(app.getPackageName()));
        });
    }

    private static boolean authenticate(Context context, String server, String username, String password) throws Exception {
        String normalized = server == null ? "" : server.trim().replaceAll("/+$", "");
        String user = username == null ? "" : username.trim();
        if (normalized.isEmpty() || user.isEmpty() || password == null) return false;
        if (!ServerAddressPolicy.isSecureOrLocal(normalized)) return false;

        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String deviceId = preferences.getString("device_id", "");
        if (deviceId.isEmpty()) deviceId = UUID.randomUUID().toString();
        SecureTokenStore store = new SecureTokenStore(preferences);

        String existing = store.read();
        if (!existing.isEmpty() && normalized.equals(preferences.getString("server", ""))
                && user.equals(preferences.getString("username", ""))
                && sessionValid(normalized, existing, deviceId)) return true;

        String authorization = "MediaBrowser Client=\"" + AppIdentity.NAME
                + "\", Device=\"Tanix TX5\", DeviceId=\"" + deviceId
                + "\", Version=\"" + AppIdentity.VERSION + "\"";
        JSONObject body = new JSONObject().put("Username", user).put("Pw", password);
        JSONObject response = JellyfinClient.request(normalized + "/Users/AuthenticateByName",
                "POST", body.toString(), null, authorization);
        String token = response.getString("AccessToken");
        String userId = response.getJSONObject("User").getString("Id");
        if (!store.write(token)) return false;
        return preferences.edit().putString("server", normalized).putString("userId", userId)
                .putString("username", user).putString("device_id", deviceId).commit();
    }

    private static boolean sessionValid(String server, String token, String deviceId) {
        try {
            String authorization = "MediaBrowser Client=\"" + AppIdentity.NAME
                    + "\", Device=\"Tanix TX5\", DeviceId=\"" + deviceId
                    + "\", Version=\"" + AppIdentity.VERSION + "\"";
            JellyfinClient.request(server + "/Users/Me", "GET", null, token, authorization);
            return true;
        } catch (Exception error) {
            return false;
        }
    }
}
