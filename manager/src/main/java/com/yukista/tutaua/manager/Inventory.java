package com.yukista.tutaua.manager;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.StatFs;

import org.json.JSONObject;

final class Inventory {
    static final String[] PACKAGES = {
            "com.yukista.tutaua.manager", "com.yukista.tutaua.box", "com.yukista.tutaua",
            "tv.tutaua.app", "com.yukista.tutaua.games"
    };
    static JSONObject collect(Context context) throws Exception {
        JSONObject root = new JSONObject();
        root.put("manufacturer", Build.MANUFACTURER).put("model", Build.MODEL)
                .put("device", Build.DEVICE).put("android", Build.VERSION.RELEASE)
                .put("sdk", Build.VERSION.SDK_INT).put("buildFingerprint", Build.FINGERPRINT)
                .put("uptimeMillis", android.os.SystemClock.elapsedRealtime());
        StatFs storage = new StatFs(context.getFilesDir().getAbsolutePath());
        root.put("storageAvailableBytes", storage.getAvailableBytes()).put("storageTotalBytes", storage.getTotalBytes());
        JSONObject applications = new JSONObject();
        PackageManager packages = context.getPackageManager();
        for (String name : PACKAGES) try {
            PackageInfo info = packages.getPackageInfo(name, 0);
            long versionCode = Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
            applications.put(name, new JSONObject().put("versionCode", versionCode)
                    .put("versionName", info.versionName == null ? "" : info.versionName));
        } catch (PackageManager.NameNotFoundException ignored) {}
        root.put("applications", applications);
        return root;
    }
    private Inventory() {}
}
