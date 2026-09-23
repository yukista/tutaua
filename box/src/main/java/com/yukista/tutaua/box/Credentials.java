package com.yukista.tutaua.box;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Base64;

import java.nio.charset.StandardCharsets;

/**
 * Hands the configured Jellyfin credentials to the Tutaua app.
 *
 * The app owns the session token; the Box only keeps the encrypted password so
 * it can re-authenticate after a reboot or when the credentials change. The
 * payload travels through signature-protected IPC and is never exposed through
 * the public configuration provider.
 */
final class Credentials {
    static final String ACTION_APPLY = "com.yukista.tutaua.action.APPLY_CREDENTIALS";
    static final String ACTION_REQUEST = "com.yukista.tutaua.box.action.REQUEST_CREDENTIALS";
    static final String APP_PACKAGE = "com.yukista.tutaua";

    private Credentials() {}

    static Bundle available(Context context) {
        String server = BoxConfig.preferences(context).getString(BoxConfig.JELLYFIN_URL, "").trim();
        String username = BoxConfig.preferences(context).getString(BoxConfig.JELLYFIN_USER, "").trim();
        String password = BoxCredentials.read(context);
        if (server.isEmpty() || username.isEmpty() || password.isEmpty()) return null;
        Bundle bundle = new Bundle();
        bundle.putString("server", server);
        bundle.putString("username", username);
        bundle.putString("password", password);
        return bundle;
    }

    static void push(Context context) {
        Bundle bundle = available(context);
        if (bundle == null) return;
        Intent intent = new Intent(ACTION_APPLY).setPackage(APP_PACKAGE)
                .putExtra("server_b64", encode(bundle.getString("server")))
                .putExtra("username_b64", encode(bundle.getString("username")))
                .putExtra("password_b64", encode(bundle.getString("password")));
        context.sendBroadcast(intent);
    }

    static String encode(String value) {
        return Base64.encodeToString(value.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
    }
}
