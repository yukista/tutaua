package com.yukista.tutaua.manager;

import android.content.Context;
import android.content.Intent;

import org.json.JSONObject;

import java.util.Arrays;

final class CommandExecutor {
    static JSONObject run(Context context, JSONObject command) throws Exception {
        String kind = command.getString("kind"); JSONObject payload = command.optJSONObject("payload");
        if (payload == null) payload = new JSONObject();
        switch (kind) {
            case "sync": case "check_updates": return new JSONObject().put("accepted", true);
            case "diagnostics": return new JSONObject().put("inventory", Inventory.collect(context));
            case "restart_app": {
                String packageName = payload.getString("applicationId");
                if (!Arrays.asList(Inventory.PACKAGES).contains(packageName)) throw new SecurityException("package not allowed");
                Process process = new ProcessBuilder("su", "-c", "am force-stop " + packageName).start();
                if (process.waitFor() != 0) throw new IllegalStateException("force-stop failed");
                Intent launch = context.getPackageManager().getLaunchIntentForPackage(packageName);
                if (launch != null) { launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); context.startActivity(launch); }
                return new JSONObject().put("applicationId", packageName);
            }
            case "reboot": {
                new ProcessBuilder("su", "-c", "reboot").start(); return new JSONObject().put("accepted", true);
            }
            default: throw new SecurityException("unsupported command");
        }
    }
    private CommandExecutor() {}
}
