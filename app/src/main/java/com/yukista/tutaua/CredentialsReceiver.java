package com.yukista.tutaua;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Base64;

import java.nio.charset.StandardCharsets;

/** Receives remotely managed credentials from the Box and signs in automatically. */
public final class CredentialsReceiver extends BroadcastReceiver {
    static final String ACTION = "com.yukista.tutaua.action.APPLY_CREDENTIALS";

    @Override public void onReceive(Context context, Intent intent) {
        if (!ACTION.equals(intent.getAction())) return;
        PendingResult pending = goAsync();
        String server = decode(intent.getStringExtra("server_b64"));
        String username = decode(intent.getStringExtra("username_b64"));
        String password = decode(intent.getStringExtra("password_b64"));
        intent.replaceExtras(new android.os.Bundle());
        JellyfinCredentials.apply(context, server, username, password, applied -> pending.finish());
    }

    private static String decode(String value) {
        if (value == null || value.isEmpty()) return "";
        return new String(Base64.decode(value, Base64.NO_WRAP), StandardCharsets.UTF_8);
    }
}
