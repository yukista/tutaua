package com.yukista.tutaua;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** ADB-only bootstrap entry point. The manifest permission prevents invocation by ordinary apps. */
public final class ProvisioningReceiver extends BroadcastReceiver {
    static final String ACTION = "com.yukista.tutaua.action.PROVISION";
    private static final String TAG = "TUTAUA_PROVISION";

    @Override public void onReceive(Context context, Intent intent) {
        if (!ACTION.equals(intent.getAction())) return;
        PendingResult pending = goAsync();
        String requestId = intent.getStringExtra("request_id");
        String server = decode(intent.getStringExtra("server_b64")).trim().replaceAll("/+$", "");
        String username = decode(intent.getStringExtra("username_b64")).trim();
        String password = decode(intent.getStringExtra("password_b64"));
        intent.replaceExtras(new android.os.Bundle());

        new Thread(() -> {
            try {
                if (requestId == null || !requestId.matches("[A-Za-z0-9-]{8,64}"))
                    throw new IllegalArgumentException("invalid_request");
                if (!ServerAddressPolicy.isSecureOrLocal(server) || username.isEmpty())
                    throw new IllegalArgumentException("invalid_configuration");

                SharedPreferences preferences = context.getSharedPreferences("finity", Context.MODE_PRIVATE);
                String deviceId = preferences.getString("device_id", "");
                if (deviceId.isEmpty()) deviceId = UUID.randomUUID().toString();
                String authorization = "MediaBrowser Client=\"" + AppIdentity.NAME
                        + "\", Device=\"Tanix TX5\", DeviceId=\"" + deviceId
                        + "\", Version=\"" + AppIdentity.VERSION + "\"";
                JSONObject body = new JSONObject().put("Username", username).put("Pw", password);
                JSONObject response = JellyfinClient.request(server + "/Users/AuthenticateByName",
                        "POST", body.toString(), null, authorization);
                String token = response.getString("AccessToken");
                String userId = response.getJSONObject("User").getString("Id");
                if (!new SecureTokenStore(preferences).write(token)) throw new Exception("token_store_failed");
                if (!preferences.edit().putString("server", server).putString("userId", userId)
                        .putString("username", username).putString("device_id", deviceId).commit())
                    throw new Exception("preferences_failed");
                Log.i(TAG, requestId + " SUCCESS");
            } catch (Exception error) {
                Log.e(TAG, safe(requestId) + " ERROR " + error.getClass().getSimpleName());
            } finally {
                pending.finish();
            }
        }, "tutaua-provision").start();
    }

    private static String decode(String value) {
        if (value == null) return "";
        return new String(Base64.decode(value, Base64.NO_WRAP), StandardCharsets.UTF_8);
    }

    private static String safe(String value) {
        return value != null && value.matches("[A-Za-z0-9-]{8,64}") ? value : "unknown";
    }
}
