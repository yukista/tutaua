package com.yukista.tutaua.box;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Soft kiosk watchdog: when the accessibility service reports a foreground package that is not
 * part of the Tutaua appliance, bring the box home back to the front. Without device owner this
 * cannot enforce lock task, but it recovers the kiosk if an unexpected app or launcher appears.
 */
public final class WatchdogService extends Service {
    private static final String TAG = "TUTAUA_BOX_WATCHDOG";
    private static final Set<String> ALLOWED = new HashSet<>(Arrays.asList(
            "com.yukista.tutaua.box",
            "com.yukista.tutaua",
            "tv.tutaua.app",
            "com.yukista.tutaua.games",
            "com.yukista.tutaua.manager",
            "com.android.systemui",
            "com.android.settings",
            "com.android.tv.settings"
    ));
    private ScheduledExecutorService executor;

    @Override public void onCreate() {
        super.onCreate();
        executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleWithFixedDelay(this::checkForeground, 3, 10, TimeUnit.SECONDS);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override public void onDestroy() {
        if (executor != null) executor.shutdownNow();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    private void checkForeground() {
        String foreground = RemoteKeyService.foregroundPackage();
        if (foreground == null || foreground.isEmpty()) return; // accessibility not yet reporting
        if (ALLOWED.contains(foreground)) return;
        Log.w(TAG, "unexpected foreground=" + foreground + " returning to home");
        Intent home = new Intent(this, HomeActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        try {
            startActivity(home);
        } catch (Exception error) {
            Log.e(TAG, "cannot bring home to front", error);
        }
    }
}
