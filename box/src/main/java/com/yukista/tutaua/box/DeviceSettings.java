package com.yukista.tutaua.box;

import android.content.Context;

final class DeviceSettings {
    private DeviceSettings() {}

    static int configuredScreenTimeoutMinutes(Context context) {
        String value = BoxConfig.preferences(context).getString(BoxConfig.SCREEN_TIMEOUT_MINUTES, "10");
        try { return Math.max(1, Math.min(120, Integer.parseInt(value))); }
        catch (NumberFormatException ignored) { return 10; }
    }

    static boolean applyScreenTimeoutMinutes(int minutes) {
        if (minutes < 1 || minutes > 120) return false;
        try {
            Process process = new ProcessBuilder("/system/bin/su", "-c",
                    "settings put system screen_off_timeout " + (minutes * 60000L))
                    .redirectErrorStream(true).start();
            return process.waitFor() == 0;
        } catch (Exception ignored) { return false; }
    }
}
