package com.yukista.tutaua.manager;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.StatFs;
import android.util.DisplayMetrics;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.Locale;
import java.util.TimeZone;

final class Inventory {
    static final String[] PACKAGES = {
            "com.yukista.tutaua.manager", "com.yukista.tutaua.box", "com.yukista.tutaua",
            "com.yukista.tutaua.tdt",
            "tv.tutaua.app", "com.yukista.tutaua.games"
    };

    static JSONObject collect(Context context) throws Exception {
        JSONObject root = new JSONObject();
        root.put("manufacturer", Build.MANUFACTURER).put("model", Build.MODEL)
                .put("device", Build.DEVICE).put("android", Build.VERSION.RELEASE)
                .put("sdk", Build.VERSION.SDK_INT).put("buildFingerprint", Build.FINGERPRINT)
                .put("uptimeMillis", android.os.SystemClock.elapsedRealtime())
                .put("locale", Locale.getDefault().toString())
                .put("timeZone", TimeZone.getDefault().getID())
                .put("connection", connectionType(context))
                .put("ipAddresses", ipAddresses());
        StatFs storage = new StatFs(context.getFilesDir().getAbsolutePath());
        root.put("storageAvailableBytes", storage.getAvailableBytes()).put("storageTotalBytes", storage.getTotalBytes());
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        root.put("screenWidthPx", metrics.widthPixels).put("screenHeightPx", metrics.heightPixels)
                .put("screenDensityDpi", metrics.densityDpi);
        ActivityManager activity = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (activity != null) {
            ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
            activity.getMemoryInfo(memory);
            root.put("memoryAvailableBytes", memory.availMem).put("memoryTotalBytes", memory.totalMem)
                    .put("lowMemory", memory.lowMemory);
        }
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

    private static JSONArray ipAddresses() throws Exception {
        JSONArray addresses = new JSONArray();
        for (NetworkInterface network : Collections.list(NetworkInterface.getNetworkInterfaces()))
            for (InetAddress address : Collections.list(network.getInetAddresses()))
                if (address instanceof Inet4Address && !address.isLoopbackAddress())
                    addresses.put(address.getHostAddress());
        return addresses;
    }

    private static String connectionType(Context context) {
        try {
            ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (manager == null) return "unknown";
            Network network = manager.getActiveNetwork();
            if (network == null) return "none";
            NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
            if (capabilities == null) return "unknown";
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return "ethernet";
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return "wifi";
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) return "cellular";
            return "other";
        } catch (RuntimeException error) { return "unknown"; }
    }

    private Inventory() {}
}
