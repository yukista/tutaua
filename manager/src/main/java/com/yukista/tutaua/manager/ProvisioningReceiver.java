package com.yukista.tutaua.manager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Base64;
import android.util.Log;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

public final class ProvisioningReceiver extends BroadcastReceiver {
    private static final String ACTION = "com.yukista.tutaua.manager.action.PROVISION";
    @Override public void onReceive(Context context, Intent intent) {
        if (!ACTION.equals(intent.getAction())) return;
        PendingResult pending = goAsync();
        String server = decode(intent.getStringExtra("server_b64"));
        String code = decode(intent.getStringExtra("code_b64"));
        String lanAddress = decode(intent.getStringExtra("lan_address_b64"));
        intent.replaceExtras(new android.os.Bundle());
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                ManagerStore store = new ManagerStore(context);
                if (store.enrolled()) {
                    Scheduler.schedule(context);
                    Log.i("TUTAUA_MANAGER", "enrollment ALREADY_ENROLLED");
                    return;
                }
                JSONObject response = ControlClient.enroll(context, server, lanAddress, code);
                store.enrollment(response.getString("apiBaseUrl"), lanAddress, response.getString("deviceId"), response.getString("deviceToken"));
                Scheduler.schedule(context); Log.i("TUTAUA_MANAGER", "enrollment SUCCESS");
            } catch (Exception error) { Log.e("TUTAUA_MANAGER", "enrollment ERROR " + error.getClass().getSimpleName(), error); }
            finally { pending.finish(); }
        });
    }
    private static String decode(String value) {
        if (value == null) return "";
        return new String(Base64.decode(value, Base64.NO_WRAP), StandardCharsets.UTF_8);
    }
}
