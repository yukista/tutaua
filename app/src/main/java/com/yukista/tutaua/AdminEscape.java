package com.yukista.tutaua;

import android.app.Activity;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.KeyEvent;
import android.widget.Toast;

/** Emergency path out of appliance mode that does not depend on ADB or the network. */
final class AdminEscape {
    private static final long HOLD_MS = 4000L;
    private final Activity activity;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean backHeld;
    private boolean opened;

    AdminEscape(Activity activity) { this.activity = activity; }

    boolean dispatch(KeyEvent event) {
        if (event.getKeyCode() != KeyEvent.KEYCODE_BACK) return false;
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            backHeld = true;
            opened = false;
            handler.postDelayed(this::openSettings, HOLD_MS);
            return false;
        }
        if (event.getAction() == KeyEvent.ACTION_UP) {
            backHeld = false;
            handler.removeCallbacksAndMessages(null);
            boolean consume = opened;
            opened = false;
            return consume;
        }
        return opened;
    }

    void cancel() {
        backHeld = false;
        opened = false;
        handler.removeCallbacksAndMessages(null);
    }

    private void openSettings() {
        if (!backHeld || activity.isFinishing()) return;
        opened = true;
        Toast.makeText(activity, "Mode administració", Toast.LENGTH_SHORT).show();
        activity.startActivity(new Intent(Settings.ACTION_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
    }
}
