package com.yukista.tutaua.box;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

public final class RemoteKeyService extends AccessibilityService {
    private static final String TAG = "TUTAUA_BOX_REMOTE";
    private static volatile String currentForeground = "";

    static String foregroundPackage() { return currentForeground; }

    @Override protected void onServiceConnected() {
        AccessibilityServiceInfo info = getServiceInfo();
        info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
        setServiceInfo(info);
        Log.i(TAG, "global key routing enabled");
    }

    @Override protected boolean onKeyEvent(KeyEvent event) {
        if ("com.yukista.tutaua.games".equals(currentForeground)) return false;
        int libraryKey = BoxConfig.keyCodeOrDefault(
                BoxConfig.preferences(this).getString(BoxConfig.LIBRARY_KEYCODE, String.valueOf(BoxConfig.DEFAULT_LIBRARY_KEYCODE)),
                BoxConfig.DEFAULT_LIBRARY_KEYCODE);
        int liveTvKey = BoxConfig.keyCodeOrDefault(
                BoxConfig.preferences(this).getString(BoxConfig.LIVE_TV_KEYCODE, String.valueOf(BoxConfig.DEFAULT_LIVE_TV_KEYCODE)),
                BoxConfig.DEFAULT_LIVE_TV_KEYCODE);
        int gamesKey = BoxConfig.keyCodeOrDefault(
                BoxConfig.preferences(this).getString(BoxConfig.GAMES_KEYCODE, String.valueOf(BoxConfig.DEFAULT_GAMES_KEYCODE)),
                BoxConfig.DEFAULT_GAMES_KEYCODE);
        boolean gamesEnabled = BoxConfig.preferences(this).getBoolean(BoxConfig.GAMES_ENABLED, BoxConfig.DEFAULT_GAMES_ENABLED);
        boolean libraryButton = libraryKey != 0 && event.getKeyCode() == libraryKey;
        boolean liveTvButton = liveTvKey != 0 && event.getKeyCode() == liveTvKey;
        boolean gamesButton = gamesEnabled && gamesKey != 0 && event.getKeyCode() == gamesKey;
        if (!libraryButton && !liveTvButton && !gamesButton) return false;
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            String packageName = libraryButton ? "com.yukista.tutaua" : liveTvButton ? "tv.tutaua.app" : "com.yukista.tutaua.games";
            Intent launch = getPackageManager().getLeanbackLaunchIntentForPackage(packageName);
            if (launch == null) launch = getPackageManager().getLaunchIntentForPackage(packageName);
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(launch);
                Log.i(TAG, "global key=" + event.getKeyCode() + " destination=" + (libraryButton ? "library" : liveTvButton ? "live_tv" : "games"));
            }
        }
        return true;
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getPackageName() != null) currentForeground = event.getPackageName().toString();
    }
    @Override public void onInterrupt() { }
}
